package com.berkay.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AccountUpdateRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 100) String industry,
        @Size(max = 254) String website,
        @Size(max = 20) String phone,
        @NotNull Long ownerId
) {
}
