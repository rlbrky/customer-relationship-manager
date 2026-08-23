/** Mirrors `com.berkay.crm.dto.FieldChange`. Either side is null when absent. */
export interface FieldChange {
  field: string
  from: string | null
  to: string | null
}

/** Mirrors `com.berkay.crm.dto.RevisionResponse`. */
export interface Revision {
  /** Envers revision number — shared by every entity changed in the same transaction. */
  revision: number
  /** Java Instant — an absolute moment: "2026-08-21T09:14:03Z" */
  changedAt: string
  /** null only for rows written before the audit listener existed. */
  changedBy: string | null
  /** ADD | MOD | DEL */
  type: string
  /** Always empty for DEL: Envers stores only the identifier on a delete. */
  changes: FieldChange[]
}

/** "ADD" → "Created". The API speaks Envers; the UI shouldn't have to. */
export function revisionTypeLabel(type: string): string {
  switch (type) {
    case 'ADD':
      return 'Created'
    case 'DEL':
      return 'Deleted'
    default:
      return 'Edited'
  }
}

/** camelCase field name → "Expected close date". */
export function fieldLabel(field: string): string {
  const spaced = field.replace(/([A-Z])/g, ' $1').toLowerCase()
  return spaced.charAt(0).toUpperCase() + spaced.slice(1)
}
