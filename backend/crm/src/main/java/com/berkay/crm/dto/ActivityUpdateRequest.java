package com.berkay.crm.dto;

import com.berkay.crm.model.ActivityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDateTime;

public record ActivityUpdateRequest(
        @NotNull ActivityType type, @NotBlank @Size(max = 200) String subject, String notes,
        @NotNull Instant occurredAt, LocalDateTime dueAt, Long contactId, boolean completed
) {
}
