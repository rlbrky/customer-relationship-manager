package com.berkay.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendEmailRequest(
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 10000) String body) {

}
