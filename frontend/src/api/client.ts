/**
 * The one place all API calls go through. Handles: JSON headers, sending the
 * session cookie, echoing the CSRF token on mutations, and turning non-2xx
 * responses into a typed ApiError (which carries the HTTP status).
 */

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

/** Carries the HTTP status so callers can branch (e.g. 401 → "not logged in"). */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'))
  return match ? decodeURIComponent(match[1]) : null
}

export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase()
  const headers = new Headers(options.headers)

  // FormData is the exception: the browser must set Content-Type itself, because
  // only it knows the multipart boundary it generated — and that boundary is what
  // marks where each part of the body starts. Setting the header by hand loses it,
  // and the server rejects the upload before @RequestParam binds anything.
  const isMultipart = options.body instanceof FormData

  if (options.body && !isMultipart && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  // CSRF: echo the token cookie on state-changing requests. Harmless until the
  // backend starts emitting the XSRF-TOKEN cookie (finalised in M5, where the
  // first protected mutation lives) — until then the cookie is simply absent.
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const token = readCookie(CSRF_COOKIE)
    if (token) headers.set(CSRF_HEADER, token)
  }

  const response = await fetch(path, {
    ...options,
    headers,
    credentials: 'include', // send/receive the session cookie
  })

  if (response.status === 204) {
    return undefined as T
  }

  const isJson = response.headers.get('Content-Type')?.includes('application/json')
  const payload = isJson ? await response.json() : await response.text()

  if (!response.ok) {
    const message =
      isJson && payload && typeof payload === 'object' && 'message' in payload
        ? String(payload.message)
        : `Request failed (${response.status})`
    throw new ApiError(response.status, message)
  }

  return payload as T
}
