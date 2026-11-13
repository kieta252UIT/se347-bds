import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
// @ts-expect-error path is available in node environment
import path from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      // @ts-expect-error __dirname is available in node environment
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // Chi chuyen tiep /api sang Spring Boot Backend (cung nguon /api/*).
      // Cac route /auth/verify-email va /auth/reset-password thuoc ve React SPA.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: false,
        xfwd: true,
      },
    },
  },
})
