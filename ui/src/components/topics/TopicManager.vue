<script setup lang="ts">
import {
  IconAddCircle,
  IconArrowDownCircleLine,
  IconArrowDownLine,
  IconCheckboxCircle,
  IconClipboardLine,
  IconClose,
  IconDeleteBin,
  IconLink,
  IconRefreshLine,
  IconRiPencilFill,
  IconSave,
  IconSearch,
  IconArrowUpLine,
  VButton,
  VEmpty,
  VLoading,
  VPagination,
  VSpace,
  VSwitch,
} from '@halo-dev/components'
import { computed, ref, watch } from 'vue'
import {
  emptyTopic,
  type SoftwareApp,
  type SoftwareTopic,
  type SoftwareTopicItem,
} from '@/api/software'
import ConsoleFilterSelect, { type FilterSelectOption } from '@/components/ConsoleFilterSelect.vue'
import ConsoleBulkActions from '@/components/console/ConsoleBulkActions.vue'
import ConsoleIconButton from '@/components/console/ConsoleIconButton.vue'
import ConsoleImagePicker from '@/components/console/ConsoleImagePicker.vue'
import ConsolePageScaffold from '@/components/console/ConsolePageScaffold.vue'
import ConsoleStatCard from '@/components/console/ConsoleStatCard.vue'
import ConsoleStatsGrid from '@/components/console/ConsoleStatsGrid.vue'
import ConsoleTableCard from '@/components/console/ConsoleTableCard.vue'
import ConsoleTextButton from '@/components/console/ConsoleTextButton.vue'
import ConsoleToolbar from '@/components/console/ConsoleToolbar.vue'
import { useDebouncedValue } from '@/composables/useDebouncedValue'
import ConsoleTokenSelector, {
  type TokenSelectorOption,
} from '@/components/console/ConsoleTokenSelector.vue'
import {
  buildTopicRows,
  buildTopicStats,
  filterTopicRows,
  formatTopicDate,
  formatTopicNumber,
  sortTopicRows,
  type TopicRow,
  type TopicSortKey,
  type TopicStatusFilter,
} from '@/views/topic-console'

const props = defineProps<{
  topics: SoftwareTopic[]
  loading: boolean
  saving: boolean
  deletingName: string
  editingName: string
  draft: SoftwareTopic
  apps: SoftwareApp[]
  appsLoading?: boolean
  createRequestKey?: number
  total?: number
  remote?: boolean
}>()

const emit = defineEmits<{
  refresh: []
  reset: []
  edit: [topic: SoftwareTopic]
  delete: [topic: SoftwareTopic]
  save: [topic: SoftwareTopic]
  'search-apps': [keyword: string]
  'selection-change': [selection: { count: number; names: string[]; titles: string[] }]
  'batch-delete': []
  'query-change': [query: {
    page: number
    size: number
    keyword: string
    status: TopicStatusFilter
    sort: TopicSortKey
  }]
}>()

const page = ref(1)
const size = ref(20)
const keyword = ref('')
const debouncedKeyword = useDebouncedValue(keyword)
const selectedStatus = ref<TopicStatusFilter>('')
const selectedSort = ref<TopicSortKey>('created-desc')
const selectedNames = ref<string[]>([])
const composerOpen = ref(false)
const draftModel = ref<SoftwareTopic>(emptyTopic())
const statusOptions: FilterSelectOption[] = [
  { label: '状态：全部', value: '' },
  { label: '状态：已发布', value: 'published' },
  { label: '状态：草稿', value: 'draft' },
  { label: '状态：精选专题', value: 'featured' },
]
const sortOptions: FilterSelectOption[] = [
  { label: '创建时间 ↓', value: 'created-desc' },
  { label: '创建时间 ↑', value: 'created-asc' },
  { label: '优先级 ↑', value: 'priority-asc' },
  { label: '关联软件 ↓', value: 'apps-desc' },
  { label: '专题名称 ↑', value: 'title-asc' },
]

