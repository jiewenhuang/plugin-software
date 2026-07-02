import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import type { SoftwareTag } from '@/api/software'
import TagManager from '../TagManager.vue'

vi.mock('@halo-dev/components', async () => {
  const IconStub = defineComponent({
    name: 'IconStub',
    setup() {
      return () => h('svg')
    },
  })
  const passthrough = (name: string, tag = 'div') =>
    defineComponent({
      name,
      setup(_, { attrs, slots }) {
        return () => h(tag, attrs, slots.default?.())
      },
    })

  return {
    IconAddCircle: IconStub,
    IconArrowDownCircleLine: IconStub,
    IconCheckboxCircle: IconStub,
    IconClipboardLine: IconStub,
    IconClose: IconStub,
    IconDeleteBin: IconStub,
    IconLink: IconStub,
    IconMore: IconStub,
    IconRefreshLine: IconStub,
    IconRiPencilFill: IconStub,
    IconSave: IconStub,
    IconSearch: IconStub,
    IconStopCircle: IconStub,
    VButton: passthrough('VButton', 'button'),
    VDropdown: defineComponent({
      name: 'VDropdown',
      setup(_, { slots }) {
        return () => h('div', [slots.default?.(), slots.popper?.()])
      },
    }),
    VDropdownItem: passthrough('VDropdownItem', 'button'),
    VEmpty: passthrough('VEmpty'),
    VLoading: passthrough('VLoading'),
    VPagination: passthrough('VPagination'),
    VSwitch: defineComponent({
      name: 'VSwitch',
      props: {
        modelValue: {
          type: Boolean,
          default: false,
        },
      },
      emits: ['update:modelValue'],
      setup(props, { emit }) {
        return () =>
          h('input', {
            checked: props.modelValue,
            type: 'checkbox',
            onChange: (event: Event) =>
              emit('update:modelValue', (event.target as HTMLInputElement).checked),
          })
      },
    }),
  }
})

describe('TagManager', () => {
  it('lets the edit composer choose and save a tag color', async () => {
    const wrapper = mount(TagManager, {
      props: {
        tags: [tag()],
        loading: false,
        saving: false,
        deletingName: '',
        editingName: '',
        draft: emptyDraft(),
      },
      global: {
        stubs: {
          ConsoleBulkActions: true,
          ConsoleFilterSelect: true,
          ConsoleIconButton: true,
          ConsolePageScaffold: {
            template: '<div><slot /></div>',
          },
          ConsoleStatCard: {
            template: '<article><slot name="icon" /><slot /></article>',
          },
          ConsoleStatsGrid: {
            template: '<section><slot /></section>',
          },
          ConsoleTableCard: {
            template: '<section><slot name="header" /><slot /><slot name="footer" /></section>',
          },
          ConsoleTextButton: true,
          ConsoleToolbar: {
            template: '<div><slot name="left" /><slot name="right" /></div>',
          },
        },
      },
    })

    await wrapper.setProps({
      editingName: 'tag-halo',
      draft: tag('#e11d48'),
    })
    await nextTick()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
    expect(wrapper.find('.tag-color-native').exists()).toBe(true)

    await wrapper.find('.tag-color-native').setValue('#16a34a')
    await wrapper.find('.modal-actions .tag-primary-action').trigger('click')

    const saved = wrapper.emitted('save')?.[0]?.[0] as SoftwareTag
    expect(saved.spec.color).toBe('#16a34a')
  })

  it('closes the edit composer after the parent resets the edit state', async () => {
    const wrapper = mount(TagManager, {
      props: {
        tags: [tag()],
        loading: false,
        saving: false,
        deletingName: '',
        editingName: '',
        draft: emptyDraft(),
      },
      global: {
        stubs: {
          ConsoleBulkActions: true,
          ConsoleFilterSelect: true,
          ConsoleIconButton: true,
          ConsolePageScaffold: {
            template: '<div><slot /></div>',
          },
          ConsoleStatCard: {
            template: '<article><slot name="icon" /><slot /></article>',
          },
          ConsoleStatsGrid: {
            template: '<section><slot /></section>',
          },
          ConsoleTableCard: {
            template: '<section><slot name="header" /><slot /><slot name="footer" /></section>',
          },
          ConsoleTextButton: true,
          ConsoleToolbar: {
            template: '<div><slot name="left" /><slot name="right" /></div>',
          },
        },
      },
    })

    await wrapper.setProps({
      editingName: 'tag-halo',
      draft: tag('#e11d48'),
    })
    await nextTick()

    await wrapper.find('.modal-actions .tag-primary-action').trigger('click')
    const saved = wrapper.emitted('save')?.[0]?.[0] as SoftwareTag

    await wrapper.setProps({
      draft: saved,
      saving: true,
    })
    await nextTick()
    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)

    await wrapper.setProps({
      editingName: '',
      draft: emptyDraft(),
      saving: false,
    })
    await nextTick()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
  })
})

function tag(color?: string): SoftwareTag {
  return {
    metadata: {
      name: 'tag-halo',
      creationTimestamp: '2024-05-20T14:30:00Z',
    },
    spec: {
      slug: 'halo',
      displayName: 'Halo',
      color,
      visible: true,
      priority: 10,
    },
    status: {
      appCount: 12,
    },
  }
}

function emptyDraft(): SoftwareTag {
  return {
    spec: {
      slug: '',
      displayName: '',
      visible: true,
      priority: 0,
    },
  }
}
