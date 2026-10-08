<template>
  <view class="page">
    <view class="nav" :style="{ paddingTop: statusBarHeight + 'px' }">
      <text class="back" @tap="goBack">‹</text>
      <text class="nav-title">智能导入</text>
      <view style="width: 80rpx" />
    </view>

    <view class="content">
      <!-- 文案与后端对齐：后端 TripImportLinkDTO 只接收 sourceUrl（纯链接），
           不解析正文，所以这里不再宣称支持「行程文本 / 酒店地址列表」。 -->
      <text class="hint">粘贴链接，自动生成行程</text>
      <textarea
        v-model="text"
        class="textarea"
        placeholder="支持小红书 / 公众号等文章链接（需包含 http 或 https 地址）"
        maxlength="5000"
      />
      <!-- 截图识别未实现（原来点击只弹「识别开发中」），不留看着能用的入口 -->
      <button class="btn-black" :loading="loading" :disabled="loading" @tap="handleImport">开始识别</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useTripStore } from '@/store/trip'
import { useUserStore } from '@/store/user'
import { importTripFromLink } from '@/api/trip'
import { isLoggedIn, redirectToLogin } from '@/utils/auth'
import { showToast } from '@/utils/feedback'

const systemInfo = uni.getSystemInfoSync()
const statusBarHeight = systemInfo.statusBarHeight || 20
const text = ref('')
const loading = ref(false)
const tripStore = useTripStore()
const userStore = useUserStore()

onMounted(() => {
  if (!isLoggedIn()) {
    showToast({ title: '请先登录后创建行程', icon: 'none' })
    setTimeout(() => {
      redirectToLogin()
    }, 500)
  }
})

function goBack() {
  uni.navigateBack()
}

async function handleImport() {
  // 防重：连点会并发多次 /trip/import/link，每次都会插一条行程（脏数据）。
  // 按钮同时加了 :disabled="loading"，这里再兜一层（禁用态生效前一帧仍可能连点）。
  if (loading.value) return

  if (!isLoggedIn()) {
    showToast({ title: '请先登录后创建行程', icon: 'none' })
    setTimeout(() => {
      redirectToLogin()
    }, 500)
    return
  }

  const sourceUrl = extractFirstUrl(text.value)
  if (!sourceUrl) {
    // 文案说明"为什么不行"：后端只认链接，没有链接就等于没内容可解析。
    showToast({ title: '未识别到链接，请粘贴含 http/https 的链接', icon: 'none' })
    return
  }

  loading.value = true
  try {
    // AI 解析外部链接（小红书/游记正文）经常超过默认 30s 超时，
    // 这里显式放宽到 120s（与 AI 对话链路一致），否则长链接必然「请求失败」。
    const trip = await importTripFromLink(
      {
        sourceUrl,
        userId: userStore.userInfo?.id
      },
      { timeout: 120000 }
    )
    tripStore.currentTrip = trip
    showToast({ title: '导入成功', icon: 'success' })
    setTimeout(() => {
      if (trip.id) {
        uni.navigateTo({ url: `/pages/trip/detail?id=${trip.id}` })
      } else {
        uni.navigateTo({ url: '/pages/plan/wizard' })
      }
      // 跳转发起后再解锁（成功路径故意不提前复位 loading）：
      // 否则「导入成功 → 600ms 后才跳转」这段窗口里按钮又可点了，防重会破功。
      // 放在这里也兼作兜底：万一跳转失败（页面栈满等），页面不会卡在禁用态。
      loading.value = false
    }, 600)
  } catch (e: any) {
    // 原来 catch 是空的：识别失败用户完全没反馈，只看到按钮转完后没动静。
    showToast({
      title: e?.data?.msg || e?.message || '识别失败，请检查链接后重试',
      icon: 'none'
    })
    // 失败必须解锁，否则用户无法重试
    loading.value = false
  }
}

function extractFirstUrl(value: string): string {
  const match = value.trim().match(/https?:\/\/[^\s]+/)
  return match ? match[0] : ''
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.page {
  min-height: 100vh;
  background: var(--bg-page);
}

.nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32rpx 24rpx;
}

.back {
  font-size: 56rpx;
  width: 80rpx;
}

.nav-title {
  font-size: var(--fs-subhead);
  font-weight: 600;
}

.content {
  padding: 32rpx;
}

.hint {
  font-size: var(--fs-heading);
  font-weight: 600;
  display: block;
  margin-bottom: 32rpx;
}

.textarea {
  width: 100%;
  min-height: 320rpx;
  background: var(--bg-card);
  border-radius: 24rpx;
  padding: 24rpx;
  font-size: var(--fs-body);
  box-sizing: border-box;
  border: 2rpx solid $accent-border;
}

.btn-black {
  width: 100%;
  margin-top: 24rpx;
}
</style>
