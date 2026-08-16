/** Mirrors `com.berkay.crm.model.ActivityType`. */
export type ActivityType = 'CALL' | 'EMAIL' | 'MEETING' | 'NOTE' | 'TASK'

export const ACTIVITY_TYPES: ActivityType[] = ['CALL', 'EMAIL', 'MEETING', 'NOTE', 'TASK']

/** Mirrors `com.berkay.crm.dto.ActivityResponse`. */
export interface Activity {
  id: number
  type: ActivityType
  subject: string
  notes: string | null
  /** Java `Instant` — absolute moment, ISO-8601 WITH a zone: "2026-09-01T11:00:00Z" */
  occurredAt: string
  /** Java `LocalDateTime` — wall clock, NO zone: "2026-09-01T14:00:00". Tasks only. */
  dueAt: string | null
  completed: boolean
  accountId: number
  contactId: number | null
  contactName: string | null
}

export interface ActivityCreateRequest {
  type: ActivityType
  subject: string
  notes: string | null
  occurredAt: string
  dueAt: string | null
  contactId: number | null
}

export interface ActivityUpdateRequest extends ActivityCreateRequest {
  completed: boolean
}

/** "MEETING" → "Meeting" */
export function activityTypeLabel(type: ActivityType): string {
  return type.charAt(0) + type.slice(1).toLowerCase()
}
