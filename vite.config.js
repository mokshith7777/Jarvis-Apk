import { defineConfig } from 'vite';

export default defineConfig({
  // Firebase serves from the domain root; GitHub Pages uses the repository subpath.
  base: process.env.GITHUB_PAGES === 'true' ? '/Jarvis-Apk/' : '/',
  build: { assetsInlineLimit: 4096, sourcemap: false },
  server: { host: true }
});
