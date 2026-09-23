/**
 * 交互反馈 API —— 对外唯一入口。
 *
 * 与 uni.showToast / showModal / showLoading / showActionSheet 同名同签名，
 * 页面/工具层只改 import 来源，调用方式与参数不变（不动业务逻辑）。
 *
 * 平台分发：
 * - H5 / App：feedback-custom.ts，主题化自定义弹层（App.vue 挂载 AppFeedback）；
 * - 微信小程序：feedback-native.ts，委托回 uni.* 原生实现
 *   （小程序端 App.vue 模板不参与页面渲染，见 feedback-native.ts 注释）。
 */

// #ifdef MP-WEIXIN
import {
  nativeHideLoading,
  nativeHideToast,
  nativeShowActionSheet,
  nativeShowLoading,
  nativeShowModal,
  nativeShowToast
} from './feedback-native'
// #endif
// #ifndef MP-WEIXIN
import {
  customHideLoading,
  customHideToast,
  customShowActionSheet,
  customShowLoading,
  customShowModal,
  customShowToast
} from './feedback-custom'
// #endif

// 类型两个平台分支共用（feedback-custom.ts 无平台差异，任何端都可解析）
import type { ShowActionSheetOptions, ShowModalOptions, ToastOptions } from './feedback-custom'

export type {
  ActionSheetResult,
  ShowActionSheetOptions,
  ShowModalOptions,
  ShowModalResult,
  ToastIcon,
  ToastOptions
} from './feedback-custom'

export function showToast(options: ToastOptions | string): void {
  const opts = typeof options === 'string' ? { title: options } : options
  // #ifdef MP-WEIXIN
  nativeShowToast(opts)
  // #endif
  // #ifndef MP-WEIXIN
  customShowToast(opts)
  // #endif
}

export function hideToast(): void {
  // #ifdef MP-WEIXIN
  nativeHideToast()
  // #endif
  // #ifndef MP-WEIXIN
  customHideToast()
  // #endif
}

export function showLoading(options: { title?: string; mask?: boolean } = {}): void {
  // #ifdef MP-WEIXIN
  nativeShowLoading(options)
  // #endif
  // #ifndef MP-WEIXIN
  customShowLoading(options)
  // #endif
}

export function hideLoading(): void {
  // #ifdef MP-WEIXIN
  nativeHideLoading()
  // #endif
  // #ifndef MP-WEIXIN
  customHideLoading()
  // #endif
}

export function showModal(options: ShowModalOptions): void {
  // #ifdef MP-WEIXIN
  nativeShowModal(options)
  // #endif
  // #ifndef MP-WEIXIN
  customShowModal(options)
  // #endif
}

export function showActionSheet(options: ShowActionSheetOptions): void {
  // #ifdef MP-WEIXIN
  nativeShowActionSheet(options)
  // #endif
  // #ifndef MP-WEIXIN
  customShowActionSheet(options)
  // #endif
}
