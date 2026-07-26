/** Mirrors `com.berkay.crm.dto.UserResponse` on the backend. */
export interface User {
  id: number
  username: string
  email: string
  firstName: string
  lastName: string
  /** Role names, e.g. ["ROLE_ADMIN"]. Java's Set<String> serializes to a JSON array. */
  roles: string[]
}
