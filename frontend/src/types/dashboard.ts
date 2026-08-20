import type { Deal } from './deal'
import type { DealStage } from './deal'

export interface StageSummary {
    stage: DealStage
    dealCount: number
    totalValue: number
}

export interface DashboardSummary {
    accountCount: number
    contactCount: number
    openDealCount: number
    openPipelineValue: number
    wonCount: number
    wonValue: number
    lostCount: number
    /* Fraction 0-1, or null when nothing has closed. Not a percentage */
    winRate: number | null
    overdueTaskCount: number
    /* Always four entries, in board order - the service guarantees it. */
    pipelineByStage: StageSummary[]
    closingSoon: Deal[]
}