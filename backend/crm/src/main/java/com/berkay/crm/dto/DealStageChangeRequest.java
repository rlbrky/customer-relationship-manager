package com.berkay.crm.dto;

import com.berkay.crm.model.DealStage;
import jakarta.validation.constraints.NotNull;

public record DealStageChangeRequest(@NotNull DealStage stage) {
}
