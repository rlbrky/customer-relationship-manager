interface MeterProps {
  value: number
  max: number
  /** What the fraction means, e.g. "tasks completed". */
  caption: string
  /** Shown when max is 0 — a ratio with no denominator is not zero percent. */
  emptyMessage: string
}

/**
 * One ratio against a limit. Deliberately not a two-slice pie: that is the
 * canonical "the number is the chart" case, and a circle adds nothing a bar and
 * a percentage don't already say.
 */
export function Meter({ value, max, caption, emptyMessage }: MeterProps) {
  if (max === 0) {
    return <p className="meter__empty">{emptyMessage}</p>
  }

  const percent = (value / max) * 100

  return (
    <div className="meter">
      <div className="meter__value">{percent.toFixed(0)}%</div>
      <div className="meter__caption">
        {value} of {max} {caption}
      </div>

      <div
        className="meter__track"
        role="progressbar"
        aria-valuenow={Math.round(percent)}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={caption}
      >
        <div className="meter__fill" style={{ width: `${percent}%` }} />
      </div>
    </div>
  )
}
