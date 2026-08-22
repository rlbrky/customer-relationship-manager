/** Mirrors `com.berkay.crm.dto.AccountResponse`. */
export interface Account {
  id: number
  version: number
  name: string
  industry: string | null
  website: string | null
  phone: string | null
  ownerId: number
  ownerName: string
  /** Number of live (non-soft-deleted) contacts — computed by an aggregate query. */
  contactCount: number
}

/** Mirrors `AccountCreateRequest` — ownerId is optional (null ⇒ "me"). */
export interface AccountCreateRequest {
  name: string
  industry: string | null
  website: string | null
  phone: string | null
  ownerId: number | null
}

/** Mirrors `AccountUpdateRequest` — ownerId is required here. */
export interface AccountUpdateRequest {
  version: number
  name: string
  industry: string | null
  website: string | null
  phone: string | null
  ownerId: number
}
