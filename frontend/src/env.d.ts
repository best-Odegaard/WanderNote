/// <reference types="@dcloudio/types" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  readonly VITE_USE_MOCK?: string
  /** 腾讯地图 Web JS API Key（H5 内嵌地图用） */
  readonly VITE_MAP_KEY?: string
  /** 腾讯地图 WebService Key（前端地理编码用） */
  readonly VITE_MAP_WS_KEY?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
