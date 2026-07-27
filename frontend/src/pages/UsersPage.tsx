import { useCallback, useEffect, useState } from 'react'
import { useAuth } from '../auth/useAuth'
import { UserForm } from '../components/UserForm'
import { ApiError } from '../api/client'
import * as usersApi from '../api/users'
import type { User } from '../types/auth'
import { roleLabel, type UserCreateRequest, type UserUpdateRequest } from '../types/user'

type Editor =
  | { kind: 'none' }
  | { kind: 'create' }
  | { kind: 'edit'; user: User }

export function UsersPage() {
  const { user: currentUser } = useAuth()

  const [users, setUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(true)
  const [listError, setListError] = useState<string | null>(null)

  const [editor, setEditor] = useState<Editor>({ kind: 'none' })
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setListError(null)
    try {
      const page = await usersApi.fetchUsers(0, 50)
      setUsers(page.content)
    } catch (err) {
      setListError(err instanceof ApiError ? err.message : 'Could not load users.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  /** Every mutation follows the same shape: run it, refresh the list, close the editor. */
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

  function handleCreate(request: UserCreateRequest) {
    void runMutation(() => usersApi.createUser(request))
  }

  function handleUpdate(id: number, request: UserUpdateRequest) {
    void runMutation(() => usersApi.updateUser(id, request))
  }

  function handleDeactivate(target: User) {
    if (!window.confirm(`Deactivate ${target.username}? They will no longer be able to sign in.`)) {
      return
    }
    void runMutation(() => usersApi.deactivateUser(target.id))
  }

  return (
    <main className="content content--wide">
      <div className="content__head">
        <div>
          <h1 className="content__title">Users</h1>
          <p className="content__lede">Create accounts, assign roles, deactivate access.</p>
        </div>
        {editor.kind === 'none' && (
          <button className="btn btn--primary" type="button" onClick={() => { setFormError(null); setEditor({ kind: 'create' }) }}>
            New user
          </button>
        )}
      </div>

      {editor.kind === 'create' && (
        <UserForm
          mode="create"
          submitting={submitting}
          error={formError}
          onSubmit={handleCreate}
          onCancel={() => setEditor({ kind: 'none' })}
        />
      )}

      {editor.kind === 'edit' && (
        <UserForm
          mode="edit"
          user={editor.user}
          submitting={submitting}
          error={formError}
          onSubmit={(request) => handleUpdate(editor.user.id, request)}
          onCancel={() => setEditor({ kind: 'none' })}
        />
      )}

      {/* a mutation error while no form is open (e.g. a failed deactivate) */}
      {editor.kind === 'none' && formError && (
        <p className="form__error" role="alert">{formError}</p>
      )}

      {loading && <p className="card__hint">Loading users…</p>}
      {listError && <p className="form__error" role="alert">{listError}</p>}

      {!loading && !listError && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>Username</th>
                <th>Name</th>
                <th>Email</th>
                <th>Roles</th>
                <th>Status</th>
                <th className="table__actions-head">Actions</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => {
                const isSelf = u.id === currentUser?.id
                return (
                  <tr key={u.id}>
                    <td className="table__mono">{u.username}</td>
                    <td>{u.firstName} {u.lastName}</td>
                    <td className="table__muted">{u.email}</td>
                    <td>
                      <span className="tags">
                        {u.roles.map((r) => (
                          <span className="tag" key={r}>{roleLabel(r)}</span>
                        ))}
                      </span>
                    </td>
                    <td>
                      <span className={u.enabled ? 'pill pill--up' : 'pill pill--down'}>
                        {u.enabled ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td className="table__actions">
                      <button
                        className="btn btn--small btn--ghost"
                        type="button"
                        onClick={() => { setFormError(null); setEditor({ kind: 'edit', user: u }) }}
                      >
                        Edit
                      </button>
                      <button
                        className="btn btn--small btn--danger"
                        type="button"
                        /* the backend's lockout guard would 409 self-deactivation
                           anyway — disabling it just avoids a pointless round-trip */
                        disabled={isSelf || !u.enabled}
                        title={isSelf ? 'You cannot deactivate your own account' : undefined}
                        onClick={() => handleDeactivate(u)}
                      >
                        Deactivate
                      </button>
                    </td>
                  </tr>
                )
              })}
              {users.length === 0 && (
                <tr>
                  <td className="table__empty" colSpan={6}>No users yet.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </main>
  )
}
