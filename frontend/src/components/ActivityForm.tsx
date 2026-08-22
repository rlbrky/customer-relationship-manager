import { useState, type FormEvent } from 'react'
import type {
  Activity,
  ActivityCreateRequest,
  ActivityType,
  ActivityUpdateRequest,
} from '../types/activity'
import { ACTIVITY_TYPES, activityTypeLabel } from '../types/activity'
import type { Contact } from '../types/contact'
import {
  inputToInstant,
  inputToLocalDateTime,
  instantToInput,
  localDateTimeToInput,
  nowForInput,
} from '../utils/datetime'

export type ActivityFormProps =
  | {
      mode: 'create'
      contacts: Contact[]
      submitting: boolean
      error: string | null
      onSubmit: (request: ActivityCreateRequest) => void
      onCancel: () => void
    }
  | {
      mode: 'edit'
      activity: Activity
      contacts: Contact[]
      submitting: boolean
      error: string | null
      onSubmit: (request: ActivityUpdateRequest) => void
      onCancel: () => void
    }

export function ActivityForm(props: ActivityFormProps) {
  const editing = props.mode === 'edit' ? props.activity : null

  const [type, setType] = useState<ActivityType>(editing?.type ?? 'CALL')
  const [subject, setSubject] = useState(editing?.subject ?? '')
  const [notes, setNotes] = useState(editing?.notes ?? '')
  const [occurredAt, setOccurredAt] = useState(
    editing ? instantToInput(editing.occurredAt) : nowForInput(),
  )
  const [dueAt, setDueAt] = useState(
    editing?.dueAt ? localDateTimeToInput(editing.dueAt) : '',
  )
  const [contactId, setContactId] = useState<number | null>(editing?.contactId ?? null)

  const isTask = type === 'TASK'

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const common = {
      type,
      subject: subject.trim(),
      notes: notes.trim() === '' ? null : notes.trim(),
      // Instant: local input reinterpreted as an absolute UTC moment
      occurredAt: inputToInstant(occurredAt),
      // LocalDateTime: passed through verbatim. Only tasks may carry one —
      // the backend rejects a due date on any other type.
      dueAt: isTask && dueAt ? inputToLocalDateTime(dueAt) : null,
      contactId,
    }

    if (props.mode === 'create') {
      props.onSubmit(common)
    } else {
      props.onSubmit({
        ...common,
        completed: props.activity.completed,
        version: props.activity.version,
      })
    }
  }

  return (
    <form className="panel form" onSubmit={handleSubmit}>
      <h2 className="panel__title">
        {props.mode === 'create' ? 'Log activity' : `Edit ${editing?.subject}`}
      </h2>

      <div className="field-row">
        <label className="field">
          <span className="field__label">Type</span>
          <select
            className="field__input"
            value={type}
            onChange={(e) => setType(e.target.value as ActivityType)}
          >
            {ACTIVITY_TYPES.map((t) => (
              <option key={t} value={t}>{activityTypeLabel(t)}</option>
            ))}
          </select>
        </label>

        <label className="field">
          <span className="field__label">When</span>
          <input
            className="field__input"
            type="datetime-local"
            value={occurredAt}
            onChange={(e) => setOccurredAt(e.target.value)}
            required
          />
        </label>
      </div>

      <label className="field">
        <span className="field__label">Subject</span>
        <input
          className="field__input"
          value={subject}
          onChange={(e) => setSubject(e.target.value)}
          maxLength={200}
          required
          autoFocus
        />
      </label>

      {/* Only tasks get a due date — mirrors the server-side rule, so the UI
          can't compose a request the backend would reject. */}
      {isTask && (
        <label className="field">
          <span className="field__label">Due</span>
          <input
            className="field__input"
            type="datetime-local"
            value={dueAt}
            onChange={(e) => setDueAt(e.target.value)}
          />
          <span className="field__hint">Optional — a task without a deadline is fine.</span>
        </label>
      )}

      {props.contacts.length > 0 && (
        <label className="field">
          <span className="field__label">Contact</span>
          <select
            className="field__input"
            value={contactId ?? ''}
            onChange={(e) => setContactId(e.target.value === '' ? null : Number(e.target.value))}
          >
            <option value="">No specific person</option>
            {props.contacts.map((contact) => (
              <option key={contact.id} value={contact.id}>
                {contact.firstName} {contact.lastName}
              </option>
            ))}
          </select>
        </label>
      )}

      <label className="field">
        <span className="field__label">Notes</span>
        <textarea
          className="field__input field__input--area"
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          rows={3}
        />
      </label>

      {props.error && <p className="form__error" role="alert">{props.error}</p>}

      <div className="form__actions">
        <button className="btn btn--primary" type="submit" disabled={props.submitting}>
          {props.submitting ? 'Saving…' : props.mode === 'create' ? 'Log activity' : 'Save changes'}
        </button>
        <button className="btn btn--ghost" type="button" onClick={props.onCancel}>
          Cancel
        </button>
      </div>
    </form>
  )
}
