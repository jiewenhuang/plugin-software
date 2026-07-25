<script setup lang="ts">
import {
  IconAddCircle,
  IconArrowDownCircleLine,
  IconCheckboxCircle,
  IconClipboardLine,
  IconClose,
  IconDeleteBin,
  IconLink,
  IconRefreshLine,
  IconRiPencilFill,
  IconSearch,
  IconShieldUser,
  IconTimerLine,
  VButton,
  VEmpty,
  VLoading,
  VPagination,
  VSpace,
} from '@halo-dev/components'
import { computed, ref, watch } from 'vue'
import type { DownloadAsset, DownloadAssetStats, SoftwareApp } from '@/api/software'
import {
  defaultSoftwareOptions,
  ensureCurrentOption,
  toFilterOptions,
} from '@/options/software-options'
import type { SoftwareOptions } from '@/api/software'
import ConsoleFilterSelect, { type FilterSelectOption } from '@/components/ConsoleFilterSelect.vue'
import ConsoleBulkActions from '@/components/console/ConsoleBulkActions.vue'
import ConsoleIconButton from '@/components/console/ConsoleIconButton.vue'
import ConsolePageScaffold from '@/components/console/ConsolePageScaffold.vue'
import ConsoleStatCard from '@/components/console/ConsoleStatCard.vue'
import ConsoleStatsGrid from '@/components/console/ConsoleStatsGrid.vue'
import ConsoleTableCard from '@/components/console/ConsoleTableCard.vue'
import ConsoleTextButton from '@/components/console/ConsoleTextButton.vue'
import ConsoleToolbar from '@/components/console/ConsoleToolbar.vue'
import { useDebouncedValue } from '@/composables/useDebouncedValue'
import {
  buildDownloadAssetRows,
  buildDownloadAssetStats,
  filterDownloadAssetRows,
  formatAssetDate,
  formatDownloadNumber,
  sortDownloadAssetRows,
  type DownloadAssetRow,
  type DownloadAssetSortKey,
  type DownloadAssetStatusFilter,
} from '@/views/download-asset-console'

const props = defineProps<{
  assets: DownloadAsset[]
  apps: SoftwareApp[]
  options?: SoftwareOptions
  loading: boolean
  deletingName: string
  togglingName: string
  total?: number
  stats?: DownloadAssetStats
  remote?: boolean
}>()

const emit = defineEmits<{
  refresh: []
  copy: [asset: DownloadAsset]
  edit: [asset: DownloadAsset]
  delete: [asset: DownloadAsset]
  toggle: [asset: DownloadAsset, enabled: boolean]
  create: [appName?: string]
  'selection-change': [selection: { count: number; names: string[]; titles: string[] }]
  'batch-delete': []
  'query-change': [query: {
    page: number
    size: number
    keyword: string
    appName: string
    type: string
    status: DownloadAssetStatusFilter
    sort: DownloadAssetSortKey
  }]
}>()

const page = ref(1)
const size = ref(20)
const keyword = ref('')
const debouncedKeyword = useDebouncedValue(keyword)
const selectedAppName = ref('')
const selectedType = ref('')
const selectedStatus = ref<DownloadAssetStatusFilter>('')
const selectedSort = ref<DownloadAssetSortKey>('updated-desc')
const selectedNames = ref<string[]>([])
const selectedRowName = ref('')
const statusOptions: FilterSelectOption[] = [
  { label: '状态：全部', value: '' },
  { label: '状态：在线', value: 'enabled' },
  { label: '状态：失效', value: 'disabled' },
  { label: '状态：受保护', value: 'protected' },
]
const sortOptions: FilterSelectOption[] = [
  { label: '更新时间 ↓', value: 'updated-desc' },
  { label: '更新时间 ↑', value: 'updated-asc' },
  { label: '点击量 ↓', value: 'clicks-desc' },
  { label: '优先级 ↑', value: 'priority-asc' },
]

