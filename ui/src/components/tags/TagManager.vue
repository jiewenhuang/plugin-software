<script setup lang="ts">
import {
  IconAddCircle,
  IconCheckboxCircle,
  IconClipboardLine,
  IconClose,
  IconDeleteBin,
  IconLink,
  IconMore,
  IconRefreshLine,
  IconRiPencilFill,
  IconSave,
  IconSearch,
  IconStopCircle,
  VButton,
  VDropdown,
  VDropdownItem,
  VEmpty,
  VLoading,
  VPagination,
  VSwitch,
} from '@halo-dev/components'
import { computed, ref, watch } from 'vue'
import { emptyTag, type SoftwareTag } from '@/api/software'
import ConsoleFilterSelect, { type FilterSelectOption } from '@/components/ConsoleFilterSelect.vue'
import ConsoleBulkActions from '@/components/console/ConsoleBulkActions.vue'
import ConsoleIconButton from '@/components/console/ConsoleIconButton.vue'
import ConsolePageScaffold from '@/components/console/ConsolePageScaffold.vue'
import ConsoleStatCard from '@/components/console/ConsoleStatCard.vue'
import ConsoleStatsGrid from '@/components/console/ConsoleStatsGrid.vue'
import ConsoleTableCard from '@/components/console/ConsoleTableCard.vue'
import ConsoleTextButton from '@/components/console/ConsoleTextButton.vue'
import ConsoleToolbar from '@/components/console/ConsoleToolbar.vue'
import {
  buildTagRows,
  buildTagStats,
  filterTagRows,
  formatTagDate,
  formatTagNumber,
  normalizeTagColor,
  sortTagRows,
  type TagRow,
  type TagSortKey,
  type TagStatusFilter,
} from '@/views/tag-console'

const props = defineProps<{
  tags: SoftwareTag[]
  loading: boolean
  saving: boolean
  deletingName: string
  editingName: string
  draft: SoftwareTag
}>()

const emit = defineEmits<{
  refresh: []
  reset: []
  edit: [tag: SoftwareTag]
  delete: [tag: SoftwareTag]
  save: [tag: SoftwareTag]
  'selection-change': [selection: { count: number; names: string[]; titles: string[] }]
  'batch-delete': []
}>()

const page = ref(1)
const size = ref(20)
const keyword = ref('')
const selectedStatus = ref<TagStatusFilter>('')
const selectedSort = ref<TagSortKey>('usage-desc')
const selectedNames = ref<string[]>([])
const composerOpen = ref(false)
const draftModel = ref<SoftwareTag>(emptyTag())
const statusOptions: FilterSelectOption[] = [
  { label: '状态：全部', value: '' },
  { label: '状态：已使用', value: 'used' },
  { label: '状态：未使用', value: 'unused' },
  { label: '状态：隐藏', value: 'hidden' },
]
const sortOptions: FilterSelectOption[] = [
  { label: '使用次数 ↓', value: 'usage-desc' },
  { label: '使用次数 ↑', value: 'usage-asc' },
  { label: '创建时间 ↓', value: 'created-desc' },
  { label: '创建时间 ↑', value: 'created-asc' },
  { label: '标签名称 ↑', value: 'name-asc' },
]
const tagColorPresets = [
  { label: '蓝色', value: '#2563eb' },
  { label: '绿色', value: '#10a76f' },
  { label: '橙色', value: '#f97316' },
  { label: '粉色', value: '#ec4899' },
  { label: '紫色', value: '#7c3aed' },
  { label: '灰色', value: '#64748b' },
]

const rows = computed(() => buildTagRows(props.tags))
const stats = computed(() => buildTagStats(props.tags))
const filteredRows = computed(() =>
  sortTagRows(
    filterTagRows(rows.value, {
      keyword: keyword.value,
      status: selectedStatus.value,
    }),
    selectedSort.value,
  ),
)
const pagedRows = computed(() => {
  const start = (page.value - 1) * size.value
  return filteredRows.value.slice(start, start + size.value)
})
const allVisibleSelected = computed(
  () =>
    Boolean(pagedRows.value.length) &&
    pagedRows.value.every((row) => selectedNames.value.includes(row.name)),
)
const hasFilters = computed(() => Boolean(keyword.value || selectedStatus.value))
const hasValidDraftColor = computed(
  () => !draftModel.value.spec.color || Boolean(normalizeTagColor(draftModel.value.spec.color)),
)
const canSaveDraft = computed(() =>
  Boolean(
    draftModel.value.spec.displayName?.trim() &&
    draftModel.value.spec.slug?.trim() &&
    hasValidDraftColor.value,
  ),
)
const composerTitle = computed(() => (props.editingName ? '编辑标签' : '新建标签'))
const draftColorValue = computed(() => normalizeTagColor(draftModel.value.spec.color) || '#2563eb')