const rows = computed(() => buildTopicRows(props.topics))
const stats = computed(() => buildTopicStats(props.topics))
const filteredRows = computed(() =>
  props.remote
    ? rows.value
    : sortTopicRows(
        filterTopicRows(rows.value, {
          keyword: keyword.value,
          status: selectedStatus.value,
        }),
        selectedSort.value,
      ),
)
const pagedRows = computed(() => {
  if (props.remote) {
    return filteredRows.value
  }
  const start = (page.value - 1) * size.value
  return filteredRows.value.slice(start, start + size.value)
})
const effectiveTotal = computed(() =>
  props.remote ? props.total || 0 : filteredRows.value.length,
)
const allVisibleSelected = computed(
  () =>
    Boolean(pagedRows.value.length) &&
    pagedRows.value.every((row) => selectedNames.value.includes(row.name)),
)
const hasFilters = computed(() => Boolean(keyword.value || selectedStatus.value))
const canSaveDraft = computed(() =>
  Boolean(draftModel.value.spec.title?.trim() && draftModel.value.spec.slug?.trim()),
)
const composerTitle = computed(() => (props.editingName ? '编辑专题' : '新建专题'))
const selectedAppNames = computed(() =>
  (draftModel.value.spec.items || []).map((item) => item.appName).filter(Boolean),
)
const appOptions = computed<TokenSelectorOption[]>(() => {
  const optionMap = new Map<string, TokenSelectorOption>()
  props.apps.forEach((app) => {
    const name = app.metadata?.name
    if (!name) {
      return
    }
    optionMap.set(name, {
      label: app.spec.displayName || name,
      value: name,
      description: [
        app.spec.slug ? `/${app.spec.slug}` : '',
        app.spec.subtitle || app.spec.vendor || '',
      ]
        .filter(Boolean)
        .join(' · '),
    })
  })
  selectedAppNames.value.forEach((name) => {
    if (!optionMap.has(name)) {
      optionMap.set(name, {
        label: name,
        value: name,
      })
    }
  })
  return [...optionMap.values()]
})

function cloneTopic(topic: SoftwareTopic) {
  return JSON.parse(JSON.stringify(topic)) as SoftwareTopic
}

function openCreate() {
  emit('reset')
  draftModel.value = emptyTopic()
  composerOpen.value = true
  emit('search-apps', '')
}

function openEdit(row: TopicRow) {
  draftModel.value = cloneTopic(row.source)
  composerOpen.value = true
  emit('edit', row.source)
  emit('search-apps', '')
}

function closeComposer() {
  composerOpen.value = false
  emit('reset')
}

function saveDraft() {
  if (!canSaveDraft.value) {
    return
  }
  emit('save', topicPayload())
  composerOpen.value = false
}

function topicPayload() {
  const payload = cloneTopic(draftModel.value)
  payload.spec.items = (payload.spec.items || [])
    .filter((item) => item.appName)
    .map((item) => ({
      appName: item.appName,
      reason: item.reason?.trim() || undefined,
      priority: Number.isFinite(Number(item.priority)) ? Number(item.priority) : undefined,
    }))
  return payload
}

function setTopicAppNames(names: string[]) {
  const existingByName = new Map(
    (draftModel.value.spec.items || []).map((item) => [item.appName, item] as const),
  )
  draftModel.value.spec.items = names.filter(Boolean).map((name, index) => ({
    appName: name,
    reason: existingByName.get(name)?.reason || '',
    priority: existingByName.get(name)?.priority ?? (index + 1) * 10,
  }))
}

function topicAppLabel(appName: string) {
  return appOptions.value.find((option) => option.value === appName)?.label || appName
}

function topicAppDescription(appName: string) {
  return appOptions.value.find((option) => option.value === appName)?.description || appName
}

function updateTopicItem(appName: string, patch: Partial<SoftwareTopicItem>) {
  draftModel.value.spec.items = (draftModel.value.spec.items || []).map((item) =>
    item.appName === appName ? { ...item, ...patch } : item,
  )
}

function removeTopicItem(appName: string) {
  setTopicAppNames(selectedAppNames.value.filter((name) => name !== appName))
}

function moveTopicItem(index: number, direction: -1 | 1) {
  const items = [...(draftModel.value.spec.items || [])]
  const nextIndex = index + direction
  if (nextIndex < 0 || nextIndex >= items.length) {
    return
  }
  const [item] = items.splice(index, 1)
  items.splice(nextIndex, 0, item)
  draftModel.value.spec.items = items.map((current, currentIndex) => ({
    ...current,
    priority: (currentIndex + 1) * 10,
  }))
}

function clearFilters() {
  keyword.value = ''
  selectedStatus.value = ''
  page.value = 1
}

