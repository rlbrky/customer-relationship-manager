import type { DailyActivity } from '../types/dashboard'

interface WeekdayHeatmapProps {
  points: DailyActivity[]
}

const LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

/** Monday-first index from a "2026-08-21" LocalDate string, with no Date parsing. */
function weekdayIndex(day: string): number {
  const [year, month, date] = day.split('-').map(Number)
  // Sakamoto's algorithm — Date would drag a timezone into a calendar-only value.
  const shift = [0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4]
  const y = month < 3 ? year - 1 : year
  const sunday = (y + Math.floor(y / 4) - Math.floor(y / 100)
    + Math.floor(y / 400) + shift[month - 1] + date) % 7
  return (sunday + 6) % 7   // 0 = Monday
}

/**
 * Magnitude over a grid, so this is SEQUENTIAL colour: one hue, more-is-darker.
 * Zero is drawn as an empty bordered cell rather than the palest step — "nothing
 * happened" should read as absence, not as a small amount.
 *
 * Derived from the same thirty numbers the area chart uses; it needs no query of
 * its own.
 */
export function WeekdayHeatmap({ points }: WeekdayHeatmapProps) {
  const totals = new Array<number>(7).fill(0)
  for (const point of points) {
    totals[weekdayIndex(point.day)] += point.total
  }

  const max = Math.max(...totals)

  function step(value: number): number {
    if (value === 0 || max === 0) return 0
    return Math.min(4, Math.ceil((value / max) * 4))
  }

  return (
    <div className="heat">
      <div className="heat__grid">
        {LABELS.map((label, index) => (
          <div className="heat__col" key={label}>
            <div
              className={`heat__cell heat__cell--${step(totals[index])}`}
              title={`${label}: ${totals[index]} ${totals[index] === 1 ? 'activity' : 'activities'}`}
            >
              <span className="visually-hidden">
                {label}: {totals[index]} activities
              </span>
            </div>
            <span className="heat__label">{label}</span>
          </div>
        ))}
      </div>

      <div className="heat__scale">
        <span>Less</span>
        <span className="heat__cell heat__cell--0 heat__cell--key" />
        <span className="heat__cell heat__cell--1 heat__cell--key" />
        <span className="heat__cell heat__cell--2 heat__cell--key" />
        <span className="heat__cell heat__cell--3 heat__cell--key" />
        <span className="heat__cell heat__cell--4 heat__cell--key" />
        <span>More</span>
      </div>
    </div>
  )
}
