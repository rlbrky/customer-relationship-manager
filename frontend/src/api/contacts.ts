import type { Page } from '../types/user'
import type { Contact, ContactCreateRequest, ContactUpdateRequest } from '../types/contact'
import { apiFetch } from './client'

/** Contacts on one account, optionally filtered by a search term. */
export async function fetchContacts(
  accountId: number,
  page = 0,
  size = 20,
  sort = 'lastName,asc',
  q?: string,
): Promise<Page<Contact>> {

  const params = new URLSearchParams({ page: String(page), size: String(size), sort })

  if(q)
    params.set('q', q);

  return apiFetch<Page<Contact>>(`/api/accounts/${accountId}/contacts?${params}`)
}

/**
 * Global contact search — every contact the current user is allowed to see,
 * across all accounts. Visibility is enforced server-side by
 * ContactSpecifications.visibleTo, so the client sends the same request for
 * every role.
 */
export async function searchContacts(
  page = 0,
  size = 20,
  sort = 'lastName,asc',
  q?: string,
): Promise<Page<Contact>> {

  const params = new URLSearchParams({ page: String(page), size: String(size), sort })
  if(q)
    params.set('q', q);

  return apiFetch<Page<Contact>>(`/api/contacts?${params}`)
}

export async function createContact(
  accountId: number,
  request: ContactCreateRequest,
): Promise<Contact> {
  return apiFetch<Contact>(`/api/accounts/${accountId}/contacts`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export async function fetchContact(id: number): Promise<Contact> {
  return apiFetch<Contact>(`/api/contacts/${id}`)
}

export async function updateContact(
  id: number,
  request: ContactUpdateRequest,
): Promise<Contact> {
  return apiFetch<Contact>(`/api/contacts/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export async function deleteContact(id: number): Promise<void> {
  return apiFetch<void>(`/api/contacts/${id}`, { method: 'DELETE' })
}
