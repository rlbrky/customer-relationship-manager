package com.berkay.crm.dto;

import com.berkay.crm.model.EmailMessage;
import com.berkay.crm.model.EmailStatus;

import java.time.Instant;

public record EmailMessageResponse(Long id,
                                   String to,
                                   String subject,
                                   EmailStatus status,
                                   String providerMessageId,
                                   String errorMessage,
                                   Instant sentAt,
                                   Long contactId
                                   ) {

    public static EmailMessageResponse from(EmailMessage emailMessage) {
        return new EmailMessageResponse(
                emailMessage.getId(),
                emailMessage.getToAddress(),
                emailMessage.getSubject(),
                emailMessage.getStatus(),
                emailMessage.getProviderMessageId(),
                emailMessage.getErrorMessage(),
                emailMessage.getCreatedDate(),
                emailMessage.getContact().getId()
                );
    }
}
