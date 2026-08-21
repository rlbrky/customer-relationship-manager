interface SparklineProps {
  values: number[]
}

const W = 100
const H = 24

/**
 * A stat tile's trend, not a chart: no axes, no labels, no hover. It says
 * "rising" or "spiky", and the tile's own number says how much.
 */
export function Sparkline({ values }: SparklineProps) {
  if (values.length < 2) return null

  const max = Math.max(...values, 1)
  const x = (i: number) => (i / (values.length - 1)) * W
  const y = (value: number) => H - (value / max) * (H - 2) - 1

  const line = values.map((v, i) => `${i === 0 ? 'M' : 'L'} ${x(i)} ${y(v)}`).join(' ')

  return (
    <svg className="spark" viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" aria-hidden="true">
      <path className="spark__area" d={`${line} L ${W} ${H} L 0 ${H} Z`} />
      <path className="spark__line" d={line} vectorEffect="non-scaling-stroke" />
    </svg>
  )
}
