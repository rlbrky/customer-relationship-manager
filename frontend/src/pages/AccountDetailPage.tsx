import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ContactForm } from '../components/ContactForm'
import { Pagination } from '../components/Pagination'
import { ApiError } from '../api/client'
import { fetchAccount } from '../api/accounts'
import * as contactsApi from '../api/contacts'
import type { Account } from '../types/account'
import type { Contact, ContactCreateRequest, ContactUpdateRequest } from '../types/contact'

type Editor = { kind: 'none' } | { kind: 'create' } | { kind: 'edit'; contact: Contact }

const PAGE_SIZE = 10

export function AccountDetailPage() {
  const { id } = useParams<{ id: string }>()
  const accountId = Number(id)

  const [account, setAccount] = useState<Account | null>(null)
  const [contacts, setContacts] = useState<Contact[]>([])
  const [pageNumber, setPageNumber] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)

  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [editor, setEditor] = useState<Editor>({ kind: 'none' })
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  const load = useCallback(
    async (page: number) => {
      setLoading(true)
      setLoadError(null)
      try {
        // both calls are authorized server-side; a 403/404 surfaces here as ApiError
        const [accountResult, contactsResult] = await Promise.all([
          fetchAccount(accountId),
          contactsApi.fetchContacts(accountId, page, PAGE_SIZE),
        ])
        setAccount(accountResult)
        setContacts(contactsResult.content)
        setPageNumber(contactsResult.page.number)
        setTotalPages(contactsResult.page.totalPages)
        setTotalElements(contactsResult.page.totalElements)
      } catch (err) {
        setLoadError(
          err instanceof ApiError && err.status === 403
            ? 'You do not have access to this account.'
            : err instanceof ApiError && err.status === 404
              ? 'That account no longer exists.'
              : 'Could not load this account.',
        )
      } finally {
        setLoading(false)
      }
    },
    [accountId],
  )

  useEffect(() => {
    void load(0)
  }, [load])

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

  function handleCreate(request: ContactCreateRequest) {
    void runMutation(() => contactsApi.createContact(accountId, request), 0)
  }

  function handleUpdate(contactId: number, request: ContactUpdateRequest) {
    void runMutation(() => contactsApi.updateContact(contactId, request))
  }

  function handleDelete(target: Contact) {
    if (!window.confirm(`Remove ${target.firstName} ${target.lastName}?`)) return
    const page = contacts.length === 1 && pageNumber > 0 ? pageNumber - 1 : pageNumber
    void runMutation(() => contactsApi.deleteContact(target.id), page)
  }

  if (loading && !account) {
    return <main className="content content--wide"><p className="card__hint">Loading…</p></main>
  }

  if (loadError) {
    return (
      <main className="content content--wide">
        <p className="form__error" role="alert">{loadError}</p>
        <p><Link className="link" to="/accounts">Back to accounts</Link></p>
      </main>
    )
  }

  return (
    <main className="content content--wide">
      <Link className="breadcrumb" to="/accounts">← Accounts</Link>

      <div className="content__head">
        <div>
          <h1 className="content__title">{account?.name}</h1>
          <p className="content__lede">
            {account?.industry ?? 'No industry set'} · owned by {account?.ownerName}
          </p>
        </div>
      </div>

      <dl className="factgrid">
        <div className="fact">
          <dt>Website</dt>
          <dd>
            {account?.website ? (
              <a className="link" href={account.website} target="_blank" rel="noreferrer">
                {account.website}
              </a>
            ) : '—'}
          </dd>
        </div>
        <div className="fact">
          <dt>Phone</dt>
          <dd>{account?.phone ?? '—'}</dd>
        </div>
        <div className="fact">
          <dt>Contacts</dt>
          <dd>{account?.contactCount ?? 0}</dd>
        </div>
      </dl>

      <div className="content__head content__head--sub">
        <h2 className="section__title">Contacts</h2>
        {editor.kind === 'none' && (
          <button
            className="btn btn--primary"
            type="button"
            onClick={() => { setFormError(null); setEditor({ kind: 'create' }) }}
          >
            Add contact
          </button>
        )}
      </div>

      {editor.kind === 'create' && (
        <ContactForm
          mode="create"
          submitting={submitting}
          error={formError}
          onSubmit={handleCreate}
          onCancel={() => setEditor({ kind: 'none' })}
        />
      )}

      {editor.kind === 'edit' && (
        <ContactForm
          mode="edit"
          contact={editor.contact}
          submitting={submitting}
          error={formError}
          onSubmit={(request) => handleUpdate(editor.contact.id, request)}
          onCancel={() => setEditor({ kind: 'none' })}
        />
      )}

      {editor.kind === 'none' && formError && (
        <p className="form__error" role="alert">{formError}</p>
      )}

      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Job title</th>
              <th>Email</th>
              <th>Phone</th>
              <th className="table__actions-head">Actions</th>
            </tr>
          </thead>
          <tbody>
            {contacts.map((contact) => (
              <tr key={contact.id}>
                <td>{contact.firstName} {contact.lastName}</td>
                <td className="table__muted">{contact.jobTitle ?? '—'}</td>
                <td className="table__muted">
                  {contact.email ? (
                    <a className="link" href={`mailto:${contact.email}`}>{contact.email}</a>
                  ) : '—'}
                </td>
                <td className="table__muted">{contact.phone ?? '—'}</td>
                <td className="table__actions">
                  <button
                    className="btn btn--small btn--ghost"
                    type="button"
                    onClick={() => { setFormError(null); setEditor({ kind: 'edit', contact }) }}
                  >
                    Edit
                  </button>
                  <button
                    className="btn btn--small btn--danger"
                    type="button"
                    onClick={() => handleDelete(contact)}
                  >
                    Remove
                  </button>
                </td>
              </tr>
            ))}
            {contacts.length === 0 && (
              <tr>
                <td className="table__empty" colSpan={5}>No contacts on this account yet.</td>
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
        label="contact"
      />
    </main>
  )
}
