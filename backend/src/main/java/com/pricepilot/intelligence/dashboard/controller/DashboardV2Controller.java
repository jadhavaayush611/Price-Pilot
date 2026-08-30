package com.pricepilot.intelligence.dashboard.controller;

import com.pricepilot.intelligence.dashboard.dto.DashboardV2ResponseDTO;
import com.pricepilot.intelligence.dashboard.service.DashboardV2Service;
import com.pricepilot.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard/v2")
@CrossOrigin(origins = "*")
public class DashboardV2Controller {

    private final DashboardV2Service dashboardV2Service;

    public DashboardV2Controller(DashboardV2Service dashboardV2Service) {
        this.dashboardV2Service = dashboardV2Service;
    }

    @GetMapping
    public ResponseEntity<DashboardV2ResponseDTO> getDashboardV2(
            @AuthenticationPrincipal UserPrincipal principal) {

        if (principal == null) {
            throw new AccessDeniedException("Authentication required to access Dashboard V2");
        }

        DashboardV2ResponseDTO response = dashboardV2Service.getDashboardV2(
                principal.getId(),
                principal.getUsername()
        );

        return ResponseEntity.ok(response);
    }
}
