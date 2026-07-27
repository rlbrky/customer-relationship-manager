import { Outlet } from 'react-router-dom'
import { useAuth } from './useAuth'

/**
 * Nested inside ProtectedRoute, so by here we know the user is authenticated —
 * this only asks whether they're an admin. Renders a 403-ish page rather than
 * redirecting, because "you're logged in but not allowed" is a different
 * situation from "you're not logged in" (401 vs 403).
 */
export function AdminRoute() {
  const { user } = useAuth()

  if (!user?.roles.includes('ROLE_ADMIN')) {
    return (
      <main className="content">
        <h1 className="content__title">Not permitted</h1>
        <p className="content__lede">
          This area is restricted to administrators.
        </p>
      </main>
    )
  }

  return <Outlet />
}
