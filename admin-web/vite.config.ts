import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    host: '127.0.0.1',
    port: 5173,
    // 只代理 /admin：管理端全部接口都在 /admin 前缀下。
    // 原来还有一条 '/user' 代理，会和 SPA 自己的路由 /user（用户管理页）撞车 ——
    // 在 /user 上硬刷新会被代理转发到后端，看到后端的 Whitelabel 404 页而不是管理页。
    // 管理端源码里没有任何 /user/** 接口调用，去掉这条不影响功能（生产 nginx 也只代理 /admin/）。
    proxy: {
      '/admin': { target: 'http://127.0.0.1:8080', changeOrigin: true }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1500
  }
})
