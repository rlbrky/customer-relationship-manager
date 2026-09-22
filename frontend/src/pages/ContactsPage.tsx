import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useDebounce } from '../hooks/useDebounce'
import { Pagination } from '../components/Pagination'
import { ApiError } from '../api/client'
import {importContacts, searchContacts} from '../api/contacts'
import type { Contact } from '../types/contact'
import type {ImportResult} from "../types/csv.ts";
import {CsvImport} from "../components/CsvImport.tsx";

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

  const [importOpen, setImportOpen] = useState(false)
  const [importing, setImporting] = useState(false)
  const [importError, setImportError] = useState<string | null>(null)
  const [importResult, setImportResult] = useState<ImportResult | null>(null)
  const [importKey, setImportKey] = useState(0)

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

  function openImport() {
    setImportError(null)
    setImportResult(null) // don't show last time's result when reopening
    setImportOpen(true)
  }

  async function handleImport(file: File) {
    setImporting(true)
    setImportError(null)
    setImportResult(null)
    try {
      const result = await importContacts(file)
      setImportResult(result)

      if (result.imported > 0) {
        setImportKey((key) => key + 1) // remount panel => clears chosen file
        await load() // refresh the table so the new contents show
      }
    } catch (err) {
      setImportError(err instanceof ApiError ? err.message : 'Could not import that file.')
    } finally {
      setImporting(false)
    }
  }

  // qParam, not qInput: the table on screen was loaded from the URL value.
  // Anything fresher would export a different set than the user is looking at while mid-typing.
  const exportParams = new URLSearchParams()
  if (qParam)
    exportParams.set('q', qParam)
  const exportHref = `/api/contacts/export.csv?${exportParams}`

  return (
    <main className="content content--wide">
      <div className="content__head">
        <div>
          <h1 className="content__title">Contacts</h1>
          <p className="content__lede">Search people across every account you can see.</p>
        </div>
        <div className="head__tools">
          <a className="btn btn--ghost" href={exportHref}>Export CSV</a>
          {!importOpen && (
              <button className="btn btn--ghost" type="button" onClick={openImport}>
                Import CSV
              </button>
          )}
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

      {importOpen && (
          <CsvImport
              key={importKey}
              noun="contact" hint={
            <>
              A CSV with <code>firstName</code>, <code>lastName</code> and{' '}
              <code>account</code> columns, plus any of <code>email</code>,{' '}
              <code>phone</code> and <code>jobTitle</code>. <code>account</code> is the
              exact name of an account you can see. Columns we don't recognise are
              ignored, so a file straight from Export CSV imports as-is. Up to 2 MB.
            </>
          }
              submitting={importing}
              error={importError}
              result={importResult}
              onSubmit={(file) => void handleImport(file)}
              onCancel={() => setImportOpen(false)}
              />
      )}

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
