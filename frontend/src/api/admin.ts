import type { DeletedAccount } from '../types/admin'
import { apiFetch } from './client'

export async function fetchDeletedAccounts(): Promise<DeletedAccount[]> {
  return apiFetch<DeletedAccount[]>('/api/admin/deleted-accounts')
}

/** 204 on success — the account is no longer deleted, so there is nothing to return. */
export async function restoreAccount(id: number): Promise<void> {
  return apiFetch<void>(`/api/admin/deleted-accounts/${id}/restore`, { method: 'POST' })
}
