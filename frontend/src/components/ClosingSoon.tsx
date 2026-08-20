import { Link } from 'react-router-dom'
import type { Deal } from '../types/deal'
import { dealStageLabel } from '../types/deal'
import { formatMoney } from '../utils/money'

interface ClosingSoonProps {
  deals: Deal[]
}

export function ClosingSoon({ deals }: ClosingSoonProps) {
  if (deals.length === 0) {
    return <p className="soon__empty">Nothing is expected to close in the next 30 days.</p>
  }

  return (
    <ul className="soon">
      {deals.map((deal) => (
        <li className="soon__row" key={deal.id}>
          <div className="soon__main">
            <span className="soon__title">{deal.title}</span>
            <span className="soon__meta">
              <Link className="link" to={`/accounts/${deal.accountId}`}>{deal.accountName}</Link>
              {' · '}{dealStageLabel(deal.stage)}
              {/* A LocalDate: a calendar day with no time and no zone. Rendered as
                  sent — running it through Date() would shift it by the offset. */}
              {deal.expectedCloseDate && <> · {deal.expectedCloseDate}</>}
            </span>
          </div>
          <span className="soon__value">{formatMoney(deal.value)}</span>
        </li>
      ))}
    </ul>
  )
}
