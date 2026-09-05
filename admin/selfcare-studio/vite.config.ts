import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

// Vite automatically loads .env.<mode> files based on --mode flag.
// 'mode' is set via `npm run dev -- --mode dev|stg|reg|prod`
// which comes from OMOBIO_ENV via the Makefile.

export default defineConfig(({ mode }) => {
  // Load all env vars from .env.<mode>, then overlay process.env so CI /
  // Docker build args can inject the target environment at build time.
  const envFile = loadEnv(mode, process.cwd(), '');
  const env = {
    ...envFile,
    VITE_APP_ENV: process.env.VITE_APP_ENV || envFile.VITE_APP_ENV || mode,
    VITE_API_GATEWAY_URL: process.env.VITE_API_GATEWAY_URL || envFile.VITE_API_GATEWAY_URL || 'http://localhost:8080',
    VITE_PUBLIC_API_BASE_URL: process.env.VITE_PUBLIC_API_BASE_URL || envFile.VITE_PUBLIC_API_BASE_URL || 'http://localhost:8080',
    VITE_ADMIN_PORTAL_URL: process.env.VITE_ADMIN_PORTAL_URL || envFile.VITE_ADMIN_PORTAL_URL || 'http://localhost:3000',
    VITE_OBSERVABILITY_URL: process.env.VITE_OBSERVABILITY_URL || envFile.VITE_OBSERVABILITY_URL || 'http://localhost:3001',
    VITE_ADMIN_PORT: process.env.VITE_ADMIN_PORT || envFile.VITE_ADMIN_PORT || '3000',
  };

  return {
    plugins: [react()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    server: {
      port: Number(env.VITE_ADMIN_PORT) || 3000,
      proxy: {
        '/api': {
          target: env.VITE_API_GATEWAY_URL || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
    build: {
      outDir: 'dist',
      sourcemap: mode === 'dev',
      rollupOptions: {
        output: {
          manualChunks: {
            'react-vendor': ['react', 'react-dom', 'react-router-dom'],
            'editor': ['monaco-editor'],
            'charts': ['recharts'],
            'dnd': ['@dnd-kit/core', '@dnd-kit/sortable'],
          },
        },
      },
    },
    define: {
      // Expose env vars to the app
      'import.meta.env.VITE_APP_ENV': JSON.stringify(env.VITE_APP_ENV || mode),
      'import.meta.env.VITE_API_GATEWAY_URL': JSON.stringify(env.VITE_API_GATEWAY_URL || 'http://localhost:8080'),
      'import.meta.env.VITE_PUBLIC_API_BASE_URL': JSON.stringify(env.VITE_PUBLIC_API_BASE_URL || 'http://localhost:8080'),
      'import.meta.env.VITE_ADMIN_PORTAL_URL': JSON.stringify(env.VITE_ADMIN_PORTAL_URL || 'http://localhost:3000'),
      'import.meta.env.VITE_OBSERVABILITY_URL': JSON.stringify(env.VITE_OBSERVABILITY_URL || 'http://localhost:3001'),
    },
    test: {
      environment: 'happy-dom',
      globals: true,
      setupFiles: ['./src/test/setup.ts'],
    },
  };
});