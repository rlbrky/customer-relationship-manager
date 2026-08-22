import type { Page } from '../types/user'
import type {
  Activity,
  ActivityCreateRequest,
  ActivityType,
  ActivityUpdateRequest,
} from '../types/activity'
import { apiFetch } from './client'

export async function fetchActivities(
  accountId: number,
  page = 0,
  size = 20,
  sort = 'occurredAt,desc',
  type?: ActivityType,
): Promise<Page<Activity>> {

  const params = new URLSearchParams({page: String(page), size: String(size), sort});

  if(type) {
    params.set('type', type);
  }

  return apiFetch<Page<Activity>>(`/api/accounts/${accountId}/activities?${params}`);
}

export async function createActivity(
  accountId: number,
  request: ActivityCreateRequest,
): Promise<Activity> {


  return apiFetch<Activity>(`/api/accounts/${accountId}/activities`, {
    method: 'POST',
    body: JSON.stringify(request),
  });
}

export async function fetchActivity(id: number): Promise<Activity> {
  return apiFetch<Activity>(`/api/activities/${id}`)
}

export async function updateActivity(
  id: number,
  request: ActivityUpdateRequest,
): Promise<Activity> {

  return apiFetch<Activity>(`/api/activities/${id}`, {
    method: 'PUT',
    body: JSON.stringify(request),
  })
}

export async function deleteActivity(id: number): Promise<void> {

  return apiFetch<void>(`/api/activities/${id}`, {
    method: 'DELETE'
  })
}
