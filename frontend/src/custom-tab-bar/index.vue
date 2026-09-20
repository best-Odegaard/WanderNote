<template>
  <view class="tab-bar-wrap">
    <view class="tab-bar safe-bottom">
      <view class="tab-bar-inner">
        <view
          v-for="(tab, index) in leftTabs"
          :key="tab.path"
          class="tab-item"
          :class="{ active: selected === index }"
          @tap="switchTab(index)"
        >
          <view class="tab-icon-wrap">
            <view class="tab-indicator" />
            <AppIcon
              :name="tab.icon"
              :size="44"
              :color="selected === index ? tab.activeColor : tab.color"
              :stroke-width="selected === index ? 2.4 : 2"
              class="tab-icon"
            />
          </view>
          <text class="tab-text">{{ tab.text }}</text>
        </view>

        <view class="tab-create" @tap="onCreate">
          <view class="create-btn">
            <view class="create-sheen" />
            <AppIcon
              name="plus"
              :size="52"
              color="var(--on-brand)"
              :stroke-width="2.6"
              class="create-icon"
            />
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
          <view class="tab-icon-wrap">
            <view class="tab-indicator" />
            <AppIcon
              :name="tab.icon"
              :size="44"
              :color="selected === index + 2 ? tab.activeColor : tab.color"
              :stroke-width="selected === index + 2 ? 2.4 : 2"
              class="tab-icon"
            />
          </view>
          <text class="tab-text">{{ tab.text }}</text>
        </view>
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
  { path: '/pages/home/index', text: '首页', icon: 'home', color: 'var(--text-tertiary)', activeColor: 'var(--brand-ink)' },
  { path: '/pages/trip/index', text: '行程', icon: 'calendar', color: 'var(--text-tertiary)', activeColor: 'var(--brand-ink)' },
  { path: '/pages/coming/index', text: '探索', icon: 'compass', color: 'var(--text-tertiary)', activeColor: 'var(--brand-ink)' },
  { path: '/pages/profile/index', text: '我的', icon: 'user', color: 'var(--text-tertiary)', activeColor: 'var(--brand-ink)' }
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
  // 悬浮玻璃胶囊：左右留边 + 底部安全区
  padding: 0 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  pointer-events: none;
}

.tab-bar {
  box-sizing: border-box;
  background: var(--glass-bg-strong);
  backdrop-filter: blur(var(--glass-blur)) saturate(180%);
  -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(180%);
  border: 1rpx solid var(--glass-border);
  border-radius: 44rpx;
  box-shadow: var(--shadow-lg), 0 2rpx 0 rgba(255, 255, 255, 0.4) inset;
  padding: 0 8rpx;
  pointer-events: auto;
}

.tab-bar-inner {
  display: flex;
  align-items: flex-end;
  height: 128rpx;
}

.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding-bottom: 14rpx;
  transition: transform $dur-fast $ease-out;

  &:active {
    transform: scale(0.94);
  }

  &.active .tab-text {
    color: var(--brand-ink);
    font-weight: 700;
  }

  &.active .tab-icon {
    transform: translateY(-2rpx) scale(1.06);
  }
}

.tab-icon-wrap {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56rpx;
  height: 56rpx;
}

/* 选中指示点：极小的一枚品牌圆点，替代整块高亮底 */
.tab-indicator {
  position: absolute;
  top: -14rpx;
  width: 8rpx;
  height: 8rpx;
  border-radius: 50%;
  background: var(--brand-grad);
  opacity: 0;
  transform: scale(0.4);
  transition: opacity $dur-base $ease-out, transform $dur-base $ease-spring;
}

.tab-item.active .tab-indicator {
  opacity: 1;
  transform: scale(1);
}

.tab-icon {
  display: block;
  transition: transform $dur-base $ease-spring;
}

.tab-text {
  font-size: 22rpx;
  color: var(--text-tertiary);
  margin-top: 4rpx;
  transition: color $dur-base $ease-out;
}

.tab-create {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
  justify-content: flex-end;
  padding-bottom: 8rpx;
}

.create-btn {
  position: relative;
  width: 104rpx;
  height: 104rpx;
  border-radius: 50%;
  background: var(--brand-grad);
  display: flex;
  align-items: center;
  justify-content: center;
  // 用玻璃高光描边替代原来的粗白圈，更贴合 B 方案
  border: 2rpx solid var(--glass-border);
  box-shadow: var(--brand-glow), var(--shadow-md);
  // 上浮突出，压住 TabBar 上沿
  margin-top: -46rpx;
  transition: transform $dur-base $ease-spring, box-shadow $dur-base $ease-out;
  overflow: hidden;

  &:active {
    transform: scale(0.92);
    box-shadow: var(--shadow-sm);
  }
}

/* 顶部弧形高光，让圆形按钮有玻璃球的质感 */
.create-sheen {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 52%;
  background: var(--sheen);
  pointer-events: none;
}

.create-icon {
  position: relative;
  z-index: 1;
  line-height: 1;
}

.create-text {
  font-size: 20rpx;
  color: var(--text-secondary);
  margin-top: 6rpx;
  font-weight: 500;
}
</style>