function cloneTag(tag: SoftwareTag) {
  return JSON.parse(JSON.stringify(tag)) as SoftwareTag
}

function openCreate() {
  emit('reset')
  draftModel.value = emptyTag()
  composerOpen.value = true
}

function openEdit(row: TagRow) {
  draftModel.value = cloneTag(row.source)
  composerOpen.value = true
  emit('edit', row.source)
}

function closeComposer() {
  composerOpen.value = false
  emit('reset')
}

function saveDraft() {
  if (!canSaveDraft.value) {
    return
  }
  const payload = cloneTag(draftModel.value)
  const color = normalizeTagColor(payload.spec.color)
  if (color) {
    payload.spec.color = color
  } else {
    delete payload.spec.color
  }
  emit('save', payload)
  if (!props.editingName) {
    composerOpen.value = false
  }
}

function setDraftColor(value: string) {
  const color = normalizeTagColor(value)
  draftModel.value.spec.color = color || value.trim()
}

function setDraftColorFromInput(event: Event) {
  setDraftColor((event.target as HTMLInputElement).value)
}

function clearDraftColor() {
  delete draftModel.value.spec.color
}

function colorVariableStyle(color?: string) {
  return color ? { '--tag-row-color': color } : undefined
}

function clearFilters() {
  keyword.value = ''
  selectedStatus.value = ''
  page.value = 1
}

