import { createPinia } from 'pinia'

/**
 * 全局唯一 Pinia 实例。
 *
 * 为什么单独抽出来：全局交互弹层（AppFeedback）是在运行时挂载到
 * document.body 的独立 Vue 应用（uni-app 的 App.vue 模板在 H5/小程序端
 * 不渲染，无处挂载全局组件），它必须与主应用共享同一个 Pinia 实例，
 * 才能消费到页面里调用 showToast/showModal 时写入的状态。
 */
export const pinia = createPinia()
