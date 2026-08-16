import type { Page } from '../types/user'
import type {
  Activity,
  ActivityCreateRequest,
  ActivityType,
  ActivityUpdateRequest,
} from '../types/activity'
import { apiFetch } from './client'

/**
 * TODO (Berkay): implement all four. Same hybrid routing as contacts —
 * the collection is nested under the account, the item is flat.
 *
 *   GET    /api/accounts/{accountId}/activities?page=&size=&sort=&type=  → Page<Activity>
 *   POST   /api/accounts/{accountId}/activities                          → Activity (201)
 *   PUT    /api/activities/{id}                                          → Activity
 *   DELETE /api/activities/{id}                                          → void (204)
 *
 * Two notes:
 *  - The timeline sorts newest-first, so pass sort = 'occurredAt,desc'.
 *    That's the order your composite index (account_id, occurred_at) exists for.
 *  - `type` is optional: append it only when set, same rule as the other clients.
 */

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
