package com.berkay.crm.dto;

import com.berkay.crm.model.Activity;
import com.berkay.crm.model.ActivityType;
import com.berkay.crm.model.Contact;

import java.time.Instant;
import java.time.LocalDateTime;

public record ActivityResponse(
        Long id, Integer version,
        ActivityType type, String subject,
        String notes, Instant occurredAt, LocalDateTime dueAt,
        boolean completed, Long accountId, Long contactId,
        String contactName
) {

    public static ActivityResponse from(Activity activity) {
        Contact contact = activity.getContact();

        return new ActivityResponse(
                activity.getId(), activity.getVersion(), activity.getType(), activity.getSubject(),
                activity.getNotes(), activity.getOccurredAt(), activity.getDueAt(),
                activity.isCompleted(), activity.getAccount().getId(),
                contact == null ? null : activity.getContact().getId(),
                contact == null ? null : activity.getContact().getFirstName() + " " + contact.getLastName()
        );
    }
}
