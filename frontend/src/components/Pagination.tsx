interface PaginationProps {
  /** zero-based */
  number: number
  totalPages: number
  totalElements: number
  onChange: (page: number) => void
  /** singular noun for the status line, e.g. "account" → "5 accounts" */
  label?: string
}

export function Pagination({
  number,
  totalPages,
  totalElements,
  onChange,
  label = 'item',
}: PaginationProps) {
  if (totalElements === 0) return null

  const first = number === 0
  const last = number >= totalPages - 1

  return (
    <nav className="pager" aria-label="Pagination">
      <span className="pager__status">
        Page {number + 1} of {Math.max(totalPages, 1)} · {totalElements}{' '}
        {totalElements === 1 ? label : `${label}s`}
      </span>
      <div className="pager__buttons">
        <button
          className="btn btn--small btn--ghost"
          type="button"
          onClick={() => onChange(number - 1)}
          disabled={first}
        >
          Previous
        </button>
        <button
          className="btn btn--small btn--ghost"
          type="button"
          onClick={() => onChange(number + 1)}
          disabled={last}
        >
          Next
        </button>
      </div>
    </nav>
  )
}
