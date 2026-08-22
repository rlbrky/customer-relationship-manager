import { useCallback, useEffect, useState } from 'react'
import { KanbanBoard } from '../components/KanbanBoard'
import { DealForm } from '../components/DealForm'
import { ConflictBanner } from '../components/ConflictBanner'
import {ApiError} from '../api/client'
import * as dealsApi from '../api/deals'
import { fetchAccounts } from '../api/accounts'
import type { Account } from '../types/account'
import type {
  Deal,
  DealCreateRequest,
  DealOutcome,
  DealStage,
  DealStageHistory,
  DealUpdateRequest,
} from '../types/deal'

type Editor =
  | { kind: 'none' }
  | { kind: 'create' }
  | { kind: 'edit'; deal: Deal; history: DealStageHistory[] }

/** The board shows every open deal at once; 100 is the server's max page size. */
const BOARD_SIZE = 100

export function DealsPage() {
  const [deals, setDeals] = useState<Deal[]>([])
  const [accounts, setAccounts] = useState<Account[]>([])
  const [showClosed, setShowClosed] = useState(false)

  const [loading, setLoading] = useState(true)
  const [listError, setListError] = useState<string | null>(null)

  const [editor, setEditor] = useState<Editor>({ kind: 'none' })
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [moveError, setMoveError] = useState<string | null>(null)
  const [conflict, setConflict] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setListError(null)
    try {
      const page = await dealsApi.fetchDeals(
        0,
        BOARD_SIZE,
        'expectedCloseDate,asc',
        undefined,
        showClosed ? undefined : true, // `true` = open only
      )
      setDeals(page.content)
    } catch (err) {
      setListError(err instanceof ApiError ? err.message : 'Could not load the pipeline.')
    } finally {
      setLoading(false)
    }
  }, [showClosed])

  useEffect(() => {
    void load()
  }, [load])

  // the create form needs somewhere to put the deal
  useEffect(() => {
    fetchAccounts(0, 100)
      .then((page) => setAccounts(page.content))
      .catch(() => setAccounts([]))
  }, [])

  async function handleMove(dealId: number, toStage: DealStage) {

    setMoveError(null) // clear any previous failure before trying again

    const deal = deals.find((deal) => deal.id === dealId)

    if (!deal || deal.stage === toStage) return // nothing moved - nothing to show

    const snapshot = deals // captured for rollback - never mutated

    // Optimistic: new array with this card already in its new column
    setDeals(deals.map((deal) => (deal.id === dealId ? { ...deal, stage: toStage } : deal)))

    try {

      const updated = await dealsApi.changeStage(dealId, toStage)
      // use server authoritative row
      setDeals((current) => current.map((deal) => (deal.id === dealId ? updated : deal)))

      if (editor.kind === 'edit' && editor.deal.id === dealId) {

        const history = await dealsApi.fetchDealHistory(dealId).catch(() => [])
        setEditor({ kind: 'edit', deal: updated, history })
      }
    } catch (err) {

      setDeals(snapshot)
      setMoveError(
          err instanceof ApiError && err.status === 409
          ? 'That deal is closed — reopen it before moving it.'
              : 'Could not move the deal.',
      )
    }
  }
  // ────────────────────────────────────────────────────────────────────────────

  async function runMutation(action: () => Promise<unknown>) {
    setSubmitting(true)
    setFormError(null)
    try {
      await action()
      setEditor({ kind: 'none' })
      await load()
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : 'Something went wrong.')
    } finally {
      setSubmitting(false)
    }
  }

  function handleCreate(accountId: number, request: DealCreateRequest) {
    void runMutation(() => dealsApi.createDeal(accountId, request))
  }

  /**
    * Only the edit form is version-guarded. changeStage and setOutcome stay on
    * runMutation on purpose: a drag or a Won/Lost click is a single-field
    * transition with nothing typed to protect, and the server already guards the
    * one transition that matters (a closed deal cannot change stage).
    */
  async function handleUpdate(id: number, request: DealUpdateRequest) {
    setSubmitting(true)
    setFormError(null)
    setConflict(false)
    try {
      await dealsApi.updateDeal(id, request)
      setEditor({ kind: 'none' })
      await load()
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setConflict(true)
      } else {
        setFormError(err instanceof ApiError ? err.message : 'Something went wrong.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  async function loadCurrentValues(id: number) {
    try {
      const [deal, history] = await Promise.all([
        dealsApi.fetchDeal(id),
        dealsApi.fetchDealHistory(id).catch(() => []),
      ])
      setEditor({ kind: 'edit', deal, history })
      setConflict(false)
    } catch {
      setFormError('Could not load the current values.')
    }
  }

  function handleSetOutcome(deal: Deal, outcome: DealOutcome | null) {
    void runMutation(() => dealsApi.setOutcome(deal.id, outcome))
  }

  function handleDelete(deal: Deal) {
    if (!window.confirm(`Delete "${deal.title}"?`)) return
    void runMutation(() => dealsApi.deleteDeal(deal.id))
  }

  async function openEditor(deal: Deal) {
    setFormError(null)
    setConflict(false)
    // history is read-only context; a failure to load it shouldn't block editing
    const history = await dealsApi.fetchDealHistory(deal.id).catch(() => [])
    setEditor({ kind: 'edit', deal, history })
  }

  return (
    <main className="content content--wide">
      <div className="content__head">
        <div>
          <h1 className="content__title">Pipeline</h1>
          <p className="content__lede">Drag a deal to move it through the pipeline.</p>
        </div>
        {editor.kind === 'none' && (
          <button
            className="btn btn--primary"
            type="button"
            onClick={() => { setFormError(null); setEditor({ kind: 'create' }) }}
          >
            New deal
          </button>
        )}
      </div>

      <div className="filters">
        <label className="check">
          <input
            type="checkbox"
            checked={showClosed}
            onChange={(e) => setShowClosed(e.target.checked)}
          />
          <span>Include closed deals</span>
        </label>
      </div>

      {editor.kind === 'create' && (
        <DealForm
          mode="create"
          accounts={accounts}
          submitting={submitting}
          error={formError}
          onSubmit={handleCreate}
          onCancel={() => setEditor({ kind: 'none' })}
        />
      )}

      {editor.kind === 'edit' && (
        <>
          {conflict && (
            <ConflictBanner
              noun="deal"
              onReload={() => void loadCurrentValues(editor.deal.id)}
            />
          )}

          <DealForm
            key={editor.deal.version}
            mode="edit"
            deal={editor.deal}
            history={editor.history}
            submitting={submitting}
            error={formError}
            onSubmit={(request) => void handleUpdate(editor.deal.id, request)}
            onCancel={() => { setConflict(false); setEditor({ kind: 'none' }) }}
          />
        </>
      )}

      {editor.kind === 'none' && formError && (
        <p className="form__error" role="alert">{formError}</p>
      )}
      {moveError && <p className="form__error" role="alert">{moveError}</p>}

      {loading && <p className="card__hint">Loading pipeline…</p>}
      {listError && <p className="form__error" role="alert">{listError}</p>}

      {!loading && !listError && (
        <KanbanBoard
          deals={deals}
          onMove={(dealId, toStage) => void handleMove(dealId, toStage)}
          onEdit={(deal) => void openEditor(deal)}
          onSetOutcome={handleSetOutcome}
          onDelete={handleDelete}
        />
      )}
    </main>
  )
}
