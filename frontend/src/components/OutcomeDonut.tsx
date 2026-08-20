import { DonutChart, type DonutSlice } from './DonutChart'

interface OutcomeDonutProps {
  openCount: number
  wonCount: number
  lostCount: number
  /** Fraction 0–1, or null when nothing has closed. */
  winRate: number | null
}

/**
 * Where every deal stands: still live, won, or lost.
 *
 * These are **status** colours, not series colours — won means good and lost means
 * bad, so they reuse the app's --up / --down tokens rather than taking categorical
 * slots. Status colour always ships with a label, which the legend provides.
 */
export function OutcomeDonut({ openCount, wonCount, lostCount, winRate }: OutcomeDonutProps) {
  const slices: DonutSlice[] = [
    { label: 'Open', value: openCount, color: 'var(--idle)', display: String(openCount) },
    { label: 'Won', value: wonCount, color: 'var(--up)', display: String(wonCount) },
    { label: 'Lost', value: lostCount, color: 'var(--down)', display: String(lostCount) },
  ]

  // null is not 0%. Nothing has closed, so there is no rate to show yet — and a
  // "0%" in the hole would claim we lose everything.
  const hasRate = winRate !== null

  return (
    <>
      <DonutChart
        slices={slices}
        centerValue={hasRate ? `${(winRate * 100).toFixed(0)}%` : '—'}
        centerLabel={hasRate ? 'win rate' : 'nothing closed'}
        ariaLabel={
          `Deals by outcome: ${openCount} open, ${wonCount} won, ${lostCount} lost.` +
          (hasRate ? ` Win rate ${(winRate * 100).toFixed(1)} percent.` : '')
        }
      />
      {!hasRate && (
        <p className="donut__note">
          Win rate appears once a deal is marked won or lost.
        </p>
      )}
    </>
  )
}
