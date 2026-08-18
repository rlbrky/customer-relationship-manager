import { useState, type DragEvent } from 'react'
import { DealCard } from './DealCard'
import type { Deal, DealStage } from '../types/deal'
import { DEAL_STAGES, dealStageLabel } from '../types/deal'
import { formatMoney, sumMoney } from '../utils/money'

interface KanbanBoardProps {
  deals: Deal[]
  onMove: (dealId: number, toStage: DealStage) => void
  onEdit: (deal: Deal) => void
  onSetOutcome: (deal: Deal, outcome: 'WON' | 'LOST' | null) => void
  onDelete: (deal: Deal) => void
}

export function KanbanBoard({ deals, onMove, onEdit, onSetOutcome, onDelete }: KanbanBoardProps) {
  const [dragOverStage, setDragOverStage] = useState<DealStage | null>(null)

  function handleDragOver(event: DragEvent<HTMLElement>, stage: DealStage) {
    // preventDefault is what marks this element as a valid drop target —
    // without it the browser refuses the drop entirely.
    event.preventDefault()
    event.dataTransfer.dropEffect = 'move'
    setDragOverStage(stage)
  }

  function handleDrop(event: DragEvent<HTMLElement>, stage: DealStage) {
    event.preventDefault()
    setDragOverStage(null)

    const dealId = Number(event.dataTransfer.getData('text/plain'))
    if (!Number.isFinite(dealId)) return

    onMove(dealId, stage)
  }

  return (
    <div className="board">
      {DEAL_STAGES.map((stage) => {
        const column = deals.filter((deal) => deal.stage === stage)
        const total = sumMoney(column.map((deal) => deal.value))

        return (
          <section
            key={stage}
            className={dragOverStage === stage ? 'column column--over' : 'column'}
            onDragOver={(e) => handleDragOver(e, stage)}
            onDragLeave={() => setDragOverStage(null)}
            onDrop={(e) => handleDrop(e, stage)}
          >
            <header className="column__head">
              <h3 className="column__title">{dealStageLabel(stage)}</h3>
              <span className="column__count">{column.length}</span>
            </header>
            <div className="column__total">{formatMoney(total)}</div>

            <div className="column__cards">
              {column.map((deal) => (
                <DealCard
                  key={deal.id}
                  deal={deal}
                  onMove={onMove}
                  onEdit={onEdit}
                  onSetOutcome={onSetOutcome}
                  onDelete={onDelete}
                />
              ))}
              {column.length === 0 && <p className="column__empty">Nothing here</p>}
            </div>
          </section>
        )
      })}
    </div>
  )
}
