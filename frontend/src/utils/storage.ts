/** 本地存储封装 */

export const storage = {
  get<T>(key: string, defaultValue?: T): T | undefined {
    try {
      const value = uni.getStorageSync(key)
      if (value === '' || value === undefined || value === null) {
        return defaultValue
      }
      if (typeof value === 'string') {
        try {
          return JSON.parse(value) as T
        } catch {
          return value as T
        }
      }
      return value as T
    } catch {
      return defaultValue
    }
  },

  set(key: string, value: unknown): void {
    const data = typeof value === 'object' ? JSON.stringify(value) : value
    uni.setStorageSync(key, data)
  },

  remove(key: string): void {
    uni.removeStorageSync(key)
  },

  clear(): void {
    uni.clearStorageSync()
  }
}
