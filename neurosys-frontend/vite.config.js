import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

import { reticle } from '@reticlehq/vite-plugin';
export default defineConfig({
  plugins: [reticle(),react()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: process.env.VITE_BACKEND_URL || 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
      },
      '/ws-neurosys': {
        target: process.env.VITE_BACKEND_URL || 'http://localhost:8080',
        ws: true,
        changeOrigin: true,
      }
    }
  },
  define: {
    global: 'window'
  }
});
