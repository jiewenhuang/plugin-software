import { afterEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, ref } from 'vue'
import { useDebouncedValue } from '../useDebouncedValue'

describe('useDebouncedValue', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('publishes only the latest value after the delay', async () => {
    vi.useFakeTimers()
    const scope = effectScope()
    const source = ref('')
    const debounced = scope.run(() => useDebouncedValue(source, 250))!

    source.value = 'soft'
    await nextTick()
    await vi.advanceTimersByTimeAsync(200)
    expect(debounced.value).toBe('')

    source.value = 'software'
    await nextTick()
    await vi.advanceTimersByTimeAsync(249)
    expect(debounced.value).toBe('')

    await vi.advanceTimersByTimeAsync(1)
    expect(debounced.value).toBe('software')
    scope.stop()
  })
})
