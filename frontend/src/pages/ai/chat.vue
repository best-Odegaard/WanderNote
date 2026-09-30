<template>
  <ChatPlanner />
</template>

<script setup lang="ts">
/**
 * 带上下文的对话页。
 *
 * 对话的全部逻辑在 components/ChatPlanner 里，这里只是「独立页面」这层壳：
 *   · 由行程列表的「进入行程对话」、行程详情的「对话修改」进入（?tripId=）
 *   · 由景点详情/探索页带入一句话（?q=）
 * 参数由组件自己从当前页 query 读取（见 ChatPlanner 的 initPlanner），
 * 这样不依赖 onLoad 与组件 mounted 的先后顺序。
 *
 * 首页也是同一套对话界面（tab 模式），见 pages/home/index.vue。
 */
import ChatPlanner from '@/components/ChatPlanner/ChatPlanner.vue'
</script>
