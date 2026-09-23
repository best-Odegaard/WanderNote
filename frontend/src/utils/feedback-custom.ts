/**
 * 交互反馈 API（H5 / App 端实现）—— 与 uni.showToast / showModal / showLoading /
 * showActionSheet 同名同签名，仅把渲染实现换成主题化的自定义弹层
 * （App.vue 全局挂载的 AppFeedback 组件消费 store/feedback 的状态）。
 *
 * 约束（保证不动业务逻辑）：
 * 1) 参数对象、success/fail/complete 回调、触发时机与调用方完全一致；
 * 2) showModal 点遮罩/返回键不关闭（原生行为），showActionSheet 点遮罩走 fail；
 * 3) 小程序端（MP-WEIXIN）App.vue 模板不参与页面渲染，全局弹层无处挂载，
 *    由 feedback.ts 分发到 feedback-native.ts 委托回 uni.* 原生实现。
 *
 * 本文件不加条件编译：两个平台分支分别放在独立文件，避免 vue-tsc 双分支重复声明。
 */

import { useFeedbackStore, nextFeedbackId } from '@/store/feedback'
import { ensureFeedbackMounted } from './feedback-mount'

export type ToastIcon = 'success' | 'error' | 'loading' | 'none'

export interface ToastOptions {
  title: string
  icon?: ToastIcon
  image?: string
  mask?: boolean
  duration?: number
}

export interface ShowModalResult {
  confirm: boolean
  cancel: boolean
  content?: string
}

export interface ShowModalOptions {
  title?: string
  content?: string
  showCancel?: boolean
  cancelText?: string
  cancelColor?: string
  confirmText?: string
  confirmColor?: string
  editable?: boolean
  placeholderText?: string
  success?: (res: ShowModalResult) => void
  fail?: () => void
  complete?: () => void
}

export interface ActionSheetResult {
  tapIndex: number
}

export interface ShowActionSheetOptions {
  itemList: string[]
  itemColor?: string
  success?: (res: ActionSheetResult) => void
  fail?: () => void
  complete?: () => void
}

/** 与 theme.scss 中 --danger 一致的危险色（含大小写变体），用于推断删除类确认弹窗 */
const DANGER_COLORS = ['#e64340', '#ef4444', '#f87171', '#ff4d4f', 'red']

interface PendingModal {
  options: ShowModalOptions
}

interface PendingSheet {
  options: ShowActionSheetOptions
}

const pendingModals = new Map<number, PendingModal>()
const pendingSheets = new Map<number, PendingSheet>()

function isDangerColor(color?: string): boolean {
  return !!color && DANGER_COLORS.includes(color.trim().toLowerCase())
}

/** toast 时长按字数自适应：短文案 1.8s，长文案不截断、给足阅读时间 */
function calcDuration(title: string, duration?: number): number {
  if (typeof duration === 'number' && duration > 0) return duration
  if (title.length <= 12) return 1800
  return Math.min(3500, 1800 + (title.length - 12) * 160)
}

/** 动作面板图标按文案关键词匹配（当前仅导航三选项在使用） */
function pickSheetIcon(label: string): string {
  if (label.includes('复制')) return 'link'
  if (label.includes('导航') || label.includes('地图')) return 'map'
  return 'compass'
}

export function customShowToast(options: ToastOptions): void {
  ensureFeedbackMounted()
  useFeedbackStore().setToast({
    id: nextFeedbackId(),
    title: options.title || '',
    icon: options.icon || 'none',
    duration: calcDuration(options.title || '', options.duration)
  })
}

export function customHideToast(): void {
  ensureFeedbackMounted()
  useFeedbackStore().setToast(null)
}

export function customShowLoading(options: { title?: string; mask?: boolean } = {}): void {
  ensureFeedbackMounted()
  useFeedbackStore().enterLoading(options.title || '加载中...')
}

export function customHideLoading(): void {
  ensureFeedbackMounted()
  useFeedbackStore().leaveLoading()
}

export function customShowModal(options: ShowModalOptions): void {
  ensureFeedbackMounted()
  const id = nextFeedbackId()
  pendingModals.set(id, { options })
  useFeedbackStore().setModal({
    id,
    title: options.title || '',
    content: options.content || '',
    showCancel: options.showCancel !== false,
    cancelText: options.cancelText || '取消',
    cancelColor: options.cancelColor || 'var(--text-tertiary)',
    confirmText: options.confirmText || '确定',
    confirmColor: options.confirmColor || '',
    editable: !!options.editable,
    placeholderText: options.placeholderText || '',
    danger: isDangerColor(options.confirmColor)
  })
}

/** 由 AppConfirm 在用户点击确认/取消后调用，回调签名与 uni.showModal 一致 */
export function settleModal(id: number, result: ShowModalResult): void {
  const pending = pendingModals.get(id)
  pendingModals.delete(id)
  useFeedbackStore().setModal(null)
  if (!pending) return
  pending.options.success?.(result)
  pending.options.complete?.()
}

export function customShowActionSheet(options: ShowActionSheetOptions): void {
  ensureFeedbackMounted()
  const id = nextFeedbackId()
  pendingSheets.set(id, { options })
  useFeedbackStore().setSheet({
    id,
    items: (options.itemList || []).map((label) => ({ label, icon: pickSheetIcon(label) })),
    itemColor: options.itemColor || 'var(--text-body)'
  })
}

/** 用户选中某一项 */
export function settleSheet(id: number, tapIndex: number): void {
  const pending = pendingSheets.get(id)
  pendingSheets.delete(id)
  useFeedbackStore().setSheet(null)
  if (!pending) return
  pending.options.success?.({ tapIndex })
  pending.options.complete?.()
}

/** 用户点取消或遮罩（与原生行为一致：走 fail 而非 success） */
export function cancelSheet(id: number): void {
  const pending = pendingSheets.get(id)
  pendingSheets.delete(id)
  useFeedbackStore().setSheet(null)
  if (!pending) return
  pending.options.fail?.()
  pending.options.complete?.()
}
