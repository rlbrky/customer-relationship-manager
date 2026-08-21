import { useState } from 'react'
import type { DailyActivity } from '../types/dashboard'

interface AreaChartProps {
  points: DailyActivity[]
  /** Announced in place of the drawing. */
  ariaLabel: string
}

// Fixed viewBox, rendered at width:100%. Strokes carry vector-effect so they stay
// 2px however far the box is scaled — the alternative, measuring the container,
// needs a ResizeObserver to say the same thing.
const W = 720
const H = 200
const PAD_LEFT = 36
const PAD_RIGHT = 8
const PAD_TOP = 10
const PLOT_H = 150

const GRID_LINES = 4

/** "2026-08-21" → "21 Aug". Sliced, never parsed: a LocalDate has no zone. */
function shortDay(day: string): string {
  const [, month, date] = day.split('-')
  const names = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
                 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
  return `${Number(date)} ${names[Number(month) - 1]}`
}

export function AreaChart({ points, ariaLabel }: AreaChartProps) {
  const [hover, setHover] = useState<number | null>(null)

  if (points.length < 2) {
    return <p className="chart__empty">Not enough history to draw a trend yet.</p>
  }

  const max = Math.max(...points.map((p) => p.total))
  // Round the axis up to something divisible by the gridline count, so ticks land
  // on whole numbers instead of 1.75 activities.
  const niceMax = Math.max(GRID_LINES, Math.ceil(max / GRID_LINES) * GRID_LINES)

  const plotW = W - PAD_LEFT - PAD_RIGHT
  const x = (i: number) => PAD_LEFT + (i / (points.length - 1)) * plotW
  const y = (value: number) => PAD_TOP + PLOT_H - (value / niceMax) * PLOT_H

  const line = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${x(i)} ${y(p.total)}`).join(' ')
  const area = `${line} L ${x(points.length - 1)} ${y(0)} L ${x(0)} ${y(0)} Z`

  const ticks = Array.from({ length: GRID_LINES + 1 }, (_, i) => (niceMax / GRID_LINES) * i)
  // First, middle and last only — a label under all thirty days is unreadable.
  const labelled = [0, Math.floor((points.length - 1) / 2), points.length - 1]

  const active = hover === null ? null : points[hover]

  return (
    <div className="chart">
      <svg
        className="chart__svg"
        viewBox={`0 0 ${W} ${H}`}
        role="img"
        aria-label={ariaLabel}
        onMouseLeave={() => setHover(null)}
        onMouseMove={(event) => {
          const box = event.currentTarget.getBoundingClientRect()
          // Back out of screen pixels into viewBox units before snapping to a point.
          const local = ((event.clientX - box.left) / box.width) * W
          const ratio = (local - PAD_LEFT) / plotW
          const index = Math.round(ratio * (points.length - 1))
          setHover(index < 0 || index >= points.length ? null : index)
        }}
      >
        {ticks.map((tick) => (
          <g key={tick}>
            <line
              className="chart__grid"
              x1={PAD_LEFT} x2={W - PAD_RIGHT}
              y1={y(tick)} y2={y(tick)}
              vectorEffect="non-scaling-stroke"
            />
            <text className="chart__tick" x={PAD_LEFT - 8} y={y(tick) + 4} textAnchor="end">
              {tick}
            </text>
          </g>
        ))}

        <path className="chart__area" d={area} />
        <path className="chart__line" d={line} vectorEffect="non-scaling-stroke" />

        {hover !== null && (
          <>
            <line
              className="chart__crosshair"
              x1={x(hover)} x2={x(hover)}
              y1={PAD_TOP} y2={PAD_TOP + PLOT_H}
              vectorEffect="non-scaling-stroke"
            />
            {/* 2px ring in the surface colour so the dot stays legible on the line */}
            <circle className="chart__dot" cx={x(hover)} cy={y(points[hover].total)} r={4.5} />
          </>
        )}

        {labelled.map((i) => (
          <text
            key={i}
            className="chart__tick"
            x={x(i)}
            y={H - 4}
            textAnchor={i === 0 ? 'start' : i === points.length - 1 ? 'end' : 'middle'}
          >
            {shortDay(points[i].day)}
          </text>
        ))}
      </svg>

      {active && (
        <div
          className="chart__tip"
          style={{ left: `${((x(hover!) - PAD_LEFT) / plotW) * 100}%` }}
        >
          <strong>{active.total}</strong> {active.total === 1 ? 'activity' : 'activities'}
          <span className="chart__tip-day">{shortDay(active.day)}</span>
        </div>
      )}
    </div>
  )
}
