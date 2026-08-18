package com.berkay.crm.dto;

import com.berkay.crm.model.DealStage;
import com.berkay.crm.model.DealStageHistory;

import java.time.Instant;

public record DealStageHistoryResponse(Long id, DealStage fromStage,
                                       DealStage toStage, Instant changedAt, String changedBy) {

    public static DealStageHistoryResponse from(DealStageHistory dealStageHistory) {

        return new DealStageHistoryResponse(
                dealStageHistory.getId(), dealStageHistory.getFromStage(),
                dealStageHistory.getToStage(), dealStageHistory.getChangedAt(),
                dealStageHistory.getChangedBy()
        );
    }
}
