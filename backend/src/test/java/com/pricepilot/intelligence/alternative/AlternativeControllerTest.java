package com.pricepilot.intelligence.alternative;

import com.pricepilot.intelligence.alternative.model.*;
import com.pricepilot.intelligence.alternative.service.AlternativeFinderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class AlternativeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlternativeFinderService alternativeFinderService;

    @Test
    @DisplayName("GET /api/v1/alternatives/product/{productId} returns 200 OK")
    void testGetAlternativesForProduct() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID altId = UUID.randomUUID();

        AlternativeResponseDTO response = AlternativeResponseDTO.builder()
                .executionMode(AlternativeMode.PRODUCT)
                .alternativeType(AlternativeType.SIMILAR)
                .totalFound(1)
                .content(List.of(
                        AlternativeProductDTO.builder()
                                .id(altId)
                                .name("Alternative Product")
                                .currentBestPrice(BigDecimal.valueOf(199.99))
                                .alternativeScore(85.0)
                                .build()
                ))
                .build();

        when(alternativeFinderService.findAlternativesForProduct(eq(productId), any(AlternativeRequest.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/alternatives/product/" + productId)
                        .param("type", "SIMILAR")
                        .param("limit", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionMode").value("PRODUCT"))
                .andExpect(jsonPath("$.alternativeType").value("SIMILAR"))
                .andExpect(jsonPath("$.totalFound").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Alternative Product"));
    }

    @Test
    @DisplayName("GET /api/v1/alternatives/query returns 200 OK")
    void testGetAlternativesForQuery() throws Exception {
        AlternativeResponseDTO response = AlternativeResponseDTO.builder()
                .executionMode(AlternativeMode.QUERY)
                .alternativeType(AlternativeType.CHEAPER)
                .query("gaming mouse")
                .totalFound(1)
                .content(List.of(
                        AlternativeProductDTO.builder()
                                .id(UUID.randomUUID())
                                .name("Affordable Gaming Mouse")
                                .currentBestPrice(BigDecimal.valueOf(29.99))
                                .build()
                ))
                .build();

        when(alternativeFinderService.findAlternativesForQuery(any(AlternativeRequest.class)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/alternatives/query")
                        .param("query", "gaming mouse")
                        .param("type", "CHEAPER")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionMode").value("QUERY"))
                .andExpect(jsonPath("$.alternativeType").value("CHEAPER"))
                .andExpect(jsonPath("$.content[0].name").value("Affordable Gaming Mouse"));
    }

    @Test
    @DisplayName("GET /api/v1/alternatives/natural-language returns 200 OK")
    void testGetNaturalLanguageAlternatives() throws Exception {
        AlternativeResponseDTO response = AlternativeResponseDTO.builder()
                .executionMode(AlternativeMode.QUERY)
                .alternativeType(AlternativeType.CHEAPER)
                .query("cheaper alternative to apple airpods pro")
                .totalFound(1)
                .content(List.of(
                        AlternativeProductDTO.builder()
                                .id(UUID.randomUUID())
                                .name("Nothing Ear (a)")
                                .currentBestPrice(BigDecimal.valueOf(99.00))
                                .build()
                ))
                .build();

        when(alternativeFinderService.findAlternativesForNaturalLanguage(eq("cheaper alternative to apple airpods pro"), any(), eq(10)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/alternatives/natural-language")
                        .param("q", "cheaper alternative to apple airpods pro")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionMode").value("QUERY"))
                .andExpect(jsonPath("$.alternativeType").value("CHEAPER"))
                .andExpect(jsonPath("$.content[0].name").value("Nothing Ear (a)"));
    }

    @MockitoBean
    private com.pricepilot.intelligence.alternative.personalized.PersonalizedAlternativeService personalizedAlternativeService;

    @Test
    @DisplayName("GET /api/v1/alternatives/product/{productId} with personalized=true without authentication throws AccessDenied")
    void testPersonalizedProductAlternativesWithoutAuth() throws Exception {
        UUID productId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/alternatives/product/" + productId)
                        .param("personalized", "true")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/alternatives/product/{productId}/personalized without authentication throws AccessDenied")
    void testDedicatedPersonalizedProductAlternativesWithoutAuth() throws Exception {
        UUID productId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/alternatives/product/" + productId + "/personalized")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/alternatives/query with personalized=true without authentication throws AccessDenied")
    void testPersonalizedQueryAlternativesWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/alternatives/query")
                        .param("query", "laptop")
                        .param("personalized", "true")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/alternatives/query/personalized without authentication throws AccessDenied")
    void testDedicatedPersonalizedQueryAlternativesWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/alternatives/query/personalized")
                        .param("query", "laptop")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/alternatives/natural-language with personalized=true without authentication throws AccessDenied")
    void testPersonalizedNaturalLanguageWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/alternatives/natural-language")
                        .param("query", "laptop")
                        .param("personalized", "true")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
