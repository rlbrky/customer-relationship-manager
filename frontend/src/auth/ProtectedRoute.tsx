import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from './useAuth'

/**
 * Gate for authenticated routes. While we're still checking the session, show a
 * placeholder (don't flash the login page); if unauthenticated, redirect to it.
 */
export function ProtectedRoute() {
  const { status } = useAuth()

  if (status === 'loading') {
    return (
      <main className="app">
        <p className="card__hint">Loading…</p>
      </main>
    )
  }

  if (status === 'unauthenticated') {
    return <Navigate to="/login" replace />
  }

  return <Outlet />
}
