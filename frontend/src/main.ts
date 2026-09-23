import { createSSRApp } from 'vue'
import App from './App.vue'
import { pinia } from './store/pinia'

export function createApp() {
  const app = createSSRApp(App)
  // 共享 Pinia 实例：运行时挂载的全局弹层应用（utils/feedback-mount.ts）也用它
  app.use(pinia)
  return { app }
}
