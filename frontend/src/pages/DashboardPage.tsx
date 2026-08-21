import { useEffect, useState } from 'react'
import { useAuth } from '../auth/useAuth'
import { HealthCard, type HealthCardProps } from '../components/HealthCard'
import { StatTile } from '../components/StatTile'
import { StageFunnel } from '../components/StageFunnel'
import { PipelineDonut } from '../components/PipelineDonut'
import { OutcomeDonut } from '../components/OutcomeDonut'
import { ActivityMixDonut } from '../components/ActivityMixDonut'
import { ClosingSoon } from '../components/ClosingSoon'
import { AreaChart } from '../components/AreaChart'
import { WeekdayHeatmap } from '../components/WeekdayHeatmap'
import { Meter } from '../components/Meter'
import { fetchHealth } from '../api/health'
import { fetchDashboardSummary } from '../api/dashboard'
import { ApiError } from '../api/client'
import type { DashboardSummary } from '../types/dashboard'
import { formatMoney } from '../utils/money'

export function DashboardPage() {
  const { user } = useAuth()
  const [summary, setSummary] = useState<DashboardSummary | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [health, setHealth] = useState<HealthCardProps>({ state: 'loading' })

  // The backend scopes every number to what this user may see. Saying so is
  // worth a line: a rep's totals being smaller than a manager's is the feature,
  // not a bug they should report.
  const isPrivileged =
    user?.roles.some((role) => role === 'ROLE_ADMIN' || role === 'ROLE_MANAGER') ?? false

  useEffect(() => {
    fetchDashboardSummary()
      .then(setSummary)
      .catch((err: unknown) =>
        setError(err instanceof ApiError ? err.message : 'Could not load the dashboard.'),
      )
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    fetchHealth()
      .then((data) => setHealth({ state: 'loaded', data }))
      .catch(() => setHealth({ state: 'error', message: 'Could not reach the CRM API.' }))
  }, [])

  return (
    <main className="content content--wide">
      <h1 className="content__title">Dashboard</h1>
      <p className="content__lede">
        {isPrivileged
          ? 'Across every account in the CRM.'
          : 'Across the accounts you own.'}
      </p>

      {loading && <p className="card__hint">Loading dashboard…</p>}
      {error && <p className="form__error" role="alert">{error}</p>}

      {summary && (
        <>
          <div className="tiles">
            <StatTile label="Accounts" value={String(summary.accountCount)} />
            <StatTile label="Contacts" value={String(summary.contactCount)} />
            <StatTile
              label="Open deals"
              value={String(summary.openDealCount)}
              hint={`${formatMoney(summary.openPipelineValue)} in pipeline`}
            />
            <StatTile
              label="Won"
              value={formatMoney(summary.wonValue)}
              hint={`${summary.wonCount} ${summary.wonCount === 1 ? 'deal' : 'deals'}`}
              tone={summary.wonCount > 0 ? 'up' : 'neutral'}
            />
            <StatTile
              label="Overdue tasks"
              value={String(summary.overdueTaskCount)}
              tone={summary.overdueTaskCount > 0 ? 'down' : 'neutral'}
            />
            <StatTile
              label="Activity"
              value={String(summary.activityByDay.reduce((sum, d) => sum + d.total, 0))}
              hint="last 30 days"
              spark={summary.activityByDay.map((d) => d.total)}
            />
          </div>

          <div className="panels">
            <section className="panel">
              <h2 className="section__title">Deals by stage</h2>
              <p className="panel__sub">How many open deals sit in each stage</p>
              <PipelineDonut stages={summary.pipelineByStage} />
            </section>

            <section className="panel">
              <h2 className="section__title">Deals by outcome</h2>
              <p className="panel__sub">Where every deal stands</p>
              <OutcomeDonut
                openCount={summary.openDealCount}
                wonCount={summary.wonCount}
                lostCount={summary.lostCount}
                winRate={summary.winRate}
              />
            </section>

            <section className="panel">
              <h2 className="section__title">Activity mix</h2>
              <p className="panel__sub">How the team has been reaching out</p>
              <ActivityMixDonut mix={summary.activityMix} />
            </section>
          </div>

          <section className="panel">
            <h2 className="section__title">Activity over time</h2>
            <p className="panel__sub">Interactions logged per day, last 30 days</p>
            <AreaChart
              points={summary.activityByDay}
              ariaLabel="Activities logged per day over the last thirty days"
            />
          </section>

          <div className="panels">
            <section className="panel">
              <h2 className="section__title">Busiest days</h2>
              <p className="panel__sub">The same 30 days, folded onto the week</p>
              <WeekdayHeatmap points={summary.activityByDay} />
            </section>

            <section className="panel">
              <h2 className="section__title">Task completion</h2>
              <p className="panel__sub">Tasks marked done, all time</p>
              <Meter
                value={summary.completedTaskCount}
                max={summary.taskCount}
                caption="tasks completed"
                emptyMessage="No tasks have been created yet."
              />
            </section>
          </div>

          <section className="panel">
            <h2 className="section__title">Open pipeline value by stage</h2>
            <p className="panel__sub">
              Money, not headcount — a stage can hold one large deal or six small ones
            </p>
            <StageFunnel stages={summary.pipelineByStage} />
          </section>

          <section className="panel">
            <h2 className="section__title">Closing soon</h2>
            <ClosingSoon deals={summary.closingSoon} />
          </section>
        </>
      )}

      <div className="dash__footer">
        <HealthCard {...health} />
      </div>
    </main>
  )
}
