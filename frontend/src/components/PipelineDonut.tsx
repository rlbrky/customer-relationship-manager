import { DonutChart, type DonutSlice } from './DonutChart'
import type { StageSummary } from '../types/dashboard'
import { dealStageLabel } from '../types/deal'

interface PipelineDonutProps {
  stages: StageSummary[]
}

/**
 * Share of open deals by stage, **by count** — the bar chart below carries value.
 * A stage holding one large deal and a stage holding six small ones look identical
 * in money and nothing alike here, which is the reason both views exist.
 *
 * Stages are ordinal, not categorical: PROSPECT → NEGOTIATION is a progression, so
 * they take one hue stepped light → dark rather than four unrelated colours. Four
 * different hues would claim the stages have nothing to do with each other.
 */
export function PipelineDonut({ stages }: PipelineDonutProps) {
  const slices: DonutSlice[] = stages.map((stage, index) => ({
    label: dealStageLabel(stage.stage),
    value: stage.dealCount,
    color: `var(--stage-${index + 1})`,
    display: String(stage.dealCount),
  }))

  const total = stages.reduce((sum, stage) => sum + stage.dealCount, 0)

  return (
    <DonutChart
      slices={slices}
      centerValue={String(total)}
      centerLabel={total === 1 ? 'open deal' : 'open deals'}
      ariaLabel={`Open deals by stage: ${slices.map((s) => `${s.label} ${s.value}`).join(', ')}`}
    />
  )
}
