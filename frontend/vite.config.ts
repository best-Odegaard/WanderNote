import { defineConfig, loadEnv } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

/**
 * 环境变量校验（构建/启动前拦一次）。
 *
 * 为什么放在这里而不是运行时提示：
 *   `VITE_USE_MOCK` 曾经默认 true，而真实生产配置写在 `.env.production.local`
 *   （被 .gitignore 忽略）。干净 clone / CI / 打包机拿不到该文件时，
 *   构建照样成功、包照样出，但整条 AI 链路静默走 mock 假数据，
 *   直到人工核对才发现。配置问题必须在**构建期**报错，
 *   不能留到用户手机上表现成「网络异常」。
 *
 * 规则：
 *   1. 显式 `VITE_USE_MOCK=true`（离线演示包）→ 跳过校验，允许无后端；
 *   2. 否则必须提供 `VITE_API_BASE_URL`；
 *   3. 且不能还是 `your-server-ip` 这类占位值。
 */
function assertEnv(mode: string, env: Record<string, string>) {
  if (env.VITE_USE_MOCK === 'true') {
    // 离线演示包：明确要走假数据，不要求后端地址
    console.warn('[env] VITE_USE_MOCK=true：本次产物走本地 mock 假数据，不要用于验收/上线')
    return
  }

  const base = (env.VITE_API_BASE_URL || '').trim()
  if (!base) {
    throw new Error(
      `[env] 缺少 VITE_API_BASE_URL（mode=${mode}）。` +
        `请在 frontend/.env.${mode}.local 或 frontend/.env.${mode} 里配置后端地址，` +
        '例如 VITE_API_BASE_URL=https://api.example.com；' +
        '若确实要出「无后端也能跑的离线演示包」，显式设置 VITE_USE_MOCK=true。'
    )
  }
  if (base.includes('your-server-ip')) {
    throw new Error(
      `[env] VITE_API_BASE_URL 仍是占位值（${base}，mode=${mode}）。` +
        '请配置真实后端地址，或显式设置 VITE_USE_MOCK=true 走离线演示包。'
    )
  }
}

export default defineConfig(({ mode }) => {
  // 第一参数 mode、第二参数目录、第三参数空前缀 = 把 .env* 里的 VITE_ 变量全读进来（含 .local）
  assertEnv(mode, loadEnv(mode, process.cwd(), 'VITE_'))

  return {
    plugins: [uni()],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, '')
        },
        '/ai': {
          target: 'http://localhost:8080',
          changeOrigin: true
        },
        '/travel': {
          target: 'http://localhost:8080',
          changeOrigin: true
        }
      }
    }
  }
})
