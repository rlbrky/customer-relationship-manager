import {
  createContext,
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import type { User } from '../types/auth'
import * as authApi from '../api/auth'

type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated'

interface AuthContextValue {
  status: AuthStatus
  user: User | null
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

// eslint-disable-next-line react-refresh/only-export-components
export const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading')
  const [user, setUser] = useState<User | null>(null)

  // On first load, ask the backend who we are. A valid session cookie → the
  // user; anything else (401, network error) → treat as logged out.
  useEffect(() => {
    authApi
      .fetchCurrentUser()
      .then((u) => {
        setUser(u)
        setStatus('authenticated')
      })
      .catch(() => {
        setUser(null)
        setStatus('unauthenticated')
      })
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const u = await authApi.login(username, password)
    setUser(u)
    setStatus('authenticated')
  }, [])

  const logout = useCallback(async () => {
    await authApi.logout()
    setUser(null)
    setStatus('unauthenticated')
  }, [])

  const value = useMemo(
    () => ({ status, user, login, logout }),
    [status, user, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
