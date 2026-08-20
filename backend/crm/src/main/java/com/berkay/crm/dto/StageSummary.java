package com.berkay.crm.dto;

import com.berkay.crm.model.DealStage;

import java.math.BigDecimal;

public record StageSummary(DealStage stage, long dealCount, BigDecimal totalValue) {
}
