package com.berkay.crm.service.mail;

import com.berkay.crm.config.CrmMailProperties;
import com.berkay.crm.exception.EmailDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;

@Component
@ConditionalOnProperty(name = "crm.mail.provider", havingValue = "resend")
public class ResendEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String from;
    private final String redirectTo; // null means no redirect

    public ResendEmailSender(CrmMailProperties properties, RestClient resendRestClient) {

        String key = properties.resend().apiKey();

        if (key == null || key.isBlank()) {
            throw new IllegalStateException("crm.mail.resend.api-key is not set (export CRM_MAIL_API_KEY)");
        }

        String redirect = properties.redirectTo();
        if (redirect != null && redirect.isBlank()) {
            throw new IllegalStateException("crm.mail.redirect-to is set but empty - set CRM_MAIL_REDIRECT_TO, "
                    + "or remove the property to send for real");
        }

        this.apiKey = key;
        this.restClient = resendRestClient;
        this.from = properties.from();
        this.redirectTo = redirect;
    }

    @Override
    public SendResult send(OutgoingEmail email) {

        OutgoingEmail actual = applyRedirect(email);
        ResendRequest request = new ResendRequest(
                from, List.of(actual.to()), actual.subject(), actual.body()
        );

        try {
            ResendResponse response = restClient.post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ResendResponse.class);

            if (response == null || response.id() == null) {
                // 2xx means accepted: say so, rather than lose an email that went out
                log.warn("Resend accepted an email to {} but returned no id", actual.to());
                return new SendResult(null);
            }

            log.info("Sent via Resend to {} (id {})", actual.to(), response.id());
            return new SendResult(response.id());
        } catch (RestClientResponseException ex) {

            int status = ex.getStatusCode().value();
            if (status == 401 || status == 403) {
                // something not fixed by retrying: a bad or suspended key,
                // or an unverified domain - every send fails until a person changes the config
                log.error("Resend refused the request with {} - check the API key and sending domain", status);
            }

            throw new EmailDeliveryException("Resend " + status + ": " + ex.getResponseBodyAsString(), ex);
        } catch (ResourceAccessException ex) {
            throw new EmailDeliveryException(describe(ex), ex);
        }
    }

    private OutgoingEmail applyRedirect(OutgoingEmail email) {

        if (redirectTo == null) {
            return email;
        }

        log.info("Redirecting email for {} to {}", email.to(), redirectTo);
        return new OutgoingEmail(
                redirectTo,
                "[to: " + email.to() + "] " +
                email.subject(),
                email.body()
        );
    }

    private String describe(ResourceAccessException ex) {
        for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {

            // HttpConnectTimeoutException extends HttpTimeoutException,
            // so it has to be tested first, or every connect timeout reads as "may have been sent"
            if (cause instanceof HttpConnectTimeoutException
            || cause instanceof ConnectException
            || cause instanceof UnknownHostException) {
                return "Could not reach Resend (" +
                        cause.getClass().getSimpleName() + ") -- not sent";
            }

            if (cause instanceof HttpTimeoutException) {
                return "No response from Resend within the read timeout - the email may have been sent";
            }
        }

        // reset mid-response or anything unrecognized
        return "I/O error talking to Resend (" + ex.getMessage() + ") - the email may have been sent";
    }

    // private and never seen by the port
    record ResendRequest(String from, List<String> to, String subject, String text) {}

    record ResendResponse(String id) {}
}
