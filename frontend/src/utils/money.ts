/**
 * The backend stores money as BigDecimal precisely to avoid binary floating
 * point error. Jackson then serialises it as a JSON *number*, and JavaScript
 * numbers are IEEE-754 doubles — so the moment a value crosses the wire, that
 * guarantee is gone on this side.
 *
 * Practical consequences:
 *  - summing values client-side can drift (0.1 + 0.2 === 0.30000000000000004),
 *    so a total is rounded to cents before it is shown;
 *  - never send a client-computed money value back to the server. Totals here
 *    are display only. Anything authoritative is computed in SQL.
 */

const formatter = new Intl.NumberFormat(undefined, {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

/** Formats a deal value. No currency symbol — the model doesn't store one. */
export function formatMoney(value: number | null): string {
  return value === null ? '—' : formatter.format(value)
}

/** Sum for display, rounded to cents so accumulated float error never shows. */
export function sumMoney(values: Array<number | null>): number {
  const total = values.reduce<number>((acc, v) => acc + (v ?? 0), 0)
  return Math.round(total * 100) / 100
}
