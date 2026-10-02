package com.berkay.crm.model;

/*
* Outcome of handing one email to the provider.
*
* Neither value is a statement about the recipient's inbox. A bounce — an SMTP 550
 * for an unknown mailbox, say — happens after the provider has accepted the message,
 * and is only reported later through a webhook this application does not consume.
 * So the UI says "Sent", never "Delivered".
 *
 * No PENDING yet: it arrives later, where a row is written BEFORE the
 * send and its id becomes Resend's Idempotency-Key.
* */
public enum EmailStatus {
    // Accepted by provider. Says nothing about whether it reached an inbox
    SENT,
    // Refused, or no answer came back. Not always "not sent": after a read timeout
    // The provider may have accepted it - error_message says which. That ambiguity is exactly what idempotency key exists to close.
    FAILED
}
