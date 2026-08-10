import type { Page } from '../types/user'
import type { Account, AccountCreateRequest, AccountUpdateRequest } from '../types/account'
import { apiFetch } from "./client.ts";

export async function fetchAccounts(
  _page = 0,
  _size = 20,
  _sort = 'name,asc',
): Promise<Page<Account>> {

  return apiFetch<Page<Account>>(`/api/accounts?page=${_page}&size=${_size}&sort=${_sort}`);
}

export async function fetchAccount(_id: number): Promise<Account> {

  return apiFetch<Account>(`/api/accounts/${_id}`);
}

export async function createAccount(_request: AccountCreateRequest): Promise<Account> {

  return apiFetch<Account>('/api/accounts', {
    method: 'POST',
    body: JSON.stringify(_request),
  })
}

export async function updateAccount(
  _id: number,
  _request: AccountUpdateRequest,
): Promise<Account> {

  return apiFetch<Account>(`/api/accounts/${_id}`, {
    method: 'PUT',
    body: JSON.stringify(_request),
  })
}

export async function deleteAccount(_id: number): Promise<void> {

  return apiFetch<void>(`/api/accounts/${_id}`, {
    method: 'DELETE'
  });
}
