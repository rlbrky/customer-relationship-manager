package com.berkay.crm;

import com.berkay.crm.dto.EmailMessageResponse;
import com.berkay.crm.dto.SendEmailRequest;
import com.berkay.crm.exception.EmailDeliveryException;
import com.berkay.crm.model.Account;
import com.berkay.crm.model.Activity;
import com.berkay.crm.model.ActivityType;
import com.berkay.crm.model.Contact;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.model.EmailMessage;
import com.berkay.crm.model.EmailStatus;
import com.berkay.crm.repository.AccountRepository;
import com.berkay.crm.repository.ActivityRepository;
import com.berkay.crm.repository.ContactRepository;
import com.berkay.crm.repository.EmailMessageRepository;
import com.berkay.crm.repository.RoleRepository;
import com.berkay.crm.repository.UserRepository;
import com.berkay.crm.security.Roles;
import com.berkay.crm.service.EmailService;
import com.berkay.crm.service.mail.EmailSender;
import com.berkay.crm.service.mail.OutgoingEmail;
import com.berkay.crm.service.mail.SendResult;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The provider is replaced by a Mockito mock, so each test decides whether the
 * "send" succeeds, fails, or blows up — and can see exactly what it was asked to send.
 *
 * Fixture owners are SALES_REPs, as everywhere else: the boundary test has to commit,
 * and its rows outlive it.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class EmailServiceTest {

    private static final String SUBJECT = "Renewal";
    private static final String BODY = "Hi Hank, your renewal is coming up.";

    @Autowired private EmailService emailService;
    @Autowired private EmailMessageRepository emailMessageRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private ContactRepository contactRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private EntityManager entityManager;

    /** Replaces LoggingEmailSender for this class. Reset after every test. */
    @MockitoBean private EmailSender emailSender;

    private CrmUser newUser(String username, String roleName) {
        CrmUser user = new CrmUser();
        user.setUsername(username);
        // a staff address that can never be mistaken for a contact's
        user.setEmail(username + "@staff.crm.test");
        user.setPasswordHash("$2a$10$notARealHashButFillsTheColumn");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEnabled(true);
        user.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
        return userRepository.save(user);
    }

    private Account newAccount(CrmUser owner, String name) {
        Account account = new Account();
        account.setName(name);
        account.setOwner(owner);
        return accountRepository.save(account);
    }

    private Contact newContact(Account account, String email) {
        Contact contact = new Contact();
        contact.setAccount(account);
        contact.setFirstName("Hank");
        contact.setLastName("Scorpio");
        contact.setEmail(email);
        return contactRepository.save(contact);
    }

    private SendEmailRequest request() {
        return new SendEmailRequest(SUBJECT, BODY);
    }

    /** Pushes pending writes to MySQL and empties the cache, so reads really hit the table. */
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private List<EmailMessage> messagesFor(Contact contact) {
        return emailMessageRepository.findAll().stream()
                .filter(message -> message.getContact().getId().equals(contact.getId()))
                .toList();
    }

    private List<Activity> emailActivitiesFor(Contact contact) {
        return activityRepository.findAll().stream()
                .filter(activity -> activity.getType() == ActivityType.EMAIL)
                .filter(activity -> activity.getContact() != null
                        && activity.getContact().getId().equals(contact.getId()))
                .toList();
    }

    // ── what gets sent ────────────────────────────────────────────────────────

    @Test
    public void send_deliversToTheContactsAddressNotTheSenders() {

        // given — two different addresses in scope, so a user/contact mix-up cannot pass
        CrmUser rep = newUser("mail1", Roles.SALES_REP);
        Contact hank = newContact(newAccount(rep, "Globex"), "hank@globex.test");
        given(emailSender.send(any())).willReturn(new SendResult("stub-1"));

        // when
        emailService.send(hank.getId(), request(), rep);

        // then — asserted on what actually reached the provider. CrmUser and Contact
        // both have getEmail(), so reading the wrong one compiles, demos correctly with
        // the logging adapter, and mails every customer's message to the sender.
        ArgumentCaptor<OutgoingEmail> sent = ArgumentCaptor.forClass(OutgoingEmail.class);
        verify(emailSender).send(sent.capture());

        assertThat(sent.getValue().to()).isEqualTo("hank@globex.test");
        assertThat(sent.getValue().to()).isNotEqualTo(rep.getEmail());
        assertThat(sent.getValue().subject()).isEqualTo(SUBJECT);
        assertThat(sent.getValue().body()).isEqualTo(BODY);
    }

    // ── what gets recorded ────────────────────────────────────────────────────

    @Test
    public void send_recordsASentMessageWithTheProvidersId() {

        // given
        CrmUser rep = newUser("mail2", Roles.SALES_REP);
        Account globex = newAccount(rep, "Globex");
        Contact hank = newContact(globex, "hank@globex.test");
        given(emailSender.send(any())).willReturn(new SendResult("stub-2"));

        // when
        EmailMessageResponse response = emailService.send(hank.getId(), request(), rep);

        // then — the response
        assertThat(response.status()).isEqualTo(EmailStatus.SENT);
        assertThat(response.providerMessageId()).isEqualTo("stub-2");
        assertThat(response.to()).isEqualTo("hank@globex.test");
        assertThat(response.sentAt()).isNotNull();
        assertThat(response.errorMessage()).isNull();

        // ... and the row, re-read from MySQL rather than from Hibernate's cache.
        // provider_message_id is the only handle for asking the provider what became
        // of a message; a SENT row without it can never be reconciled.
        flushAndClear();
        EmailMessage stored = emailMessageRepository.findById(response.id()).orElseThrow();

        assertThat(stored.getStatus()).isEqualTo(EmailStatus.SENT);
        assertThat(stored.getProviderMessageId()).isEqualTo("stub-2");
        assertThat(stored.getToAddress()).isEqualTo("hank@globex.test");
        assertThat(stored.getBody()).isEqualTo(BODY);
        assertThat(stored.getAccount().getId()).isEqualTo(globex.getId());
    }

    @Test
    public void send_logsAnEmailActivityOnTheContactsTimeline() {

        // given
        CrmUser rep = newUser("mail3", Roles.SALES_REP);
        Account globex = newAccount(rep, "Globex");
        Contact hank = newContact(globex, "hank@globex.test");
        given(emailSender.send(any())).willReturn(new SendResult("stub-3"));

        // when
        emailService.send(hank.getId(), request(), rep);

        // then — this is what makes the email visible anywhere in the product
        flushAndClear();
        List<Activity> activities = emailActivitiesFor(hank);

        assertThat(activities).hasSize(1);
        Activity activity = activities.get(0);
        assertThat(activity.getSubject()).isEqualTo(SUBJECT);
        assertThat(activity.getNotes()).isEqualTo(BODY);
        assertThat(activity.getAccount().getId()).isEqualTo(globex.getId());
        assertThat(activity.getDueAt()).isNull();
    }

    // ── when the provider refuses ─────────────────────────────────────────────

    @Test
    public void send_recordsAFailureAndReturnsItRatherThanThrowing() {

        // given
        CrmUser rep = newUser("mail4", Roles.SALES_REP);
        Contact hank = newContact(newAccount(rep, "Globex"), "hank@globex.test");
        given(emailSender.send(any()))
                .willThrow(new EmailDeliveryException("550 mailbox unavailable"));

        // when — no exception: the attempt ran to completion and produced a record
        EmailMessageResponse response = emailService.send(hank.getId(), request(), rep);

        // then
        assertThat(response.status()).isEqualTo(EmailStatus.FAILED);
        assertThat(response.errorMessage()).contains("550 mailbox unavailable");
        assertThat(response.providerMessageId()).isNull();

        flushAndClear();
        assertThat(messagesFor(hank)).singleElement()
                .extracting(EmailMessage::getStatus).isEqualTo(EmailStatus.FAILED);
    }

    @Test
    public void send_writesNoActivityWhenTheSendFailed() {

        // given
        CrmUser rep = newUser("mail5", Roles.SALES_REP);
        Contact hank = newContact(newAccount(rep, "Globex"), "hank@globex.test");
        given(emailSender.send(any())).willThrow(new EmailDeliveryException("rejected"));

        // when
        emailService.send(hank.getId(), request(), rep);

        // then — the timeline records things that happened. The failure is kept in
        // email_message, where M15's retries will look for it.
        flushAndClear();
        assertThat(emailActivitiesFor(hank)).isEmpty();
    }

    @Test
    public void send_truncatesAProviderErrorLongerThanTheColumn() {

        // given — error_message is VARCHAR(500); provider messages are often longer
        CrmUser rep = newUser("mail6", Roles.SALES_REP);
        Contact hank = newContact(newAccount(rep, "Globex"), "hank@globex.test");
        given(emailSender.send(any())).willThrow(new EmailDeliveryException("x".repeat(2000)));

        // when — strict-mode MySQL rejects an over-long value outright, so without the
        // truncation this call would throw: the failure path itself failing
        EmailMessageResponse response = emailService.send(hank.getId(), request(), rep);

        // then
        flushAndClear();
        EmailMessage stored = emailMessageRepository.findById(response.id()).orElseThrow();
        assertThat(stored.getErrorMessage()).hasSize(EmailMessage.MAX_ERROR_LENGTH);
    }

    @Test
    public void send_letsABugInTheSenderPropagateInsteadOfRecordingAFailure() {

        // given — not a delivery failure: a defect
        CrmUser rep = newUser("mail7", Roles.SALES_REP);
        Contact hank = newContact(newAccount(rep, "Globex"), "hank@globex.test");
        given(emailSender.send(any())).willThrow(new IllegalStateException("adapter bug"));

        // when / then — catching Exception instead of EmailDeliveryException would turn
        // this into a FAILED row reading "the email failed", and everyone would go on
        // believing the provider was flaky
        assertThatThrownBy(() -> emailService.send(hank.getId(), request(), rep))
                .isInstanceOf(IllegalStateException.class);

        flushAndClear();
        assertThat(messagesFor(hank)).isEmpty();
    }

    // ── refusing before anything is sent ─────────────────────────────────────

    @Test
    public void send_rejectsAContactWithNoEmailAddress() {

        // given
        CrmUser rep = newUser("mail8", Roles.SALES_REP);
        Contact noAddress = newContact(newAccount(rep, "Globex"), null);

        // when / then
        assertThatThrownBy(() -> emailService.send(noAddress.getId(), request(), rep))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");

        // nothing reached the provider, and nothing was recorded
        verifyNoInteractions(emailSender);
        assertThat(messagesFor(noAddress)).isEmpty();
    }

    @Test
    public void send_refusesAContactTheCallerCannotSee() {

        // given — another rep's contact
        CrmUser rep = newUser("mail9", Roles.SALES_REP);
        CrmUser other = newUser("mail10", Roles.SALES_REP);
        Contact theirs = newContact(newAccount(other, "Theirs"), "them@theirs.test");

        // when / then
        assertThatThrownBy(() -> emailService.send(theirs.getId(), request(), rep))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(emailSender);
        assertThat(messagesFor(theirs)).isEmpty();
    }

    @Test
    public void send_checksAccessBeforeLookingAtTheAddress() {

        // given — another rep's contact that ALSO has no address, so both checks
        // would fail. Which one wins is the point of the test.
        CrmUser rep = newUser("mail11", Roles.SALES_REP);
        CrmUser other = newUser("mail12", Roles.SALES_REP);
        Contact theirs = newContact(newAccount(other, "Theirs"), null);

        // when / then — "this contact has no email address" would tell the caller a
        // fact about a contact they are not allowed to see
        assertThatThrownBy(() -> emailService.send(theirs.getId(), request(), rep))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── the transaction boundary ──────────────────────────────────────────────

    @Test
    public void send_callsTheProviderWithNoTransactionOpen() {

        // given — the stub records whether a transaction was open at the moment it ran
        CrmUser rep = newUser("mail13", Roles.SALES_REP);
        Contact hank = newContact(newAccount(rep, "Globex"), "hank@globex.test");

        AtomicBoolean transactionOpenDuringSend = new AtomicBoolean(true);
        given(emailSender.send(any())).willAnswer(invocation -> {
            transactionOpenDuringSend.set(TransactionSynchronizationManager.isActualTransactionActive());
            return new SendResult("stub-13");
        });

        // This class is @Transactional, so the test itself runs inside a transaction,
        // and send() would simply join it — the stub would see one open no matter what
        // EmailService does, and this test would fail for a reason that has nothing to
        // do with the code under test. So: commit the fixtures and step outside.
        TestTransaction.flagForCommit();
        TestTransaction.end();

        // when — no outer transaction now; whatever is open is EmailService's doing
        emailService.send(hank.getId(), request(), rep);

        // give the test framework a transaction to finish, before anything can throw
        TestTransaction.start();

        // then — if EmailService (or anything it calls first) held a transaction open
        // across the provider call, a slow provider would hold a pooled connection with
        // it, and a rollback after a successful send would erase the only record
        assertThat(transactionOpenDuringSend.get()).isFalse();
    }
}
