import type { User } from '../types/auth'
import type { Page, UserCreateRequest, UserUpdateRequest } from '../types/user'
import { apiFetch } from './client'

export async function fetchUsers(_page = 0, _size = 20): Promise<Page<User>> {

  return apiFetch<Page<User>>(`/api/users?page=${_page}&size=${_size}`);
}

export async function createUser(_request: UserCreateRequest): Promise<User> {
  return apiFetch<User>('/api/users', {
    method: 'POST',
    body: JSON.stringify(_request),
  });
}

export async function updateUser(_id: number, _request: UserUpdateRequest): Promise<User> {
  return apiFetch<User>(`/api/users/${_id}`, {
    method: 'PUT',
    body: JSON.stringify(_request),
  });
}

export async function deactivateUser(_id: number): Promise<void> {
  return apiFetch<void>(`/api/users/${_id}`, {method: 'DELETE'});
}
