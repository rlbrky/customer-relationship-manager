import type { HealthResponse, HealthStatus } from '../types/health'

/**
 * A discriminated union: the card can only ever be in exactly one of these
 * three shapes, and TypeScript will not let you read `data` unless you have
 * already narrowed to `state: 'loaded'`.
 */
export type HealthCardProps =
  | { state: 'loading' }
  | { state: 'error'; message: string }
  | { state: 'loaded'; data: HealthResponse }

export function HealthCard(props: HealthCardProps) {
  if (props.state === 'loading') {
    return (
      <section className="card">
        <header className="card__header">
          <span className="dot dot--idle" />
          <h2 className="card__title">Checking…</h2>
        </header>
        <p className="card__hint">Contacting the CRM API.</p>
      </section>
    )
  }

  if (props.state === 'error') {
    return (
      <section className="card card--down">
        <header className="card__header">
          <span className="dot dot--down" />
          <h2 className="card__title">Unreachable</h2>
        </header>
        <p className="card__hint">{props.message}</p>
      </section>
    )
  }

  const { status, db, timestamp } = props.data
  const healthy = status === 'UP' && db === 'UP'

  return (
    <section className={healthy ? 'card card--up' : 'card card--down'}>
      <header className="card__header">
        <span className={healthy ? 'dot dot--up' : 'dot dot--down'} />
        <h2 className="card__title">{healthy ? 'All systems up' : 'Degraded'}</h2>
      </header>

      <dl className="rows">
        <div className="row">
          <dt>API</dt>
          <dd><StatusPill value={status} /></dd>
        </div>
        <div className="row">
          <dt>Database</dt>
          <dd><StatusPill value={db} /></dd>
        </div>
      </dl>

      <p className="card__hint">Checked at {new Date(timestamp).toLocaleTimeString()}</p>
    </section>
  )
}

function StatusPill({ value }: { value: HealthStatus }) {
  return <span className={value === 'UP' ? 'pill pill--up' : 'pill pill--down'}>{value}</span>
}
