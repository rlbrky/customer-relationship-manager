import type { User } from './auth'

/** The subset of Spring Data's Page JSON that we actually use. */
export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  /** zero-based page index */
  number: number
  size: number
}

/** Mirrors `com.berkay.crm.dto.UserCreateRequest`. */
export interface UserCreateRequest {
  username: string
  email: string
  password: string
  firstName: string
  lastName: string
  roles: string[]
}

/** Mirrors `com.berkay.crm.dto.UserUpdateRequest` — no username, no password. */
export interface UserUpdateRequest {
  email: string
  firstName: string
  lastName: string
  enabled: boolean
  roles: string[]
}

/** The roles seeded by V2__seed_roles.sql. */
export const ALL_ROLES = ['ROLE_ADMIN', 'ROLE_MANAGER', 'ROLE_SALES_REP'] as const

/** "ROLE_SALES_REP" → "Sales rep" — for display only. */
export function roleLabel(role: string): string {
  const bare = role.replace(/^ROLE_/, '').replace(/_/g, ' ').toLowerCase()
  return bare.charAt(0).toUpperCase() + bare.slice(1)
}

export type { User }
