import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [react()],
    server: {
      proxy: {
        '/api': {
          target: env.BACKEND_PROXY_TARGET || 'http://localhost:8080',
          // Preserve the browser's host and port, matching the Nginx proxy.
          changeOrigin: false,
        },
      },
    },
  }
})