function toggleAllVisible(event: Event) {
  const checked = (event.target as HTMLInputElement).checked
  const visibleNames = pagedRows.value.map((row) => row.name).filter(Boolean)
  if (checked) {
    selectedNames.value = Array.from(new Set([...selectedNames.value, ...visibleNames]))
    return
  }
  selectedNames.value = selectedNames.value.filter((name) => !visibleNames.includes(name))
}

function toggleRow(row: TopicRow, event: Event) {
  const checked = (event.target as HTMLInputElement).checked
  if (checked) {
    selectedNames.value = Array.from(new Set([...selectedNames.value, row.name]))
    return
  }
  selectedNames.value = selectedNames.value.filter((name) => name !== row.name)
}

function clearSelection() {
  selectedNames.value = []
}

function selectedTopicTitles() {
  const titleByName = new Map(rows.value.map((row) => [row.name, row.title]))
  return selectedNames.value.map((name) => titleByName.get(name) || name)
}

watch([debouncedKeyword, selectedStatus, selectedSort, size], () => {
  page.value = 1
})

watch(
  [page, size, debouncedKeyword, selectedStatus, selectedSort],
  () => {
    if (props.remote) {
      emit('query-change', {
        page: page.value,
        size: size.value,
        keyword: debouncedKeyword.value,
        status: selectedStatus.value,
        sort: selectedSort.value,
      })
    }
  },
)

watch(effectiveTotal, (value) => {
  const lastPage = Math.max(1, Math.ceil(value / size.value))
  if (page.value > lastPage) {
    page.value = lastPage
  }
})

watch(
  () => props.draft,
  (draft) => {
    if (props.editingName) {
      draftModel.value = cloneTopic(draft)
      composerOpen.value = true
    }
  },
  { deep: true },
)

watch(
  () => props.createRequestKey,
  (key, oldKey) => {
    if (key === undefined || key === oldKey) {
      return
    }
    openCreate()
  },
)

watch(
  () => rows.value.map((row) => row.name).join(','),
  () => {
    const visible = new Set(rows.value.map((row) => row.name))
    selectedNames.value = selectedNames.value.filter((name) => visible.has(name))
  },
)

watch(
  [selectedNames, () => rows.value.map((row) => `${row.name}:${row.title}`).join(',')],
  () => {
    emit('selection-change', {
      count: selectedNames.value.length,
      names: [...selectedNames.value],
      titles: selectedTopicTitles(),
    })
  },
  { immediate: true },
)

defineExpose({
  openCreate,
  clearSelection,
})
</script>

