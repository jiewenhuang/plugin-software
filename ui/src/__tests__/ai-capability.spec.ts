import { beforeEach, describe, expect, it, vi } from 'vitest'

const { get } = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('@halo-dev/api-client', () => ({
  axiosInstance: { get },
  consoleApiClient: {},
  coreApiClient: {},
}))

import { getSoftwareAiCapabilities } from '@/api/software'

describe('optional AI capability client', () => {
  beforeEach(() => get.mockReset())

  it('queries the conditional capability endpoint', async () => {
    get.mockResolvedValue({ data: { available: true } })

    await expect(getSoftwareAiCapabilities()).resolves.toEqual({ available: true })
    expect(get).toHaveBeenCalledWith(
      '/apis/console.api.software.jiewen.run/v1alpha1/ai/capabilities',
    )
  })
})
