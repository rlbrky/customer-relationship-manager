package com.berkay.crm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record UserUpdateRequest(
        @NotNull Integer version,
        @Email @NotBlank String email,
        @NotBlank String firstName,
        @NotBlank String lastName,
        boolean enabled,
        @NotEmpty Set<String> roles
) {
}