<template>
  <ConsolePageScaffold class="topic-manager">
    <ConsoleStatsGrid>
      <ConsoleStatCard
        tone="mint"
        label="专题总数"
        :value="formatTopicNumber(remote ? effectiveTotal : stats.total)"
        :meta="`当前页 ${formatTopicNumber(rows.length)}`"
      >
        <template #icon>
          <IconClipboardLine />
        </template>
      </ConsoleStatCard>
      <ConsoleStatCard
        tone="green"
        label="已发布"
        :value="formatTopicNumber(stats.published)"
        :meta="remote ? '当前页公开专题' : '公开展示专题'"
      >
        <template #icon>
          <IconCheckboxCircle />
        </template>
      </ConsoleStatCard>
      <ConsoleStatCard
        tone="amber"
        label="草稿"
        :value="formatTopicNumber(stats.drafts)"
        :meta="remote ? '当前页未发布专题' : '待完善专题'"
      >
        <template #icon>
          <IconArrowDownCircleLine />
        </template>
      </ConsoleStatCard>
      <ConsoleStatCard
        tone="blue"
        label="关联软件"
        :value="formatTopicNumber(stats.linkedApps)"
        :meta="remote ? `当前页精选 ${formatTopicNumber(stats.featured)}` : `精选 ${formatTopicNumber(stats.featured)}`"
      >
        <template #icon>
          <IconLink />
        </template>
      </ConsoleStatCard>
    </ConsoleStatsGrid>

    <div class="topic-workspace">
      <ConsoleTableCard class="topic-table-panel">
        <template #header>
          <ConsoleToolbar>
            <template #left>
              <input
                class="toolbar-check"
                type="checkbox"
                :checked="allVisibleSelected"
                aria-label="选择当前页专题"
                @change="toggleAllVisible"
              />
              <ConsoleBulkActions :count="selectedNames.length" @action="emit('batch-delete')" />
              <label class="topic-search">
                <IconSearch />
                <input v-model.trim="keyword" placeholder="输入关键词搜索" type="search" />
              </label>
            </template>

            <template #right>
              <ConsoleFilterSelect
                v-model="selectedStatus"
                class="topic-select"
                :options="statusOptions"
                aria-label="筛选专题状态"
              />
              <ConsoleFilterSelect
                v-model="selectedSort"
                class="topic-select sort-select"
                :options="sortOptions"
                aria-label="专题排序"
              />
              <ConsoleTextButton v-if="hasFilters" @click="clearFilters"> 重置 </ConsoleTextButton>
              <ConsoleIconButton title="刷新" @click="emit('refresh')">
                <IconRefreshLine :class="{ spinning: loading }" />
              </ConsoleIconButton>
            </template>
          </ConsoleToolbar>
        </template>

        <VLoading v-if="loading" />
        <div v-else class="topic-table-wrap">
          <table class="topic-table">
            <thead>
              <tr>
                <th class="check-col"></th>
                <th>专题名称</th>
                <th>状态</th>
                <th>关联软件</th>
                <th>创建时间</th>
                <th class="actions-col">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!filteredRows.length" class="topic-empty-row">
                <td colspan="6">
                  <VEmpty :title="rows.length ? '没有匹配的专题' : '暂无专题'">
                    <template #actions>
                      <VButton v-if="rows.length" @click="clearFilters">重置筛选</VButton>
                      <VButton
                        v-else
                        class="topic-primary-action"
                        type="secondary"
                        @click="openCreate"
                      >
                        <template #icon>
                          <IconAddCircle />
                        </template>
                        新建专题
                      </VButton>
                    </template>
                  </VEmpty>
                </td>
              </tr>
              <tr v-for="row in pagedRows" :key="row.name">
                <td class="check-col" @click.stop>
                  <input
                    type="checkbox"
                    :checked="selectedNames.includes(row.name)"
                    @change="toggleRow(row, $event)"
                  />
                </td>
                <td>
                  <div class="topic-name-cell">
                    <span class="topic-cover" :class="row.tone">
                      <img v-if="row.cover" :src="row.cover" alt="" />
                      <span v-else>{{ row.initial }}</span>
                    </span>
                    <div>
                      <strong>{{ row.title }}</strong>
                      <small>/topics/{{ row.slug }} · {{ row.subtitle || row.name }}</small>
                    </div>
                  </div>
                </td>
                <td>
                  <span class="topic-status" :class="row.status.className">
                    <i></i>
                    {{ row.status.text }}
                  </span>
                </td>
                <td>{{ formatTopicNumber(row.appCount) }}</td>
                <td>{{ formatTopicDate(row.createdAt) }}</td>
                <td class="actions-col" @click.stop>
                  <VSpace>
                    <button
                      class="topic-row-action"
                      type="button"
                      title="编辑"
                      @click="openEdit(row)"
                    >
                      <IconRiPencilFill />
                    </button>
                    <button
                      class="topic-row-action danger"
                      type="button"
                      title="删除"
                      :disabled="deletingName === row.name"
                      @click="emit('delete', row.source)"
                    >
                      <IconDeleteBin />
                    </button>
                  </VSpace>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <template v-if="rows.length" #footer>
          <div class="topic-pagination">
            <span>共 {{ formatTopicNumber(effectiveTotal) }} 条</span>
            <VPagination
              v-model:page="page"
              v-model:size="size"
              :total="effectiveTotal"
              :size-options="[10, 20, 50]"
            />
          </div>
        </template>
      </ConsoleTableCard>
    </div>

    <div v-if="composerOpen" class="topic-modal-backdrop" @click.self="closeComposer">
      <section class="topic-modal-card" role="dialog" aria-modal="true" :aria-label="composerTitle">
        <button class="modal-close" type="button" title="关闭编辑" @click="closeComposer">
          <IconClose />
        </button>
        <div class="modal-heading">
          <strong>{{ composerTitle }}</strong>
          <span>维护专题基本信息、封面、关联软件和前台路径</span>
        </div>

        <div class="topic-form">
          <label class="topic-form-field full">
            <span>专题标题</span>
            <input v-model.trim="draftModel.spec.title" class="topic-field" />
          </label>
          <label class="topic-form-field">
            <span>公开 slug</span>
            <input v-model.trim="draftModel.spec.slug" class="topic-field" />
          </label>
          <label class="topic-form-field">
            <span>排序</span>
            <input v-model.number="draftModel.spec.priority" class="topic-field" type="number" />
          </label>
          <label class="topic-form-field full">
            <span>副标题</span>
            <input v-model.trim="draftModel.spec.subtitle" class="topic-field" />
          </label>
          <ConsoleImagePicker
            v-model="draftModel.spec.cover"
            class="topic-cover-picker"
            label="专题封面"
            description="用于专题列表和专题详情页展示。"
            empty-text="选择封面"
            preview-variant="cover"
          />
          <label class="switch-field">
            <VSwitch v-model="draftModel.spec.published" />
            <span>发布</span>
          </label>
          <label class="switch-field">
            <VSwitch v-model="draftModel.spec.featured" />
            <span>精选</span>
          </label>
          <label class="topic-form-field full">
            <span>描述</span>
            <textarea v-model.trim="draftModel.spec.description" class="topic-textarea" rows="5" />
          </label>
        </div>

        <section class="topic-app-section">
          <div class="topic-app-heading">
            <div>
              <strong>关联软件</strong>
              <span>搜索软件后加入专题，可为每个软件填写推荐理由并调整展示顺序。</span>
            </div>
            <small>{{ formatTopicNumber(selectedAppNames.length) }} 个软件</small>
          </div>
          <ConsoleTokenSelector
            :model-value="selectedAppNames"
            :options="appOptions"
            :loading="appsLoading"
            placeholder="输入软件名称搜索"
            empty-text="没有匹配的软件"
            @update:model-value="setTopicAppNames"
            @search="emit('search-apps', $event)"
          />
          <div v-if="draftModel.spec.items?.length" class="topic-app-list">
            <article
              v-for="(item, index) in draftModel.spec.items"
              :key="item.appName"
              class="topic-app-item"
            >
              <div class="topic-app-main">
                <span class="topic-app-order">{{ index + 1 }}</span>
                <div>
                  <strong>{{ topicAppLabel(item.appName) }}</strong>
                  <small>{{ topicAppDescription(item.appName) }}</small>
                </div>
              </div>
              <label class="topic-app-reason">
                <span>推荐理由</span>
                <input
                  :value="item.reason || ''"
                  class="topic-field"
                  placeholder="例如：适合设计师的素材管理工具"
                  @input="
                    updateTopicItem(item.appName, {
                      reason: ($event.target as HTMLInputElement).value,
                    })
                  "
                />
              </label>
              <label class="topic-app-priority">
                <span>排序</span>
                <input
                  :value="item.priority ?? 0"
                  class="topic-field"
                  type="number"
                  @input="
                    updateTopicItem(item.appName, {
                      priority: Number(($event.target as HTMLInputElement).value),
                    })
                  "
                />
              </label>
              <div class="topic-app-actions">
                <button
                  class="topic-row-action"
                  type="button"
                  title="上移"
                  :disabled="index === 0"
                  @click="moveTopicItem(index, -1)"
                >
                  <IconArrowUpLine />
                </button>
                <button
                  class="topic-row-action"
                  type="button"
                  title="下移"
                  :disabled="index === draftModel.spec.items!.length - 1"
                  @click="moveTopicItem(index, 1)"
                >
                  <IconArrowDownLine />
                </button>
                <button
                  class="topic-row-action danger"
                  type="button"
                  title="移除"
                  @click="removeTopicItem(item.appName)"
                >
                  <IconDeleteBin />
                </button>
              </div>
            </article>
          </div>
          <div v-else class="topic-app-empty">暂未添加软件</div>
        </section>

        <div class="modal-actions">
          <VButton @click="closeComposer">取消</VButton>
          <VButton
            class="topic-primary-action"
            type="secondary"
            :disabled="!canSaveDraft"
            :loading="saving"
            @click="saveDraft"
          >
            <template #icon>
              <IconSave />
            </template>
            保存专题
          </VButton>
        </div>
      </section>
    </div>
  </ConsolePageScaffold>
