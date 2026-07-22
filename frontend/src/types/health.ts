/** Mirrors `com.berkay.crm.dto.HealthResponse` on the backend. */

export type HealthStatus = 'UP' | 'DOWN'

export interface HealthResponse {
  status: HealthStatus
  db: HealthStatus
  /** Java `Instant`, serialized as an ISO-8601 string. */
  timestamp: string
}
