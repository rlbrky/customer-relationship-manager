package com.berkay.crm.model;

import jakarta.persistence.*;

@Entity
@Table(name = "email_message")
public class EmailMessage extends BaseEntity {

    // COPY of contact's address
    @Column(name = "to_address", nullable = false, length = 254)
    private String toAddress;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailStatus status;

    // Null on failure - no accepted message to point
    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    // null on success
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    // every visibility check starts from account so if we don't add it each one would pay an extra join
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    // error_message is VARCHAR(500) MySQL could reject an over-long insert so we add this
    public static final int MAX_ERROR_LENGTH = 500;

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public void setProviderMessageId(String providerMessageId) {
        this.providerMessageId = providerMessageId;
    }

    public EmailStatus getStatus() {
        return status;
    }

    public void setStatus(EmailStatus status) {
        this.status = status;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getToAddress() {
        return toAddress;
    }

    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }

    // Trims the message to what a column can hold
    public void setErrorMessage(String errorMessage) {

        this.errorMessage = errorMessage == null || errorMessage.length() <= MAX_ERROR_LENGTH
                ? errorMessage
                : errorMessage.substring(0, MAX_ERROR_LENGTH);
    }
}


