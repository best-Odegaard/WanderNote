import { ref } from 'vue'
import { API_BASE_URL, USE_MOCK } from '@/utils/constant'
import { getToken } from '@/utils/auth'

export interface ImageItem {
  /** 本地临时路径（选择后即得，可直接显示） */
  localPath: string
  /** 上传完成后的远程 URL（上传完成后填充） */
  remoteUrl: string
  /** 是否正在上传 */
  uploading: boolean
  /** 上传进度 0-100 */
  progress: number
  /** 上传失败的错误信息 */
  error: string
}

/** 图片上传 Hook — 选择后立即展示本地图，后台异步上传到腾讯云 COS */
export function useUpload() {
  const uploading = ref(false)
  const progress = ref(0)

  /**
   * 选择图片，返回 ImageItem[]，localPath 立即可用
   * remoteUrl 在后台上传完成后填充
   */
  async function chooseImages(count = 9): Promise<ImageItem[]> {
    const chooseRes = await new Promise<UniApp.ChooseImageSuccessCallbackResult>((resolve, reject) => {
      uni.chooseImage({
        count,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera'],
        success: resolve,
        fail: reject
      })
    })

    const items: ImageItem[] = chooseRes.tempFilePaths.map((path) => ({
      localPath: path,
      remoteUrl: '',
      uploading: false,
      progress: 0,
      error: ''
    }))

    // Mock 模式：直接拿本地路径当远程 URL（H5 下 blob URL 可直接显示）
    if (USE_MOCK) {
      return items.map((item) => ({
        ...item,
        remoteUrl: item.localPath,
        uploading: false,
        progress: 100
      }))
    }

    // 真实模式：后台逐张上传到 COS
    uploading.value = true
    progress.value = 0
    const total = items.length
    const uploadPromises = items.map(async (item, i) => {
      item.uploading = true
      try {
        const url = await uploadFile(item.localPath)
        item.remoteUrl = url
        item.progress = 100
      } catch (err: any) {
        item.error = err.message || '上传失败'
        // 失败时保留本地路径作为降级
        item.remoteUrl = item.localPath
      } finally {
        item.uploading = false
        progress.value = Math.round(((i + 1) / total) * 100)
      }
    })
    await Promise.all(uploadPromises)
    uploading.value = false

    return items
  }

  /** 上传单个文件到 COS */
  function uploadFile(filePath: string): Promise<string> {
    return new Promise((resolve, reject) => {
      const token = getToken()
      uni.uploadFile({
        url: `${API_BASE_URL}/user/private/file/cos/upload`,
        filePath,
        name: 'file',
        formData: { type: '4' },
        header: token ? { Authentication: token } : {},
        success: (res) => {
          try {
            const data = JSON.parse(res.data)
            if (data.code === 1 || data.code === 200) {
              resolve(data.data)
            } else {
              reject(new Error(data.msg || '上传失败'))
            }
          } catch {
            reject(new Error('上传响应解析失败'))
          }
        },
        fail: reject
      })
    })
  }

  return { uploading, progress, chooseImages, uploadFile }
}
