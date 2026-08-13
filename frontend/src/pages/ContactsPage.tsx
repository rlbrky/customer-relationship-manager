import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useDebounce } from '../hooks/useDebounce'
import { Pagination } from '../components/Pagination'
import { ApiError } from '../api/client'
import { searchContacts } from '../api/contacts'
import type { Contact } from '../types/contact'

const PAGE_SIZE = 10

export function ContactsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const qParam = searchParams.get('q') ?? ''
  const pageParam = Number(searchParams.get('page') ?? '0')

  const [qInput, setQInput] = useState(qParam)
  const debouncedQ = useDebounce(qInput)

  const [contacts, setContacts] = useState<Contact[]>([])
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loading, setLoading] = useState(true)
  const [listError, setListError] = useState<string | null>(null)

  useEffect(() => {
    if (debouncedQ === qParam) return
    const next = new URLSearchParams(searchParams)
    if (debouncedQ) next.set('q', debouncedQ)
    else next.delete('q')
    next.delete('page')
    setSearchParams(next, { replace: true })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedQ])

  const load = useCallback(async () => {
    setLoading(true)
    setListError(null)
    try {
      // Visibility is decided server-side — a sales rep gets only contacts on
      // accounts they own, from this very same request.
      const result = await searchContacts(pageParam, PAGE_SIZE, 'lastName,asc', qParam || undefined)
      setContacts(result.content)
      setTotalPages(result.page.totalPages)
      setTotalElements(result.page.totalElements)
    } catch (err) {
      setListError(err instanceof ApiError ? err.message : 'Could not load contacts.')
    } finally {
      setLoading(false)
    }
  }, [pageParam, qParam])

  useEffect(() => {
    void load()
  }, [load])

  function changePage(page: number) {
    const next = new URLSearchParams(searchParams)
    if (page > 0) next.set('page', String(page))
    else next.delete('page')
    setSearchParams(next)
  }

  return (
    <main className="content content--wide">
      <div className="content__head">
        <div>
          <h1 className="content__title">Contacts</h1>
          <p className="content__lede">Search people across every account you can see.</p>
        </div>
      </div>

      <div className="filters">
        <input
          className="field__input filters__search"
          type="search"
          value={qInput}
          onChange={(e) => setQInput(e.target.value)}
          placeholder="Search name or email…"
          aria-label="Search contacts"
          autoFocus
        />
      </div>

      {loading && <p className="card__hint">Loading contacts…</p>}
      {listError && <p className="form__error" role="alert">{listError}</p>}

      {!loading && !listError && (
        <>
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Job title</th>
                  <th>Email</th>
                  <th>Phone</th>
                  <th>Account</th>
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
                    <td>
                      <Link className="link" to={`/accounts/${contact.accountId}`}>
                        {contact.accountName}
                      </Link>
                    </td>
                  </tr>
                ))}
                {contacts.length === 0 && (
                  <tr>
                    <td className="table__empty" colSpan={5}>
                      {qParam ? 'No contacts match that search.' : 'No contacts yet.'}
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
            label="contact"
          />
        </>
      )}
    </main>
  )
}
