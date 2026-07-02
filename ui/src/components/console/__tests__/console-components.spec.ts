import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ConsoleBulkActions from '../ConsoleBulkActions.vue'
import ConsoleFilterSelect from '../../ConsoleFilterSelect.vue'
import ConsoleIconButton from '../ConsoleIconButton.vue'
import ConsoleImagePicker from '../ConsoleImagePicker.vue'
import ConsolePageScaffold from '../ConsolePageScaffold.vue'
import ConsoleStatCard from '../ConsoleStatCard.vue'
import ConsoleStatsGrid from '../ConsoleStatsGrid.vue'
import ConsoleTableCard from '../ConsoleTableCard.vue'
import ConsoleTextButton from '../ConsoleTextButton.vue'
import ConsoleToolbar from '../ConsoleToolbar.vue'

vi.mock('@halo-dev/components', async () => {
  const { defineComponent, h } = await import('vue')
  const IconStub = defineComponent({
    name: 'IconStub',
    setup() {
      return () => h('svg')
    },
  })
  return {
    IconArrowDownLine: IconStub,
    IconArrowUpDownLine: IconStub,
    IconDeleteBin: IconStub,
    IconImageAddLine: IconStub,
    IconLink: IconStub,
    IconUpload: IconStub,
    Toast: {
      error: vi.fn(),
      success: vi.fn(),
      warning: vi.fn(),
    },
    VButton: defineComponent({
      name: 'VButton',
      setup(_, { attrs, slots }) {
        return () => h('button', attrs, slots.default?.())
      },
    }),
    VCard: defineComponent({
      name: 'VCard',
      props: {
        bodyClass: {
          type: [String, Array],
          default: '',
        },
      },
      setup(props, { slots }) {
        return () =>
          h(
            'section',
            {
              class: 'v-card-stub',
              'data-body-class': Array.isArray(props.bodyClass)
                ? props.bodyClass.join(' ')
                : props.bodyClass,
            },
            [
              h('header', slots.header?.()),
              h('div', { class: 'v-card-body' }, slots.default?.()),
              h('footer', slots.footer?.()),
            ],
          )
      },
    }),
    VDropdown: defineComponent({
      name: 'VDropdown',
      setup(_, { slots }) {
        return () =>
          h('div', { class: 'v-dropdown-stub' }, [
            slots.default?.(),
            h('div', { class: 'v-dropdown-popper' }, slots.popper?.()),
          ])
      },
    }),
    VDropdownItem: defineComponent({
      name: 'VDropdownItem',
      props: {
        disabled: {
          type: Boolean,
          default: false,
        },
        selected: {
          type: Boolean,
          default: false,
        },
      },
      setup(props, { attrs, slots }) {
        return () =>
          h(
            'button',
            {
              ...attrs,
              disabled: props.disabled,
              'data-selected': props.selected ? 'true' : 'false',
            },
            slots.default?.(),
          )
      },
    }),
    VModal: defineComponent({
      name: 'VModal',
      props: {
        title: {
          type: String,
          default: '',
        },
      },
      setup(props, { slots }) {
        return () =>
          h('section', { class: 'v-modal-stub' }, [
            h('h2', props.title),
            h('div', slots.default?.()),
            h('footer', slots.footer?.()),
          ])
      },
    }),
    VSpace: defineComponent({
      name: 'VSpace',
      setup(_, { slots }) {
        return () => h('div', { class: 'v-space-stub' }, slots.default?.())
      },
    }),
  }
})

vi.mock('@halo-dev/api-client', () => ({
  consoleApiClient: {
    storage: {
      attachment: {
        uploadAttachmentForConsole: vi.fn(),
      },
    },
  },
}))

