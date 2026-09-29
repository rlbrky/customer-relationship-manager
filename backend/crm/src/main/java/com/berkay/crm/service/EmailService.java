package com.berkay.crm.service;

import com.berkay.crm.dto.EmailMessageResponse;
import com.berkay.crm.dto.SendEmailRequest;
import com.berkay.crm.exception.EmailDeliveryException;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.service.mail.EmailSender;
import com.berkay.crm.service.mail.OutgoingEmail;
import com.berkay.crm.service.mail.SendResult;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final EmailSender emailSender;
    private final EmailRecorder recorder;

    public EmailService(EmailSender emailSender, EmailRecorder recorder) {
        this.emailSender = emailSender;
        this.recorder = recorder;
    }

    // If the send succeeds and recordSent fails, the email is gone out but there is no record of it so the client gets a 500
    public EmailMessageResponse send(Long contactId, SendEmailRequest request, CrmUser user) {

        // read transaction opens and closes inside this call
        EmailRecorder.Recipient recipient = recorder.loadRecipient(contactId, user);

        OutgoingEmail email = new OutgoingEmail(recipient.address(), request.subject(), request.body());

        // no transaction open: whole point of this class
        SendResult result;
        try {
            result = emailSender.send(email);
        } catch (EmailDeliveryException exception) {
            return recorder.recordFailed(recipient, request, exception.getMessage());
        }

        // a fresh, short write transaction
        return recorder.recordSent(recipient, request, result, user);
    }
}
