import type { Revision } from '../types/audit'
import { apiFetch } from './client'

/**
 * /revisions, not /history — the latter already means the deal stage log, which is
 * a domain event log features depend on. This is infrastructure nothing depends on.
 */
export async function fetchAccountRevisions(accountId: number): Promise<Revision[]> {
  return apiFetch<Revision[]>(`/api/accounts/${accountId}/revisions`)
}
