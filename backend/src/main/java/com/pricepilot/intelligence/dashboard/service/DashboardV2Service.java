package com.pricepilot.intelligence.dashboard.service;

import com.pricepilot.intelligence.dashboard.dto.DashboardV2ResponseDTO;

import java.util.UUID;

/**
 * Service interface for Dashboard V2 personalized shopping intelligence orchestration.
 */
public interface DashboardV2Service {

    DashboardV2ResponseDTO getDashboardV2(UUID userId, String email);
}
