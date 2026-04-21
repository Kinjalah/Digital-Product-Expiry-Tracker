import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      '/auth': 'http://localhost:8081',
      '/product': 'http://localhost:8081',
      '/notification': 'http://localhost:8081',
    },
  },
})
