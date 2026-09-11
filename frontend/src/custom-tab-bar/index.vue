<template>
  <view class="tab-bar-wrap">
    <view class="tab-bar safe-bottom">
      <view
        v-for="(tab, index) in leftTabs"
        :key="tab.path"
        class="tab-item"
        :class="{ active: selected === index }"
        @tap="switchTab(index)"
      >
        <AppIcon
          :name="tab.icon"
          :size="44"
          :color="selected === index ? tab.activeColor : tab.color"
          :stroke-width="selected === index ? 2.4 : 2"
          class="tab-icon"
        />
        <text class="tab-text">{{ tab.text }}</text>
      </view>

      <view class="tab-create" @tap="onCreate">
        <view class="create-btn soft-shadow">
          <AppIcon name="plus" :size="52" color="#fff" :stroke-width="2.4" class="create-icon" />
        </view>
        <text class="create-text">创建行程</text>
      </view>

      <view
        v-for="(tab, index) in rightTabs"
        :key="tab.path"
        class="tab-item"
        :class="{ active: selected === index + 2 }"
        @tap="switchTab(index + 2)"
      >
        <AppIcon
          :name="tab.icon"
          :size="44"
          :color="selected === index + 2 ? tab.activeColor : tab.color"
          :stroke-width="selected === index + 2 ? 2.4 : 2"
          class="tab-icon"
        />
        <text class="tab-text">{{ tab.text }}</text>
      </view>
    </view>

    <CreatePlanMenu />
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useAppStore } from '@/store/app'
import CreatePlanMenu from '@/components/CreatePlanMenu/CreatePlanMenu.vue'
import AppIcon from '@/components/AppIcon/AppIcon.vue'

const appStore = useAppStore()
const { tabbarIndex: selected } = storeToRefs(appStore)

const tabs = [
  { path: '/pages/home/index', text: '首页', icon: 'home', color: 'var(--text-tertiary)', activeColor: '#86dfc4' },
  { path: '/pages/trip/index', text: '行程', icon: 'calendar', color: 'var(--text-tertiary)', activeColor: '#86dfc4' },
  { path: '/pages/coming/index', text: '探索', icon: 'compass', color: 'var(--text-tertiary)', activeColor: '#86dfc4' },
  { path: '/pages/profile/index', text: '我的', icon: 'user', color: 'var(--text-tertiary)', activeColor: '#86dfc4' }
]

const leftTabs = computed(() => tabs.slice(0, 2))
const rightTabs = computed(() => tabs.slice(2))

function switchTab(index: number) {
  if (selected.value === index) return
  appStore.closeCreateMenu()
  appStore.setTabbarIndex(index)
  uni.switchTab({ url: tabs[index].path })
}

function onCreate() {
  appStore.toggleCreateMenu()
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.tab-bar-wrap {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 999;
}

.tab-bar {
   min-height: 120rpx;
   height: calc(120rpx + env(safe-area-inset-bottom));
   box-sizing: border-box;
   background: var(--glass-bg, var(--bg-card));
   backdrop-filter: blur(12px);
   -webkit-backdrop-filter: blur(12px);
   display: flex;
   align-items: flex-end;
   border-top: 1rpx solid rgba(134, 223, 196, 0.15);
   box-shadow: 0 -4rpx 20rpx rgba(134, 223, 196, 0.08);
   padding: 0 8rpx env(safe-area-inset-bottom);
 }

.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-bottom: 12rpx;

  &.active .tab-text {
    color: $mint-primary;
    font-weight: 600;
  }

  &.active .tab-icon {
    transform: scale(1.08);
  }
}

.tab-icon {
  display: block;
  margin-bottom: 2rpx;
  transition: transform 0.15s ease;
}

.tab-text {
  font-size: 22rpx;
  color: var(--text-tertiary);
  margin-top: 4rpx;
}

.tab-create {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-top: -36rpx;
  padding-bottom: 8rpx;
}

.create-btn {
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  // 修改为薄荷绿主题色，取消蓝渐变
  background: $main-color;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 6rpx solid #fff;
  // 添加柔和外光晕
  box-shadow: 0 0 22rpx rgba(134, 223, 196, 0.45);
}

.create-icon {
  font-size: 56rpx;
  color: #fff;
  font-weight: 300;
  line-height: 1;
  margin-top: -4rpx;
}

.create-text {
  font-size: 20rpx;
  color: var(--text-secondary);
  margin-top: 6rpx;
}
</style>
