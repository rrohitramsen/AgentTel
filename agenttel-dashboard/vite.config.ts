import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 3001,
    proxy: {
      '/mcp': 'http://localhost:8081',
      '/instrument': {
        target: 'http://localhost:8082',
        rewrite: (path) => path.replace(/^\/instrument/, '/mcp'),
      },
      '/api': {
        target: 'http://localhost:8080',
      },
      '/jaeger': {
        target: 'http://localhost:16686',
        rewrite: (path) => path.replace(/^\/jaeger/, ''),
      },
      '/admin': {
        target: 'http://localhost:8083',
        rewrite: (path) => path.replace(/^\/admin/, ''),
      },
    },
  },
});
