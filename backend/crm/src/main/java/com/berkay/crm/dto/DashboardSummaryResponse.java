package com.berkay.crm.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardSummaryResponse(
        long accountCount,
        long contactCount,
        long openDealCount,
        BigDecimal openPipelineValue,
        long wonCount,
        BigDecimal wonValue,
        long lostCount,
        BigDecimal winRate, // null = nothing closed yet
        long overdueTaskCount,
        List<StageSummary> pipelineByStage,
        List<ActivityTypeSummary> activityMix,
        List<DealResponse> closingSoon,
        /** One entry per day of the trend window, gap-filled — never sparse. */
        List<DailyActivity> activityByDay,
        long taskCount,
        long completedTaskCount
) {
}
