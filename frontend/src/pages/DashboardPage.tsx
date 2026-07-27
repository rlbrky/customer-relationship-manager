import { useEffect, useState } from 'react'
import { useAuth } from '../auth/useAuth'
import { HealthCard, type HealthCardProps } from '../components/HealthCard'
import { fetchHealth } from '../api/health'

export function DashboardPage() {
  const { user } = useAuth()
  const [health, setHealth] = useState<HealthCardProps>({ state: 'loading' })

  useEffect(() => {
    fetchHealth()
      .then((data) => setHealth({ state: 'loaded', data }))
      .catch(() =>
        setHealth({ state: 'error', message: 'Could not reach the CRM API.' }),
      )
  }, [])

  return (
    <main className="content">
      <h1 className="content__title">Dashboard</h1>
      <p className="content__lede">
        Signed in as <strong>{user?.username}</strong> · {user?.roles.join(', ')}
      </p>

      <div className="content__panel">
        <HealthCard {...health} />
      </div>
    </main>
  )
}
