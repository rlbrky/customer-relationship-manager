package com.berkay.crm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactUpdateRequest(@NotBlank @Size(max = 50) String firstName, @NotBlank @Size(max = 50) String lastName,
                                   @Email @Size(max = 254) String email, @Size(max = 20) String phone, @Size(max = 50) String jobTitle) {
}
