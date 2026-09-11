import { storage } from '@/utils/storage'

/** 当前设备正在进行中的 AI 规划会话，仅用于刷新当前聊天页。 */
export const ACTIVE_CHAT_SESSION_KEY = 'ai_chat_session_id'

export function getActiveChatSession(): string {
  return storage.get<string>(ACTIVE_CHAT_SESSION_KEY, '') || ''
}

export function setActiveChatSession(sessionId: string): void {
  if (sessionId) storage.set(ACTIVE_CHAT_SESSION_KEY, sessionId)
}

export function clearActiveChatSession(): void {
  storage.remove(ACTIVE_CHAT_SESSION_KEY)
}
