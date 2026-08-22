interface ConflictBannerProps {
  /** The record type in lower case — "account", "deal", "contact". */
  noun: string
  onReload: () => void
}

/**
 * Shown when a save came back 409: the record moved on while the user was typing.
 *
 * The edits stay on screen. Discarding someone's work to show them fresher data
 * is not a fix, so the choice is theirs — and the button says plainly what it costs.
 */
export function ConflictBanner({ noun, onReload }: ConflictBannerProps) {
  return (
    <div className="conflict" role="alert">
      <p className="conflict__text">
        Someone else changed this {noun} while you were editing. Your changes are
        still here — loading the current values will discard them.
      </p>
      <button className="btn btn--small" type="button" onClick={onReload}>
        Load current values
      </button>
    </div>
  )
}
