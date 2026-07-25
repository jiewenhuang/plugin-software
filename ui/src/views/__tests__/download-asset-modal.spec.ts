import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defaultSoftwareOptions } from '@/options/software-options'
import DownloadAssetModal from '@/components/software/DownloadAssetModal.vue'

const api = vi.hoisted(() => ({
  createDownloadAsset: vi.fn(),
  getDownloadAssetSecret: vi.fn(),
  updateDownloadAsset: vi.fn(),
}))

vi.mock('@/api/software', () => api)

vi.mock('@halo-dev/components', async () => {
  const { defineComponent, h } = await import('vue')
  const passthrough = (name: string, tag = 'div') =>
    defineComponent({
      name,
      setup(_, { attrs, slots }) {
        return () => h(tag, attrs, [slots.default?.(), slots.popper?.()])
      },
    })

  return {
    IconArrowDownLine: passthrough('IconArrowDownLine', 'span'),
    Toast: {
      success: vi.fn(),
      warning: vi.fn(),
    },
    VButton: passthrough('VButton', 'button'),
    VDropdown: passthrough('VDropdown'),
    VDropdownItem: passthrough('VDropdownItem', 'button'),
    VModal: defineComponent({
      name: 'VModal',
      setup(_, { slots }) {
        return () => h('section', [slots.default?.(), slots.footer?.()])
      },
    }),
    VSpace: passthrough('VSpace'),
    VSwitch: passthrough('VSwitch', 'input'),
  }
})

describe('DownloadAssetModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('preserves existing secrets when their authorized read fails', async () => {
    const asset = {
      metadata: { name: 'asset-1' },
      spec: {
        appName: 'app-1',
        versionName: 'version-1',
        title: 'Private mirror',
        enabled: false,
        hasUrl: true,
        hasPasswordSecret: true,
      },
    }
    api.getDownloadAssetSecret.mockRejectedValueOnce(new Error('temporary failure'))
    api.updateDownloadAsset.mockResolvedValueOnce(asset)
    const wrapper = mount(DownloadAssetModal, {
      props: {
        appName: 'app-1',
        versions: [
          {
            metadata: { name: 'version-1' },
            spec: { appName: 'app-1', version: '1.0.0' },
          },
        ],
        softwareOptions: defaultSoftwareOptions,
        canManageSecrets: true,
      },
      global: {
        stubs: {
          ConsoleFilterSelect: true,
        },
      },
    })

    await wrapper.vm.openEdit(asset)
    await flushPromises()

    expect(wrapper.get('[name="download-asset-url"]').attributes()).toHaveProperty('disabled')
    expect(wrapper.get('[name="download-asset-password-secret"]').attributes()).toHaveProperty(
      'disabled',
    )

    const saveButton = wrapper.findAll('button').find((button) =>
      button.text().includes('保存下载源'),
    )
    expect(saveButton).toBeDefined()
    await saveButton?.trigger('click')
    await flushPromises()

    expect(api.updateDownloadAsset).toHaveBeenCalledOnce()
    const payload = api.updateDownloadAsset.mock.calls[0][1]
    expect(payload.spec).not.toHaveProperty('url')
    expect(payload.spec).not.toHaveProperty('storageKey')
    expect(payload.spec).not.toHaveProperty('extractionCode')
    expect(payload.spec).not.toHaveProperty('passwordSecretName')
  })
})
