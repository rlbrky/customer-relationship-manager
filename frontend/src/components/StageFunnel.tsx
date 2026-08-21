import type { StageSummary } from '../types/dashboard'
import { DEAL_STAGES, dealStageLabel } from '../types/deal'
import { formatMoney } from '../utils/money'

interface StageFunnelProps {
  stages: StageSummary[]
}

/**
 * Four bars, widths relative to the largest stage total. No chart library: this
 * is a div with a percentage width, so it themes with the rest of the app and
 * costs nothing to ship.
 */
export function StageFunnel({ stages }: StageFunnelProps) {
  const max = Math.max(...stages.map((s) => s.totalValue), 0)

  return (
    <div className="funnel">
      {stages.map((stage) => {
        // Every stage empty means max is 0 — dividing by it would give NaN and
        // React would render "NaN%" straight into the style attribute.
        const share = max === 0 ? 0 : (stage.totalValue / max) * 100

        // One 250k deal beside three 3k ones is a 100:1 ratio, and the small bars
        // round down to nothing. The floor keeps a non-zero value visibly non-zero;
        // the exact figure sits underneath, so nothing is being overstated.
        const width = stage.totalValue > 0 ? Math.max(share, 1.5) : 0

        return (
          <div className="funnel__row" key={stage.stage}>
            <div className="funnel__head">
              <span className="funnel__label">{dealStageLabel(stage.stage)}</span>
              <span className="funnel__count">
                {stage.dealCount} {stage.dealCount === 1 ? 'deal' : 'deals'}
              </span>
            </div>

            {/* The numbers are already text, so the bar itself is decoration. */}
            <div className="funnel__track" aria-hidden="true">
              {/* Same ramp step as the donut, so a stage keeps one identity across
                  both panels. Green here would be the status token for "good",
                  which pipeline value is not. */}
              <div
                className="funnel__fill"
                style={{
                  width: `${width}%`,
                  background: `var(--stage-${DEAL_STAGES.indexOf(stage.stage) + 1})`,
                }}
              />
            </div>

            <div className="funnel__value">{formatMoney(stage.totalValue)}</div>
          </div>
        )
      })}
    </div>
  )
}
