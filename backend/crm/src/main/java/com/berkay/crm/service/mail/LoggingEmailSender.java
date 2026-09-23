package com.berkay.crm.service.mail;

import com.berkay.crm.config.CrmMailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "crm.mail.provider", havingValue = "logging", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    private final CrmMailProperties mailProperties;

    public LoggingEmailSender(CrmMailProperties mailProperties) {
        this.mailProperties = mailProperties;
    }

    @Override
    public SendResult send(OutgoingEmail email) {

        log.info("NOT SENDING (logging mail adapter): from = {} to={} subject={}",
                mailProperties.from(), email.to(), email.subject());

        log.debug("Body of unsent email to={}: {}", email.to(), email.body());

        // Never null, and recognisable later in the table as "this one wasn't real".
        return new SendResult("logged-" + UUID.randomUUID());
    }
}
