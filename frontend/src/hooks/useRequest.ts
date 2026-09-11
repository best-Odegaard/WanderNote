import { ref } from 'vue'

/** 通用请求 Hook */
export function useRequest<T>(
  fetcher: () => Promise<T>,
  options: { immediate?: boolean; defaultValue?: T } = {}
) {
  const { immediate = true, defaultValue } = options
  const data = ref<T | undefined>(defaultValue)
  const loading = ref(false)
  const error = ref<Error | null>(null)

  async function run() {
    loading.value = true
    error.value = null
    try {
      data.value = await fetcher()
      return data.value
    } catch (e) {
      error.value = e as Error
      throw e
    } finally {
      loading.value = false
    }
  }

  if (immediate) {
    run()
  }

  return { data, loading, error, run, refresh: run }
}
