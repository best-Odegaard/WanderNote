/**
 * 统一请求封装（Axios 风格，基于 uni.request 实现）
 * 支持：Token 自动携带、Loading 控制、统一错误处理、401 跳转登录
 */
import { API_BASE_URL, REQUEST_TIMEOUT, USE_MOCK } from './constant'
import { getToken, redirectToLogin } from './auth'
export interface RequestConfig {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: Record<string, unknown> | unknown
  params?: Record<string, unknown>
  header?: Record<string, string>
  showLoading?: boolean
  loadingText?: string
  skipAuth?: boolean
  /** 覆盖默认超时（毫秒）。AI 长请求（生成行程）需远大于默认 30s */
  timeout?: number
}

export interface ApiResponse<T = unknown> {
  code: number
  msg: string
  data: T
}

let loadingCount = 0

/** 进行中的请求任务集合（支持"停止生成"等中止操作） */
const pendingTasks = new Set<UniApp.RequestTask>()

/** 中止所有进行中的请求（AI 长请求的"暂停/停止"按钮使用） */
export function abortCurrentRequest() {
  pendingTasks.forEach((t) => t.abort())
  pendingTasks.clear()
}

function showLoading(text = '加载中...') {
  loadingCount++
  if (loadingCount === 1) {
    uni.showLoading({ title: text, mask: true })
  }
}

function hideLoading() {
  loadingCount = Math.max(0, loadingCount - 1)
  if (loadingCount === 0) {
    uni.hideLoading()
  }
}

function buildUrl(url: string, params?: Record<string, unknown>): string {
  const base = url.startsWith('http') ? url : `${API_BASE_URL}${url}`
  if (!params || Object.keys(params).length === 0) return base

  const query = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
    .join('&')

  return query ? `${base}${base.includes('?') ? '&' : '?'}${query}` : base
}

function request<T = unknown>(config: RequestConfig): Promise<T> {
  const {
    url,
    method = 'GET',
    data,
    params,
    header = {},
    showLoading: needLoading = false,
    loadingText,
    skipAuth = false,
    timeout
  } = config

  if (needLoading) showLoading(loadingText)

  const token = getToken()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...header
  }
  if (token && !skipAuth) {
    headers.Authentication = token
  }

  return new Promise((resolve, reject) => {
    const task = uni.request({
      url: buildUrl(url, method === 'GET' ? (params as Record<string, unknown>) : undefined),
      method,
      data: method !== 'GET' ? (data as Record<string, unknown>) : undefined,
      header: headers,
      timeout: timeout ?? REQUEST_TIMEOUT,
      success: (res) => {
        const statusCode = res.statusCode
        const body = res.data as ApiResponse<T>

        if (statusCode === 401) {
          uni.showToast({ title: '请先登录', icon: 'none' })
          redirectToLogin()
          reject(new Error('未授权'))
          return
        }

        if (statusCode >= 200 && statusCode < 300) {
          if (body && typeof body === 'object' && 'code' in body) {
            // 后端约定：code=1 成功，0 及其它为失败（如"预算不能为负数"等业务错误需提示用户）
            if (body.code === 1 || body.code === 200) {
              resolve(body.data)
            } else {
              const msg = body.msg || '请求失败'
              uni.showToast({ title: msg, icon: 'none' })
              console.error('[API Error]', url, body)
              reject(new Error(msg))
            }
          } else {
            resolve(body as T)
          }
        } else {
          const msg = `请求失败(${statusCode})`
          uni.showToast({ title: msg, icon: 'none' })
          console.error('[HTTP Error]', url, statusCode, res.data)
          reject(new Error(msg))
        }
      },
      fail: (err) => {
        // 用户主动中止（abort）时不算网络异常，不弹提示
        const aborted = String(err?.errMsg || '').includes('abort')
        if (!aborted && !USE_MOCK) {
          uni.showToast({ title: '网络异常，请稍后重试', icon: 'none' })
        }
        console.warn('[Network Error]', url, err)
        reject(err)
      },
      complete: () => {
        pendingTasks.delete(task)
        if (needLoading) hideLoading()
      }
    })
    pendingTasks.add(task)
  })
}

export const http = {
  get<T>(url: string, params?: Record<string, unknown>, config?: Partial<RequestConfig>) {
    return request<T>({ url, method: 'GET', params, ...config })
  },
  post<T>(url: string, data?: unknown, config?: Partial<RequestConfig>) {
    return request<T>({ url, method: 'POST', data, ...config })
  },
  put<T>(url: string, data?: unknown, config?: Partial<RequestConfig>) {
    return request<T>({ url, method: 'PUT', data, ...config })
  },
  delete<T>(url: string, params?: Record<string, unknown>, config?: Partial<RequestConfig>) {
    return request<T>({ url, method: 'DELETE', params, ...config })
  }
}

export default http
