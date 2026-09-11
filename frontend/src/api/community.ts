import http from '@/utils/request'
import { USE_MOCK } from '@/utils/constant'
import * as mock from '@/api/mock/handlers'

/**
 * 游记帖子（对齐后端 TravelJournalVO）
 * 后端 VO 字段是扁平的（nickname/avatar 在顶层），前端保留 author 嵌套对象便于模板使用
 */
export interface CommunityPost {
  id: number
  title: string
  cover: string
  content?: string
  images?: string[]
  /** 后端 VO 返回 imgUrls */
  imgUrls?: string[]
  tags?: string[]
  location?: string
  author: {
    id: number
    nickname: string
    avatar: string
  }
  /** 后端 VO 顶层字段（原始值，用于映射） */
  userId?: number
  nickname?: string
  avatar?: string
  likeCount: number
  collectCount: number
  commentCount: number
  isLiked?: boolean
  isCollected?: boolean
  isFollowed?: boolean
  createdAt: string
  createTime?: string
}

/**
 * 将后端 TravelJournalVO 扁平结构转换为前端 CommunityPost
 * 后端: { id, userId, title, content, avatar, nickname, location, tags, imgUrls, likeCount, collectCount, createTime }
 * 前端: { id, title, cover, content, images, tags, location, author: { id, nickname, avatar }, likeCount, collectCount, createdAt }
 */
export function normalizeJournalVO(raw: Record<string, unknown>): CommunityPost {
  const images = (raw.imgUrls as string[]) || (raw.images as string[]) || []
  return {
    id: raw.id as number,
    title: raw.title as string,
    cover: (raw.cover as string) || images[0] || '',
    content: raw.content as string,
    images,
    imgUrls: images,
    tags: (raw.tags as string[]) || [],
    location: raw.location as string,
    author: {
      id: (raw.userId as number) || 0,
      nickname: (raw.nickname as string) || '',
      avatar: (raw.avatar as string) || ''
    },
    userId: raw.userId as number,
    nickname: raw.nickname as string,
    avatar: raw.avatar as string,
    likeCount: (raw.likeCount as number) || 0,
    collectCount: (raw.collectCount as number) || 0,
    commentCount: (raw.commentCount as number) || 0,
    isLiked: !!raw.isLiked,
    isCollected: !!raw.isCollected,
    createdAt: (raw.createTime as string) || (raw.createdAt as string) || '',
    createTime: (raw.createTime as string) || (raw.createdAt as string) || ''
  }
}

export interface CommunityQuery {
  keyword?: string
  tag?: string
  /** 后端 PageQuery 字段名为 pageNum */
  pageNum?: number
  pageSize?: number
}

/** 对齐后端 TravelJournalPublishDTO */
export interface PublishPostParams {
  title: string
  content: string
  /** 后端字段名为 imageUrls */
  imageUrls: string[]
  tags: string[]
  location?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
}

/** 游记列表 — GET /journal/list */
export async function getCommunityList(params: CommunityQuery): Promise<PageResult<CommunityPost>> {
  if (USE_MOCK) return mock.mockGetCommunityList(params)
  const res = await http.get<PageResult<Record<string, unknown>>>('/journal/list', params as Record<string, unknown>)
  return {
    total: res.total,
    records: (res.records || []).map(normalizeJournalVO)
  }
}

/** 游记详情 — GET /journal/:id */
export async function getCommunityDetail(id: number): Promise<CommunityPost> {
  if (USE_MOCK) return mock.mockGetCommunityDetail(id)
  const raw = await http.get<Record<string, unknown>>(`/journal/${id}`)
  return normalizeJournalVO(raw)
}

/** 发布游记 — POST /journal/publish（字段对齐 TravelJournalPublishDTO） */
export function publishPost(data: PublishPostParams) {
  if (USE_MOCK) return mock.mockPublishPost(data)
  return http.post<CommunityPost>('/journal/publish', data, { showLoading: true, loadingText: '发布中...' })
}

/** 点赞/取消点赞 — PUT /journal/like/{journalId}（后端 toggle 实现） */
export function toggleJournalLike(journalId: number) {
  if (USE_MOCK) return mock.mockToggleJournalLike(journalId)
  return http.put<void>(`/journal/like/${journalId}`)
}

/** @deprecated 使用 toggleJournalLike 代替 */
export function likePost(id: number) {
  return toggleJournalLike(id)
}

/** @deprecated 使用 toggleJournalLike 代替，后端统一用 PUT toggle */
export function unlikePost(_id: number) {
  if (USE_MOCK) return mock.mockUnlikePost(_id)
  // 后端没有单独取消点赞接口，统一走 toggle
  return http.put<void>(`/journal/like/${_id}`)
}

// ── 以下接口后端暂未实现，当前仅走 Mock ──

/** 收藏 — POST /journal/collect/:id（对齐后端 Controller 映射） */
export function collectPost(id: number) {
  if (USE_MOCK) return mock.mockCollectPost(id)
  return http.post<void>(`/journal/collect/${id}`)
}

/** 取消收藏 — DELETE /journal/collect/:id（对齐后端 Controller 映射） */
export function uncollectPost(id: number) {
  if (USE_MOCK) return mock.mockUncollectPost(id)
  return http.delete<void>(`/journal/collect/${id}`)
}

/** 删除游记 — DELETE /journal/:id（后端校验仅本人可删） */
export function deletePost(journalId: number) {
  if (USE_MOCK) return mock.mockDeletePost(journalId)
  return http.delete<void>(`/journal/${journalId}`)
}

/** 我的游记列表 — GET /journal/my/list */
export async function getMyJournals(): Promise<CommunityPost[]> {
  if (USE_MOCK) return mock.mockGetMyJournals()
  const res = await http.get<Record<string, unknown>[]>('/journal/my/list')
  return (res || []).map(normalizeJournalVO)
}

/** 我的收藏游记列表 — GET /journal/my/collects */
export async function getMyCollects(): Promise<CommunityPost[]> {
  if (USE_MOCK) return mock.mockGetMyCollects()
  const res = await http.get<Record<string, unknown>[]>('/journal/my/collects')
  return (res || []).map(normalizeJournalVO)
}

/** 关注作者 — POST /journal/follow/:authorId（后端暂未实现） */
export function followAuthor(authorId: number) {
  if (USE_MOCK) return mock.mockFollowAuthor(authorId)
  return http.post<void>(`/journal/follow/${authorId}`)
}

/** 热门攻略 — GET /journal/hot（后端暂未实现） */
export function getHotPosts(limit = 10) {
  if (USE_MOCK) return mock.mockGetHotPosts(limit)
  return http.get<CommunityPost[]>('/journal/hot', { limit })
}

/** 发表评论 — POST /journal/:postId/comment（后端暂未实现） */
export function addComment(postId: number, content: string) {
  if (USE_MOCK) return mock.mockCommentPost(postId, content)
  return http.post<void>(`/journal/${postId}/comment`, { content })
}