describe('console shared components', () => {
  it('renders the page scaffold with consistent content class', () => {
    const wrapper = mount(ConsolePageScaffold, {
      slots: {
        default: '<p>页面内容</p>',
      },
    })

    expect(wrapper.classes()).toContain('console-page-scaffold')
    expect(wrapper.text()).toContain('页面内容')
  })

  it('groups stat cards and renders stat content through a stable API', () => {
    const wrapper = mount(ConsoleStatsGrid, {
      slots: {
        default: `
          <ConsoleStatCard tone="blue" label="总数" value="12" meta="本页 4">
            <template #icon><span data-test="icon">i</span></template>
          </ConsoleStatCard>
        `,
      },
      global: {
        components: {
          ConsoleStatCard,
        },
      },
    })

    expect(wrapper.classes()).toContain('console-stats-grid')
    expect(wrapper.find('.console-stat-card').classes()).toContain('blue')
    expect(wrapper.text()).toContain('总数')
    expect(wrapper.text()).toContain('12')
    expect(wrapper.text()).toContain('本页 4')
    expect(wrapper.find('[data-test="icon"]').exists()).toBe(true)
  })

  it('renders a table card with header body footer and the shared body class', () => {
    const wrapper = mount(ConsoleTableCard, {
      props: {
        bodyClass: 'custom-body',
      },
      slots: {
        header: '<span>筛选栏</span>',
        default: '<table><tbody><tr><td>列表</td></tr></tbody></table>',
        footer: '<span>分页</span>',
      },
    })

    expect(wrapper.find('.v-card-stub').attributes('data-body-class')).toContain(
      'console-table-card-body',
    )
    expect(wrapper.find('.v-card-stub').attributes('data-body-class')).toContain('custom-body')
    expect(wrapper.text()).toContain('筛选栏')
    expect(wrapper.text()).toContain('列表')
    expect(wrapper.text()).toContain('分页')
  })

  it('separates toolbar left and right slots', () => {
    const wrapper = mount(ConsoleToolbar, {
      slots: {
        left: '<span class="left-content">搜索</span>',
        right: '<span class="right-content">操作</span>',
      },
    })

    expect(wrapper.classes()).toContain('console-toolbar')
    expect(wrapper.find('.console-toolbar-left .left-content').exists()).toBe(true)
    expect(wrapper.find('.console-toolbar-right .right-content').exists()).toBe(true)
  })

  it('renders long filter select menus through the shared popper content', async () => {
    const wrapper = mount(ConsoleFilterSelect, {
      props: {
        modelValue: '',
        options: [
          { label: '全部分类', value: '' },
          ...Array.from({ length: 48 }, (_, index) => ({
            label: `测试分类 ${index + 1}`,
            value: `category-${index + 1}`,
          })),
        ],
      },
    })

    expect(wrapper.find('.console-filter-select-menu').exists()).toBe(true)
    expect(wrapper.findAll('.v-dropdown-popper button')).toHaveLength(49)

    await wrapper.findAll('.v-dropdown-popper button')[12].trigger('click')

    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual(['category-12'])
  })

  it('emits click events from shared buttons', async () => {
    const iconButton = mount(ConsoleIconButton, {
      props: {
        title: '刷新',
      },
      slots: {
        default: '<span>R</span>',
      },
    })
    const textButton = mount(ConsoleTextButton, {
      props: {
        danger: true,
      },
      slots: {
        default: '重置',
      },
    })

    await iconButton.trigger('click')
    await textButton.trigger('click')

    expect(iconButton.classes()).toContain('console-icon-button')
    expect(iconButton.attributes('title')).toBe('刷新')
    expect(iconButton.attributes('aria-label')).toBe('刷新')
    expect(iconButton.emitted('click')).toHaveLength(1)
    expect(textButton.classes()).toContain('console-text-button')
    expect(textButton.classes()).toContain('danger')
    expect(textButton.emitted('click')).toHaveLength(1)
  })

  it('renders bulk actions only after rows are selected', async () => {
    const hidden = mount(ConsoleBulkActions, {
      props: {
        count: 0,
      },
    })
    const visible = mount(ConsoleBulkActions, {
      props: {
        count: 3,
      },
    })

    expect(hidden.find('.console-bulk-actions').exists()).toBe(false)
    expect(visible.text()).toContain('已选 3 项')

    await visible.find('button').trigger('click')

    expect(visible.emitted('action')).toHaveLength(1)
  })

  it('opens an explicit image source chooser before selecting media', async () => {
    const wrapper = mount(ConsoleImagePicker, {
      props: {
        label: '软件图标',
        modelValue: '/upload/icon.png',
      },
      global: {
        components: {
          AttachmentSelectorModal: {
            template: '<div class="attachment-selector-stub" />',
          },
        },
      },
    })

    await wrapper.find('.image-preview-action').trigger('click')

    expect(wrapper.find('.source-options').exists()).toBe(true)
    expect(wrapper.text()).toContain('直接上传')
    expect(wrapper.text()).toContain('从附件库选择')
    expect(wrapper.text()).toContain('输入图片地址')
  })
})
