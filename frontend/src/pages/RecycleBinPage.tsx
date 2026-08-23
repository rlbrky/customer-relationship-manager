import { useCallback, useEffect, useState } from 'react'
import * as adminApi from '../api/admin'
import { ApiError } from '../api/client'
import type { DeletedAccount } from '../types/admin'
import { formatInstant } from '../utils/datetime'

export function RecycleBinPage() {
  const [accounts, setAccounts] = useState<DeletedAccount[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [restoring, setRestoring] = useState<number | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setAccounts(await adminApi.fetchDeletedAccounts())
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not load deleted accounts.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  async function handleRestore(target: DeletedAccount) {
    // Say what restore does NOT do before they click. Delete cascaded to contacts,
    // activities and deals; restore cannot, because deleted_at cannot say which of
    // them went with the cascade and which were removed deliberately.
    const confirmed = window.confirm(
      `Restore "${target.name}"?\n\n` +
        'Only the account comes back. Its contacts, activities and deals stay deleted ' +
        'and have to be restored individually.',
    )
    if (!confirmed) return

    setRestoring(target.id)
    setError(null)
    try {
      await adminApi.restoreAccount(target.id)
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not restore the account.')
    } finally {
      setRestoring(null)
    }
  }

  return (
    <main className="content content--wide">
      <h1 className="content__title">Recycle bin</h1>
      <p className="content__lede">
        Accounts that were deleted. Nothing here is gone from the database — a delete
        only hides a record from the rest of the app.
      </p>

      {error && <p className="form__error" role="alert">{error}</p>}
      {loading && <p className="card__hint">Loading deleted accounts…</p>}

      {!loading && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Industry</th>
                <th>Owner</th>
                <th>Deleted</th>
                <th>Deleted by</th>
                <th className="table__actions-head">Actions</th>
              </tr>
            </thead>
            <tbody>
              {accounts.map((account) => (
                <tr key={account.id}>
                  <td>{account.name}</td>
                  <td>{account.industry ?? '—'}</td>
                  <td>{account.ownerName}</td>
                  <td>{formatInstant(account.deletedAt)}</td>
                  {/* null when the delete predates the audit log */}
                  <td>{account.deletedBy ?? '—'}</td>
                  <td className="table__actions">
                    <button
                      className="btn btn--small btn--ghost"
                      type="button"
                      disabled={restoring === account.id}
                      onClick={() => void handleRestore(account)}
                    >
                      {restoring === account.id ? 'Restoring…' : 'Restore'}
                    </button>
                  </td>
                </tr>
              ))}
              {accounts.length === 0 && (
                <tr>
                  <td className="table__empty" colSpan={6}>Nothing has been deleted.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </main>
  )
}
