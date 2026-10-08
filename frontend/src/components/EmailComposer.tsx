import { useId, useState } from 'react'
import type { FormEvent } from 'react'
import type { EmailMessage, SendEmailRequest } from '../types/email'

interface EmailComposerProps {
  /** Shown, never sent: the server derives the address from the contact itself. */
  recipientName: string
  recipientEmail: string
  sending: boolean
  /** The REQUEST failed: validation (422), access (403), or the network. */
  error: string | null
  /** The request went through, but the provider didn't confirm it — status FAILED. */
  failure: EmailMessage | null
  onSend: (request: SendEmailRequest) => void
  onCancel: () => void
}

/** The same limits as @Size on SendEmailRequest. */
const SUBJECT_MAX = 200
const BODY_MAX = 10_000

/** Past this share of the body limit, the counter turns into a warning. */
const NEAR_LIMIT = 0.9

/**
 * Compose one email to one contact.
 *
 * Presentational, like CsvImport: it owns the two inputs and nothing else. The
 * request and its outcome belong to the page, through useEmailComposer.
 *
 * The draft lives here, in this component's own state, so it survives a FAILED
 * result for free — the page keeps the panel mounted, and nothing typed is lost.
 * A SENT result unmounts the panel, which is the only way the draft goes away.
 */
export function EmailComposer({
  recipientName, recipientEmail, sending, error, failure, onSend, onCancel,
}: EmailComposerProps) {

  const [subject, setSubject] = useState('')
  const [body, setBody] = useState('')

  const subjectId = useId()
  const bodyId = useId()

  // The server's @NotBlank, applied before the request rather than after it.
  // Disabled while sending too: every POST is a real email, so a double click
  // would be two.
  const canSend = !sending && subject.trim() !== '' && body.trim() !== ''

  // JavaScript's length and Java's String.length() both count UTF-16 code units,
  // so this agrees with the server exactly — an emoji counts as 2 on both sides.
  const nearLimit = body.length >= BODY_MAX * NEAR_LIMIT

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    // Subject trimmed (stray spaces from a paste); the body is sent as typed,
    // since its spacing is the writer's formatting.
    if (canSend) onSend({ subject: subject.trim(), body })
  }

  return (
    <section className="panel composer" aria-label={`Email ${recipientName}`}>
      <h2 className="panel__title">Email {recipientName}</h2>
      <p className="composer__to">
        {/* a real space, not just min-width: screen readers and copy-paste read text */}
        <span className="composer__label">To</span>{' '}
        {recipientName} &lt;{recipientEmail}&gt;
      </p>

      <form className="form" onSubmit={handleSubmit}>
        <div className="field">
          <label className="field__label" htmlFor={subjectId}>Subject</label>
          <input
            className="field__input"
            id={subjectId}
            value={subject}
            maxLength={SUBJECT_MAX}
            onChange={(e) => setSubject(e.target.value)}
            disabled={sending}
            autoFocus
          />
        </div>

        <div className="field">
          <div className="composer__body-head">
            <label className="field__label" htmlFor={bodyId}>Message</label>
            <span
              className={`composer__count${nearLimit ? ' composer__count--near' : ''}`}
              aria-live="polite"
            >
              {body.length.toLocaleString()} / {BODY_MAX.toLocaleString()}
            </span>
          </div>
          <textarea
            className="field__input field__input--area composer__body"
            id={bodyId}
            value={body}
            maxLength={BODY_MAX}
            onChange={(e) => setBody(e.target.value)}
            disabled={sending}
            rows={8}
          />
        </div>

        {failure && (
          // "Not sent" would be false after a read timeout, when the provider may
          // have accepted it. This headline is true in every FAILED case; the
          // detail, written by the adapter for people to read, says which it was.
          <div className="composer__failure" role="alert">
            <p className="composer__failure-title">Couldn't confirm the email was sent</p>
            <p className="composer__failure-detail">
              {failure.errorMessage ?? 'The provider gave no reason.'}
            </p>
            <p>
              Your draft is still here. If the detail says it may have been sent, it
              might already have arrived, and sending again could deliver it twice.
            </p>
          </div>
        )}

        {error && <p className="form__error" role="alert">{error}</p>}

        <div className="form__actions">
          <button className="btn btn--primary" type="submit" disabled={!canSend}>
            {sending ? 'Sending…' : 'Send'}
          </button>
          <button className="btn btn--ghost" type="button" onClick={onCancel} disabled={sending}>
            Cancel
          </button>
        </div>
      </form>
    </section>
  )
}
