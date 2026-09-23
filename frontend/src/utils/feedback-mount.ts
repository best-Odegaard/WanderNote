import { createSSRApp } from 'vue'
import { pinia } from '@/store/pinia'
import AppFeedback from '@/components/AppFeedback/AppFeedback.vue'

/**
 * 运行时挂载全局交互反馈层到 document.body。
 *
 * 背景：uni-app 的 App.vue 模板只在 App-plus 端渲染（H5 / 微信小程序端
 * 实测编译产物中均被剥离），全局弹层无法挂在 App.vue 上。这里在首次
 * 调用反馈 API 时创建一个独立的 Vue 应用挂到 body 下，与主应用共享
 * store/pinia.ts 的同一个 Pinia 实例，从而消费到同一份弹层状态。
 *
 * 小程序端不引入本文件（调用方在 #ifndef MP-WEIXIN 分支内），
 * 该端委托回 uni.* 原生弹层。
 */

const ROOT_ID = 'app-feedback-root'

let mounting = false

export function ensureFeedbackMounted(): void {
  if (typeof document === 'undefined' || mounting) return
  if (document.getElementById(ROOT_ID)) return

  mounting = true
  try {
    const el = document.createElement('div')
    el.id = ROOT_ID
    document.body.appendChild(el)
    createSSRApp(AppFeedback).use(pinia).mount(el)
  } finally {
    mounting = false
  }
}
