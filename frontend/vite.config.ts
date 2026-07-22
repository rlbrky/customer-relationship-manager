import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The dev server proxies API traffic to Spring on :8080 so the browser only ever
// talks to :5173. Same-origin by construction — no CORS in dev, and it matches the
// session-cookie auth model landing in M3.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
})
