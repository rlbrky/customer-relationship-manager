import type { Page } from '../types/user'
import type {
  Deal,
  DealCreateRequest,
  DealOutcome,
  DealStage,
  DealStageHistory,
  DealUpdateRequest,
} from '../types/deal'
import { apiFetch } from './client'

export async function fetchDeals(
  page = 0,
  size = 100,
  sort = 'expectedCloseDate,asc',
  stage?: DealStage,
  open?: boolean,
): Promise<Page<Deal>> {

  const params = new URLSearchParams({ page: String(page), size: String(size), sort })

  if (stage)
    params.set('stage', stage)

  if (open !== undefined)
    params.set('open', String(open))

  return apiFetch<Page<Deal>>(`/api/deals?${params}`);
}

export async function fetchAccountDeals(
  accountId: number,
  page = 0,
  size = 20,
): Promise<Page<Deal>> {

  const params = new URLSearchParams({
    page: String(page),
    size: String(size)
  });

  return apiFetch<Page<Deal>>(`/api/accounts/${accountId}/deals?${params}`);
}

export async function createDeal(
  accountId: number,
  request: DealCreateRequest,
): Promise<Deal> {

  return apiFetch<Deal>(`/api/accounts/${accountId}/deals`, {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export async function updateDeal(id: number, request: DealUpdateRequest): Promise<Deal> {

  return apiFetch<Deal>(`/api/deals/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  });
}

export async function changeStage(id: number, stage: DealStage): Promise<Deal> {

  return apiFetch<Deal>(`/api/deals/${id}/stage`, {
    method: 'PATCH',
    body: JSON.stringify({ stage }),
  })
}

export async function setOutcome(id: number, outcome: DealOutcome | null): Promise<Deal> {

  return apiFetch<Deal>(`/api/deals/${id}/outcome`, {
    method: 'PATCH',
    body: JSON.stringify({ outcome }),
  })
}

export async function deleteDeal(id: number): Promise<void> {

  return apiFetch<void>(`/api/deals/${id}`, {
    method: 'DELETE',
  })
}

export async function fetchDealHistory(id: number): Promise<DealStageHistory[]> {

  return apiFetch<DealStageHistory[]>(`/api/deals/${id}/history`);
}
