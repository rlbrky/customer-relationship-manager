/** Mirrors `com.berkay.crm.model.DealStage` — declaration order is board order. */
export type DealStage = 'PROSPECT' | 'QUALIFIED' | 'PROPOSAL' | 'NEGOTIATION'

export const DEAL_STAGES: DealStage[] = ['PROSPECT', 'QUALIFIED', 'PROPOSAL', 'NEGOTIATION']

/** Mirrors `com.berkay.crm.model.DealOutcome`. null means the deal is still open. */
export type DealOutcome = 'WON' | 'LOST'

/** Mirrors `com.berkay.crm.dto.DealResponse`. */
export interface Deal {
  id: number
  version: number
  title: string
  /** Java BigDecimal → JSON number. See utils/money.ts for why that matters. */
  value: number | null
  stage: DealStage
  outcome: DealOutcome | null
  /** Java LocalDate — a calendar day, no time, no zone: "2026-09-30" */
  expectedCloseDate: string | null
  /** Java Instant — an absolute moment: "2026-09-30T11:00:00Z" */
  closedAt: string | null
  accountId: number
  accountName: string
}

/** Mirrors `DealStageHistoryResponse` — an append-only audit row. */
export interface DealStageHistory {
  id: number
  /** null on the first row: the deal came from nowhere */
  fromStage: DealStage | null
  toStage: DealStage
  changedAt: string
  changedBy: string | null
}

export interface DealCreateRequest {
  title: string
  value: number | null
  stage: DealStage | null
  expectedCloseDate: string | null
}

/** No stage, no outcome — those have their own endpoints, by design. */
export interface DealUpdateRequest {
  version: number
  title: string
  value: number | null
  expectedCloseDate: string | null
}

/** "NEGOTIATION" → "Negotiation" */
export function dealStageLabel(stage: DealStage): string {
  return stage.charAt(0) + stage.slice(1).toLowerCase()
}