const optionSettings = computed(() => props.options || defaultSoftwareOptions)
const rows = computed(() => buildDownloadAssetRows(props.assets, props.apps, optionSettings.value))
const pageStats = computed(() => buildDownloadAssetStats(props.assets))
const stats = computed<DownloadAssetStats>(() =>
  props.stats || {
    ...pageStats.value,
    protectedCount: pageStats.value.protected,
  },
)
const appFilterOptions = computed<FilterSelectOption[]>(() => [
  { label: '全部软件', value: '' },
  ...props.apps.map((app) => ({
    label: appOptionLabel(app),
    value: app.metadata?.name || '',
    disabled: !app.metadata?.name,
  })),
])
const typeFilterOptions = computed<FilterSelectOption[]>(() => [
  { label: '全部类型', value: '' },
  ...Array.from(new Set(rows.value.map((row) => row.typeValue).filter(Boolean))).reduce(
    (options, type) => ensureCurrentOption(options, type),
    toFilterOptions(optionSettings.value.downloadTypes),
  ),
])
const filteredRows = computed(() =>
  props.remote
    ? rows.value
    : sortDownloadAssetRows(
        filterDownloadAssetRows(rows.value, {
          keyword: keyword.value,
          appName: selectedAppName.value,
          type: selectedType.value,
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
const selectedRow = computed(() => {
  if (!selectedRowName.value) {
    return undefined
  }
  return rows.value.find((row) => row.name === selectedRowName.value)
})
const selectedAsset = computed(() => selectedRow.value?.source)
const hasFilters = computed(() =>
  Boolean(keyword.value || selectedAppName.value || selectedType.value || selectedStatus.value),
)
const allVisibleSelected = computed(
  () => Boolean(pagedRows.value.length) && pagedRows.value.every((row) => selectedNames.value.includes(row.name)),
)

function selectRow(row: DownloadAssetRow) {
  selectedRowName.value = row.name
}

function appOptionLabel(app: SoftwareApp) {
  return app.spec.displayName || app.metadata?.name || '未命名软件'
}

function clearFilters() {
  keyword.value = ''
  selectedAppName.value = ''
  selectedType.value = ''
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

function toggleRow(row: DownloadAssetRow, event: Event) {
  const checked = (event.target as HTMLInputElement).checked
  if (checked) {
    selectedNames.value = Array.from(new Set([...selectedNames.value, row.name]))
    return
  }
  selectedNames.value = selectedNames.value.filter((name) => name !== row.name)
}

function statDelta(value: number) {
  if (!value) {
    return '0'
  }
  return `+${formatDownloadNumber(value)}`
}

function createForCurrentApp() {
  emit('create', selectedAppName.value || selectedRow.value?.appName)
}

function selectedAssetTitles() {
  const titleByName = new Map(rows.value.map((row) => [row.name, row.title]))
  return selectedNames.value.map((name) => titleByName.get(name) || name)
}

watch([debouncedKeyword, selectedAppName, selectedType, selectedStatus, selectedSort, size], () => {
  page.value = 1
})

watch(
  [page, size, debouncedKeyword, selectedAppName, selectedType, selectedStatus, selectedSort],
  () => {
    if (props.remote) {
      emit('query-change', {
        page: page.value,
        size: size.value,
        keyword: debouncedKeyword.value,
        appName: selectedAppName.value,
        type: selectedType.value,
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
  () => pagedRows.value.map((row) => row.name).join(','),
  () => {
    const visible = new Set(rows.value.map((row) => row.name))
    selectedNames.value = selectedNames.value.filter((name) => visible.has(name))
    if (selectedRowName.value && !visible.has(selectedRowName.value)) {
      selectedRowName.value = ''
    }
  },
)

watch(
  [selectedNames, () => rows.value.map((row) => `${row.name}:${row.title}`).join(',')],
  () => {
    emit('selection-change', {
      count: selectedNames.value.length,
      names: [...selectedNames.value],
      titles: selectedAssetTitles(),
    })
  },
  { immediate: true },
)

defineExpose({
  createForCurrentApp,
  clearSelection,
})
</script>

<template>
  <ConsolePageScaffold class="download-manager">
    <ConsoleStatsGrid>
      <ConsoleStatCard
        tone="mint"
        label="总链接数"
        :value="formatDownloadNumber(stats.total)"
        :meta="remote ? `当前页 ${formatDownloadNumber(rows.length)}` : `当前筛选 ${formatDownloadNumber(filteredRows.length)}`"
      >
        <template #icon>
          <IconLink />
        </template>
      </ConsoleStatCard>
      <ConsoleStatCard
        tone="green"
        label="在线中"
        :value="formatDownloadNumber(stats.enabled)"
        :meta="`启用 +${formatDownloadNumber(stats.enabled)}`"
      >
        <template #icon>
          <IconCheckboxCircle />
        </template>
      </ConsoleStatCard>
      <ConsoleStatCard
        tone="orange"
        label="已失效"
        :value="formatDownloadNumber(stats.disabled)"
        :meta="`需处理 ${formatDownloadNumber(stats.disabled)}`"
      >
        <template #icon>
          <IconTimerLine />
        </template>
      </ConsoleStatCard>
      <ConsoleStatCard
        tone="blue"
        label="累计点击"
        :value="formatDownloadNumber(stats.downloads)"
        :meta="`本页 ${statDelta(filteredRows.reduce((sum, row) => sum + row.downloadCount, 0))}`"
      >
        <template #icon>
          <IconArrowDownCircleLine />
        </template>
      </ConsoleStatCard>
    </ConsoleStatsGrid>

    <div class="download-workspace" :class="{ 'without-detail': !selectedRow }">
      <ConsoleTableCard class="download-table-panel">
        <template #header>
          <ConsoleToolbar>
            <template #left>
              <input
                class="toolbar-check"
                type="checkbox"
                :checked="allVisibleSelected"
                aria-label="选择当前页下载源"
                @change="toggleAllVisible"
              />
              <ConsoleBulkActions :count="selectedNames.length" @action="emit('batch-delete')" />
              <label class="download-search">
                <IconSearch />
                <input
                  v-model.trim="keyword"
                  name="download-asset-keyword"
                  placeholder="搜索软件 / 链接 / URL"
                  type="search"
                  autocomplete="off"
                  aria-label="搜索下载源"
                />
              </label>
            </template>

            <template #right>
              <ConsoleFilterSelect
                v-model="selectedAppName"
                class="download-select app-select"
                :options="appFilterOptions"
                aria-label="筛选软件"
              />
              <ConsoleFilterSelect
                v-model="selectedType"
                class="download-select"
                :options="typeFilterOptions"
                aria-label="筛选下载类型"
              />
              <ConsoleFilterSelect
                v-model="selectedStatus"
                class="download-select"
                :options="statusOptions"
                aria-label="筛选下载源状态"
              />
              <ConsoleFilterSelect
                v-model="selectedSort"
                class="download-select sort-select"
                :options="sortOptions"
                aria-label="下载源排序"
              />
              <ConsoleTextButton v-if="hasFilters" @click="clearFilters">
                重置
              </ConsoleTextButton>
              <ConsoleIconButton title="刷新" @click="emit('refresh')">
                <IconRefreshLine :class="{ spinning: loading }" />
              </ConsoleIconButton>
            </template>
          </ConsoleToolbar>
        </template>

        <VLoading v-if="loading" />
        <div v-else class="download-table-wrap">
          <table class="download-table">
            <thead>
              <tr>
                <th class="check-col"></th>
                <th>软件名称</th>
                <th>下载版本</th>
                <th>平台</th>
                <th>下载地址</th>
                <th>状态</th>
                <th>点击量</th>
                <th>更新时间</th>
                <th class="actions-col">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!filteredRows.length" class="download-empty-row">
                <td colspan="9">
                  <VEmpty :title="effectiveTotal ? '没有匹配的下载链接' : '暂无下载链接'">
                    <template #actions>
                      <VButton v-if="effectiveTotal" @click="clearFilters">重置筛选</VButton>
                      <VButton
                        v-else
                        class="download-primary-action"
                        type="secondary"
                        @click="createForCurrentApp"
                      >
                        <template #icon>
                          <IconAddCircle />
                        </template>
                        新建链接
                      </VButton>
                    </template>
                  </VEmpty>
                </td>
              </tr>
              <tr
                v-for="row in pagedRows"
                :key="row.name"
                :class="{ selected: selectedRow?.name === row.name }"
                tabindex="0"
                :aria-selected="selectedRow?.name === row.name"
                :aria-label="`查看 ${row.appLabel} ${row.title} 的下载源详情`"
                @click="selectRow(row)"
                @keydown.enter.prevent="selectRow(row)"
                @keydown.space.prevent="selectRow(row)"
              >
                <td class="check-col" @click.stop>
                  <input
                    type="checkbox"
                    :checked="selectedNames.includes(row.name)"
                    :aria-label="`选择 ${row.appLabel} ${row.title}`"
                    @change="toggleRow(row, $event)"
                  />
                </td>
                <td>
                  <div class="asset-app-cell">
                    <span class="asset-icon">
                      <img
                        v-if="row.appIcon"
                        :src="row.appIcon"
                        alt=""
                        width="28"
                        height="28"
                        loading="lazy"
                        decoding="async"
                      />
                      <span v-else>{{ row.appInitial }}</span>
                    </span>
                    <div>
                      <strong>{{ row.appLabel }}</strong>
                      <small>{{ row.title }}</small>
                    </div>
                  </div>
                </td>
                <td>{{ row.versionName }}</td>
                <td>
                  <div class="platform-list">
                    <span
                      v-for="platform in row.platforms"
                      :key="`${row.name}-${platform.label}`"
                      class="platform-token"
                      :class="platform.className"
                    >
                      {{ platform.shortLabel }}
                    </span>
                  </div>
                </td>
                <td>
                  <button
                    class="asset-url"
                    type="button"
                    title="复制下载地址"
                    aria-label="复制下载地址"
                    @click.stop="emit('copy', row.source)"
                  >
                    {{ row.displayUrl }}
                  </button>
                </td>
                <td>
                  <span class="download-status" :class="row.status.className">
                    <i></i>
                    {{ row.status.text }}
                  </span>
                </td>
                <td>{{ formatDownloadNumber(row.downloadCount) }}</td>
                <td>{{ formatAssetDate(row.updatedAt) }}</td>
                <td class="actions-col" @click.stop>
                  <VSpace>
                    <button
                      class="download-row-action"
                      type="button"
                      title="复制"
                      aria-label="复制下载源"
                      @click="emit('copy', row.source)"
                    >
                      <IconClipboardLine />
                    </button>
                    <button
                      class="download-row-action"
                      type="button"
                      title="编辑"
                      aria-label="编辑下载源"
                      @click="emit('edit', row.source)"
                    >
                      <IconRiPencilFill />
                    </button>
                    <button
                      class="download-row-action danger"
                      type="button"
                      title="删除"
                      aria-label="删除下载源"
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

        <template v-if="effectiveTotal" #footer>
          <div class="download-pagination">
            <span>共 {{ formatDownloadNumber(effectiveTotal) }} 条</span>
            <VPagination
              v-model:page="page"
              v-model:size="size"
              :total="effectiveTotal"
              :size-options="[10, 20, 50]"
          />
        </div>
      </template>
      </ConsoleTableCard>

      <aside v-if="selectedRow" class="download-detail-panel">
        <button
          class="detail-close"
          type="button"
          title="关闭详情"
          aria-label="关闭详情"
          @click="selectedRowName = ''"
        >
          <IconClose />
        </button>
        <div class="detail-heading">
          <strong>{{ selectedRow.appLabel }}</strong>
          <span>{{ selectedRow.versionName }} · {{ selectedRow.typeLabel }}</span>
        </div>
        <span class="download-status detail-status" :class="selectedRow.status.className">
          <i></i>
          {{ selectedRow.status.text }}
        </span>

        <div class="detail-block">
          <small>原始地址</small>
          <div class="detail-copy-row">
            <span>{{ selectedRow.displayUrl }}</span>
            <button
              type="button"
              title="复制原始地址"
              aria-label="复制原始地址"
              @click="emit('copy', selectedRow.source)"
            >
              <IconClipboardLine />
            </button>
          </div>
        </div>

        <div class="detail-block">
          <small>访问保护</small>
          <div class="detail-line">
            <IconShieldUser />
            <span>{{ selectedRow.protectionText }}</span>
          </div>
        </div>

        <div class="detail-block two-col">
          <div>
            <small>创建时间</small>
            <strong>{{ formatAssetDate(selectedRow.source.metadata?.creationTimestamp) }}</strong>
          </div>
          <div>
            <small>更新时间</small>
            <strong>{{ formatAssetDate(selectedRow.updatedAt) }}</strong>
          </div>
        </div>

        <div class="detail-actions">
          <VButton :disabled="!selectedAsset" @click="selectedAsset && emit('edit', selectedAsset)">
            <template #icon>
              <IconRiPencilFill />
            </template>
            编辑
          </VButton>
          <VButton
            type="secondary"
            :disabled="!selectedAsset"
            @click="selectedAsset && emit('toggle', selectedAsset, selectedAsset.spec.enabled === false)"
          >
            {{ selectedAsset?.spec.enabled === false ? '设为在线' : '设为失效' }}
          </VButton>
        </div>
      </aside>
    </div>
  </ConsolePageScaffold>
</template>

<style scoped lang="scss">
.download-manager {
  --download-accent: #2563eb;
  --download-text: #152033;
  --download-muted: #6d7a8c;
  --download-line: #e3e9f1;
  --download-soft-line: #edf2f7;

  position: relative;
}

.toolbar-check {
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
}

.download-search {
  height: 42px;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: #fff;
  color: var(--download-text);
  font-size: 14px;
}

.download-search {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 0 13px;
  color: #8c98aa;
}

.download-search input {
  width: 100%;
  min-width: 0;
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--download-text);
  outline: none;
}

.download-search svg,
.download-row-action svg,
.detail-copy-row svg,
.detail-line svg {
  width: 17px;
  height: 17px;
}

.download-select {
  min-width: 130px;
}

.app-select {
  min-width: 152px;
}

.sort-select {
  min-width: 150px;
}

.download-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(282px, 310px);
  gap: 16px;
  align-items: start;
}

.download-workspace.without-detail {
  grid-template-columns: minmax(0, 1fr);
}

.download-table-panel,
.download-detail-panel {
  border: 1px solid var(--download-line);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 10px 28px rgba(18, 30, 45, 0.035);
}

.download-table-panel {
  overflow: hidden;
  min-width: 0;
}

.download-table-wrap {
  overflow-x: auto;
  padding: 12px 16px;
}

.download-table {
  width: 100%;
  min-width: 1060px;
  border-collapse: collapse;
}

.download-table th {
  height: 48px;
  border-bottom: 1px solid var(--download-soft-line);
  padding: 0 14px;
  background: #fbfcfe;
  color: #627087;
  font-size: 13px;
  font-weight: 700;
  text-align: left;
  white-space: nowrap;
}

.download-table td {
  height: 59px;
  border-bottom: 1px solid var(--download-soft-line);
  padding: 0 14px;
  color: #46566d;
  font-size: 13px;
  vertical-align: middle;
}

.download-table tbody tr {
  cursor: pointer;
}

.download-table tbody tr:hover td,
.download-table tbody tr.selected td {
  background: #fbfefd;
}

.download-table tbody tr:focus-visible {
  outline: none;
}

.download-table tbody tr:focus-visible td {
  background: #f7fbff;
  box-shadow: inset 0 1px 0 #bfdbfe, inset 0 -1px 0 #bfdbfe;
}

.download-table tbody tr:last-child td {
  border-bottom: 0;
}

.download-empty-row td {
  height: 270px;
  padding: 0;
  text-align: center;
}

.download-empty-row:hover td {
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

.asset-app-cell {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  align-items: center;
  gap: 11px;
  min-width: 0;
}

.asset-icon {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  overflow: hidden;
  border-radius: 7px;
  background: linear-gradient(135deg, #1e75d8, #083f96);
  color: #fff;
  font-size: 14px;
  font-weight: 800;
}

.asset-icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.asset-app-cell strong,
.asset-app-cell small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.asset-app-cell strong {
  color: #182235;
  font-weight: 700;
}

.asset-app-cell small {
  margin-top: 3px;
  color: #748196;
}

.platform-list {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
}

.platform-token {
  min-width: 28px;
  border-radius: 6px;
  padding: 3px 6px;
  background: #edf2f7;
  color: #69788f;
  font-size: 10px;
  font-weight: 800;
  line-height: 1;
  text-align: center;
}

.platform-token.windows {
  color: #64748b;
}

.platform-token.macos,
.platform-token.ios {
  color: #4b5563;
}

.platform-token.linux {
  color: #334155;
}

.asset-url {
  display: block;
  max-width: 270px;
  overflow: hidden;
  border: 0;
  padding: 0;
  background: transparent;
  color: #1266d6;
  font-size: 13px;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.asset-url:hover {
  text-decoration: underline;
}

.asset-url:focus-visible,
.download-row-action:focus-visible,
.detail-close:focus-visible,
.detail-copy-row button:focus-visible {
  outline: 2px solid #93c5fd;
  outline-offset: 2px;
}

.download-status {
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

.download-status i {
  width: 7px;
  height: 7px;
  border-radius: 999px;
}

.download-status.online {
  background: #e8f8ef;
  color: #0b9c65;
}

.download-status.online i {
  background: #22c878;
}

.download-status.failed {
  background: #ffeceb;
  color: #e2463d;
}

.download-status.failed i {
  background: #ff5a52;
}

.actions-col {
  width: 118px;
  text-align: right;
}

.actions-col :deep(.space) {
  justify-content: flex-end;
}

.download-row-action {
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

.download-row-action:hover {
  background: #eefaf5;
  color: var(--download-accent);
}

.download-row-action.danger:hover {
  background: #fff1f2;
  color: #e2463d;
}

.download-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  border-top: 1px solid var(--download-soft-line);
  padding: 13px 16px;
  color: var(--download-muted);
  font-size: 13px;
}

.download-detail-panel {
  position: relative;
  min-height: 540px;
  padding: 18px 18px 16px;
}

.detail-close {
  position: absolute;
  top: 16px;
  right: 14px;
  display: inline-grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #526176;
  cursor: pointer;
}

.detail-close:hover {
  background: #f3f6fa;
}

.detail-heading {
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding-right: 32px;
}

.detail-heading strong {
  color: #152033;
  font-size: 15px;
}

.detail-heading span,
.detail-muted {
  color: var(--download-muted);
  font-size: 12px;
}

.detail-status {
  margin-top: 12px;
}

.detail-block {
  border-top: 1px solid var(--download-soft-line);
  margin-top: 16px;
  padding-top: 14px;
}

.detail-block small {
  display: block;
  margin-bottom: 8px;
  color: #46566d;
  font-size: 12px;
  font-weight: 700;
}

.detail-copy-row,
.detail-line {
  display: flex;
  align-items: center;
  gap: 10px;
}

.detail-copy-row {
  justify-content: space-between;
}

.detail-copy-row span {
  overflow: hidden;
  color: #4b5d76;
  font-size: 12px;
  line-height: 1.5;
  text-overflow: ellipsis;
}

.detail-copy-row button {
  display: inline-grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 1px solid #dbe3ee;
  border-radius: 6px;
  background: #fff;
  color: #607088;
  cursor: pointer;
}

.detail-copy-row button:hover {
  border-color: #bfdbfe;
  color: var(--download-accent);
}

.detail-line {
  color: #4d5f78;
  font-size: 13px;
}

.two-col {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.two-col strong {
  display: block;
  color: #41516a;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.5;
}

.detail-actions {
  display: flex;
  gap: 10px;
  border-top: 1px solid var(--download-soft-line);
  margin-top: 16px;
  padding-top: 14px;
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
  .download-search {
    flex: 1 1 250px;
  }

  .download-select,
  .app-select {
    flex: 1 1 142px;
  }

  .sort-select {
    flex: 1 1 165px;
  }

  .download-workspace {
    grid-template-columns: 1fr;
  }

  .download-detail-panel {
    min-height: 0;
  }
}

@media (max-width: 640px) {
  .two-col {
    grid-template-columns: 1fr;
  }

  .download-pagination,
  .detail-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
