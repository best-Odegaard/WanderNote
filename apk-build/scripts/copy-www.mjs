/**
 * 把 frontend 的 uni-app H5 生产构建产物同步到 Capacitor 的 webDir（www/）。
 * Capacitor 打包时需要 webDir 已存在，所以每次 cap sync 前先跑这一步。
 */
import { cp, rm, access, readdir } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const source = resolve(here, '../../frontend/dist/build/h5')
const target = resolve(here, '../www')

async function main() {
  try {
    await access(source)
  } catch {
    console.error(`[copy-www] 找不到 H5 构建产物：${source}`)
    console.error('[copy-www] 请先在 frontend/ 执行：npm run build:h5')
    process.exit(1)
  }

  await rm(target, { recursive: true, force: true })
  await cp(source, target, { recursive: true })

  const entries = await readdir(target)
  if (!entries.includes('index.html')) {
    console.error(`[copy-www] 产物里没有 index.html，构建可能不完整：${target}`)
    process.exit(1)
  }
  console.log(`[copy-www] 已同步 ${source} -> ${target}`)
}

main().catch((err) => {
  console.error('[copy-www] 同步失败：', err)
  process.exit(1)
})