function clearSelection() {
  selectedNames.value = []
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

function toggleRow(row: TagRow, event: Event) {
  const checked = (event.target as HTMLInputElement).checked
  if (checked) {
    selectedNames.value = Array.from(new Set([...selectedNames.value, row.name]))
    return
  }
  selectedNames.value = selectedNames.value.filter((name) => name !== row.name)
}

function selectedTagTitles() {
  const titleByName = new Map(rows.value.map((row) => [row.name, row.displayName]))
  return selectedNames.value.map((name) => titleByName.get(name) || name)
}

watch([keyword, selectedStatus, selectedSort, size], () => {
  page.value = 1
})

watch(
  () => props.draft,
  (draft) => {
    if (props.editingName) {
      draftModel.value = cloneTag(draft)
      composerOpen.value = true
    }
  },
  { deep: true },
)

watch(
  () => props.editingName,
  (editingName) => {
    if (!editingName) {
      composerOpen.value = false
    }
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
  [selectedNames, () => rows.value.map((row) => `${row.name}:${row.displayName}`).join(',')],
  () => {
    emit('selection-change', {
      count: selectedNames.value.length,
      names: [...selectedNames.value],
      titles: selectedTagTitles(),
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
  <ConsolePageScaffold class="tag-manager">
    <ConsoleStatsGrid>
      <ConsoleStatCard
        tone="violet"
        label="标签总数"
        :value="formatTagNumber(stats.total)"
        meta="所有标签的总数"
      >
        <template #icon>
          <IconLink />
        </template>
      </ConsoleStatCard>
      <ConsoleStatCard
        tone="green"
        label="已使用"
        :value="formatTagNumber(stats.used)"
        meta="已关联软件的标签"
      >
        <template #icon>
          <IconCheckboxCircle />
        </template>
      </ConsoleStatCard>
      <ConsoleStatCard
        tone="orange"
        label="未使用"
        :value="formatTagNumber(stats.unused)"
        meta="未关联软件的标签"
      >
        <template #icon>
          <IconStopCircle />
        </template>
      </ConsoleStatCard>
      <ConsoleStatCard
        tone="blue"
        label="关联软件数"
        :value="formatTagNumber(stats.linkedApps)"
        meta="标签关联的软件总数"
      >
        <template #icon>
          <IconClipboardLine />
        </template>
      </ConsoleStatCard>
    </ConsoleStatsGrid>

    <ConsoleTableCard class="tag-table-panel">
      <template #header>
        <ConsoleToolbar>
          <template #left>
            <input
              class="toolbar-check"
              type="checkbox"
              :checked="allVisibleSelected"
              aria-label="选择当前页标签"
              @change="toggleAllVisible"
            />
            <ConsoleBulkActions :count="selectedNames.length" @action="emit('batch-delete')" />
            <label class="tag-search">
              <IconSearch />
              <input v-model.trim="keyword" placeholder="搜索标签名称 / slug" type="search" />
            </label>
            <ConsoleFilterSelect
              v-model="selectedSort"
              class="tag-select sort-select"
              :options="sortOptions"
              aria-label="标签排序"
            />
            <ConsoleFilterSelect
              v-model="selectedStatus"
              class="tag-select status-select"
              :options="statusOptions"
              aria-label="筛选标签状态"
            />
          </template>
          <template #right>
            <ConsoleTextButton v-if="hasFilters" @click="clearFilters"> 重置 </ConsoleTextButton>
            <ConsoleIconButton title="刷新" @click="emit('refresh')">
              <IconRefreshLine :class="{ spinning: loading }" />
            </ConsoleIconButton>
          </template>
        </ConsoleToolbar>
      </template>

      <VLoading v-if="loading" />
      <div v-else class="tag-table-wrap">
        <table class="tag-table">
          <thead>
            <tr>
              <th class="check-col"></th>
              <th>标签名称</th>
              <th>Slug</th>
              <th>使用次数</th>
              <th>关联软件数</th>
              <th>状态</th>
              <th>创建时间</th>
              <th class="actions-col">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!filteredRows.length" class="tag-empty-row">
              <td colspan="8">
                <VEmpty :title="rows.length ? '没有匹配的标签' : '暂无标签'">
                  <template #actions>
                    <VButton v-if="rows.length" @click="clearFilters">重置筛选</VButton>
                    <VButton v-else class="tag-primary-action" type="secondary" @click="openCreate">
                      <template #icon>
                        <IconAddCircle />
                      </template>
                      新建标签
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
                <div class="tag-name-cell" :style="colorVariableStyle(row.colorValue)">
                  <span class="tag-dot" :class="row.colorClass"></span>
                  <strong>{{ row.displayName }}</strong>
                  <span class="tag-chip" :class="row.chipClass">{{
                    formatTagNumber(row.usageCount)
                  }}</span>
                </div>
              </td>
              <td class="slug-cell">{{ row.slug }}</td>
              <td>
                <span class="usage-count">{{ formatTagNumber(row.usageCount) }}</span>
              </td>
              <td>
                <button class="linked-count" type="button">
                  {{ formatTagNumber(row.linkedApps) }}
                </button>
              </td>
              <td>
                <span class="tag-status" :class="row.status.className">
                  {{ row.status.text }}
                </span>
              </td>
              <td class="date-cell">{{ formatTagDate(row.createdAt) }}</td>
              <td class="actions-col" @click.stop>
                <VDropdown placement="bottom-end">
                  <button class="tag-more-button" type="button" title="更多操作">
                    <IconMore />
                  </button>
                  <template #popper>
                    <VDropdownItem @click="openEdit(row)">
                      <template #prefix-icon>
                        <IconRiPencilFill />
                      </template>
                      编辑标签
                    </VDropdownItem>
                    <VDropdownItem
                      type="danger"
                      :disabled="deletingName === row.name"
                      @click="emit('delete', row.source)"
                    >
                      <template #prefix-icon>
                        <IconDeleteBin />
                      </template>
                      删除标签
                    </VDropdownItem>
                  </template>
                </VDropdown>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <template v-if="rows.length" #footer>
        <div class="tag-pagination">
          <span>共 {{ formatTagNumber(filteredRows.length) }} 个标签</span>
          <VPagination
            v-model:page="page"
            v-model:size="size"
            :total="filteredRows.length"
            :size-options="[10, 20, 50]"
          />
        </div>
      </template>
    </ConsoleTableCard>

    <div v-if="composerOpen" class="tag-modal-backdrop" @click.self="closeComposer">
      <section class="tag-modal-card" role="dialog" aria-modal="true" :aria-label="composerTitle">
        <button class="modal-close" type="button" title="关闭编辑" @click="closeComposer">
          <IconClose />
        </button>
        <div class="modal-heading">
          <strong>{{ composerTitle }}</strong>
          <span>维护标签名称、公开路径、颜色、可见状态和描述。</span>
        </div>

        <div class="tag-form">
          <label class="tag-form-field">
            <span>标签名称</span>
            <input v-model.trim="draftModel.spec.displayName" class="tag-field" />
          </label>
          <label class="tag-form-field">
            <span>公开 slug</span>
            <input v-model.trim="draftModel.spec.slug" class="tag-field" />
          </label>
          <div class="tag-form-field full">
            <span>标签颜色</span>
            <div class="tag-color-row">
              <label class="tag-color-picker" :style="colorVariableStyle(draftColorValue)">
                <input
                  class="tag-color-native"
                  type="color"
                  :value="draftColorValue"
                  aria-label="选择标签颜色"
                  @input="setDraftColorFromInput"
                />
              </label>
              <input
                v-model.trim="draftModel.spec.color"
                class="tag-field tag-color-text"
                placeholder="#2563eb"
              />
              <button class="tag-color-clear" type="button" @click="clearDraftColor">清除</button>
            </div>
            <div class="tag-color-presets" aria-label="常用标签颜色">
              <button
                v-for="preset in tagColorPresets"
                :key="preset.value"
                class="tag-color-preset"
                :class="{ active: normalizeTagColor(draftModel.spec.color) === preset.value }"
                :style="colorVariableStyle(preset.value)"
                type="button"
                :title="preset.label"
                :aria-label="preset.label"
                @click="setDraftColor(preset.value)"
              ></button>
            </div>
            <span v-if="!hasValidDraftColor" class="tag-color-error"
              >请输入 #RRGGBB 格式的颜色值</span
            >
          </div>
          <label class="tag-form-field">
            <span>排序</span>
            <input v-model.number="draftModel.spec.priority" class="tag-field" type="number" />
          </label>
          <label class="switch-field">
            <VSwitch v-model="draftModel.spec.visible" />
            <span>可见</span>
          </label>
          <label class="tag-form-field full">
            <span>描述</span>
            <textarea v-model.trim="draftModel.spec.description" class="tag-textarea" rows="4" />
          </label>
        </div>

        <div class="modal-actions">
          <VButton @click="closeComposer">取消</VButton>
          <VButton
            class="tag-primary-action"
            type="secondary"
            :disabled="!canSaveDraft"
            :loading="saving"
            @click="saveDraft"
          >
            <template #icon>
              <IconSave />
            </template>
            保存标签
          </VButton>
        </div>
      </section>
    </div>
  </ConsolePageScaffold>
</template>

<style scoped lang="scss">
.tag-manager {
  --tag-accent: #2563eb;
  --tag-text: #152033;
  --tag-muted: #6d7a8c;
  --tag-line: #e3e9f1;
  --tag-soft-line: #edf2f7;
}

.toolbar-check {
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
}

.tag-search,
.tag-field,
.tag-textarea {
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: #fff;
  color: var(--tag-text);
  font-size: 14px;
}

.tag-search,
.tag-field {
  height: 42px;
}

.tag-search {
  display: flex;
  align-items: center;
  gap: 10px;
  width: min(285px, 100%);
  min-width: 240px;
  padding: 0 13px;
  color: #8c98aa;
}

.tag-search input {
  width: 100%;
  min-width: 0;
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--tag-text);
  outline: none;
}

.tag-search svg,
.tag-more-button svg {
  width: 17px;
  height: 17px;
}

.tag-select {
  width: 160px;
}

.status-select {
  width: 146px;
}

.tag-table-wrap {
  overflow-x: auto;
  padding: 12px 16px 14px;
}

.tag-table {
  width: 100%;
  min-width: 1060px;
  border-collapse: collapse;
}

.tag-table th {
  height: 48px;
  border-bottom: 1px solid var(--tag-soft-line);
  padding: 0 14px;
  background: #fbfcfe;
  color: #627087;
  font-size: 13px;
  font-weight: 700;
  text-align: left;
  white-space: nowrap;
}

.tag-table td {
  height: 56px;
  border-bottom: 1px solid var(--tag-soft-line);
  padding: 0 14px;
  color: #46566d;
  font-size: 13px;
  vertical-align: middle;
}

.tag-table tbody tr:hover td {
  background: #fbfefd;
}

.tag-table tbody tr:last-child td {
  border-bottom: 0;
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

.tag-name-cell {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.tag-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  flex: 0 0 auto;
}

.tag-dot.violet {
  background: #7c3aed;
}

.tag-dot.emerald {
  background: #10a76f;
}

.tag-dot.orange {
  background: #f97316;
}

.tag-dot.blue {
  background: #2563eb;
}

.tag-dot.pink {
  background: #ec4899;
}

.tag-dot.slate {
  background: #7b8794;
}

.tag-dot.custom {
  background: var(--tag-row-color);
}

.tag-name-cell strong {
  overflow: hidden;
  color: #182235;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tag-chip {
  border-radius: 999px;
  padding: 3px 9px;
  background: #eef2ff;
  color: #6d4fd6;
  font-size: 12px;
  font-weight: 700;
}

.tag-chip.emerald {
  background: #dff8ea;
  color: #0b9c65;
}

.tag-chip.orange {
  background: #fff0dc;
  color: #d97706;
}

.tag-chip.blue {
  background: #e7f0ff;
  color: #2563eb;
}

.tag-chip.pink {
  background: #fde7f3;
  color: #db2777;
}

.tag-chip.slate,
.tag-chip.muted {
  background: #eef2f7;
  color: #69788f;
}

.tag-chip.custom {
  background: color-mix(in srgb, var(--tag-row-color) 14%, #fff);
  color: var(--tag-row-color);
}

.slug-cell,
.date-cell {
  color: #60708a;
}

.usage-count {
  color: #26364c;
  font-weight: 700;
}

.linked-count {
  border: 0;
  padding: 0;
  background: transparent;
  color: #1266d6;
  cursor: pointer;
  font-weight: 700;
}

.tag-status {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 5px 10px;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
}

.tag-status.used {
  background: #dff8ea;
  color: #0b9c65;
}

.tag-status.unused,
.tag-status.hidden {
  background: #eef2f7;
  color: #64748b;
}

.actions-col {
  width: 82px;
  text-align: right;
}

.tag-more-button {
  display: inline-grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #506078;
  cursor: pointer;
}

.tag-more-button:hover {
  background: #eefaf5;
  color: var(--tag-accent);
}

.tag-empty-row td {
  height: 270px;
  padding: 0;
  text-align: center;
}

.tag-empty-row:hover td {
  background: #fff;
}

.tag-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  border-top: 1px solid var(--tag-soft-line);
  padding: 13px 16px;
  color: var(--tag-muted);
  font-size: 13px;
}

.tag-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.36);
  backdrop-filter: blur(3px);
}

.tag-modal-card {
  position: relative;
  width: min(640px, calc(100vw - 48px));
  max-height: calc(100vh - 72px);
  overflow: auto;
  border: 1px solid var(--tag-line);
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
  color: var(--tag-muted);
  font-size: 13px;
  line-height: 1.5;
}

.tag-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  border-top: 1px solid var(--tag-soft-line);
  margin-top: 16px;
  padding-top: 14px;
}

.tag-form-field {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.tag-form-field.full {
  grid-column: 1 / -1;
}

.tag-form-field span,
.switch-field span {
  color: #46566d;
  font-size: 12px;
  font-weight: 700;
}

.tag-field,
.tag-textarea {
  width: 100%;
  padding: 0 11px;
  outline: none;
}

.tag-textarea {
  min-height: 104px;
  padding: 10px 11px;
  resize: vertical;
}

.tag-field:focus,
.tag-textarea:focus {
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.tag-color-row {
  display: grid;
  grid-template-columns: 46px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
}

.tag-color-picker {
  position: relative;
  display: inline-grid;
  place-items: center;
  width: 46px;
  height: 42px;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
}

.tag-color-picker::before,
.tag-color-preset::before {
  width: 24px;
  height: 24px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 999px;
  background: var(--tag-row-color);
  content: '';
}

.tag-color-native {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: pointer;
}

.tag-color-text {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
}

.tag-color-clear {
  height: 42px;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  padding: 0 12px;
  background: #fff;
  color: #526176;
  cursor: pointer;
  font-size: 13px;
  font-weight: 700;
}

.tag-color-clear:hover {
  border-color: #bfdbfe;
  color: var(--tag-accent);
}

.tag-color-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-color-preset {
  display: inline-grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
}

.tag-color-preset.active {
  border-color: var(--tag-row-color);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--tag-row-color) 16%, transparent);
}

.tag-color-error {
  color: #dc2626;
  font-size: 12px;
}

.switch-field {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 42px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  border-top: 1px solid var(--tag-soft-line);
  margin-top: 18px;
  padding-top: 16px;
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
  .tag-search {
    flex: 1 1 260px;
    width: auto;
  }

  .tag-select,
  .status-select {
    flex: 1 1 150px;
  }
}

@media (max-width: 640px) {
  .tag-pagination,
  .modal-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .tag-form {
    grid-template-columns: 1fr;
  }
}
</style>
