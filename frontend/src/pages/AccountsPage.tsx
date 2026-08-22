import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { useDebounce } from '../hooks/useDebounce'
import { AccountForm } from '../components/AccountForm'
import { ConflictBanner } from '../components/ConflictBanner'
import { Pagination } from '../components/Pagination'
import { ApiError } from '../api/client'
import * as accountsApi from '../api/accounts'
import { fetchUsers } from '../api/users'
import type { Account, AccountCreateRequest, AccountUpdateRequest } from '../types/account'
import type { User } from '../types/auth'

type Editor = { kind: 'none' } | { kind: 'create' } | { kind: 'edit'; account: Account }

const PAGE_SIZE = 10

/** Set a param when it has a value, drop it entirely when it doesn't. */
function setOrDelete(params: URLSearchParams, key: string, value: string) {
  if (value) params.set(key, value)
  else params.delete(key)
}

export function AccountsPage() {
  const { user } = useAuth()
  const isAdmin = user?.roles.includes('ROLE_ADMIN') ?? false

  // The URL is the source of truth for the query, so results are shareable
  // and the browser's back button moves through searches.
  const [searchParams, setSearchParams] = useSearchParams()
  const nameParam = searchParams.get('name') ?? ''
  const industryParam = searchParams.get('industry') ?? ''
  const ownerParam = searchParams.get('ownerId') ?? ''
  const pageParam = Number(searchParams.get('page') ?? '0')

  // Local state holds what's typed right now; the URL only gets the settled value.
  const [nameInput, setNameInput] = useState(nameParam)
  const [industryInput, setIndustryInput] = useState(industryParam)
  const debouncedName = useDebounce(nameInput)
  const debouncedIndustry = useDebounce(industryInput)

  const [accounts, setAccounts] = useState<Account[]>([])
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)

  const [loading, setLoading] = useState(true)
  const [listError, setListError] = useState<string | null>(null)

  const [owners, setOwners] = useState<User[]>([])
  const [editor, setEditor] = useState<Editor>({ kind: 'none' })
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [conflict, setConflict] = useState(false)

  // typed text (settled) → URL
  useEffect(() => {
    if (debouncedName === nameParam && debouncedIndustry === industryParam) return
    const next = new URLSearchParams(searchParams)
    setOrDelete(next, 'name', debouncedName)
    setOrDelete(next, 'industry', debouncedIndustry)
    next.delete('page') // a changed filter always restarts at page 1
    setSearchParams(next, { replace: true })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedName, debouncedIndustry])

  function changePage(page: number) {
    const next = new URLSearchParams(searchParams)
    setOrDelete(next, 'page', page > 0 ? String(page) : '')
    setSearchParams(next)
  }

  function changeOwner(ownerId: string) {
    const next = new URLSearchParams(searchParams)
    setOrDelete(next, 'ownerId', ownerId)
    next.delete('page')
    setSearchParams(next, { replace: true })
  }

  // URL → request
  const load = useCallback(async () => {
    setLoading(true)
    setListError(null)
    try {
      const result = await accountsApi.fetchAccounts(pageParam, PAGE_SIZE, 'name,asc', {
        name: nameParam || undefined,
        industry: industryParam || undefined,
        ownerId: ownerParam ? Number(ownerParam) : undefined,
      })
      setAccounts(result.content)
      setTotalPages(result.page.totalPages)
      setTotalElements(result.page.totalElements)
    } catch (err) {
      setListError(err instanceof ApiError ? err.message : 'Could not load accounts.')
    } finally {
      setLoading(false)
    }
  }, [pageParam, nameParam, industryParam, ownerParam])

  useEffect(() => {
    void load()
  }, [load])

  useEffect(() => {
    if (!isAdmin) return
    fetchUsers(0, 100)
      .then((page) => setOwners(page.content))
      .catch(() => setOwners([]))
  }, [isAdmin])

  async function runMutation(action: () => Promise<unknown>) {
    setSubmitting(true)
    setFormError(null)
    try {
      await action()
      setEditor({ kind: 'none' })
      await load()
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : 'Something went wrong.')
    } finally {
      setSubmitting(false)
    }
  }

  function handleCreate(request: AccountCreateRequest) {
    void runMutation(() => accountsApi.createAccount(request))
  }

  /**
   * Updates do not go through runMutation, because a 409 is not an ordinary
   * failure: the user's edits are still perfectly good, they were just written
   * against a version someone else has since replaced. The editor stays open with
   * everything they typed, and they choose what to do about it.
   */
  async function handleUpdate(id: number, request: AccountUpdateRequest) {
    setSubmitting(true)
    setFormError(null)
    setConflict(false)
    try {
      await accountsApi.updateAccount(id, request)
      setEditor({ kind: 'none' })
      await load()
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setConflict(true)
      } else {
        setFormError(err instanceof ApiError ? err.message : 'Something went wrong.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  /** Discards the user's edits in exchange for the server's current state. */
  async function loadCurrentValues(id: number) {
    try {
      const fresh = await accountsApi.fetchAccount(id)
      setEditor({ kind: 'edit', account: fresh })
      setConflict(false)
    } catch {
      setFormError('Could not load the current values.')
    }
  }

  function handleDelete(target: Account) {
    if (!window.confirm(`Delete ${target.name}? It will be hidden from all lists.`)) return
    void runMutation(() => accountsApi.deleteAccount(target.id))
  }

  const filtered = Boolean(nameParam || industryParam || ownerParam)

  function clearFilters() {
    setNameInput('')
    setIndustryInput('')
    setSearchParams(new URLSearchParams(), { replace: true })
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
            onClick={() => { setFormError(null); setConflict(false); setEditor({ kind: 'create' }) }}
          >
            New account
          </button>
        )}
      </div>

      <div className="filters">
        <input
          className="field__input filters__search"
          type="search"
          value={nameInput}
          onChange={(e) => setNameInput(e.target.value)}
          placeholder="Search company name…"
          aria-label="Search by company name"
        />
        <input
          className="field__input filters__item"
          type="search"
          value={industryInput}
          onChange={(e) => setIndustryInput(e.target.value)}
          placeholder="Industry"
          aria-label="Filter by industry"
        />
        {isAdmin && owners.length > 0 && (
          <select
            className="field__input filters__item"
            value={ownerParam}
            onChange={(e) => changeOwner(e.target.value)}
            aria-label="Filter by owner"
          >
            <option value="">Any owner</option>
            {owners.map((owner) => (
              <option key={owner.id} value={owner.id}>{owner.username}</option>
            ))}
          </select>
        )}
        {filtered && (
          <button className="btn btn--small btn--ghost" type="button" onClick={clearFilters}>
            Clear
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
        <>
          {conflict && (
            <ConflictBanner
              noun="account"
              onReload={() => void loadCurrentValues(editor.account.id)}
            />
          )}

          {/* Keyed on the version so React remounts the form when a reload brings a
              new one. AccountForm seeds its fields with useState(account.name), which
              reads its argument only on mount — without the key, swapping the account
              prop would change nothing on screen. */}
          <AccountForm
            key={editor.account.version}
            mode="edit"
            account={editor.account}
            owners={owners}
            submitting={submitting}
            error={formError}
            onSubmit={(request) => void handleUpdate(editor.account.id, request)}
            onCancel={() => { setConflict(false); setEditor({ kind: 'none' }) }}
          />
        </>
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
                  <th>Contacts</th>
                  <th className="table__actions-head">Actions</th>
                </tr>
              </thead>
              <tbody>
                {accounts.map((account) => (
                  <tr key={account.id}>
                    <td>
                      <Link className="link" to={`/accounts/${account.id}`}>{account.name}</Link>
                    </td>
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
                    <td className="table__muted">{account.contactCount}</td>
                    <td className="table__actions">
                      {/* No permission check needed: the list only ever contains
                          accounts this user may edit — reps see only their own,
                          managers and admins may edit all. */}
                      <button
                        className="btn btn--small btn--ghost"
                        type="button"
                        onClick={() => { setFormError(null); setConflict(false); setEditor({ kind: 'edit', account }) }}
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
                    <td className="table__empty" colSpan={7}>
                      {filtered ? 'No accounts match these filters.' : 'No accounts yet.'}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <Pagination
            number={pageParam}
            totalPages={totalPages}
            totalElements={totalElements}
            onChange={changePage}
            label="account"
          />
        </>
      )}
    </main>
  )
}
