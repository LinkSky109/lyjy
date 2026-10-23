import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const target = process.env.LHJYPT_API_TARGET ?? 'http://localhost:8081';

export default defineConfig({
  plugins: [react()],
  server: {
    port: Number(process.env.LHJYPT_WEB_PORT ?? 5173),
    host: true,
    proxy: {
      '/api': { target, changeOrigin: true },
      '/actuator': { target, changeOrigin: true },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    chunkSizeWarningLimit: 1200,
  },
});
