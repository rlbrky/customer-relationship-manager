package com.berkay.crm;

import com.berkay.crm.config.CrmMailProperties;
import com.berkay.crm.exception.EmailDeliveryException;
import com.berkay.crm.service.mail.OutgoingEmail;
import com.berkay.crm.service.mail.ResendEmailSender;
import com.berkay.crm.service.mail.SendResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * No Spring context, no Docker, no network.
 *
 * MockRestServiceServer installs a fake request factory on the builder, so every
 * request the adapter makes is matched against the expectations below and answered
 * with whatever each test chooses — including I/O failures, via withException, so no
 * test ever actually waits for a timeout.
 *
 * This only works because the adapter is handed a finished RestClient instead of
 * building its own: had it set its own request factory for timeouts, it would have
 * replaced the mock and these tests would be calling api.resend.com.
 */
public class ResendEmailSenderTest {

    private static final String BASE_URL = "https://api.resend.com";
    private static final String API_KEY = "re_test_key_never_real";

    private static final OutgoingEmail EMAIL =
            new OutgoingEmail("maxx@example.com", "Renewal", "Hi Maxx, your renewal is coming up.");

    private MockRestServiceServer server;
    private RestClient restClient;

    @BeforeEach
    public void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        // bind BEFORE build(): the mock is installed on the builder, not the client
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    /** Every expectation a test set up must actually have been requested. */
    @AfterEach
    public void verifyAllExpectedRequestsWereMade() {
        server.verify();
    }

    private CrmMailProperties properties(String apiKey, String redirectTo) {
        return new CrmMailProperties("crm@example.test", redirectTo,
                new CrmMailProperties.Resend(apiKey, URI.create(BASE_URL),
                        Duration.ofSeconds(5), Duration.ofSeconds(10)));
    }

    /** No redirect: how it runs in production. */
    private ResendEmailSender sender() {
        return new ResendEmailSender(properties(API_KEY, null), restClient);
    }

    private ResendEmailSender redirectingSender() {
        return new ResendEmailSender(properties(API_KEY, "dev@example.test"), restClient);
    }

    // ── the request ───────────────────────────────────────────────────────────

    @Test
    public void send_postsResendsSchemaAndReturnsTheId() {

        // given — every part of the request Resend will look at
        server.expect(requestTo(BASE_URL + "/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.from").value("crm@example.test"))
                .andExpect(jsonPath("$.to").isArray())
                .andExpect(jsonPath("$.to[0]").value("maxx@example.com"))
                .andExpect(jsonPath("$.subject").value("Renewal"))
                .andExpect(jsonPath("$.text").value("Hi Maxx, your renewal is coming up."))
                // plain text a user typed: as html, every '<' they wrote would be markup
                .andExpect(jsonPath("$.html").doesNotExist())
                .andRespond(withSuccess("{\"id\":\"49a3999c-0ce1-4ea6-ab68-afcd6dc2e794\"}",
                        MediaType.APPLICATION_JSON));

        // when — with NO redirect configured, which is how production runs. A guard
        // on the wrong field once made this exact path redirect to null and throw.
        SendResult result = sender().send(EMAIL);

        // then
        assertThat(result.providerMessageId()).isEqualTo("49a3999c-0ce1-4ea6-ab68-afcd6dc2e794");
    }

    // ── the provider answered, and said no ───────────────────────────────────

    @Test
    public void send_reportsTheStatusAndResendsOwnExplanation() {

        // given
        server.expect(requestTo(BASE_URL + "/emails"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"name\":\"missing_required_field\",\"message\":\"Missing `to` field.\"}"));

        // when / then — the raw body, not a record guessed at: Resend documents its
        // status codes and error names but not the shape of the error body
        assertThatThrownBy(() -> sender().send(EMAIL))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("422")
                .hasMessageContaining("missing_required_field")
                // the cause is kept, so the stack trace reaches what RestClient saw
                .hasCauseInstanceOf(RestClientResponseException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 429, 500, 503})
    public void send_turnsEveryRefusalIntoADeliveryFailure(int status) {

        // given
        server.expect(requestTo(BASE_URL + "/emails"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));

        // when / then — an answer from the provider, whatever it says, is an attempt
        // that failed: recorded as FAILED, never a 500. 401/403 also log at ERROR,
        // since no retry fixes a bad key or an unverified domain.
        assertThatThrownBy(() -> sender().send(EMAIL))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining(String.valueOf(status));
    }

    @Test
    public void send_neverPutsTheApiKeyInAFailureMessage() {

        // given — an auth failure, the case where a key is most tempting to log
        server.expect(requestTo(BASE_URL + "/emails"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"name\":\"restricted_api_key\"}"));

        // when / then — this message ends up in email_message.error_message, in the
        // API response, and in the UI
        assertThatThrownBy(() -> sender().send(EMAIL))
                .isInstanceOf(EmailDeliveryException.class)
                .message().doesNotContain(API_KEY);
    }

