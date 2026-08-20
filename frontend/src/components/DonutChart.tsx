export interface DonutSlice {
  label: string
  value: number
  /** A CSS colour — normally a var(--…) so it follows the theme. */
  color: string
  /** Legend text for the value column. Falls back to the raw number. */
  display?: string
}

interface DonutChartProps {
  slices: DonutSlice[]
  /** The number in the hole. Pre-formatted; the chart never decides that. */
  centerValue: string
  centerLabel: string
  /** Announced to screen readers in place of the drawing. */
  ariaLabel: string
}

const SIZE = 168
const STROKE = 18
const RADIUS = (SIZE - STROKE) / 2
const CIRCUMFERENCE = 2 * Math.PI * RADIUS

/** 2px of surface between touching arcs — the gap separates them, not a border. */
const GAP = 2

export function DonutChart({ slices, centerValue, centerLabel, ariaLabel }: DonutChartProps) {
  const total = slices.reduce((sum, slice) => sum + slice.value, 0)

  // A zero-length arc still paints its line caps, so empty slices are dropped
  // rather than drawn. The legend still lists them at 0.
  const drawn = slices.filter((slice) => slice.value > 0)

  const arcs: Array<{ slice: DonutSlice; length: number; offset: number }> = []
  let cursor = 0
  for (const slice of drawn) {
    const length = (slice.value / total) * CIRCUMFERENCE
    arcs.push({ slice, length, offset: cursor })
    cursor += length
  }

  return (
    <div className="donut">
      <div className="donut__figure">
        <svg
          className="donut__svg"
          viewBox={`0 0 ${SIZE} ${SIZE}`}
          width={SIZE}
          height={SIZE}
          role="img"
          aria-label={ariaLabel}
        >
          {/* Rotated so the first slice starts at twelve o'clock instead of three. */}
          <g transform={`rotate(-90 ${SIZE / 2} ${SIZE / 2})`}>
            <circle
              className="donut__track"
              cx={SIZE / 2}
              cy={SIZE / 2}
              r={RADIUS}
              fill="none"
              strokeWidth={STROKE}
            />
            {arcs.map(({ slice, length, offset }) => {
              // Shorten every arc by the gap so neighbours never touch. Clamped
              // so a sliver never inverts into a full ring.
              const visible = Math.max(length - GAP, 0.5)

              return (
                <circle
                  key={slice.label}
                  className="donut__arc"
                  cx={SIZE / 2}
                  cy={SIZE / 2}
                  r={RADIUS}
                  fill="none"
                  stroke={slice.color}
                  strokeWidth={STROKE}
                  strokeDasharray={`${visible} ${CIRCUMFERENCE - visible}`}
                  strokeDashoffset={-offset}
                >
                  <title>
                    {slice.label}: {slice.display ?? slice.value}
                    {' '}({Math.round((slice.value / total) * 100)}%)
                  </title>
                </circle>
              )
            })}
          </g>
        </svg>

        <div className="donut__center">
          <span className="donut__value">{centerValue}</span>
          <span className="donut__caption">{centerLabel}</span>
        </div>
      </div>

      {/* The legend is the dependable identity channel — colour alone never is. */}
      <dl className="donut__legend">
        {slices.map((slice) => (
          <div className="donut__item" key={slice.label}>
            <dt className="donut__key">
              <span className="donut__swatch" style={{ background: slice.color }} />
              {slice.label}
            </dt>
            <dd className="donut__data">
              <span className="donut__amount">{slice.display ?? slice.value}</span>
              <span className="donut__share">
                {total === 0 ? '—' : `${Math.round((slice.value / total) * 100)}%`}
              </span>
            </dd>
          </div>
        ))}
      </dl>
    </div>
  )
}
