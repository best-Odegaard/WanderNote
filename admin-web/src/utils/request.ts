import axios, { AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, clearAuth } from '@/utils/auth'

/** 后端统一返回结构 */
export interface ApiResult<T> {
  code: number
  msg: string
  data: T
}

const instance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000
})

instance.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.token = token
  }
  return config
})

instance.interceptors.response.use(
  (res) => {
    const r = res.data as ApiResult<unknown>
    // 业务成功：code 1 或 200
    if (r && (r.code === 1 || r.code === 200)) {
      return res
    }
    // 二进制文件流（如 Excel 模板下载）直接放行，不走业务码校验
    if (res.data instanceof Blob) {
      return res
    }
    ElMessage.error(r?.msg || '请求失败')
    return Promise.reject(new Error(r?.msg || '请求失败'))
  },
  (err) => {
    const status = err.response?.status
    if (status === 401) {
      clearAuth()
      ElMessage.warning('登录已过期，请重新登录')
      if (!location.pathname.startsWith('/login')) {
        location.href = '/login'
      }
    } else if (status === 403) {
      ElMessage.error('没有操作权限')
    } else {
      ElMessage.error(err.response?.data?.msg || '网络异常，请稍后重试')
    }
    return Promise.reject(err)
  }
)

/** 泛型请求封装：返回 Result.data */
async function unwrap<T>(promise: Promise<{ data: ApiResult<T> }>): Promise<T> {
  const res = await promise
  return res.data.data
}

export const http = {
  get: <T>(url: string, params?: Record<string, unknown>) =>
    unwrap<T>(instance.get(url, { params })),
  post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    unwrap<T>(instance.post(url, data, config)),
  put: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) =>
    unwrap<T>(instance.put(url, data, config)),
  delete: <T>(url: string, config?: AxiosRequestConfig) =>
    unwrap<T>(instance.delete(url, config))
}

/** 文件下载（带 token 请求头，blob 方式） */
export async function download(url: string, filename: string): Promise<void> {
  const res = await instance.get(url, { responseType: 'blob' })
  const blob = new Blob([res.data])
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = filename
  link.click()
  URL.revokeObjectURL(link.href)
}

/**
 * 导出 CSV（内容由前端拼，服务端只出数据）
 *
 * 两个必须做的事，少一个运营就会以为功能坏了：
 *   1. 带 BOM（\uFEFF）：不带的话 Excel 会按本地编码猜，中文列名和数据全是乱码。
 *   2. 每个字段用双引号包裹，内部的双引号转义成两个双引号：
 *      画像摘要、运营备注里出现逗号/换行/引号时不会把列串行。
 */
export function downloadCsv(
  filename: string,
  rows: Array<Array<string | number | null | undefined>>
): void {
  const body = rows
    .map((row) => row.map((cell) => `"${String(cell ?? '').replace(/"/g, '""')}"`).join(','))
    .join('\r\n')
  const blob = new Blob(['\uFEFF' + body], { type: 'text/csv;charset=utf-8' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = filename
  link.click()
  URL.revokeObjectURL(link.href)
}
