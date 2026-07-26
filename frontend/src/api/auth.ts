import type { User } from '../types/auth';
import { apiFetch } from './client';

export async function login(username: string, password: string): Promise<User> {

  return await apiFetch<User>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({username, password})
  });
}

export async function logout(): Promise<void> {

  return await apiFetch<void>('/api/auth/logout',
      {
        method: 'POST',
      }
      );
}

export async function fetchCurrentUser(): Promise<User> {

  return await apiFetch<User>('/api/auth/me', {
    method: 'GET',
  });
}
