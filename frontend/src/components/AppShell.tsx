import { Link, NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

/**
 * Shared chrome for every signed-in page: brand, nav, current user, sign out.
 * Rendered as a layout route, so pages just supply their own <main>.
 */
export function AppShell() {
  const { user, logout } = useAuth()
  const isAdmin = user?.roles.includes('ROLE_ADMIN') ?? false

  return (
    <div className="shell">
      <header className="topbar">
        <div className="topbar__left">
          <Link className="topbar__brand" to="/">CRM</Link>
          <nav className="nav">
            <NavLink className="nav__link" to="/" end>Dashboard</NavLink>
            <NavLink className="nav__link" to="/accounts">Accounts</NavLink>
            <NavLink className="nav__link" to="/contacts">Contacts</NavLink>
            {/* Hiding this is UX, not security — the backend still enforces
                ROLE_ADMIN on every /api/users call. */}
            {isAdmin && <NavLink className="nav__link" to="/users">Users</NavLink>}
          </nav>
        </div>

        <div className="topbar__right">
          <span className="topbar__user">{user?.firstName} {user?.lastName}</span>
          <button className="btn btn--ghost" type="button" onClick={() => logout()}>
            Sign out
          </button>
        </div>
      </header>

      <Outlet />
    </div>
  )
}