</template>

<style scoped lang="scss">
.topic-manager {
  --topic-accent: #2563eb;
  --topic-text: #152033;
  --topic-muted: #6d7a8c;
  --topic-line: #e3e9f1;
  --topic-soft-line: #edf2f7;

  position: relative;
}

.toolbar-check {
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
}

.topic-search,
.topic-field,
.topic-textarea {
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: #fff;
  color: var(--topic-text);
  font-size: 14px;
}

.topic-search,
.topic-field {
  height: 42px;
}

.topic-search {
  display: flex;
  align-items: center;
  gap: 10px;
  width: min(290px, 100%);
  min-width: 240px;
  padding: 0 13px;
  color: #8c98aa;
}

.topic-search input {
  width: 100%;
  min-width: 0;
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--topic-text);
  outline: none;
}

.topic-search svg,
.topic-row-action svg {
  width: 17px;
  height: 17px;
}

.topic-select {
  min-width: 122px;
}

.sort-select {
  min-width: 150px;
}

.topic-workspace {
  min-width: 0;
}

.topic-table-panel {
  overflow: hidden;
  min-width: 0;
}

.topic-table-wrap {
  overflow-x: auto;
}

.topic-table {
  width: 100%;
  min-width: 720px;
  border-collapse: collapse;
}

