import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': 'http://localhost:8080',
      '/otlp': {
        target: 'http://localhost:4318',
        rewrite: (path) => path.replace(/^\/otlp/, ''),
      },
    },
  },
});
