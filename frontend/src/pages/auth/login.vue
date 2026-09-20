<template>
  <view class="page">
    <view class="header">
      <text class="logo">🗺️</text>
      <text class="title">WanderNote 行笺</text>
      <text class="subtitle">智能文旅行程规划平台</text>
    </view>

    <view class="form card">
      <view class="form-item">
        <text class="label">用户名</text>
        <input v-model="username" class="input" placeholder="请输入用户名" />
      </view>
      <view class="form-item">
        <text class="label">密码</text>
        <input v-model="password" class="input" password placeholder="请输入密码" />
      </view>
    </view>

    <button class="btn-primary login-btn" :loading="loading" @tap="submit">登录</button>

    <view class="footer">
      <text class="link" @tap="goRegister">还没有账号？立即注册</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useLogin } from '@/hooks/useLogin'

const { loading, handleLogin } = useLogin()
const username = ref('')
const password = ref('')
/** 登录成功后的回跳地址（由 redirectToLogin 传入，onLoad 解析） */
const redirect = ref('')

onLoad((options?: Record<string, string>) => {
  // uni-app 对 URL 参数已解码一次，这里再解一次还原完整路径（兼容部分平台未解码的情况）
  if (options?.redirect) {
    try {
      redirect.value = decodeURIComponent(options.redirect)
    } catch {
      redirect.value = options.redirect
    }
  }
})

async function submit() {
  if (!username.value) {
    uni.showToast({ title: '请输入用户名', icon: 'none' })
    return
  }
  if (!password.value) {
    uni.showToast({ title: '请输入密码', icon: 'none' })
    return
  }
  await handleLogin(username.value, password.value, redirect.value)
}

function goRegister() {
  uni.navigateTo({ url: '/pages/auth/register' })
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.page {
  min-height: 100vh;
  background: linear-gradient(180deg, rgba($primary-color, 0.08) 0%, var(--bg-page) 40%);
  padding: 120rpx 48rpx 48rpx;
}

.header {
  text-align: center;
  margin-bottom: 60rpx;
}

.logo {
  font-size: 80rpx;
  display: block;
}

.title {
  font-size: var(--fs-heading);
  font-weight: 700;
  color: var(--text-body);
  margin-top: 16rpx;
  display: block;
}

.subtitle {
  font-size: var(--fs-body);
  color: var(--text-secondary);
  margin-top: 8rpx;
  display: block;
}

.form {
  margin-bottom: 40rpx;
}

.form-item {
  margin-bottom: 28rpx;
}

.label {
  font-size: var(--fs-body);
  color: var(--text-secondary);
  margin-bottom: 12rpx;
  display: block;
}

.input {
  height: 88rpx;
  padding: 0 24rpx;
  background: var(--bg-input);
  border-radius: 12rpx;
  font-size: var(--fs-body);
}

.login-btn {
  margin-bottom: 32rpx;
}

.footer {
  text-align: center;
}

.link {
  font-size: var(--fs-body);
  color: $primary-color;
}
</style>
