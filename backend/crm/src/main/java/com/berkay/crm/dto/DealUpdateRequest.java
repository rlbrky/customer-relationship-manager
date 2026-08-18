package com.berkay.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DealUpdateRequest(
        @NotBlank @Size(max = 200) String title,
        @PositiveOrZero BigDecimal value,
        LocalDate expectedCloseDate
        ) {
}
