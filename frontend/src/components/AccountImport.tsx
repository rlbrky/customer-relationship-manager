import { useState } from 'react'
import type { FormEvent } from 'react'
import type { ImportResult } from '../types/csv'

interface AccountImportProps {
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

export function AccountImport({
  submitting, error, result, onSubmit, onCancel,
}: AccountImportProps) {

  const [file, setFile] = useState<File | null>(null)

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (file) onSubmit(file)
  }

  const shown = result?.errors.slice(0, MAX_SHOWN) ?? []
  const hidden = (result?.errors.length ?? 0) - shown.length

  return (
    <section className="panel import">
      <h2 className="panel__title">Import accounts</h2>
      <p className="field__hint">
        A CSV with a <code>name</code> column, plus any of <code>industry</code>,{' '}
        <code>website</code>, <code>phone</code> and <code>owner</code>. Owner is a
        username; leave it out and the accounts come to you. Columns we don't
        recognise are ignored, so a file straight from Export CSV imports as-is.
        Up to 2 MB.
      </p>

      <form className="form" onSubmit={handleSubmit}>
        <div className="field">
          <label className="field__label" htmlFor="import-file">CSV file</label>
          <input
            className="field__input"
            id="import-file"
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
              Imported {result.imported}{' '}
              {result.imported === 1 ? 'account' : 'accounts'}.
            </p>
          )}
        </div>
      )}
    </section>
  )
}
