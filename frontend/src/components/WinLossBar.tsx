import { formatMoney } from '../utils/money'

interface WinLossBarProps {
  wonCount: number
  lostCount: number
  wonValue: number
  /** Fraction 0–1, or null when nothing has closed yet. */
  winRate: number | null
}

export function WinLossBar({ wonCount, lostCount, wonValue, winRate }: WinLossBarProps) {
  // null is not 0%. "Nothing has closed yet" and "we lose every deal" are
  // different facts, and a 0%-wide bar would say the second one.
  if (winRate === null) {
    return (
      <p className="split__empty">
        No deals have closed yet — win rate appears once one is marked won or lost.
      </p>
    )
  }

  const closed = wonCount + lostCount
  const wonPercent = (wonCount / closed) * 100

  return (
    <div className="split">
      <div className="split__rate">{(winRate * 100).toFixed(1)}%</div>
      <div className="split__caption">win rate across {closed} closed deals</div>

      <div className="split__bar" aria-hidden="true">
        <div className="split__seg split__seg--won" style={{ width: `${wonPercent}%` }} />
        <div className="split__seg split__seg--lost" style={{ width: `${100 - wonPercent}%` }} />
      </div>

      <dl className="split__legend">
        <div className="split__item">
          <dt><span className="split__swatch split__swatch--won" />Won</dt>
          <dd>{wonCount} · {formatMoney(wonValue)}</dd>
        </div>
        <div className="split__item">
          <dt><span className="split__swatch split__swatch--lost" />Lost</dt>
          <dd>{lostCount}</dd>
        </div>
      </dl>
    </div>
  )
}
