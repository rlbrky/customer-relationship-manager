import { useState, type FormEvent } from 'react'
import type { Account } from '../types/account'
import type {
  Deal,
  DealCreateRequest,
  DealStage,
  DealStageHistory,
  DealUpdateRequest,
} from '../types/deal'
import { DEAL_STAGES, dealStageLabel } from '../types/deal'

export type DealFormProps =
  | {
      mode: 'create'
      accounts: Account[]
      submitting: boolean
      error: string | null
      onSubmit: (accountId: number, request: DealCreateRequest) => void
      onCancel: () => void
    }
  | {
      mode: 'edit'
      deal: Deal
      /** Append-only stage history — read-only, shown for context. */
      history: DealStageHistory[]
      submitting: boolean
      error: string | null
      onSubmit: (request: DealUpdateRequest) => void
      onCancel: () => void
    }

export function DealForm(props: DealFormProps) {
  const editing = props.mode === 'edit' ? props.deal : null

  const [accountId, setAccountId] = useState<number | ''>('')
  const [title, setTitle] = useState(editing?.title ?? '')
  const [value, setValue] = useState(editing?.value?.toString() ?? '')
  const [stage, setStage] = useState<DealStage>('PROSPECT')
  const [expectedCloseDate, setExpectedCloseDate] = useState(editing?.expectedCloseDate ?? '')

  function handleSubmit(event: FormEvent) {
    event.preventDefault()

    const common = {
      title: title.trim(),
      // empty input means "not estimated yet", which the backend models as null
      value: value.trim() === '' ? null : Number(value),
      expectedCloseDate: expectedCloseDate === '' ? null : expectedCloseDate,
    }

    if (props.mode === 'create') {
      if (accountId === '') return
      props.onSubmit(accountId, { ...common, stage })
    } else {
      // no stage here on purpose — moving a deal is a transition, not an edit
      props.onSubmit(common)
    }
  }

  return (
    <form className="panel form" onSubmit={handleSubmit}>
      <h2 className="panel__title">
        {props.mode === 'create' ? 'New deal' : `Edit ${editing?.title}`}
      </h2>

      {props.mode === 'create' && (
        <div className="field-row">
          <label className="field">
            <span className="field__label">Account</span>
            <select
              className="field__input"
              value={accountId}
              onChange={(e) => setAccountId(e.target.value === '' ? '' : Number(e.target.value))}
              required
            >
              <option value="">Select an account…</option>
              {props.accounts.map((account) => (
                <option key={account.id} value={account.id}>{account.name}</option>
              ))}
            </select>
          </label>

          <label className="field">
            <span className="field__label">Starting stage</span>
            <select
              className="field__input"
              value={stage}
              onChange={(e) => setStage(e.target.value as DealStage)}
            >
              {DEAL_STAGES.map((s) => (
                <option key={s} value={s}>{dealStageLabel(s)}</option>
              ))}
            </select>
          </label>
        </div>
      )}

      <label className="field">
        <span className="field__label">Title</span>
        <input
          className="field__input"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={200}
          required
          autoFocus
        />
      </label>

      <div className="field-row">
        <label className="field">
          <span className="field__label">Value</span>
          <input
            className="field__input"
            type="number"
            step="0.01"
            min="0"
            value={value}
            onChange={(e) => setValue(e.target.value)}
          />
          <span className="field__hint">Leave blank if not estimated yet.</span>
        </label>

        <label className="field">
          <span className="field__label">Expected close</span>
          <input
            className="field__input"
            type="date"
            value={expectedCloseDate}
            onChange={(e) => setExpectedCloseDate(e.target.value)}
          />
        </label>
      </div>

      {props.error && <p className="form__error" role="alert">{props.error}</p>}

      <div className="form__actions">
        <button className="btn btn--primary" type="submit" disabled={props.submitting}>
          {props.submitting ? 'Saving…' : props.mode === 'create' ? 'Create deal' : 'Save changes'}
        </button>
        <button className="btn btn--ghost" type="button" onClick={props.onCancel}>
          Cancel
        </button>
      </div>

      {props.mode === 'edit' && (
        <section className="history">
          <h3 className="history__title">Stage history</h3>
          {props.history.length === 0 ? (
            <p className="card__hint">
              No transitions recorded. Seeded deals start without history — it appears
              once the deal actually moves.
            </p>
          ) : (
            <ol className="history__list">
              {props.history.map((row) => (
                <li className="history__row" key={row.id}>
                  <span className="history__move">
                    {row.fromStage ? dealStageLabel(row.fromStage) : 'Created'}
                    {' → '}
                    {dealStageLabel(row.toStage)}
                  </span>
                  <span className="history__when">
                    {new Date(row.changedAt).toLocaleString()}
                    {row.changedBy && <> · {row.changedBy}</>}
                  </span>
                </li>
              ))}
            </ol>
          )}
        </section>
      )}
    </form>
  )
}
