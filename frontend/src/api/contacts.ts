import type { Page } from '../types/user'
import type { Contact, ContactCreateRequest, ContactUpdateRequest } from '../types/contact'
import { apiFetch } from './client'

export async function fetchContacts(
  accountId: number,
  page = 0,
  size = 20,
  sort = 'lastName,asc',
): Promise<Page<Contact>> {

  return apiFetch<Page<Contact>>(`/api/accounts/${accountId}/contacts?page=${page}&_size=${size}&_sort=${sort}`);
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

  return apiFetch<Contact>(`/api/contacts/${id}`);
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

  return apiFetch<void>(`/api/contacts/${id}`, {
    method: 'DELETE',
  })
}
