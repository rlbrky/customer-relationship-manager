import type { DragEvent } from 'react'
import { Link } from 'react-router-dom'
import type { Deal, DealStage } from '../types/deal'
import { DEAL_STAGES, dealStageLabel } from '../types/deal'
import { formatMoney } from '../utils/money'

interface DealCardProps {
  deal: Deal
  onMove: (dealId: number, toStage: DealStage) => void
  onEdit: (deal: Deal) => void
  onSetOutcome: (deal: Deal, outcome: 'WON' | 'LOST' | null) => void
  onDelete: (deal: Deal) => void
}

export function DealCard({ deal, onMove, onEdit, onSetOutcome, onDelete }: DealCardProps) {
  const closed = deal.outcome !== null

  function handleDragStart(event: DragEvent<HTMLElement>) {
    event.dataTransfer.setData('text/plain', String(deal.id))
    event.dataTransfer.effectAllowed = 'move'
  }

  return (
    <article
      className={closed ? 'deal deal--closed' : 'deal'}
      // A closed deal can't change stage — the server returns 409, so don't
      // let it be dragged in the first place.
      draggable={!closed}
      onDragStart={handleDragStart}
    >
      <div className="deal__head">
        <span className="deal__title">{deal.title}</span>
        {deal.outcome && (
          <span className={deal.outcome === 'WON' ? 'pill pill--up' : 'pill pill--down'}>
            {deal.outcome === 'WON' ? 'Won' : 'Lost'}
          </span>
        )}
      </div>

      <div className="deal__value">{formatMoney(deal.value)}</div>

      <div className="deal__meta">
        <Link className="link" to={`/accounts/${deal.accountId}`}>{deal.accountName}</Link>
        {deal.expectedCloseDate && <> · {deal.expectedCloseDate}</>}
      </div>

      {/* Drag is an enhancement, not the only path: native HTML5 drag-and-drop
          is keyboard-hostile and unusable on touch, so every card also carries
          a plain stage selector. */}
      {!closed && (
        <label className="deal__move">
          <span className="visually-hidden">Move {deal.title} to stage</span>
          <select
            className="field__input deal__select"
            value={deal.stage}
            onChange={(e) => onMove(deal.id, e.target.value as DealStage)}
          >
            {DEAL_STAGES.map((stage) => (
              <option key={stage} value={stage}>{dealStageLabel(stage)}</option>
            ))}
          </select>
        </label>
      )}

      <div className="deal__actions">
        <button className="btn btn--small btn--ghost" type="button" onClick={() => onEdit(deal)}>
          Edit
        </button>
        {closed ? (
          <button
            className="btn btn--small btn--ghost"
            type="button"
            onClick={() => onSetOutcome(deal, null)}
          >
            Reopen
          </button>
        ) : (
          <>
            <button
              className="btn btn--small btn--ghost"
              type="button"
              onClick={() => onSetOutcome(deal, 'WON')}
            >
              Won
            </button>
            <button
              className="btn btn--small btn--ghost"
              type="button"
              onClick={() => onSetOutcome(deal, 'LOST')}
            >
              Lost
            </button>
          </>
        )}
        <button className="btn btn--small btn--danger" type="button" onClick={() => onDelete(deal)}>
          Delete
        </button>
      </div>
    </article>
  )
}
