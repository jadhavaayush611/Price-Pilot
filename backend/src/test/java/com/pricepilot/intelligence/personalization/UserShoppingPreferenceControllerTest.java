package com.pricepilot.intelligence.personalization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricepilot.intelligence.personalization.preference.AvailabilityPreference;
import com.pricepilot.intelligence.personalization.preference.DealSensitivity;
import com.pricepilot.intelligence.personalization.preference.PriceSensitivity;
import com.pricepilot.intelligence.personalization.preference.UserShoppingPreferenceController;
import com.pricepilot.intelligence.personalization.preference.UserShoppingPreferenceService;
import com.pricepilot.intelligence.personalization.preference.dto.UpdateShoppingPreferenceRequest;
import com.pricepilot.intelligence.personalization.preference.dto.UserShoppingPreferenceDTO;
import com.pricepilot.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserShoppingPreferenceControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserShoppingPreferenceService preferenceService;

    @InjectMocks
    private UserShoppingPreferenceController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID testUserId;
    private UserPrincipal testPrincipal;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testPrincipal = new UserPrincipal(
                testUserId,
                "shopper@pricepilot.com",
                "Password123!",
                com.pricepilot.user.Role.USER,
                true,
                false
        );

        HandlerMethodArgumentResolver authPrincipalResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                        && parameter.getParameterType().equals(UserPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                return testPrincipal;
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(authPrincipalResolver)
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/users/preferences returns authenticated user preferences")
    void testGetPreferences() throws Exception {
        UserShoppingPreferenceDTO dto = UserShoppingPreferenceDTO.builder()
                .userId(testUserId)
                .preferredCategories(Set.of("Electronics"))
                .preferredBrands(Set.of("Sony"))
                .dealSensitivity(DealSensitivity.HIGH)
                .priceSensitivity(PriceSensitivity.MEDIUM)
                .availabilityPreference(AvailabilityPreference.IN_STOCK_ONLY)
                .build();

        when(preferenceService.getPreferences(testUserId)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/users/preferences")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(testUserId.toString()))
                .andExpect(jsonPath("$.preferredCategories[0]").value("Electronics"))
                .andExpect(jsonPath("$.dealSensitivity").value("HIGH"));

        verify(preferenceService).getPreferences(testUserId);
    }

    @Test
    @DisplayName("PUT /api/v1/users/preferences updates preferences for authenticated user")
    void testUpdatePreferences() throws Exception {
        UpdateShoppingPreferenceRequest request = UpdateShoppingPreferenceRequest.builder()
                .preferredCategories(Set.of("Audio"))
                .minBudget(BigDecimal.valueOf(100))
                .maxBudget(BigDecimal.valueOf(500))
                .dealSensitivity(DealSensitivity.HIGH)
                .build();

        UserShoppingPreferenceDTO dto = UserShoppingPreferenceDTO.builder()
                .userId(testUserId)
                .preferredCategories(Set.of("Audio"))
                .minBudget(BigDecimal.valueOf(100))
                .maxBudget(BigDecimal.valueOf(500))
                .build();

        when(preferenceService.updatePreferences(eq(testUserId), any())).thenReturn(dto);

        mockMvc.perform(put("/api/v1/users/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredCategories[0]").value("Audio"))
                .andExpect(jsonPath("$.minBudget").value(100));

        verify(preferenceService).updatePreferences(eq(testUserId), any());
    }

    @Test
    @DisplayName("DELETE /api/v1/users/preferences resets user preferences to defaults")
    void testResetPreferences() throws Exception {
        mockMvc.perform(delete("/api/v1/users/preferences"))
                .andExpect(status().isNoContent());

        verify(preferenceService).resetPreferences(testUserId);
    }
}
