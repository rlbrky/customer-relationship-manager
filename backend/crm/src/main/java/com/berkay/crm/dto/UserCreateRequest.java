package com.berkay.crm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UserCreateRequest(@NotBlank @Size(max = 30) String username,
                                @Email @NotBlank @Size(max = 254) String email,
                                @NotBlank @Size(min = 8) String password,
                                @NotBlank String firstName,
                                @NotBlank String lastName,
                                @NotEmpty Set<String> roles) {
}
