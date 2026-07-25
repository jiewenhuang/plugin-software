import { onScopeDispose, ref, watch, type Ref } from 'vue'

export function useDebouncedValue<T>(source: Ref<T>, delay = 250): Readonly<Ref<T>> {
  const debounced = ref(source.value) as Ref<T>
  let timer: ReturnType<typeof setTimeout> | undefined

  watch(source, (value) => {
    if (timer) {
      clearTimeout(timer)
    }
    timer = setTimeout(() => {
      debounced.value = value
      timer = undefined
    }, delay)
  })

  onScopeDispose(() => {
    if (timer) {
      clearTimeout(timer)
    }
  })

  return debounced
}
