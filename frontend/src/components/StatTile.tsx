import { Sparkline } from './Sparkline'

interface StatTileProps {
  label: string
  /** Pre-formatted — the tile never decides how a number should read. */
  value: string
  hint?: string
  tone?: 'neutral' | 'up' | 'down'
  /** Optional trend for the same measure the value reports. */
  spark?: number[]
}

export function StatTile({ label, value, hint, tone = 'neutral', spark }: StatTileProps) {
  return (
    <div className={tone === 'neutral' ? 'tile' : `tile tile--${tone}`}>
      <span className="tile__label">{label}</span>
      <span className="tile__value">{value}</span>
      {hint && <span className="tile__hint">{hint}</span>}
      {spark && <Sparkline values={spark} />}
    </div>
  )
}
