/** Mirrors `com.berkay.crm.dto.DeletedAccountResponse`. */
export interface DeletedAccount {
  id: number
  name: string
  industry: string | null
  /** Java Instant — the moment the soft delete ran. */
  deletedAt: string
  ownerName: string
  /**
   * Read out of the audit log, not the account row: @SQLDelete is raw SQL and
   * never wrote last_modified_by. Null for anything deleted before auditing
   * was switched on, because nothing recorded it at the time.
   */
  deletedBy: string | null
}
