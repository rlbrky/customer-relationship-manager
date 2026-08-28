/** Mirrors `com.berkay.crm.dto.ImportError`. */
export interface ImportError {
  /**
   * 1-based line number IN THE FILE, counting the header — the number Excel shows
   * in its row gutter. Not an index into `errors`, and not a row index.
   */
  line: number
  /** The CSV column at fault: "name", "owner". */
  column: string
  message: string
}

/**
 * Mirrors `com.berkay.crm.dto.ImportResult`.
 *
 * `imported` is either 0 or `totalRows`, never in between — the backend validates
 * every row before writing any of them. So a non-empty `errors` always means
 * nothing was written, and the user can fix the file and re-upload the whole thing
 * without worrying about which rows already landed.
 */
export interface ImportResult {
  totalRows: number
  imported: number
  errors: ImportError[]
}
