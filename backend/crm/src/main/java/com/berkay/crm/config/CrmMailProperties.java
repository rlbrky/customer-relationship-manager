package com.berkay.crm.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties("crm.mail")
public record CrmMailProperties(
        @NotBlank @Email String from,
        // this will stop dev machine from mailing customers
        @Email String redirectTo,
        // Never null bc of @DefaultValue
        @DefaultValue Resend resend
        ) {

    public record Resend(
            // required only when resend adapter exists and dev and test have no key
            String apiKey,
            @DefaultValue("https://api.resend.com") URI baseUrl,
            @DefaultValue("5s") Duration connectTimeout,
            @DefaultValue("10s") Duration readTimeout
            ) {}
}
