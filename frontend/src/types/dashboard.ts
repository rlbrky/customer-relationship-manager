import type { Deal } from './deal'
import type { DealStage } from './deal'
import type {ActivityType} from "./activity.ts";


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
    activityMix: ActivityTypeSummary[]
    /* One entry per day of the window, gap-filled — the backend never sends a
       sparse series, because a line chart would slope straight through the hole. */
    activityByDay: DailyActivity[]
    taskCount: number
    completedTaskCount: number
}

export interface ActivityTypeSummary {

    type: ActivityType
    total: number
}

export interface DailyActivity {

    /* Java LocalDate — a calendar day, no time, no zone: "2026-08-21".
       Never run through new Date(): that reads it as UTC midnight, which is the
       day before for anyone west of Greenwich. */
    day: string
    total: number
}
