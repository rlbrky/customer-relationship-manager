/** Mirrors `com.berkay.crm.dto.ContactResponse`. */
export interface Contact {
  id: number
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

/** Mirrors `ContactUpdateRequest` — same shape; contacts don't move between accounts. */
export type ContactUpdateRequest = ContactCreateRequest
