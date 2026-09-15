import http from '@/utils/request'
import { USE_MOCK } from '@/utils/constant'

/**
 * 用户端「AI 记住的偏好」开关
 *
 * 这是用户端对 AI 画像唯一的可见入口 —— 文案上刻意不出现"画像"这类内部词。
 * 语义：只关「回灌」（不再把历史偏好喂给 AI），不关「沉淀」（后台仍继续记录），
 * 所以关闭后对话照常，只是 AI 会重新问一遍偏好。
 */

/** mock 模式的开关状态（仅本地开发用） */
let mockSwitchEnabled = true

/** 查询开关 — GET /user/profile/switch */
export function getProfileSwitch(): Promise<{ enabled: boolean }> {
  if (USE_MOCK) {
    return Promise.resolve({ enabled: mockSwitchEnabled })
  }
  return http.get<{ enabled: boolean }>('/user/profile/switch')
}

/** 更新开关 — PUT /user/profile/switch */
export function setProfileSwitch(enabled: boolean): Promise<{ enabled: boolean }> {
  if (USE_MOCK) {
    mockSwitchEnabled = enabled
    return Promise.resolve({ enabled })
  }
  return http.put<{ enabled: boolean }>('/user/profile/switch', { enabled })
}
