package com.berkay.crm.service.mail;

public record OutgoingEmail(String to, String subject, String body) {
}