.topic-table th {
  height: 48px;
  border-bottom: 1px solid var(--topic-soft-line);
  padding: 0 14px;
  background: #fbfcfe;
  color: #627087;
  font-size: 13px;
  font-weight: 700;
  text-align: left;
  white-space: nowrap;
}

.topic-table td {
  height: 62px;
  border-bottom: 1px solid var(--topic-soft-line);
  padding: 0 14px;
  color: #46566d;
  font-size: 13px;
  vertical-align: middle;
}

.topic-table tbody tr {
  cursor: default;
}

.topic-table tbody tr:hover td,
.topic-table tbody tr.selected td {
  background: #fbfefd;
}

.topic-table tbody tr:last-child td {
  border-bottom: 0;
}

.topic-table th:nth-child(3),
.topic-table td:nth-child(3) {
  min-width: 82px;
}

.topic-table th:nth-child(4),
.topic-table td:nth-child(4) {
  width: 76px;
}

.topic-table th:nth-child(5),
.topic-table td:nth-child(5) {
  min-width: 128px;
  white-space: nowrap;
}

.topic-empty-row td {
  height: 270px;
  padding: 0;
  text-align: center;
}

.topic-empty-row:hover td {
  background: #fff;
}

.check-col {
  width: 38px;
}

.check-col input {
  width: 16px;
  height: 16px;
  border: 1px solid #cdd7e4;
  border-radius: 4px;
}

