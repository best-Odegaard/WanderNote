<template>
  <view class="page">
    <view class="form card">
      <input v-model="form.title" class="title-input" placeholder="填写标题，吸引更多阅读" />

      <textarea
        v-model="form.content"
        class="content-input"
        placeholder="分享你的旅行故事..."
        maxlength="2000"
      />

      <!-- 图片列表 -->
      <view class="images">
        <view v-for="(img, i) in imageItems" :key="i" class="img-item">
          <image :src="img.localPath" mode="aspectFill" class="img" />
          <!-- 上传中遮罩 -->
          <view v-if="img.uploading" class="upload-overlay">
            <view class="upload-spinner" />
            <text class="upload-pct">{{ img.progress }}%</text>
          </view>
          <!-- 上传失败提示 -->
          <view v-if="img.error" class="error-badge">
            <text>!</text>
          </view>
          <!-- 删除按钮 -->
          <text class="remove" @tap="removeImage(i)">×</text>
        </view>
        <!-- 添加按钮 -->
        <view v-if="imageItems.length < 9" class="add-btn" @tap="addImages">
          <text v-if="uploading">{{ totalProgress }}%</text>
          <text v-else>+</text>
        </view>
      </view>

      <!-- 标签 -->
      <view class="tags-section">
        <text class="label">添加标签</text>
        <view v-for="group in COMMUNITY_TAG_GROUPS" :key="group.title" class="tag-group">
          <text class="group-title">{{ group.title }}</text>
          <view class="tags">
            <text
              v-for="tag in group.tags"
              :key="tag.name"
              class="tag"
              :class="{ active: form.tags.includes(tag.name) }"
              @tap="toggleTag(tag.name)"
            >{{ tag.emoji }} {{ tag.name }}</text>
          </view>
        </view>

        <!-- 自定义标签 -->
        <view class="tag-group">
          <text class="group-title">自定义标签（选填，最多 {{ CUSTOM_TAG_LIMIT }} 个）</text>
          <view class="custom-row">
            <input
              v-model="customTagInput"
              class="custom-input"
              maxlength="8"
              placeholder="输入自定义标签，如：小众宝藏地"
              confirm-type="done"
              @confirm="addCustomTag"
            />
            <view class="custom-add" @tap="addCustomTag">添加</view>
          </view>
          <view v-if="customTags.length" class="tags">
            <view v-for="(tag, i) in customTags" :key="tag" class="tag custom-tag">
              <text>#{{ tag }}</text>
              <text class="tag-remove" @tap="removeCustomTag(i)">×</text>
            </view>
          </view>
        </view>
      </view>

      <input v-model="form.location" class="location-input" placeholder="填写发布城市，如：杭州（必填）" />
    </view>

    <button class="btn-primary publish-btn" :loading="publishing" @tap="handlePublish">发布游记</button>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { COMMUNITY_TAG_GROUPS, CUSTOM_TAG_LIMIT } from '@/utils/constant'
import { publishPost } from '@/api/community'
import { useUpload } from '@/hooks/useUpload'
import type { ImageItem } from '@/hooks/useUpload'

const { uploading, progress, chooseImages } = useUpload()
const publishing = ref(false)

/** 图片列表（本地预览 + 上传状态） */
const imageItems = ref<ImageItem[]>([])

/** 总上传进度 */
const totalProgress = computed(() => {
  if (imageItems.value.length === 0) return 0
  const sum = imageItems.value.reduce((acc, img) => acc + img.progress, 0)
  return Math.round(sum / imageItems.value.length)
})

const form = reactive({
  title: '',
  content: '',
  tags: [] as string[],
  location: ''
})

// ── 自定义标签 ──
const customTagInput = ref('')
const customTags = ref<string[]>([])

function toggleTag(tag: string) {
  const idx = form.tags.indexOf(tag)
  if (idx >= 0) form.tags.splice(idx, 1)
  else form.tags.push(tag)
}

function addCustomTag() {
  const val = customTagInput.value.trim()
  if (!val) return
  if (customTags.value.length >= CUSTOM_TAG_LIMIT) {
    uni.showToast({ title: `最多添加 ${CUSTOM_TAG_LIMIT} 个自定义标签`, icon: 'none' })
    return
  }
  if (customTags.value.includes(val) || form.tags.includes(val)) {
    uni.showToast({ title: '标签已存在', icon: 'none' })
    return
  }
  customTags.value.push(val)
  customTagInput.value = ''
}

function removeCustomTag(index: number) {
  customTags.value.splice(index, 1)
}

async function addImages() {
  const remain = 9 - imageItems.value.length
  if (remain <= 0) return
  try {
    const items = await chooseImages(remain)
    imageItems.value.push(...items)
  } catch {
    uni.showToast({ title: '选择图片失败', icon: 'none' })
  }
}

function removeImage(index: number) {
  imageItems.value.splice(index, 1)
}

/** 获取所有已上传完成的图片 URL（发布时用） */
function getUploadedUrls(): string[] {
  return imageItems.value
    .filter((img) => img.remoteUrl && !img.error)
    .map((img) => img.remoteUrl)
}

