import { defineConfig } from 'vite';
export default defineConfig({
  base: '/Jarvis-Apk/',
  build: { assetsInlineLimit: 4096, sourcemap: false },
  server: { host: true }
});