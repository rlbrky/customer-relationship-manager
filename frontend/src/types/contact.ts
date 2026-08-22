/** Mirrors `com.berkay.crm.dto.ContactResponse`. */
export interface Contact {
  id: number
  version: number
  firstName: string
  lastName: string
  email: string | null
  phone: string | null
  jobTitle: string | null
  accountId: number
  accountName: string
}

/** Mirrors `ContactCreateRequest` — no accountId: it comes from the URL path. */
export interface ContactCreateRequest {
  firstName: string
  lastName: string
  email: string | null
  phone: string | null
  jobTitle: string | null
}

/**
 * Mirrors `ContactUpdateRequest`. No longer an alias of the create shape: an update
 * has to say which version of the record it was written against, and a create has
 * nothing to be stale against.
 */
export interface ContactUpdateRequest extends ContactCreateRequest {
  version: number
}
