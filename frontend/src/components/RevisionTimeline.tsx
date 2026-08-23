import type { Revision } from '../types/audit'
import { fieldLabel, revisionTypeLabel } from '../types/audit'
import { dayKey, formatInstant } from '../utils/datetime'

interface RevisionTimelineProps {
  revisions: Revision[]
}

/** Consecutive revisions sharing a local calendar day, in the order given. */
function groupByDay(revisions: Revision[]): Array<{ day: string; items: Revision[] }> {
  const groups: Array<{ day: string; items: Revision[] }> = []
  for (const revision of revisions) {
    const day = dayKey(revision.changedAt)
    const last = groups[groups.length - 1]
    if (last && last.day === day) last.items.push(revision)
    else groups.push({ day, items: [revision] })
  }
  return groups
}

/** A missing value is an em dash, never the string "null". */
function display(value: string | null): string {
  return value === null || value === '' ? '—' : value
}

export function RevisionTimeline({ revisions }: RevisionTimelineProps) {
  if (revisions.length === 0) {
    return (
      <p className="card__hint timeline__empty">
        No recorded changes. Records edited before auditing was switched on have no
        history until their next change.
      </p>
    )
  }

  return (
    <div className="timeline">
      {groupByDay(revisions).map(({ day, items }) => (
        <section className="timeline__group" key={day}>
          <h3 className="timeline__day">{day}</h3>

          {items.map((revision) => (
            <article className="entry" key={revision.revision}>
              <span className={`entry__type rev__type--${revision.type.toLowerCase()}`}>
                {revisionTypeLabel(revision.type)}
              </span>

              <div className="entry__body">
                <div className="entry__meta">
                  {display(revision.changedBy)} · {formatInstant(revision.changedAt)}
                  {' · '}
                  <span className="rev__number">r{revision.revision}</span>
                </div>

                {/* Empty on DEL by design — the type already says everything, and
                    Envers has no field data to show for a deletion. */}
                {revision.changes.length > 0 && (
                  <dl className="rev__changes">
                    {revision.changes.map((change) => (
                      <div className="rev__change" key={change.field}>
                        <dt className="rev__field">{fieldLabel(change.field)}</dt>
                        <dd className="rev__values">
                          {/* On a creation every field comes from nothing; showing
                              "— → Acme" for each one is noise, so ADD shows values only. */}
                          {revision.type !== 'ADD' && (
                            <>
                              <span className="rev__from">{display(change.from)}</span>
                              <span className="rev__arrow" aria-label="changed to">→</span>
                            </>
                          )}
                          <span className="rev__to">{display(change.to)}</span>
                        </dd>
                      </div>
                    ))}
                  </dl>
                )}
              </div>
            </article>
          ))}
        </section>
      ))}
    </div>
  )
}
