import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  base: '/',
  build: {
    outDir: resolve(__dirname, 'dist'),
    emptyOutDir: true,
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-element': ['element-plus'],
          'vendor-highlight': ['highlight.js'],
          'vendor-markdown': ['markdown-it'],
          'vendor-vue': ['vue', 'vue-router', 'vue-i18n']
        }
      }
    }
  },
  resolve: {
    alias: { '@': resolve(__dirname, 'src') }
  },
  server: {
    port: 3001,
    host: true,
    allowedHosts: ['cloudclaw.run', '.cloudclaw.run'],
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: 'ws://localhost:8080', ws: true, changeOrigin: true }
    }
  }
})
