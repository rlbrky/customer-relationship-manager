package com.berkay.crm.api;

import com.berkay.crm.dto.DashboardSummaryResponse;
import com.berkay.crm.security.CrmUserDetails;
import com.berkay.crm.service.DashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * No @PreAuthorize: every signed-in role gets a dashboard, and the per-user
     * scoping inside the service is what does the authorising. A role gate here
     * would lock sales reps out of their own numbers.
     */
    @GetMapping("/summary")
    public DashboardSummaryResponse getSummary(@AuthenticationPrincipal CrmUserDetails principal) {

        return dashboardService.summary(principal.getCrmUser());
    }
}
