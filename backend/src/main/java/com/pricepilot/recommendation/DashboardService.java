package com.pricepilot.recommendation;

import com.pricepilot.recommendation.dto.DashboardDTO;
import java.util.UUID;

public interface DashboardService {
    
    /**
     * Aggregates all dashboard data for a single user in one optimized call.
     *
     * @param userId The User's UUID.
     * @param email  The User's email.
     * @return DashboardDTO containing widgets data.
     */
    DashboardDTO getDashboardData(UUID userId, String email);
}
