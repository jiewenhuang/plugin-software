import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import TopicManager from '../TopicManager.vue'

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
    IconArrowDownLine: IconStub,
    IconArrowUpLine: IconStub,
    IconCheckboxCircle: IconStub,
    IconClipboardLine: IconStub,
    IconClose: IconStub,
    IconDeleteBin: IconStub,
    IconLink: IconStub,
    IconRefreshLine: IconStub,
    IconRiPencilFill: IconStub,
    IconSave: IconStub,
    IconSearch: IconStub,
    VButton: passthrough('VButton', 'button'),
    VEmpty: passthrough('VEmpty'),
    VLoading: passthrough('VLoading'),
    VPagination: passthrough('VPagination'),
    VSpace: passthrough('VSpace'),
    VSwitch: passthrough('VSwitch', 'input'),
  }
})

describe('TopicManager', () => {
  it('opens the topic composer when a create request is received', async () => {
    const wrapper = mount(TopicManager, {
      props: {
        topics: [],
        loading: false,
        saving: false,
        deletingName: '',
        editingName: '',
        draft: {
          spec: {
            title: '',
            slug: '',
          },
        },
        apps: [],
        createRequestKey: 0,
      },
      global: {
        stubs: {
          ConsoleBulkActions: true,
          ConsoleFilterSelect: true,
          ConsoleIconButton: true,
          ConsoleImagePicker: {
            props: ['label'],
            template: '<div class="console-image-picker topic-cover-picker">{{ label }}</div>',
          },
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
          ConsoleTokenSelector: {
            template: '<div class="console-token-selector" />',
          },
        },
      },
    })

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)

    await wrapper.setProps({
      createRequestKey: 1,
    })
    await nextTick()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('专题封面')
    expect(wrapper.text()).toContain('关联软件')
    expect(wrapper.emitted('search-apps')?.[0]).toEqual([''])
  })

  it('lets the topic composer select a cover and attach software items', async () => {
    const wrapper = mount(TopicManager, {
      props: {
        topics: [],
        loading: false,
        saving: false,
        deletingName: '',
        editingName: '',
        draft: {
          spec: {
            title: '',
            slug: '',
          },
        },
        apps: [
          {
            metadata: {
              name: 'software-app-bridge',
            },
            spec: {
              displayName: 'Adobe Bridge',
              slug: 'adobe-bridge',
              subtitle: '素材管理工具',
            },
          },
        ],
      },
      global: {
        stubs: {
          ConsoleBulkActions: true,
          ConsoleFilterSelect: true,
          ConsoleIconButton: true,
          ConsoleImagePicker: {
            props: ['label'],
            template: '<div class="console-image-picker topic-cover-picker">{{ label }}</div>',
          },
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
          ConsoleTokenSelector: {
            props: ['modelValue', 'options'],
            emits: ['update:modelValue', 'search'],
            template:
              '<div class="console-token-selector"><button type="button" @click="$emit(\'update:modelValue\', [\'software-app-bridge\'])">选择软件</button></div>',
          },
        },
      },
    })

    wrapper.vm.openCreate()
    await nextTick()

    expect(wrapper.text()).toContain('专题封面')
    expect(wrapper.find('.topic-cover-picker').exists()).toBe(true)
    expect(wrapper.find('.console-token-selector').exists()).toBe(true)
    expect(wrapper.emitted('search-apps')?.[0]).toEqual([''])

    await wrapper.find('.console-token-selector button').trigger('click')
    await nextTick()

    expect(wrapper.text()).toContain('Adobe Bridge')
    expect(wrapper.text()).toContain('/adobe-bridge · 素材管理工具')
  })
})
