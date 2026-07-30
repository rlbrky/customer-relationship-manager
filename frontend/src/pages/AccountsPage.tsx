import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../auth/useAuth'
import { AccountForm } from '../components/AccountForm'
import { Pagination } from '../components/Pagination'
import { ApiError } from '../api/client'
import * as accountsApi from '../api/accounts'
import { fetchUsers } from '../api/users'
import type { Account, AccountCreateRequest, AccountUpdateRequest } from '../types/account'
import type { User } from '../types/auth'

type Editor = { kind: 'none' } | { kind: 'create' } | { kind: 'edit'; account: Account }

const PAGE_SIZE = 2

export function AccountsPage() {
  const { user } = useAuth()
  const isAdmin = user?.roles.includes('ROLE_ADMIN') ?? false

  const [accounts, setAccounts] = useState<Account[]>([])
  const [pageNumber, setPageNumber] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)

  const [loading, setLoading] = useState(true)
  const [listError, setListError] = useState<string | null>(null)

  const [owners, setOwners] = useState<User[]>([])
  const [editor, setEditor] = useState<Editor>({ kind: 'none' })
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  const load = useCallback(async (page: number) => {
    setLoading(true)
    setListError(null)
    try {
      const result = await accountsApi.fetchAccounts(page, PAGE_SIZE, 'name,asc')
      setAccounts(result.content)
      setPageNumber(result.page.number)
      setTotalPages(result.page.totalPages)
      setTotalElements(result.page.totalElements)
    } catch (err) {
      setListError(err instanceof ApiError ? err.message : 'Could not load accounts.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load(0)
  }, [load])

  // The owner picker needs the user list, and /api/users is admin-only — so
  // only admins can reassign from the UI. Everyone else keeps the current owner.
  useEffect(() => {
    if (!isAdmin) return
    fetchUsers(0, 100)
      .then((page) => setOwners(page.content))
      .catch(() => setOwners([]))
  }, [isAdmin])

  async function runMutation(action: () => Promise<unknown>, backToPage = pageNumber) {
    setSubmitting(true)
    setFormError(null)
    try {
      await action()
      setEditor({ kind: 'none' })
      await load(backToPage)
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : 'Something went wrong.')
    } finally {
      setSubmitting(false)
    }
  }

  function handleCreate(request: AccountCreateRequest) {
    void runMutation(() => accountsApi.createAccount(request), 0)
  }

  function handleUpdate(id: number, request: AccountUpdateRequest) {
    void runMutation(() => accountsApi.updateAccount(id, request))
  }

  function handleDelete(target: Account) {
    if (!window.confirm(`Delete ${target.name}? It will be hidden from all lists.`)) return
    // if this was the last row on the page, step back one
    const page = accounts.length === 1 && pageNumber > 0 ? pageNumber - 1 : pageNumber
    void runMutation(() => accountsApi.deleteAccount(target.id), page)
  }

  return (
    <main className="content content--wide">
      <div className="content__head">
        <div>
          <h1 className="content__title">Accounts</h1>
          <p className="content__lede">
            {isAdmin || user?.roles.includes('ROLE_MANAGER')
              ? 'All company accounts.'
              : 'Accounts you own.'}
          </p>
        </div>
        {editor.kind === 'none' && (
          <button
            className="btn btn--primary"
            type="button"
            onClick={() => { setFormError(null); setEditor({ kind: 'create' }) }}
          >
            New account
          </button>
        )}
      </div>

      {editor.kind === 'create' && (
        <AccountForm
          mode="create"
          owners={owners}
          submitting={submitting}
          error={formError}
          onSubmit={handleCreate}
          onCancel={() => setEditor({ kind: 'none' })}
        />
      )}

      {editor.kind === 'edit' && (
        <AccountForm
          mode="edit"
          account={editor.account}
          owners={owners}
          submitting={submitting}
          error={formError}
          onSubmit={(request) => handleUpdate(editor.account.id, request)}
          onCancel={() => setEditor({ kind: 'none' })}
        />
      )}

      {editor.kind === 'none' && formError && (
        <p className="form__error" role="alert">{formError}</p>
      )}

      {loading && <p className="card__hint">Loading accounts…</p>}
      {listError && <p className="form__error" role="alert">{listError}</p>}

      {!loading && !listError && (
        <>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Company</th>
                  <th>Industry</th>
                  <th>Website</th>
                  <th>Phone</th>
                  <th>Owner</th>
                  <th className="table__actions-head">Actions</th>
                </tr>
              </thead>
              <tbody>
                {accounts.map((account) => (
                  <tr key={account.id}>
                    <td>{account.name}</td>
                    <td className="table__muted">{account.industry ?? '—'}</td>
                    <td className="table__muted">
                      {account.website ? (
                        <a className="link" href={account.website} target="_blank" rel="noreferrer">
                          {account.website}
                        </a>
                      ) : '—'}
                    </td>
                    <td className="table__muted">{account.phone ?? '—'}</td>
                    <td><span className="tag">{account.ownerName}</span></td>
                    <td className="table__actions">
                      {/* No permission check needed: the list only ever contains
                          accounts this user may edit — reps see only their own,
                          managers and admins may edit all. */}
                      <button
                        className="btn btn--small btn--ghost"
                        type="button"
                        onClick={() => { setFormError(null); setEditor({ kind: 'edit', account }) }}
                      >
                        Edit
                      </button>
                      <button
                        className="btn btn--small btn--danger"
                        type="button"
                        onClick={() => handleDelete(account)}
                      >
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
                {accounts.length === 0 && (
                  <tr>
                    <td className="table__empty" colSpan={6}>No accounts yet.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <Pagination
            number={pageNumber}
            totalPages={totalPages}
            totalElements={totalElements}
            onChange={(page) => void load(page)}
          />
        </>
      )}
    </main>
  )
}
