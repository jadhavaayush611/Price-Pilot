package com.pricepilot.intelligence.dashboard;

import com.pricepilot.intelligence.dashboard.controller.DashboardV2Controller;
import com.pricepilot.intelligence.dashboard.dto.DashboardOverviewDTO;
import com.pricepilot.intelligence.dashboard.dto.DashboardV2ResponseDTO;
import com.pricepilot.intelligence.dashboard.service.DashboardV2Service;
import com.pricepilot.security.UserPrincipal;
import com.pricepilot.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardV2ControllerTest {

    @Mock
    private DashboardV2Service dashboardV2Service;

    private DashboardV2Controller controller;

    private UUID userId;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        controller = new DashboardV2Controller(dashboardV2Service);
        userId = UUID.randomUUID();
        principal = new UserPrincipal(userId, "shopper@pricepilot.io", "password", Role.USER, true, false);
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/v2 returns 200 OK with aggregated intelligence for authenticated user")
    void testGetDashboardV2Success() {
        DashboardV2ResponseDTO mockResponse = DashboardV2ResponseDTO.builder()
                .overview(DashboardOverviewDTO.builder()
                        .activeWatchlistsCount(3)
                        .unreadAlertsCount(1)
                        .build())
                .attentionItems(Collections.emptyList())
                .priceOpportunities(Collections.emptyList())
                .watchedProducts(Collections.emptyList())
                .recentAlerts(Collections.emptyList())
                .recentActivity(Collections.emptyList())
                .generatedAt(LocalDateTime.now())
                .build();

        when(dashboardV2Service.getDashboardV2(userId, "shopper@pricepilot.io")).thenReturn(mockResponse);

        ResponseEntity<DashboardV2ResponseDTO> response = controller.getDashboardV2(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().getOverview().getActiveWatchlistsCount());
        assertEquals(1L, response.getBody().getOverview().getUnreadAlertsCount());
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/v2 rejects unauthenticated request with AccessDeniedException")
    void testUnauthenticatedAccessRejected() {
        assertThrows(AccessDeniedException.class, () -> controller.getDashboardV2(null));
        verify(dashboardV2Service, never()).getDashboardV2(any(), any());
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/v2 strictly binds to authenticated principal without parameter override")
    void testPrincipalStrictBindingAndFullContract() {
        UUID authenticatedUserId = UUID.randomUUID();
        String userEmail = "secure.shopper@pricepilot.io";
        UserPrincipal authPrincipal = new UserPrincipal(authenticatedUserId, userEmail, "pass", Role.USER, true, false);

        LocalDateTime now = LocalDateTime.now();
        DashboardV2ResponseDTO contractResponse = DashboardV2ResponseDTO.builder()
                .overview(DashboardOverviewDTO.builder().activeWatchlistsCount(5).unreadAlertsCount(2L).build())
                .attentionItems(Collections.emptyList())
                .priceOpportunities(Collections.emptyList())
                .watchedProducts(Collections.emptyList())
                .recentAlerts(Collections.emptyList())
                .recommendations(null)
                .recentActivity(Collections.emptyList())
                .generatedAt(now)
                .build();

        when(dashboardV2Service.getDashboardV2(authenticatedUserId, userEmail)).thenReturn(contractResponse);

        ResponseEntity<DashboardV2ResponseDTO> response = controller.getDashboardV2(authPrincipal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        DashboardV2ResponseDTO body = response.getBody();
        assertNotNull(body);
        // Verify all 8 core contract keys exist on the response
        assertNotNull(body.getOverview());
        assertNotNull(body.getAttentionItems());
        assertNotNull(body.getPriceOpportunities());
        assertNotNull(body.getWatchedProducts());
        assertNotNull(body.getRecentAlerts());
        assertNotNull(body.getRecentActivity());
        assertNotNull(body.getGeneratedAt());

        // Verify strictly bound to authenticated principal
        verify(dashboardV2Service).getDashboardV2(authenticatedUserId, userEmail);
        verifyNoMoreInteractions(dashboardV2Service);
    }
}
