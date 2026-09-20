<template>
  <view class="auth-page">
    <CustomNavbar title="注册" show-back />

    <view class="intro" :style="{ paddingTop: navHeight + 'px' }">
      <text class="intro-title">创建你的行笺账号</text>
      <text class="intro-desc">保存行程、收藏游记、随时接着规划</text>
    </view>

    <view class="auth-card">
      <view class="field">
        <view class="field-icon">
          <AppIcon name="user" :size="30" color="var(--brand-ink)" />
        </view>
        <input
          v-model="form.username"
          class="field-input"
          placeholder="请输入用户名（3~20位）"
          placeholder-class="field-placeholder"
          confirm-type="next"
        />
      </view>

      <view class="field">
        <view class="field-icon">
          <AppIcon name="lock" :size="30" color="var(--brand-ink)" />
        </view>
        <input
          v-model="form.password"
          class="field-input"
          :password="!showPassword"
          placeholder="请输入密码（6~20位）"
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

      <button class="btn-primary" :loading="loading" @tap="submit">注册</button>

      <view class="switch-row">
        <text class="switch-text">已有账号？</text>
        <text class="switch-link" @tap="goLogin">去登录</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import CustomNavbar from '@/components/CustomNavbar/CustomNavbar.vue'
import AppIcon from '@/components/AppIcon/AppIcon.vue'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const loading = ref(false)
/** 密码是否明文显示 */
const showPassword = ref(false)

const systemInfo = uni.getSystemInfoSync()
const navHeight = (systemInfo.statusBarHeight || 20) + 44

const form = reactive({
  username: '',
  password: ''
})

async function submit() {
  if (!form.username || form.username.length < 3 || form.username.length > 20) {
    uni.showToast({ title: '用户名长度3~20位', icon: 'none' })
    return
  }
  if (form.password.length < 6 || form.password.length > 20) {
    uni.showToast({ title: '密码长度6~20位', icon: 'none' })
    return
  }

  loading.value = true
  try {
    await userStore.register(form)
    uni.showToast({ title: '注册成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1000)
  } catch {
    // handled by request interceptor
  } finally {
    loading.value = false
  }
}

function goLogin() {
  uni.navigateBack()
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.auth-page {
  min-height: 100vh;
  box-sizing: border-box;
  padding: 0 48rpx 48rpx;
  position: relative;
  // 与登录页同一套品牌极光底
  background:
    radial-gradient(64% 36% at 18% 0%, var(--aurora-1) 0%, transparent 70%),
    radial-gradient(56% 30% at 92% 8%, var(--aurora-2) 0%, transparent 74%),
    var(--bg-page);
}

.intro {
  padding-bottom: 48rpx;
}

.intro-title {
  font-size: 44rpx;
  font-weight: 800;
  color: var(--text-main);
  display: block;
  letter-spacing: -0.6rpx;
}

.intro-desc {
  font-size: 26rpx;
  color: var(--text-secondary);
  margin-top: 12rpx;
  display: block;
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
</style>
