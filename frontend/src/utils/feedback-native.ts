/**
 * 交互反馈 API（小程序端实现）—— 委托回 uni.* 原生弹层。
 *
 * 为什么小程序端不走自定义弹层：实测 uni-app 编译产物中 App.vue 被编译为
 * 空渲染函数，其模板只进入 App.wxml（小程序外壳，不作为页面 UI 渲染），
 * 页面 wxml 中不存在该模板，全局弹层无处挂载。故该端保持原生行为不变。
 */

import type {
  ShowActionSheetOptions,
  ShowModalOptions,
  ToastOptions
} from './feedback-custom'

export function nativeShowToast(options: ToastOptions): void {
  uni.showToast(options as UniApp.ShowToastOptions)
}

export function nativeHideToast(): void {
  uni.hideToast()
}

export function nativeShowLoading(options: { title?: string; mask?: boolean } = {}): void {
  uni.showLoading({ title: options.title || '加载中...', mask: true })
}

export function nativeHideLoading(): void {
  uni.hideLoading()
}

export function nativeShowModal(options: ShowModalOptions): void {
  uni.showModal(options as UniApp.ShowModalOptions)
}

export function nativeShowActionSheet(options: ShowActionSheetOptions): void {
  uni.showActionSheet(options as UniApp.ShowActionSheetOptions)
}
