<template>
  <view class="page">
    <!-- 反馈类型 -->
    <view class="card">
      <view class="section-title">反馈类型</view>
      <view class="type-list">
        <view
          v-for="t in feedbackTypes"
          :key="t"
          class="type-item"
          :class="{ active: form.type === t }"
          @tap="form.type = t"
        >
          {{ t }}
        </view>
      </view>
    </view>

    <!-- 反馈内容 -->
    <view class="card">
      <view class="section-title">反馈内容</view>
      <textarea
        v-model="form.content"
        class="content-input"
        placeholder="请描述您遇到的问题或建议，我们会认真处理每一条反馈"
        placeholder-class="placeholder"
        :maxlength="500"
        :show-confirm-bar="false"
      />
      <view class="count">{{ form.content.length }}/500</view>
    </view>

    <!-- 图片（选填） -->
    <view class="card">
      <view class="section-title">图片（选填）</view>
      <view class="image-grid">
        <view v-for="(img, i) in imageItems" :key="img.localPath" class="image-item">
          <image class="preview" :src="img.localPath" mode="aspectFill" />
          <view v-if="img.uploading" class="mask">
            <text class="mask-text">上传中...</text>
          </view>
          <view v-else-if="img.error" class="mask error-mask">
            <text class="mask-text">上传失败</text>
          </view>
          <view class="remove" @tap.stop="removeImage(i)">
            <AppIcon name="delete" :size="22" color="#fff" />
          </view>
        </view>
        <view v-if="imageItems.length < 4" class="add-btn" @tap="chooseImage">
          <AppIcon name="camera" :size="40" color="var(--text-tertiary)" />
          <text class="add-text">添加图片</text>
        </view>
      </view>
    </view>

    <!-- 联系方式（选填） -->
    <view class="card">
      <view class="section-title">联系方式（选填）</view>
      <input
        v-model="form.contact"
        class="contact-input"
        placeholder="手机号 / 邮箱，方便我们回复您"
        placeholder-class="placeholder"
      />
    </view>

    <button class="submit-btn" :class="{ disabled: submitting }" :disabled="submitting" @tap="handleSubmit">
      {{ submitting ? '提交中...' : '提交反馈' }}
    </button>

    <view class="safe-bottom" style="height: 40rpx" />
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { submitFeedback } from '@/api/feedback'
import { useUpload, type ImageItem } from '@/hooks/useUpload'
import { useLogin } from '@/hooks/useLogin'

const feedbackTypes = ['功能建议', '内容纠错', '投诉举报', '其他']

const form = reactive({
  type: '功能建议',
  content: '',
  contact: ''
})

const submitting = ref(false)
const imageItems = ref<ImageItem[]>([])
const { uploading, chooseImages } = useUpload()
const { checkLogin } = useLogin()

async function chooseImage() {
  const items = await chooseImages(4 - imageItems.value.length)
  imageItems.value = imageItems.value.concat(items)
}

function removeImage(index: number) {
  imageItems.value.splice(index, 1)
}

async function handleSubmit() {
  if (!checkLogin()) return

  const content = form.content.trim()
  if (content.length < 5) {
    uni.showToast({ title: '请至少输入5个字', icon: 'none' })
    return
  }
  if (uploading.value) {
    uni.showToast({ title: '图片上传中，请稍候', icon: 'none' })
    return
  }

  submitting.value = true
  try {
    const images = imageItems.value.map((i) => i.remoteUrl).filter(Boolean)
    await submitFeedback({
      type: form.type,
      content,
      images,
      contact: form.contact.trim() || undefined
    })
    uni.showToast({ title: '提交成功，感谢您的反馈', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1200)
  } catch {
    // request.ts 已统一 toast 错误信息
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.page {
  min-height: 100vh;
  background: var(--bg-page);
  padding: 24rpx 32rpx;
  box-sizing: border-box;
}

.card {
  background: var(--bg-card);
  border-radius: $card-radius;
  padding: 28rpx;
  margin-bottom: 24rpx;
}

.section-title {
  font-size: var(--fs-body);
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 20rpx;
}

.type-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.type-item {
  padding: 12rpx 32rpx;
  border-radius: 32rpx;
  background: var(--bg-input);
  font-size: var(--fs-body);
  color: var(--text-secondary);
  border: 2rpx solid transparent;

  &.active {
    background: rgba($primary-color, 0.1);
    color: $primary-color;
    border-color: $primary-color;
    font-weight: 500;
  }
}

.content-input {
  width: 100%;
  height: 260rpx;
  background: var(--bg-input);
  border-radius: $card-radius;
  padding: 20rpx;
  box-sizing: border-box;
  font-size: var(--fs-body);
  color: var(--text-main);
}

.count {
  text-align: right;
  font-size: var(--fs-meta);
  color: var(--text-tertiary);
  margin-top: 8rpx;
}

.placeholder {
  color: var(--text-tertiary);
}

.image-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.image-item {
  position: relative;
  width: 150rpx;
  height: 150rpx;
  border-radius: $card-radius;
  overflow: hidden;
}

.preview {
  width: 100%;
  height: 100%;
}

.mask {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
}

.error-mask {
  background: rgba(255, 0, 0, 0.3);
}

.mask-text {
  color: #fff;
  font-size: var(--fs-meta);
}

.remove {
  position: absolute;
  top: 0;
  right: 0;
  width: 40rpx;
  height: 40rpx;
  background: rgba(0, 0, 0, 0.5);
  border-bottom-left-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.add-btn {
  width: 150rpx;
  height: 150rpx;
  border-radius: $card-radius;
  background: var(--bg-input);
  border: 2rpx dashed var(--text-tertiary);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
}

.add-text {
  font-size: var(--fs-meta);
  color: var(--text-tertiary);
}

.contact-input {
  width: 100%;
  height: 80rpx;
  background: var(--bg-input);
  border-radius: $card-radius;
  padding: 0 20rpx;
  box-sizing: border-box;
  font-size: var(--fs-body);
  color: var(--text-main);
}

.submit-btn {
  background: $primary-color;
  color: #fff;
  font-size: var(--fs-body);
  border-radius: $card-radius;
  height: 88rpx;
  line-height: 88rpx;
  border: none;

  &.disabled {
    opacity: 0.6;
  }

  &::after { border: none; }
}
</style>
