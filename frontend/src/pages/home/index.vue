<template>
  <view class="home-page">
    <ChatPlanner tab-mode />

    <!-- #ifndef MP-WEIXIN -->
    <AppTabBar />
    <!-- #endif -->
  </view>
</template>

<script setup lang="ts">
/**
 * 首页 = AI 对话生成行程。
 *
 * 改造前首页是「搜索框 + 轮播 + 我的行程卡片」，用户想规划还得先点进表单页；
 * 现在进入首页直接就是对话界面，说一句话就能开始规划。
 *
 * 首页与「带上下文的对话页」（pages/ai/chat）共用 components/ChatPlanner，
 * 区别只在 tabMode：无返回键、底部输入区让开 TabBar。
 *
 * 迁移去向：
 *   · 原来的「我的行程」卡片 → 行程 tab（pages/trip/index）
 *   · 原来的轮播图 → 以卡片形式放到探索 tab（pages/coming/index）
 */
import ChatPlanner from '@/components/ChatPlanner/ChatPlanner.vue'
import AppTabBar from '@/components/AppTabBar/AppTabBar.vue'
import { useTabBarPage } from '@/hooks/useTabBarPage'

// 同步自定义 TabBar 选中态、隐藏系统 TabBar（H5/App）
useTabBarPage(0)
</script>

<style lang="scss" scoped>
.home-page {
  min-height: 100vh;
  background: var(--bg-page);
}
</style>
