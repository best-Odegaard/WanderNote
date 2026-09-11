<template>
  <view class="page">
    <view class="form card">
      <view class="avatar-section" @tap="changeAvatar">
        <image class="avatar" :src="form.avatar || defaultAvatar" mode="aspectFill" />
        <text class="change-text">点击更换头像</text>
      </view>
      <view class="form-item">
        <text class="label">昵称</text>
        <input v-model="form.nickname" class="input" />
      </view>
      <view class="form-item">
        <text class="label">手机号</text>
        <input v-model="form.phone" class="input" disabled />
      </view>
      <view class="form-item">
        <text class="label">个人简介</text>
        <textarea v-model="form.bio" class="textarea" placeholder="介绍一下自己吧" />
      </view>
    </view>
    <button class="btn-primary" :loading="saving" @tap="handleSave">保存</button>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useUserStore } from '@/store/user'
import { useUpload } from '@/hooks/useUpload'

const userStore = useUserStore()
const { userInfo } = storeToRefs(userStore)
	const { chooseImages } = useUpload()
	const saving = ref(false)
const defaultAvatar = 'https://picsum.photos/seed/default/200/200'

const form = reactive({
  nickname: '',
  phone: '',
  avatar: '',
  bio: ''
})

onMounted(async () => {
  // 优先拉取后端最新资料（GET /user/info），失败时用本地 store 缓存兜底
  if (userInfo.value) {
    Object.assign(form, userInfo.value)
  }
  try {
    const fresh = await userStore.getUserInfo()
    Object.assign(form, fresh)
  } catch {
    // 接口失败时保持本地数据
  }
})

async function changeAvatar() {
  try {
    const items = await chooseImages(1)
    const item = items[0]
    if (item?.remoteUrl) form.avatar = item.remoteUrl
    else if (item?.localPath) form.avatar = item.localPath
  } catch {
    uni.showToast({ title: '头像上传失败，请重试', icon: 'none' })
  }
}

async function handleSave() {
  saving.value = true
  try {
    await userStore.updateUserInfo({ ...form })
    uni.showToast({ title: '保存成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1000)
  } catch (e: any) {
    uni.showToast({ title: e?.data?.msg || e?.message || '保存失败，请重试', icon: 'none' })
  } finally {
    saving.value = false
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

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32rpx 0;
}

.avatar {
  width: 160rpx;
  height: 160rpx;
  border-radius: 50%;
}

.change-text {
  font-size: 24rpx;
  color: $primary-color;
  margin-top: 12rpx;
}

.form-item { margin-bottom: 24rpx; }

.label {
  font-size: 26rpx;
  color: var(--text-secondary);
  margin-bottom: 8rpx;
  display: block;
}

.input {
  height: 80rpx;
  padding: 0 20rpx;
  background: var(--bg-input);
  border-radius: 12rpx;
  font-size: 28rpx;
}

.textarea {
  width: 100%;
  min-height: 160rpx;
  padding: 20rpx;
  background: var(--bg-input);
  border-radius: 12rpx;
  font-size: 28rpx;
}
</style>