    // ── the provider never answered ───────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"refused", "unknown-host"})
    public void send_reportsNotSentWhenTheConnectionNeverOpened(String failure) {

        // given — the request provably never left
        IOException cause = failure.equals("refused")
                ? new ConnectException("Connection refused")
                : new UnknownHostException("api.resend.com");
        server.expect(requestTo(BASE_URL + "/emails")).andRespond(withException(cause));

        // when / then
        assertThatThrownBy(() -> sender().send(EMAIL))
                .isInstanceOf(EmailDeliveryException.class)
                .hasCauseInstanceOf(ResourceAccessException.class)
                .hasMessageContaining("not sent")
                .message().doesNotContain("may have been sent");
    }

    @Test
    public void send_reportsAConnectTimeoutAsNotSentEvenThoughItIsATimeout() {

        // given — HttpConnectTimeoutException EXTENDS HttpTimeoutException. Test them
        // in the wrong order and this lands in the read-timeout branch: a request that
        // never left gets reported as possibly sent.
        server.expect(requestTo(BASE_URL + "/emails"))
                .andRespond(withException(new HttpConnectTimeoutException("connect timed out")));

        // when / then
        assertThatThrownBy(() -> sender().send(EMAIL))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("not sent")
                .message().doesNotContain("may have been sent");
    }

    @Test
    public void send_reportsAReadTimeoutAsPossiblySent() {

        // given — the request went out; the answer never came back
        server.expect(requestTo(BASE_URL + "/emails"))
                .andRespond(withException(new HttpTimeoutException("request timed out")));

        // when / then — the dangerous case. Resend may have accepted and sent it; a
        // message saying "failed" invites the retry that emails the customer twice.
        assertThatThrownBy(() -> sender().send(EMAIL))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("may have been sent");
    }

    @Test
    public void send_assumesTheWorstForAnUnrecognisedIoFailure() {

        // given — a connection reset mid-response: it can't be known whether it went
        server.expect(requestTo(BASE_URL + "/emails"))
                .andRespond(withException(new IOException("Connection reset")));

        // when / then — "not sent" is a claim that needs proof; without it, default
        // to "may have been sent"
        assertThatThrownBy(() -> sender().send(EMAIL))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("may have been sent")
                .message().doesNotContain(API_KEY);
    }

    // ── a 2xx that breaks the contract ───────────────────────────────────────

    @Test
    public void send_treatsAnEmptySuccessBodyAsSentWithoutAnId() {

        // given — accepted, but no body at all
        server.expect(requestTo(BASE_URL + "/emails")).andRespond(withSuccess());

        // when — no exception: a 2xx means Resend took the email. Throwing here would
        // become a 500 with no record, for a message that has actually gone out.
        SendResult result = sender().send(EMAIL);

        // then
        assertThat(result.providerMessageId()).isNull();
    }

    @Test
    public void send_treatsASuccessBodyWithoutAnIdAsSentWithoutAnId() {

        // given — a body, but not the field we need
        server.expect(requestTo(BASE_URL + "/emails"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // when
        SendResult result = sender().send(EMAIL);

        // then
        assertThat(result.providerMessageId()).isNull();
    }

    // ── the redirect rail ─────────────────────────────────────────────────────

    @Test
    public void send_divertsToTheRedirectAddressAndNamesTheIntendedRecipient() {

        // given
        server.expect(requestTo(BASE_URL + "/emails"))
                .andExpect(jsonPath("$.to[0]").value("dev@example.test"))
                // the inbox must still say who it was really for
                .andExpect(jsonPath("$.subject").value("[to: maxx@example.com] Renewal"))
                .andExpect(jsonPath("$.text").value("Hi Maxx, your renewal is coming up."))
                .andRespond(withSuccess("{\"id\":\"redirected-1\"}", MediaType.APPLICATION_JSON));

        // when
        SendResult result = redirectingSender().send(EMAIL);

        // then
        assertThat(result.providerMessageId()).isEqualTo("redirected-1");
    }

    // ── refusing to start misconfigured ───────────────────────────────────────

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    public void constructor_refusesAMissingApiKey(String apiKey) {

        // when / then — null is what you get when the provider is switched without the
        // profile; "" and blanks when the variable exists but is empty. All three must
        // fail at startup, with a message that says which variable to set.
        assertThatThrownBy(() -> new ResendEmailSender(properties(apiKey, null), restClient))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CRM_MAIL_API_KEY");
    }

    @Test
    public void constructor_refusesAnEmptyRedirectRatherThanSendingForReal() {

        // when / then — fail closed. If an empty CRM_MAIL_REDIRECT_TO meant "no
        // redirect", an unset variable would silently switch off the one thing between
        // a dev machine and real customers.
        assertThatThrownBy(() -> new ResendEmailSender(properties(API_KEY, ""), restClient))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CRM_MAIL_REDIRECT_TO");
    }
}