.topic-name-cell {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.topic-cover {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  overflow: hidden;
  border-radius: 8px;
  color: #fff;
  font-size: 17px;
  font-weight: 800;
}

.topic-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.topic-cover.mint {
  background: linear-gradient(135deg, #0fb981, #047857);
}

.topic-cover.blue {
  background: linear-gradient(135deg, #2775e8, #1644aa);
}

.topic-cover.violet {
  background: linear-gradient(135deg, #7c3aed, #4c1d95);
}

.topic-cover.amber {
  background: linear-gradient(135deg, #f59e0b, #b45309);
}

.topic-cover.rose {
  background: linear-gradient(135deg, #f43f5e, #be123c);
}

.topic-cover.slate {
  background: linear-gradient(135deg, #64748b, #1f2937);
}

.topic-name-cell strong,
.topic-name-cell small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topic-name-cell strong {
  color: #182235;
  font-weight: 700;
}

.topic-name-cell small {
  margin-top: 4px;
  color: #748196;
}

.topic-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 58px;
  border-radius: 999px;
  padding: 5px 9px;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
}

.topic-status i {
  width: 7px;
  height: 7px;
  border-radius: 999px;
}

.topic-status.published {
  background: #e8f8ef;
  color: #0b9c65;
}

.topic-status.published i {
  background: #22c878;
}

.topic-status.draft {
  background: #fff3df;
  color: #c76a10;
}

.topic-status.draft i {
  background: #f59e0b;
}

.actions-col {
  width: 82px;
  text-align: right;
}

.actions-col :deep(.space) {
  justify-content: flex-end;
}

.topic-row-action {
  display: inline-grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #506078;
  cursor: pointer;
}

.topic-row-action:hover {
  background: #eff6ff;
  color: var(--topic-accent);
}

.topic-row-action.danger:hover {
  background: #fff1f2;
  color: #e2463d;
}

.topic-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  border-top: 1px solid var(--topic-soft-line);
  padding: 13px 16px;
  color: var(--topic-muted);
  font-size: 13px;
}

.topic-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.36);
  backdrop-filter: blur(3px);
}

.topic-modal-card {
  position: relative;
  width: min(880px, calc(100vw - 48px));
  max-height: calc(100vh - 72px);
  overflow: auto;
  border: 1px solid var(--topic-line);
  border-radius: 10px;
  padding: 22px;
  background: #fff;
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.24);
}

.modal-close {
  position: absolute;
  top: 16px;
  right: 16px;
  display: inline-grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #526176;
  cursor: pointer;
}

.modal-close:hover {
  background: #f3f6fa;
}

.modal-heading {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding-right: 40px;
}

.modal-heading strong {
  color: #152033;
  font-size: 18px;
  line-height: 1.25;
}

.modal-heading span {
  color: var(--topic-muted);
  font-size: 13px;
  line-height: 1.5;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  border-top: 1px solid var(--topic-soft-line);
  margin-top: 18px;
  padding-top: 16px;
}

.topic-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  border-top: 1px solid var(--topic-soft-line);
  margin-top: 16px;
  padding-top: 14px;
}

.topic-form-field {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.topic-form-field.full {
  grid-column: 1 / -1;
}

.topic-cover-picker {
  grid-column: 1 / -1;
}

.topic-form-field span,
.switch-field span {
  color: #46566d;
  font-size: 12px;
  font-weight: 700;
}

.topic-field,
.topic-textarea {
  width: 100%;
  padding: 0 11px;
  outline: none;
}

.topic-textarea {
  min-height: 104px;
  padding: 10px 11px;
  resize: vertical;
}

.topic-field:focus,
.topic-textarea:focus {
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.switch-field {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 42px;
}

.topic-app-section {
  border-top: 1px solid var(--topic-soft-line);
  margin-top: 16px;
  padding-top: 16px;
}

.topic-app-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 12px;
}

.topic-app-heading div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.topic-app-heading strong {
  color: #152033;
  font-size: 14px;
  font-weight: 700;
}

.topic-app-heading span,
.topic-app-heading small {
  color: var(--topic-muted);
  font-size: 12px;
  line-height: 1.45;
}

.topic-app-heading small {
  flex: 0 0 auto;
}

.topic-app-list {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}

.topic-app-item {
  display: grid;
  grid-template-columns: minmax(170px, 1.1fr) minmax(220px, 1.4fr) 92px auto;
  gap: 10px;
  align-items: end;
  border: 1px solid #e5ebf3;
  border-radius: 8px;
  padding: 12px;
  background: #fbfcfe;
}

.topic-app-main {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  min-width: 0;
  padding-bottom: 3px;
}

.topic-app-order {
  display: inline-grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 7px;
  background: #eff6ff;
  color: var(--topic-accent);
  font-size: 12px;
  font-weight: 800;
}

.topic-app-main strong,
.topic-app-main small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topic-app-main strong {
  color: #152033;
  font-size: 13px;
  font-weight: 700;
}

.topic-app-main small {
  margin-top: 3px;
  color: var(--topic-muted);
  font-size: 12px;
}

.topic-app-reason,
.topic-app-priority {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.topic-app-reason span,
.topic-app-priority span {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.topic-app-actions {
  display: flex;
  gap: 4px;
  justify-content: flex-end;
  padding-bottom: 5px;
}

.topic-app-actions .topic-row-action:disabled {
  cursor: not-allowed;
  opacity: 0.38;
}

.topic-app-empty {
  display: grid;
  place-items: center;
  min-height: 72px;
  border: 1px dashed #dbe3ee;
  border-radius: 8px;
  margin-top: 12px;
  background: #fbfcfe;
  color: var(--topic-muted);
  font-size: 13px;
}

.spinning {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1280px) {
  .topic-search {
    flex: 1 1 260px;
    width: auto;
  }

  .topic-select {
    flex: 1 1 150px;
  }

  .sort-select {
    flex: 1 1 170px;
  }

  .topic-workspace {
    min-width: 0;
  }
}

@media (max-width: 640px) {
  .topic-pagination,
  .modal-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .topic-form {
    grid-template-columns: 1fr;
  }

  .topic-app-heading {
    flex-direction: column;
  }

  .topic-app-item {
    grid-template-columns: 1fr;
  }

  .topic-app-actions {
    justify-content: flex-start;
    padding-bottom: 0;
  }

  .topic-modal-backdrop {
    align-items: end;
    padding: 14px;
  }

  .topic-modal-card {
    width: 100%;
    max-height: calc(100vh - 28px);
    padding: 18px;
  }
}
</style>
