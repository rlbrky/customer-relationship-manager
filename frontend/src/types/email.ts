export interface SendEmailRequest {
    subject: string;
    body: string;
}

export type EmailStatus = 'SENT' | 'FAILED';

export interface EmailMessage {
    id: number;
    to: string;
    subject: string;
    status: EmailStatus;
    providerMessageId: string | null;
    errorMessage: string | null;
    sentAt: string;
    contactId: number;
}