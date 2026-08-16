import type { Activity } from '../types/activity'
import { activityTypeLabel } from '../types/activity'
import { dayKey, formatInstant, formatLocalDateTime, isOverdue } from '../utils/datetime'

interface ActivityTimelineProps {
  activities: Activity[]
  onEdit: (activity: Activity) => void
  onDelete: (activity: Activity) => void
  onToggleComplete: (activity: Activity) => void
  emptyMessage: string
}

/** Consecutive activities sharing a local calendar day, in the order given. */
function groupByDay(activities: Activity[]): Array<{ day: string; items: Activity[] }> {
  const groups: Array<{ day: string; items: Activity[] }> = []
  for (const activity of activities) {
    const day = dayKey(activity.occurredAt)
    const last = groups[groups.length - 1]
    if (last && last.day === day) last.items.push(activity)
    else groups.push({ day, items: [activity] })
  }
  return groups
}

export function ActivityTimeline({
  activities,
  onEdit,
  onDelete,
  onToggleComplete,
  emptyMessage,
}: ActivityTimelineProps) {
  if (activities.length === 0) {
    return <p className="card__hint timeline__empty">{emptyMessage}</p>
  }

  return (
    <div className="timeline">
      {groupByDay(activities).map(({ day, items }) => (
        <section className="timeline__group" key={day}>
          <h3 className="timeline__day">{day}</h3>

          {items.map((activity) => {
            const overdue =
              activity.type === 'TASK' &&
              !activity.completed &&
              activity.dueAt !== null &&
              isOverdue(activity.dueAt)

            return (
              <article className="entry" key={activity.id}>
                <span className={`entry__type entry__type--${activity.type.toLowerCase()}`}>
                  {activityTypeLabel(activity.type)}
                </span>

                <div className="entry__body">
                  <div className="entry__head">
                    {activity.type === 'TASK' && (
                      <input
                        className="entry__check"
                        type="checkbox"
                        checked={activity.completed}
                        onChange={() => onToggleComplete(activity)}
                        aria-label={`Mark "${activity.subject}" ${activity.completed ? 'incomplete' : 'complete'}`}
                      />
                    )}
                    <span className={activity.completed ? 'entry__subject entry__subject--done' : 'entry__subject'}>
                      {activity.subject}
                    </span>
                  </div>

                  <p className="entry__meta">
                    {formatInstant(activity.occurredAt)}
                    {activity.contactName && <> · with <strong>{activity.contactName}</strong></>}
                    {activity.dueAt && (
                      <>
                        {' · '}
                        <span className={overdue ? 'entry__due entry__due--overdue' : 'entry__due'}>
                          due {formatLocalDateTime(activity.dueAt)}
                        </span>
                      </>
                    )}
                  </p>

                  {activity.notes && <p className="entry__notes">{activity.notes}</p>}
                </div>

                <div className="entry__actions">
                  <button
                    className="btn btn--small btn--ghost"
                    type="button"
                    onClick={() => onEdit(activity)}
                  >
                    Edit
                  </button>
                  <button
                    className="btn btn--small btn--danger"
                    type="button"
                    onClick={() => onDelete(activity)}
                  >
                    Delete
                  </button>
                </div>
              </article>
            )
          })}
        </section>
      ))}
    </div>
  )
}
