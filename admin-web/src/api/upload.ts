import { http } from '@/utils/request'

/**
 * 上传图片，返回可直接访问的 URL（封面图等）。
 * 走管理端自己的入口 /admin/upload/image，请求头由 axios 拦截器自动带上管理端 token。
 */
export function uploadImage(file: File) {
  const data = new FormData()
  data.append('file', file)
  // type=4：UploadService 会把文件放到「AI文旅/图片/」下并按图片校验
  data.append('type', '4')
  // 图片可能有好几 MB，走单独的超时（全局是 30s）
  return http.post<string>('/admin/upload/image', data, { timeout: 120000 })
}
