/** Mirrors `com.berkay.crm.dto.AccountResponse`. */
export interface Account {
  id: number
  name: string
  industry: string | null
  website: string | null
  phone: string | null
  ownerId: number
  ownerName: string
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
  name: string
  industry: string | null
  website: string | null
  phone: string | null
  ownerId: number
}
