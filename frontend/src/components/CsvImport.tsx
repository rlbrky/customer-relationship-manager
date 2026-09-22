import { useId, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import type { ImportResult } from '../types/csv'

interface CsvImportProps {
  /** The record type in lower case — "account", "contact". */
  noun: string
  /** Override when the plural isn't just noun + "s". */
  nounPlural?: string
  /** Which columns this import understands. The only part that differs per page. */
  hint: ReactNode
  submitting: boolean
  /** A failed REQUEST — 413, 403, or a file that is not parseable CSV at all. */
  error: string | null
  /** A finished analysis. Row errors here are an outcome, not a failure. */
  result: ImportResult | null
  onSubmit: (file: File) => void
  onCancel: () => void
}

/**
 * A long file can produce a long list of complaints, and rendering all of them
 * helps nobody: fixing the first fifty will usually change the rest anyway.
 */
const MAX_SHOWN = 50

/**
 * Upload a CSV and show what the backend made of it. Every import endpoint returns
 * the same ImportResult shape, so everything except the column hint is shared.
 */
export function CsvImport({
  noun, nounPlural, hint, submitting, error, result, onSubmit, onCancel,
}: CsvImportProps) {

  const [file, setFile] = useState<File | null>(null)

  // Not a fixed "import-file": two panels on one page would share an id and the
  // second label would focus the first input.
  const inputId = useId()

  const plural = nounPlural ?? `${noun}s`

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (file) onSubmit(file)
  }

  const shown = result?.errors.slice(0, MAX_SHOWN) ?? []
  const hidden = (result?.errors.length ?? 0) - shown.length

  return (
    <section className="panel import">
      <h2 className="panel__title">Import {plural}</h2>
      <p className="field__hint">{hint}</p>

      <form className="form" onSubmit={handleSubmit}>
        <div className="field">
          <label className="field__label" htmlFor={inputId}>CSV file</label>
          <input
            className="field__input"
            id={inputId}
            type="file"
            accept=".csv,text/csv"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          />
        </div>

        {error && <p className="form__error" role="alert">{error}</p>}

        <div className="form__actions">
          <button className="btn btn--primary" type="submit" disabled={submitting || !file}>
            {submitting ? 'Importing…' : 'Import'}
          </button>
          <button className="btn btn--ghost" type="button" onClick={onCancel} disabled={submitting}>
            Close
          </button>
        </div>
      </form>

      {result && (
        <div className="import__result">
          {result.errors.length > 0 ? (
            <>
              {/* Said plainly, because "some of it worked" is the assumption people
                  arrive with — and acting on it means hunting for which rows landed.
                  Nothing was written, so the fix is: edit the file, upload it again. */}
              <p className="import__summary import__summary--bad" role="alert">
                Nothing was imported. {result.errors.length === 1
                  ? 'One row needs fixing'
                  : `${result.errors.length} problems need fixing`} — correct the file
                and upload it again.
              </p>

              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th className="import__line-head">Line</th>
                      <th>Column</th>
                      <th>Problem</th>
                    </tr>
                  </thead>
                  <tbody>
                    {shown.map((rowError, index) => (
                      // Nothing here is unique on its own: one line can fail on two
                      // columns, and the same column fails on many lines.
                      <tr key={`${rowError.line}-${rowError.column}-${index}`}>
                        <td className="table__mono">{rowError.line}</td>
                        <td className="table__muted">{rowError.column}</td>
                        <td>{rowError.message}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {hidden > 0 && (
                <p className="field__hint">…and {hidden} more.</p>
              )}
            </>
          ) : result.totalRows === 0 ? (
            <p className="import__summary">
              That file has a header but no rows — nothing to import.
            </p>
          ) : (
            <p className="import__summary import__summary--good">
              Imported {result.imported} {result.imported === 1 ? noun : plural}.
            </p>
          )}
        </div>
      )}
    </section>
  )
}