async function handlePublish() {
  if (!form.title.trim()) {
    uni.showToast({ title: '请填写标题', icon: 'none' })
    return
  }
  if (!form.content.trim()) {
    uni.showToast({ title: '请填写内容', icon: 'none' })
    return
  }
  if (imageItems.value.length === 0) {
    uni.showToast({ title: '请至少添加一张图片', icon: 'none' })
    return
  }
  if (!form.location.trim()) {
    uni.showToast({ title: '请填写发布城市', icon: 'none' })
    return
  }

  // 等待所有正在上传的图片完成
  const uploadingItems = imageItems.value.filter((img) => img.uploading)
  if (uploadingItems.length > 0) {
    uni.showToast({ title: `正在上传 ${uploadingItems.length} 张图片...`, icon: 'none' })
    return
  }

  const uploadedUrls = getUploadedUrls()
  if (uploadedUrls.length === 0) {
    uni.showToast({ title: '图片上传失败，请重试', icon: 'none' })
    return
  }

  publishing.value = true
  try {
    await publishPost({
      title: form.title,
      content: form.content,
      imageUrls: uploadedUrls,
      tags: [...form.tags, ...customTags.value],
      location: form.location
    })
    uni.showToast({ title: '发布成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1000)
  } catch {
    uni.showToast({ title: '发布失败，请重试', icon: 'none' })
  } finally {
    publishing.value = false
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.page {
  min-height: 100vh;
  background: var(--bg-page);
  padding: 24rpx 32rpx;
}

.title-input {
  font-size: var(--fs-subhead);
  font-weight: 500;
  padding-bottom: 20rpx;
  border-bottom: 1rpx solid var(--border);
  margin-bottom: 20rpx;
}

.content-input {
  width: 100%;
  min-height: 300rpx;
  font-size: var(--fs-body);
  line-height: 1.8;
}

.images {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin: 24rpx 0;
}

.img-item {
  position: relative;
  width: 200rpx;
  height: 200rpx;
}

.img {
  width: 100%;
  height: 100%;
  border-radius: 12rpx;
}

/* 上传中遮罩 */
.upload-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  border-radius: 12rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
}

.upload-spinner {
  width: 40rpx;
  height: 40rpx;
  border: 4rpx solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.upload-pct {
  color: #fff;
  font-size: var(--fs-meta);
  font-weight: 500;
}

.error-badge {
  position: absolute;
  top: 8rpx;
  left: 8rpx;
  width: 32rpx;
  height: 32rpx;
  background: #e53e3e;
  color: #fff;
  border-radius: 50%;
  text-align: center;
  line-height: 32rpx;
  font-size: var(--fs-meta);
  font-weight: 700;
}

.remove {
  position: absolute;
  top: -8rpx;
  right: -8rpx;
  width: 36rpx;
  height: 36rpx;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  border-radius: 50%;
  text-align: center;
  line-height: 36rpx;
  font-size: var(--fs-meta);
  z-index: 2;
}

.add-btn {
  width: 200rpx;
  height: 200rpx;
  border: 2rpx dashed var(--border);
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 60rpx;
  color: var(--text-placeholder);
}

.tags-section { margin: 24rpx 0; }

.label {
  font-size: var(--fs-body);
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 20rpx;
  display: block;
}

.tag-group { margin-bottom: 28rpx; }

.group-title {
  font-size: var(--fs-meta);
  color: var(--text-tertiary);
  margin-bottom: 14rpx;
  display: block;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.tag {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  padding: 12rpx 24rpx;
  font-size: var(--fs-body);
  color: var(--text-secondary);
  background: var(--bg-input);
  border: 2rpx solid var(--border);
  border-radius: 999rpx;
  transition: all 0.18s ease;

  &.active {
    color: #fff;
    background: linear-gradient(135deg, #48bb88, #2f9d6f);
    border-color: transparent;
    box-shadow: 0 4rpx 14rpx rgba(47, 157, 111, 0.3);
  }

  &:active {
    transform: scale(0.95);
  }
}

/* 自定义标签输入 */
.custom-row {
  display: flex;
  gap: 12rpx;
  margin-bottom: 14rpx;
}

.custom-input {
  flex: 1;
  height: 72rpx;
  padding: 0 24rpx;
  background: var(--bg-input);
  border-radius: 999rpx;
  font-size: var(--fs-body);
}

.custom-add {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  height: 72rpx;
  padding: 0 32rpx;
  border-radius: 999rpx;
  background: var(--bg-card);
  border: 2rpx solid rgba(72, 187, 136, 0.5);
  color: #2f9d6f;
  font-size: var(--fs-body);
  font-weight: 600;

  &:active {
    transform: scale(0.95);
  }
}

/* 已添加的自定义标签胶囊（可删除） */
.custom-tag {
  background: rgba(168, 230, 207, 0.35);
  border-color: rgba(72, 187, 136, 0.4);
  color: var(--text-body);
}

.tag-remove {
  font-size: var(--fs-body);
  color: var(--text-tertiary);
  margin-left: 4rpx;
  line-height: 1;
  padding-left: 4rpx;
}

.location-input {
  height: 72rpx;
  padding: 0 20rpx;
  background: var(--bg-input);
  border-radius: 12rpx;
  font-size: var(--fs-body);
  margin-top: 16rpx;
}

.publish-btn { margin-top: 32rpx; }
</style>
