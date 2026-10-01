package com.berkay.crm.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

// transport for resend: where it lives and how long to wait for it
// to keep the tests from overwriting our mock we do it here instead of ResendEmailSender
// No auth header because: adapter adds it per request

@Configuration
public class ResendClientConfig {

    @Bean
    @ConditionalOnProperty(name = "crm.mail.provider", havingValue = "resend")
    RestClient resendRestClient(CrmMailProperties properties) {

        CrmMailProperties.Resend resend = properties.resend();

        // HttpClient is immutable once built.
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(resend.connectTimeout())
                .build();

        // read timeout on factory: waiting for each response is Spring's side
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(resend.readTimeout());

        return RestClient.builder()
                .baseUrl(resend.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
