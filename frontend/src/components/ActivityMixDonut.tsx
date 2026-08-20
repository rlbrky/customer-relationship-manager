import { DonutChart, type DonutSlice } from './DonutChart'
import type { ActivityTypeSummary } from '../types/dashboard'
import type { ActivityType } from '../types/activity'
import { activityTypeLabel } from '../types/activity'

interface ActivityMixDonutProps {
  mix: ActivityTypeSummary[]
}

/**
 * Activity types are genuinely CATEGORICAL — a call is not more or less than a
 * note, they are five unrelated identities. So unlike the deal stages (one hue
 * stepped light to dark, because they are a progression) these take five
 * different hues from the categorical palette.
 *
 * Keyed by type rather than by array index, so a call is blue no matter what the
 * server sends or in what order. Colour follows the entity, never its rank.
 */
const SERIES: Record<ActivityType, string> = {
  CALL: 'var(--series-1)',
  EMAIL: 'var(--series-2)',
  MEETING: 'var(--series-3)',
  NOTE: 'var(--series-4)',
  TASK: 'var(--series-5)',
}

export function ActivityMixDonut({ mix }: ActivityMixDonutProps) {
  const slices: DonutSlice[] = mix.map((row) => ({
    label: activityTypeLabel(row.type),
    value: row.total,
    color: SERIES[row.type],
    display: String(row.total),
  }))

  const total = mix.reduce((sum, row) => sum + row.total, 0)

  return (
    <DonutChart
      slices={slices}
      centerValue={String(total)}
      centerLabel={total === 1 ? 'interaction' : 'interactions'}
      ariaLabel={
        `Activities by type: ${slices.map((s) => `${s.label} ${s.value}`).join(', ')}`
      }
    />
  )
}
