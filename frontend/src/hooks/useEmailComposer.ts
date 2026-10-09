import type {EmailMessage, SendEmailRequest} from "../types/email.ts";
import {useState} from "react";
import type {Contact} from "../types/contact.ts";
import {sendEmail} from "../api/contacts.ts";
import {ApiError} from "../api/client.ts";

interface UseEmailComposerOptions {
    /** What this page does after a SENT — the account page refreshes its timeline. */
    onSent?: (message: EmailMessage) => void
}

export function useEmailComposer({ onSent }: UseEmailComposerOptions = {}) {
    const [contact, setContact] = useState<Contact | null>(null)
    const [sending, setSending] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [failure, setFailure] = useState<EmailMessage | null>(null)
    const [notice, setNotice] = useState<string | null>(null)

    function open(target: Contact) {
        if (sending) return;

        setContact(target);
        setError(null);
        setFailure(null);
        setNotice(null);
    }

    function close() {

        setContact(null);
        setError(null);
        setFailure(null);
    }

    async function send(request: SendEmailRequest) {
        if (!contact) return
        setSending(true)
        setError(null)
        setFailure(null)
        try {
            const message = await sendEmail(contact.id, request)

            if (message.status === "FAILED") {

                setFailure(message);
                return;
            }

            setNotice(`Sent to ${contact.firstName} ${contact.lastName}`);
            setContact(null);
            onSent?.(message);

        } catch (err) {
            setError(err instanceof ApiError ? err.message : 'Could not send the email.')
        } finally {
            setSending(false)
        }
    }

    return { contact, sending, error, failure, notice, open, close, send }
}