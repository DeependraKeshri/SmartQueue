import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development the Vite server proxies API and WebSocket traffic to the Spring Boot backend.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: process.env.VITE_BACKEND || 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: (process.env.VITE_BACKEND || 'http://localhost:8080').replace(/^http/, 'ws'), ws: true }
    }
  }
});
