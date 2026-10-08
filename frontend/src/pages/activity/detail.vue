<template>
  <view class="page">
    <LoadingView v-if="loading" />
    <view v-else-if="detail">
      <image class="cover" :src="detail.cover" mode="aspectFill" />
      <view class="content">
        <text class="title">{{ detail.title }}</text>
        <view class="row"><text>📅</text><text>{{ formatDate(detail.startTime) }}</text></view>
        <view class="row"><text>📍</text><text>{{ detail.location }}</text></view>
        <view v-if="detail.organizer" class="row"><text>🏢</text><text>{{ detail.organizer }}</text></view>

        <view class="section">
          <text class="section-title">活动介绍</text>
          <text class="desc">{{ detail.description || '精彩活动，欢迎参与' }}</text>
        </view>
      </view>

      <view class="bottom-bar safe-bottom">
        <button class="bar-btn" :disabled="collecting" @tap="toggleCollect">
          {{ detail.isCollected ? '已收藏' : '收藏' }}
        </button>
        <button class="bar-btn primary" :disabled="enrolling" @tap="enroll">
          {{ enrolling ? '报名中…' : '立即报名' }}
        </button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import LoadingView from '@/components/LoadingView/LoadingView.vue'
import { getActivityDetail, collectActivity, uncollectActivity, enrollActivity } from '@/api/activity'
import { formatDate } from '@/utils/format'
import type { ActivityItem } from '@/api/activity'
import { isLoggedIn, redirectToLogin } from '@/utils/auth'
import { showToast } from '@/utils/feedback'

const loading = ref(true)
const detail = ref<ActivityItem | null>(null)
/** 收藏请求进行中：防连点（乐观更新只在前端，连点会把状态与服务端搞反） */
const collecting = ref(false)
/** 报名请求进行中：报名是真实动作，必须防重复提交 */
const enrolling = ref(false)

onMounted(async () => {
  const pages = getCurrentPages()
  const page = pages[pages.length - 1] as { options?: { id?: string } }
  const id = Number(page.options?.id || 0)
  detail.value = await getActivityDetail(id)
  loading.value = false
})

/** 需要登录才能做的动作：未登录先跳登录（带 redirect 回跳，回来还能接着点） */
function ensureLogin(): boolean {
  if (isLoggedIn()) return true
  showToast({ title: '请先登录', icon: 'none' })
  setTimeout(() => redirectToLogin(), 300)
  return false
}

/**
 * 收藏活动：真的要调接口。
 *
 * 原来是「只翻转本地值 + toast」的假动作：点了变「已收藏」，退出重进又变回来，
 * 而「我的收藏」读的是服务端 —— 用户资产与界面直接对不上。
 * 现在：乐观更新 → 失败回滚并如实提示。
 */
async function toggleCollect() {
  if (!detail.value || collecting.value) return
  if (!ensureLogin()) return

  const target = !detail.value.isCollected
  const before = detail.value.isCollected
  detail.value.isCollected = target
  collecting.value = true
  try {
    if (target) {
      await collectActivity(detail.value.id)
    } else {
      await uncollectActivity(detail.value.id)
    }
    showToast({ title: target ? '已收藏' : '已取消', icon: 'none' })
  } catch (e) {
    // 失败必须回滚：不能让界面停在一个服务端并不存在的状态上
    detail.value.isCollected = before
    console.warn('[activity/detail] 收藏失败:', e)
    showToast({ title: '操作失败，请重试', icon: 'none' })
  } finally {
    collecting.value = false
  }
}

/**
 * 立即报名：失败就是失败。
 *
 * 原来 catch 里提示的是「报名成功（演示）」—— 接口 404/断网/未登录都被伪装成成功，
 * 而报名是真实世界动作（占名额、可能付费），用户到现场才发现没报上。
 */
async function enroll() {
  if (!detail.value || enrolling.value) return
  if (!ensureLogin()) return

  enrolling.value = true
  try {
    await enrollActivity(detail.value.id)
    showToast({ title: '报名成功', icon: 'success' })
    // 回拉一次详情，让状态以服务端为准（不要只信本地假设）
    try {
      detail.value = await getActivityDetail(detail.value.id)
    } catch (e) {
      console.warn('[activity/detail] 报名后刷新详情失败:', e)
    }
  } catch (e) {
    console.warn('[activity/detail] 报名失败:', e)
    showToast({ title: '报名失败，请稍后重试', icon: 'none' })
  } finally {
    enrolling.value = false
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.cover { width: 100%; height: 400rpx; }

.content { padding: 32rpx; padding-bottom: 160rpx; }

.title {
  font-size: var(--fs-subhead);
  font-weight: 600;
  color: var(--text-body);
  display: block;
  margin-bottom: 20rpx;
}

.row {
  display: flex;
  gap: 12rpx;
  font-size: var(--fs-body);
  color: var(--text-secondary);
  margin-bottom: 12rpx;
}

.section { margin-top: 32rpx; }

.section-title {
  font-size: var(--fs-title);
  font-weight: 600;
  margin-bottom: 12rpx;
  display: block;
}

.desc {
  font-size: var(--fs-body);
  color: var(--text-secondary);
  line-height: 1.8;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  gap: 20rpx;
  padding: 20rpx 32rpx;
  background: var(--bg-card);
  box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.06);
}

.bar-btn {
  flex: 1;
  height: 80rpx;
  line-height: 80rpx;
  font-size: var(--fs-body);
  border-radius: 40rpx;
  background: var(--bg-input);
  border: none;
  &::after { border: none; }
  &.primary { background: $primary-color; color: #fff; }
}
</style>
