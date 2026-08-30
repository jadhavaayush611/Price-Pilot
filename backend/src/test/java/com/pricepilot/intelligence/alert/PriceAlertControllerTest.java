package com.pricepilot.intelligence.alert;

import com.pricepilot.intelligence.alert.controller.PriceAlertController;
import com.pricepilot.intelligence.alert.dto.PriceAlertResponseDTO;
import com.pricepilot.intelligence.alert.model.AlertType;
import com.pricepilot.intelligence.alert.service.PriceAlertService;
import com.pricepilot.security.UserPrincipal;
import com.pricepilot.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceAlertControllerTest {

    @Mock
    private PriceAlertService priceAlertService;

    private PriceAlertController controller;

    private UUID userId;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        controller = new PriceAlertController(priceAlertService);
        userId = UUID.randomUUID();
        principal = new UserPrincipal(userId, "user@example.com", "password", Role.USER, true, false);
    }

    @Test
    @DisplayName("GET /api/v1/alerts returns paginated user alerts")
    void testGetUserAlerts() {
        PriceAlertResponseDTO alert = PriceAlertResponseDTO.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .alertType(AlertType.PRICE_DROP)
                .title("Price Drop")
                .message("Dropped 10%")
                .observedValue(BigDecimal.valueOf(10.0))
                .build();

        when(priceAlertService.getUserAlerts(eq(userId), any())).thenReturn(new PageImpl<>(List.of(alert)));

        var response = controller.getUserAlerts(principal, PageRequest.of(0, 10));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getContent().size());
    }

    @Test
    @DisplayName("GET /api/v1/alerts/unread returns unread list")
    void testGetUnreadAlerts() {
        PriceAlertResponseDTO alert = PriceAlertResponseDTO.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .alertType(AlertType.PRICE_TARGET_REACHED)
                .read(false)
                .build();

        when(priceAlertService.getUnreadAlerts(userId)).thenReturn(List.of(alert));

        var response = controller.getUnreadAlerts(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    @DisplayName("GET /api/v1/alerts/unread/count returns unread count map")
    void testGetUnreadCount() {
        when(priceAlertService.getUnreadCount(userId)).thenReturn(3L);

        var response = controller.getUnreadCount(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(3L, response.getBody().get("unreadCount"));
    }

    @Test
    @DisplayName("PATCH /api/v1/alerts/{id}/read marks alert as read")
    void testMarkAsRead() {
        UUID alertId = UUID.randomUUID();
        PriceAlertResponseDTO alert = PriceAlertResponseDTO.builder()
                .id(alertId)
                .userId(userId)
                .read(true)
                .readAt(LocalDateTime.now())
                .build();

        when(priceAlertService.markAsRead(alertId, userId)).thenReturn(alert);

        var response = controller.markAsRead(alertId, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isRead());
    }

    @Test
    @DisplayName("PATCH /api/v1/alerts/read-all marks all unread alerts read")
    void testMarkAllAsRead() {
        when(priceAlertService.markAllAsRead(userId)).thenReturn(5);

        var response = controller.markAllAsRead(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(5, response.getBody().get("markedCount"));
    }

    @Test
    @DisplayName("Unauthenticated request throws AccessDeniedException")
    void testUnauthenticatedAccess() {
        assertThrows(AccessDeniedException.class, () -> controller.getUserAlerts(null, PageRequest.of(0, 10)));
        assertThrows(AccessDeniedException.class, () -> controller.getUnreadAlerts(null));
        assertThrows(AccessDeniedException.class, () -> controller.getUnreadCount(null));
    }
}
