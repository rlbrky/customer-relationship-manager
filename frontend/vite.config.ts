import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The dev server proxies API traffic to Spring on :8080 so the browser only ever
// talks to :5173. Same-origin by construction — no CORS in dev, and it matches the
// session-cookie auth model landing in M3.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // 127.0.0.1, not localhost: on Windows/Node 18+ 'localhost' can resolve to
      // IPv6 (::1) first while Tomcat listens on IPv4, adding a per-request delay.
      '/api': 'http://127.0.0.1:8080',
      '/actuator': 'http://127.0.0.1:8080',
    },
  },
})
