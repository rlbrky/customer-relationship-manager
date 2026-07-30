import { useState, type FormEvent } from 'react'
import type { Account, AccountCreateRequest, AccountUpdateRequest } from '../types/account'
import type { User } from '../types/auth'

/**
 * `owners` is non-empty only for admins (see AccountsPage) — everyone else keeps
 * the existing owner on edit, and gets themselves as owner on create, which is
 * exactly what the backend does when ownerId is null.
 */
export type AccountFormProps =
  | {
      mode: 'create'
      owners: User[]
      submitting: boolean
      error: string | null
      onSubmit: (request: AccountCreateRequest) => void
      onCancel: () => void
    }
  | {
      mode: 'edit'
      account: Account
      owners: User[]
      submitting: boolean
      error: string | null
      onSubmit: (request: AccountUpdateRequest) => void
      onCancel: () => void
    }

export function AccountForm(props: AccountFormProps) {
  const editing = props.mode === 'edit' ? props.account : null

  const [name, setName] = useState(editing?.name ?? '')
  const [industry, setIndustry] = useState(editing?.industry ?? '')
  const [website, setWebsite] = useState(editing?.website ?? '')
  const [phone, setPhone] = useState(editing?.phone ?? '')
  const [ownerId, setOwnerId] = useState<number | null>(editing?.ownerId ?? null)

  /** empty string → null: the backend columns are nullable, "" is not the same as absent */
  const orNull = (value: string) => (value.trim() === '' ? null : value.trim())

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const common = {
      name: name.trim(),
      industry: orNull(industry),
      website: orNull(website),
      phone: orNull(phone),
    }

    if (props.mode === 'create') {
      props.onSubmit({ ...common, ownerId })
    } else {
      // update requires an ownerId — fall back to the account's current owner
      props.onSubmit({ ...common, ownerId: ownerId ?? props.account.ownerId })
    }
  }

  return (
    <form className="panel form" onSubmit={handleSubmit}>
      <h2 className="panel__title">
        {props.mode === 'create' ? 'New account' : `Edit ${editing?.name}`}
      </h2>

      <label className="field">
        <span className="field__label">Company name</span>
        <input
          className="field__input"
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={150}
          required
          autoFocus
        />
      </label>

      <div className="field-row">
        <label className="field">
          <span className="field__label">Industry</span>
          <input
            className="field__input"
            value={industry}
            onChange={(e) => setIndustry(e.target.value)}
            maxLength={100}
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

      <label className="field">
        <span className="field__label">Website</span>
        <input
          className="field__input"
          value={website}
          onChange={(e) => setWebsite(e.target.value)}
          maxLength={254}
          placeholder="https://example.com"
        />
      </label>

      {props.owners.length > 0 && (
        <label className="field">
          <span className="field__label">Owner</span>
          <select
            className="field__input"
            value={ownerId ?? ''}
            onChange={(e) => setOwnerId(e.target.value === '' ? null : Number(e.target.value))}
          >
            {props.mode === 'create' && <option value="">Me</option>}
            {props.owners.map((owner) => (
              <option key={owner.id} value={owner.id}>
                {owner.firstName} {owner.lastName} ({owner.username})
              </option>
            ))}
          </select>
        </label>
      )}

      {props.error && <p className="form__error" role="alert">{props.error}</p>}

      <div className="form__actions">
        <button className="btn btn--primary" type="submit" disabled={props.submitting}>
          {props.submitting ? 'Saving…' : props.mode === 'create' ? 'Create account' : 'Save changes'}
        </button>
        <button className="btn btn--ghost" type="button" onClick={props.onCancel}>
          Cancel
        </button>
      </div>
    </form>
  )
}
