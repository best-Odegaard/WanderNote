import { onShow } from '@dcloudio/uni-app'
import { useAppStore } from '@/store/app'

/** Tab 页 onShow：同步选中态并隐藏系统默认 TabBar（H5/App 使用自定义底栏） */
export function useTabBarPage(index: number, extra?: () => void) {
  const appStore = useAppStore()

  onShow(() => {
    // #ifdef H5 || APP-PLUS
    uni.hideTabBar({ animation: false })
    // #endif
    appStore.setTabbarIndex(index)
    appStore.closeCreateMenu()
    extra?.()
  })
}
