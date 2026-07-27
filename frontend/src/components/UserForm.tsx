import { useState, type FormEvent } from 'react'
import type { User } from '../types/auth'
import { ALL_ROLES, roleLabel, type UserCreateRequest, type UserUpdateRequest } from '../types/user'

/**
 * One form, two modes. The union keeps them honest: create needs a username and
 * password, edit has neither (the backend's UserUpdateRequest has no password
 * field) but gains the `enabled` toggle.
 */
export type UserFormProps =
  | {
      mode: 'create'
      submitting: boolean
      error: string | null
      onSubmit: (request: UserCreateRequest) => void
      onCancel: () => void
    }
  | {
      mode: 'edit'
      user: User
      submitting: boolean
      error: string | null
      onSubmit: (request: UserUpdateRequest) => void
      onCancel: () => void
    }

export function UserForm(props: UserFormProps) {
  const editing = props.mode === 'edit' ? props.user : null

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [email, setEmail] = useState(editing?.email ?? '')
  const [firstName, setFirstName] = useState(editing?.firstName ?? '')
  const [lastName, setLastName] = useState(editing?.lastName ?? '')
  const [enabled, setEnabled] = useState(editing?.enabled ?? true)
  const [roles, setRoles] = useState<string[]>(editing?.roles ?? ['ROLE_SALES_REP'])

  function toggleRole(role: string) {
    setRoles((current) =>
      current.includes(role) ? current.filter((r) => r !== role) : [...current, role],
    )
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (props.mode === 'create') {
      props.onSubmit({ username, email, password, firstName, lastName, roles })
    } else {
      props.onSubmit({ email, firstName, lastName, enabled, roles })
    }
  }

  return (
    <form className="panel form" onSubmit={handleSubmit}>
      <h2 className="panel__title">
        {props.mode === 'create' ? 'New user' : `Edit ${editing?.username}`}
      </h2>

      {props.mode === 'create' && (
        <label className="field">
          <span className="field__label">Username</span>
          <input
            className="field__input"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            maxLength={30}
            required
            autoFocus
          />
        </label>
      )}

      <div className="field-row">
        <label className="field">
          <span className="field__label">First name</span>
          <input
            className="field__input"
            value={firstName}
            onChange={(e) => setFirstName(e.target.value)}
            required
          />
        </label>
        <label className="field">
          <span className="field__label">Last name</span>
          <input
            className="field__input"
            value={lastName}
            onChange={(e) => setLastName(e.target.value)}
            required
          />
        </label>
      </div>

      <label className="field">
        <span className="field__label">Email</span>
        <input
          className="field__input"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
      </label>

      {props.mode === 'create' && (
        <label className="field">
          <span className="field__label">Password</span>
          <input
            className="field__input"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            /* mirrors the backend's @Size(min = 8) — the server is the real gate */
            minLength={8}
            autoComplete="new-password"
            required
          />
          <span className="field__hint">At least 8 characters.</span>
        </label>
      )}

      <fieldset className="fieldset">
        <legend className="field__label">Roles</legend>
        <div className="checks">
          {ALL_ROLES.map((role) => (
            <label className="check" key={role}>
              <input
                type="checkbox"
                checked={roles.includes(role)}
                onChange={() => toggleRole(role)}
              />
              <span>{roleLabel(role)}</span>
            </label>
          ))}
        </div>
      </fieldset>

      {props.mode === 'edit' && (
        <label className="check">
          <input
            type="checkbox"
            checked={enabled}
            onChange={(e) => setEnabled(e.target.checked)}
          />
          <span>Active</span>
        </label>
      )}

      {props.error && <p className="form__error" role="alert">{props.error}</p>}

      <div className="form__actions">
        <button className="btn btn--primary" type="submit" disabled={props.submitting || roles.length === 0}>
          {props.submitting ? 'Saving…' : props.mode === 'create' ? 'Create user' : 'Save changes'}
        </button>
        <button className="btn btn--ghost" type="button" onClick={props.onCancel}>
          Cancel
        </button>
      </div>
    </form>
  )
}
