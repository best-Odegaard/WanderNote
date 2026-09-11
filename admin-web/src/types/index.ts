/** 通用类型（与后端 Result/PageResult/DTO/VO 对齐） */

export interface PageResult<T> {
  total: number
  records: T[]
}

export interface AdminInfo {
  id: number
  username: string
  nickname: string
  roleId: number | null
  roleKey: string | null
  roleName: string | null
  perms: string[]
}

export interface LoginResult extends AdminInfo {
  token: string
}

/** 意见反馈 */
export interface FeedbackItem {
  id: number
  userId: number
  username: string
  nickname: string
  avatar: string
  type: string
  content: string
  images: string[]
  contact: string
  status: number
  reply: string
  adminId: number
  createTime: string
  updateTime: string
}

/** App 用户 */
export interface UserItem {
  id: number
  username: string
  /** 登录密码（明文，管理端查看） */
  password: string
  phone: string
  email: string
  nickname: string
  avatar: string
  gender: number
  birthday: string
  bio: string
  points: number
  status: number
  createTime: string
}

/** 游记列表项 */
export interface JournalItem {
  id: number
  userId: number
  username: string
  nickname: string
  title: string
  location: string
  status: number
  likeCount: number
  collectCount: number
  commentCount: number
  createTime: string
}

/** 评论 */
export interface CommentItem {
  id: number
  journalId: number
  userId: number
  username: string
  nickname: string
  avatar: string
  parentCommentId?: number
  parentUsername?: string
  content: string
  status: number
  createTime: string
}

/** 游记详情 */
export interface JournalDetail {
  id: number
  userId: number
  username: string
  nickname: string
  title: string
  content: string
  images: string
  tags: string
  location: string
  status: number
  likeCount: number
  collectCount: number
  createTime: string
  comments: CommentItem[]
}

/** 景点 */
export interface ScenicItem {
  id: number
  name: string
  cover: string
  city: string
  rating: number
  category: string
  price: number
  openTime: string
  description: string
  address: string
  images: string
  isHot: number
  viewCount: number
  sortOrder: number
  status: number
  createTime: string
}

/** 活动 */
export interface ActivityItem {
  id: number
  title: string
  cover: string
  city: string
  location: string
  category: string
  startTime: string
  endTime: string
  description: string
  isHot: number
  enrollCount: number
  sortOrder: number
  status: number
  createTime: string
}

/** Banner */
export interface BannerItem {
  id: number
  title: string
  subtitle: string
  emoji: string
  imageUrl: string
  linkUrl: string
  sortOrder: number
  status: number
  createTime: string
}

/** 城市 */
export interface CityItem {
  id: number
  name: string
  cover: string
  rating: number
  sortOrder: number
  status: number
  createTime: string
}

/** 管理员 */
export interface AdminItem {
  id: number
  username: string
  nickname: string
  roleId: number
  roleName: string
  status: number
  lastLoginTime: string
  createTime: string
}

/** 角色 */
export interface RoleItem {
  id: number
  roleKey: string
  roleName: string
  perms: string
  description: string
  createTime: string
}

/** 权限点 */
export interface PermItem {
  key: string
  name: string
}

/** 看板 */
export interface TrendItem {
  date: string
  userCount: number
  journalCount: number
}

/** Excel 导入失败明细 */
export interface ImportErrorItem {
  row: number
  message: string
}

/** Excel 批量导入结果 */
export interface ImportResult {
  total: number
  success: number
  fail: number
  errors: ImportErrorItem[]
}

export interface FeedbackDistItem {
  status: number
  count: number
}

export interface DashboardData {
  totalUser: number
  todayUser: number
  totalJournal: number
  todayJournal: number
  totalComment: number
  totalFeedback: number
  pendingFeedback: number
  trend: TrendItem[]
  feedbackDist: FeedbackDistItem[]
}
