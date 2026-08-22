package com.berkay.crm.dto;

import com.berkay.crm.model.Deal;
import com.berkay.crm.model.DealOutcome;
import com.berkay.crm.model.DealStage;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record DealResponse(Long id, Integer version,
                           String title, BigDecimal value,
                           DealStage stage, DealOutcome outcome, LocalDate expectedCloseDate,
                           Instant closedAt, Long accountId, String accountName) {

    public static DealResponse from(Deal deal) {

        return new DealResponse(
                deal.getId(), deal.getVersion(), deal.getTitle(), deal.getValue(),
                deal.getStage(), deal.getOutcome(), deal.getExpectedCloseDate(),
                deal.getClosedAt(), deal.getAccount().getId(), deal.getAccount().getName()
        );
    }
}
