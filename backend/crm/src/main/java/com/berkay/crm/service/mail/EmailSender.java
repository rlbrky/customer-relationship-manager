package com.berkay.crm.service.mail;

public interface EmailSender {

    SendResult send(OutgoingEmail email);
}
