/** 格式化工具函数 */

/** 格式化日期 */
export function formatDate(date: string | Date, fmt = 'YYYY-MM-DD'): string {
  const d = typeof date === 'string' ? new Date(date) : date
  if (Number.isNaN(d.getTime())) return ''

  const map: Record<string, number | string> = {
    YYYY: d.getFullYear(),
    MM: String(d.getMonth() + 1).padStart(2, '0'),
    DD: String(d.getDate()).padStart(2, '0'),
    HH: String(d.getHours()).padStart(2, '0'),
    mm: String(d.getMinutes()).padStart(2, '0'),
    ss: String(d.getSeconds()).padStart(2, '0')
  }

  return fmt.replace(/YYYY|MM|DD|HH|mm|ss/g, (key) => String(map[key]))
}

/** 格式化相对时间 */
export function formatRelativeTime(date: string | Date): string {
  const d = typeof date === 'string' ? new Date(date) : date
  const diff = Date.now() - d.getTime()
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour

  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)}分钟前`
  if (diff < day) return `${Math.floor(diff / hour)}小时前`
  if (diff < 7 * day) return `${Math.floor(diff / day)}天前`
  return formatDate(d)
}

/** 格式化数字（点赞数等） */
export function formatCount(count: number): string {
  if (count >= 10000) return `${(count / 10000).toFixed(1)}w`
  if (count >= 1000) return `${(count / 1000).toFixed(1)}k`
  return String(count)
}

/** 格式化金额 */
export function formatPrice(price: number): string {
  return `¥${price.toFixed(0)}`
}

/** 格式化评分 */
export function formatRating(rating: number): string {
  return rating.toFixed(1)
}
