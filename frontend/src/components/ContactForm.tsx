import { useState, type FormEvent } from 'react'
import type { Contact, ContactCreateRequest, ContactUpdateRequest } from '../types/contact'

/**
 * Create and edit share the same fields — a contact never moves between
 * accounts, so the parent is fixed either way.
 */
export type ContactFormProps =
  | {
      mode: 'create'
      submitting: boolean
      error: string | null
      onSubmit: (request: ContactCreateRequest) => void
      onCancel: () => void
    }
  | {
      mode: 'edit'
      contact: Contact
      submitting: boolean
      error: string | null
      onSubmit: (request: ContactUpdateRequest) => void
      onCancel: () => void
    }

export function ContactForm(props: ContactFormProps) {
  const editing = props.mode === 'edit' ? props.contact : null

  const [firstName, setFirstName] = useState(editing?.firstName ?? '')
  const [lastName, setLastName] = useState(editing?.lastName ?? '')
  const [email, setEmail] = useState(editing?.email ?? '')
  const [phone, setPhone] = useState(editing?.phone ?? '')
  const [jobTitle, setJobTitle] = useState(editing?.jobTitle ?? '')

  /** empty string → null: those columns are nullable, and "" is not the same as absent */
  const orNull = (value: string) => (value.trim() === '' ? null : value.trim())

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const common = {
      firstName: firstName.trim(),
      lastName: lastName.trim(),
      email: orNull(email),
      phone: orNull(phone),
      jobTitle: orNull(jobTitle),
    }

    if (props.mode === 'create') {
      props.onSubmit(common)
    } else {
      // Read straight off the prop, never from state: this is the version of the
      // snapshot the user opened, not something they are allowed to influence.
      props.onSubmit({ ...common, version: props.contact.version })
    }
  }

  return (
    <form className="panel form" onSubmit={handleSubmit}>
      <h2 className="panel__title">
        {props.mode === 'create'
          ? 'New contact'
          : `Edit ${editing?.firstName} ${editing?.lastName}`}
      </h2>

      <div className="field-row">
        <label className="field">
          <span className="field__label">First name</span>
          <input
            className="field__input"
            value={firstName}
            onChange={(e) => setFirstName(e.target.value)}
            maxLength={50}
            required
            autoFocus
          />
        </label>
        <label className="field">
          <span className="field__label">Last name</span>
          <input
            className="field__input"
            value={lastName}
            onChange={(e) => setLastName(e.target.value)}
            maxLength={50}
            required
          />
        </label>
      </div>

      <label className="field">
        <span className="field__label">Job title</span>
        <input
          className="field__input"
          value={jobTitle}
          onChange={(e) => setJobTitle(e.target.value)}
          maxLength={50}
        />
      </label>

      <div className="field-row">
        <label className="field">
          <span className="field__label">Email</span>
          <input
            className="field__input"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            maxLength={254}
          />
        </label>
        <label className="field">
          <span className="field__label">Phone</span>
          <input
            className="field__input"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            maxLength={20}
          />
        </label>
      </div>

      {props.error && <p className="form__error" role="alert">{props.error}</p>}

      <div className="form__actions">
        <button className="btn btn--primary" type="submit" disabled={props.submitting}>
          {props.submitting ? 'Saving…' : props.mode === 'create' ? 'Add contact' : 'Save changes'}
        </button>
        <button className="btn btn--ghost" type="button" onClick={props.onCancel}>
          Cancel
        </button>
      </div>
    </form>
  )
}
