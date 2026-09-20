<template>
  <view class="auth-page">
    <!-- 品牌主视觉 -->
    <view class="hero">
      <view class="logo-badge">
        <view class="logo-sheen" />
        <AppIcon name="map" :size="64" color="var(--on-brand)" :stroke-width="1.8" />
      </view>
      <text class="title">WanderNote 行笺</text>
      <text class="subtitle">智能文旅行程规划平台</text>
      <view class="hero-chips">
        <view class="chip">AI 行程规划</view>
        <view class="chip">景点推荐</view>
        <view class="chip">游记社区</view>
      </view>
    </view>

    <!-- 表单卡 -->
    <view class="auth-card">
      <view class="field">
        <view class="field-icon">
          <AppIcon name="user" :size="30" color="var(--brand-ink)" />
        </view>
        <input
          v-model="username"
          class="field-input"
          placeholder="请输入用户名"
          placeholder-class="field-placeholder"
          confirm-type="next"
        />
      </view>

      <view class="field">
        <view class="field-icon">
          <AppIcon name="lock" :size="30" color="var(--brand-ink)" />
        </view>
        <input
          v-model="password"
          class="field-input"
          :password="!showPassword"
          placeholder="请输入密码"
          placeholder-class="field-placeholder"
          confirm-type="done"
          @confirm="submit"
        />
        <view class="field-action" @tap="showPassword = !showPassword">
          <AppIcon
            :name="showPassword ? 'eye' : 'eye-off'"
            :size="30"
            color="var(--text-tertiary)"
          />
        </view>
      </view>

      <button class="btn-primary login-btn" :loading="loading" @tap="submit">登录</button>

      <view class="switch-row">
        <text class="switch-text">还没有账号？</text>
        <text class="switch-link" @tap="goRegister">立即注册</text>
      </view>
    </view>

    <text class="agreement">登录即表示同意《用户协议》与《隐私政策》</text>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppIcon from '@/components/AppIcon/AppIcon.vue'
import { useLogin } from '@/hooks/useLogin'

const { loading, handleLogin } = useLogin()
const username = ref('')
const password = ref('')
/** 密码是否明文显示 */
const showPassword = ref(false)
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

.auth-page {
  min-height: 100vh;
  box-sizing: border-box;
  padding: 120rpx 48rpx 48rpx;
  position: relative;
  // 品牌极光底：与首页顶部同一套光晕语言
  background:
    radial-gradient(64% 38% at 18% 0%, var(--aurora-1) 0%, transparent 70%),
    radial-gradient(56% 32% at 92% 10%, var(--aurora-2) 0%, transparent 74%),
    radial-gradient(70% 36% at 50% 100%, var(--aurora-3) 0%, transparent 76%),
    var(--bg-page);
}

// ── 主视觉 ──
.hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 56rpx;
}

.logo-badge {
  position: relative;
  width: 144rpx;
  height: 144rpx;
  border-radius: 44rpx;
  background: var(--brand-grad);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2rpx solid var(--glass-border);
  box-shadow: var(--brand-glow), var(--shadow-md);
  overflow: hidden;
}

/* 顶部弧形高光：玻璃球质感 */
.logo-sheen {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 52%;
  background: var(--sheen);
  pointer-events: none;
}

.title {
  font-size: 48rpx;
  font-weight: 800;
  color: var(--text-main);
  margin-top: 28rpx;
  display: block;
  letter-spacing: -0.8rpx;
}

.subtitle {
  font-size: 26rpx;
  color: var(--text-secondary);
  margin-top: 10rpx;
  display: block;
}

.hero-chips {
  display: flex;
  gap: 12rpx;
  margin-top: 24rpx;
}

.chip {
  font-size: 22rpx;
  font-weight: 500;
  color: var(--brand-ink);
  background: var(--brand-soft);
  border: 1rpx solid var(--glass-border);
  border-radius: $radius-pill;
  padding: 8rpx 22rpx;
  line-height: 1.4;
}

// ── 表单卡（玻璃）──
.auth-card {
  background: var(--glass-bg);
  backdrop-filter: blur(var(--glass-blur)) saturate(180%);
  -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(180%);
  border: 1rpx solid var(--glass-border);
  border-radius: $card-radius-lg;
  box-shadow: var(--glass-shadow);
  padding: 40rpx 32rpx 36rpx;
}

.field {
  display: flex;
  align-items: center;
  height: 104rpx;
  padding: 0 28rpx;
  margin-bottom: 24rpx;
  background: var(--bg-input);
  border: 2rpx solid transparent;
  border-radius: $radius-lg;
  transition: border-color $dur-base $ease-out, background $dur-base $ease-out;

  // 聚焦反馈：H5 端 uni-input 会收到原生 focus，用 focus-within 兜住
  &:focus-within {
    border-color: var(--brand);
    background: var(--bg-card);
  }
}

.field-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48rpx;
  margin-right: 16rpx;
  flex-shrink: 0;
}

.field-input {
  flex: 1;
  height: 104rpx;
  font-size: 30rpx;
  color: var(--text-main);
  background: transparent;
}

:deep(.field-placeholder) {
  color: var(--text-placeholder);
  font-size: 28rpx;
}

.field-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  margin-left: 8rpx;
  flex-shrink: 0;
  transition: transform $dur-fast $ease-out;

  &:active {
    transform: scale(0.88);
  }
}

.login-btn {
  margin-top: 12rpx;
  margin-bottom: 0;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
  margin-top: 28rpx;
}

.switch-text {
  font-size: 26rpx;
  color: var(--text-secondary);
}

.switch-link {
  font-size: 26rpx;
  font-weight: 600;
  color: var(--brand-ink);
  padding: 8rpx 4rpx;
}

.agreement {
  display: block;
  text-align: center;
  font-size: 22rpx;
  color: var(--text-tertiary);
  margin-top: 48rpx;
  line-height: 1.6;
}
</style>
