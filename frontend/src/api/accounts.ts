import type { Page } from '../types/user'
import type { Account, AccountCreateRequest, AccountUpdateRequest } from '../types/account'
import type { ImportResult } from '../types/csv'
import { apiFetch } from './client'

/** Optional search filters — each maps to a @RequestParam on the backend. */
export interface AccountFilters {
  name?: string
  industry?: string
  ownerId?: number
}

export async function fetchAccounts(
  page = 0,
  size = 20,
  sort = 'name,asc',
  filters: AccountFilters = {},
): Promise<Page<Account>> {

  const params = new URLSearchParams({ page: String(page), size: String(size), sort })

  if(filters.name)
    params.set('name', filters.name);
  if(filters.industry)
    params.set('industry', filters.industry);
  if(filters.ownerId !== undefined)
    params.set('ownerId', String(filters.ownerId));

  return apiFetch<Page<Account>>(`/api/accounts?${params}`);
}

export async function fetchAccount(id: number): Promise<Account> {
  return apiFetch<Account>(`/api/accounts/${id}`)
}

export async function createAccount(request: AccountCreateRequest): Promise<Account> {
  return apiFetch<Account>('/api/accounts', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export async function updateAccount(
  id: number,
  request: AccountUpdateRequest,
): Promise<Account> {
  return apiFetch<Account>(`/api/accounts/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export async function deleteAccount(id: number): Promise<void> {
  return apiFetch<void>(`/api/accounts/${id}`, { method: 'DELETE' })
}

/**
 * Uploads a CSV. Deliberately returns the result rather than throwing on bad rows:
 * a file the backend fully analysed came back 200, and the analysis is the whole
 * point. Only a failed REQUEST throws — 413 over the 2 MB limit, 403 for a rep,
 * or 400 for something that is not parseable CSV at all.
 *
 * No Content-Type is set here. apiFetch leaves FormData alone so the browser can
 * generate the multipart boundary; see the comment there.
 */
export async function importAccounts(file: File): Promise<ImportResult> {
  const body = new FormData()
  body.append('file', file)   // 'file' is the @RequestParam name on the endpoint

  return apiFetch<ImportResult>('/api/accounts/import', { method: 'POST', body })
}
