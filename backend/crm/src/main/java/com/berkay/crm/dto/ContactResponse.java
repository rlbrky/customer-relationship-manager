package com.berkay.crm.dto;

import com.berkay.crm.model.Contact;

public record ContactResponse(Long id, String firstName, String lastName,
                              String email, String phone, String jobTitle,
                              Long accountId, String accountName
                              ) {

    public static ContactResponse from(Contact contact) {

        return new ContactResponse(
                contact.getId(), contact.getFirstName(), contact.getLastName(),
                contact.getEmail(), contact.getPhone(), contact.getJobTitle(),
                contact.getAccount().getId(), contact.getAccount().getName()
        );
    }
}
