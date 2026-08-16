/**
 * The backend uses two different time types, and they must NOT be treated the same.
 *
 *   occurredAt  Java Instant        "2026-09-01T11:00:00Z"   absolute moment, UTC
 *   dueAt       Java LocalDateTime  "2026-09-01T14:00:00"    wall clock, no zone
 *
 * An Instant must be converted between UTC and the viewer's local time.
 * A LocalDateTime must NOT be converted — 2pm is 2pm regardless of where you
 * are or whether DST shifted. Running it through a Date would silently move it.
 */

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

/** Local wall-clock parts of a Date, as an <input type="datetime-local"> value. */
function toInputValue(date: Date): string {
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  )
}

/** "Now", ready for a datetime-local input. */
export function nowForInput(): string {
  return toInputValue(new Date())
}

// ── Instant (occurredAt) ─────────────────────────────────────────────────────

/** Instant → input value, converted into the viewer's local time. */
export function instantToInput(instant: string): string {
  return toInputValue(new Date(instant))
}

/** Input value → Instant. The naive string is read as local time, then sent as UTC. */
export function inputToInstant(value: string): string {
  return new Date(value).toISOString()
}

/** Instant → human text in the viewer's own timezone. */
export function formatInstant(instant: string): string {
  return new Date(instant).toLocaleString(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  })
}

/** Local calendar day of an Instant — used to group the timeline. */
export function dayKey(instant: string): string {
  return new Date(instant).toLocaleDateString(undefined, {
    weekday: 'long',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })
}

// ── LocalDateTime (dueAt) ────────────────────────────────────────────────────

/** LocalDateTime → input value. Pure string surgery: no Date, no zone shift. */
export function localDateTimeToInput(value: string): string {
  return value.slice(0, 16)
}

/** Input value → LocalDateTime. Sent through untouched, seconds added. */
export function inputToLocalDateTime(value: string): string {
  return value.length === 16 ? `${value}:00` : value
}

/** LocalDateTime → human text, read literally off the string. */
export function formatLocalDateTime(value: string): string {
  const [date, time] = value.split('T')
  return `${date} ${time?.slice(0, 5) ?? ''}`.trim()
}

/** Is this task due before now? Compared in local wall-clock terms. */
export function isOverdue(dueAt: string): boolean {
  return new Date(dueAt).getTime() < Date.now()
}
