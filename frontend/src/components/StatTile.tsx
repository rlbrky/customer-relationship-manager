interface StatTileProps {
  label: string
  /** Pre-formatted — the tile never decides how a number should read. */
  value: string
  hint?: string
  tone?: 'neutral' | 'up' | 'down'
}

export function StatTile({ label, value, hint, tone = 'neutral' }: StatTileProps) {
  return (
    <div className={tone === 'neutral' ? 'tile' : `tile tile--${tone}`}>
      <span className="tile__label">{label}</span>
      <span className="tile__value">{value}</span>
      {hint && <span className="tile__hint">{hint}</span>}
    </div>
  )
}
