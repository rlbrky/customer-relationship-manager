/** Mirrors `com.berkay.crm.dto.UserResponse` on the backend. */
export interface User {
  id: number
  version: number
  username: string
  email: string
  firstName: string
  lastName: string
  /** Whether the account can sign in. Deactivating a user sets this false. */
  enabled: boolean
  /** Role names, e.g. ["ROLE_ADMIN"]. Java's Set<String> serializes to a JSON array. */
  roles: string[]
}
