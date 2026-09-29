package com.berkay.crm.service;

import com.berkay.crm.dto.ActivityCreateRequest;
import com.berkay.crm.dto.EmailMessageResponse;
import com.berkay.crm.dto.SendEmailRequest;
import com.berkay.crm.model.*;
import com.berkay.crm.repository.AccountRepository;
import com.berkay.crm.repository.ContactRepository;
import com.berkay.crm.repository.EmailMessageRepository;
import com.berkay.crm.service.mail.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class EmailRecorder {

    private final EmailMessageRepository emailMessageRepository;
    private final ContactRepository contactRepository;
    private final AccountRepository accountRepository;
    private final ContactService contactService;
    private final ActivityService activityService;

    public EmailRecorder(EmailMessageRepository emailMessageRepository,
                         ContactRepository contactRepository,
                         AccountRepository accountRepository,
                         ContactService contactService,
                         ActivityService activityService) {
        this.emailMessageRepository = emailMessageRepository;
        this.contactRepository = contactRepository;
        this.accountRepository = accountRepository;
        this.activityService = activityService;
        this.contactService = contactService;
    }

    public record Recipient(Long contactId, Long accountId, String address) {}

    @Transactional(readOnly = true)
    public Recipient loadRecipient(Long contactId, CrmUser user) {

        Contact contact = contactService.loadAccessible(contactId, user);

        if (contact.getEmail() == null || contact.getEmail().isBlank()) {
            throw new IllegalArgumentException("No email found for contact");
        }

        return new Recipient(contact.getId(), contact.getAccount().getId(), contact.getEmail());
    }

    @Transactional
    public EmailMessageResponse recordFailed(Recipient recipient, SendEmailRequest request, String error) {

        EmailMessage message = newMessage(recipient, request);
        message.setStatus(EmailStatus.FAILED);
        message.setErrorMessage(error);

        EmailMessage saved = emailMessageRepository.save(message);
        return EmailMessageResponse.from(saved);
    }

    @Transactional
    public EmailMessageResponse recordSent(
            Recipient recipient, SendEmailRequest request,
            SendResult result, CrmUser user) {

        EmailMessage message = newMessage(recipient, request);
        message.setStatus(EmailStatus.SENT);
        message.setProviderMessageId(result.providerMessageId());
        EmailMessage saved = emailMessageRepository.save(message);

        activityService.create(recipient.accountId(),
                new ActivityCreateRequest(
                        ActivityType.EMAIL,
                        request.subject(),
                        request.body(),
                        Instant.now(),
                        null,
                        recipient.contactId()), user);

        return EmailMessageResponse.from(saved);
    }

    private EmailMessage newMessage(Recipient recipient, SendEmailRequest request) {

        EmailMessage message = new EmailMessage();
        message.setToAddress(recipient.address());
        message.setSubject(request.subject());
        message.setBody(request.body());
        message.setContact(contactRepository.getReferenceById(recipient.contactId()));
        message.setAccount(accountRepository.getReferenceById(recipient.accountId()));

        return message;
    }
}
