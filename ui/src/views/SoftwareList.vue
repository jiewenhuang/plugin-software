<script setup lang="ts">
import {
  Dialog,
  IconAddCircle,
  IconArrowDownCircleLine,
  IconCheckboxCircle,
  IconClipboardLine,
  IconDeleteBin,
  IconEye,
  IconGrid,
  IconListSettings,
  IconMore,
  IconRefreshLine,
  IconRiPencilFill,
  IconSearch,
  Toast,
  VButton,
  VDropdown,
  VDropdownItem,
  VEmpty,
  VLoading,
  VPageHeader,
  VPagination,
  VSpace,
  VStatusDot,
  VTag,
} from '@halo-dev/components'
import { RouterLink } from 'vue-router'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  deleteApp,
  getSoftwareAppStats,
  getTag,
  getSoftwareOptions,
  listApps,
  listCategories,
  type SoftwareApp,
  type SoftwareCategory,
  type SoftwareConsoleStats,
  type SoftwareOptions,
  type SoftwareTag,
} from '@/api/software'
import ConsoleFilterSelect, { type FilterSelectOption } from '@/components/ConsoleFilterSelect.vue'
import ConsoleIconButton from '@/components/console/ConsoleIconButton.vue'
import ConsolePageScaffold from '@/components/console/ConsolePageScaffold.vue'
import ConsoleStatCard from '@/components/console/ConsoleStatCard.vue'
import ConsoleStatsGrid from '@/components/console/ConsoleStatsGrid.vue'
import ConsoleTableCard from '@/components/console/ConsoleTableCard.vue'
import ConsoleTextButton from '@/components/console/ConsoleTextButton.vue'
import {
  defaultSoftwareOptions,
  ensureCurrentOption,
  normalizeSoftwareOptions,
  splitOptionText,
  toFilterOptions,
} from '@/options/software-options'
import {
  appSearchPlaceholder,
  buildSoftwareListQuery,
  categoryLabel as resolveCategoryLabel,
  formatDownloadCount,
  formatSoftwareDate,
  latestVersionText,
  hiddenTagCount,
  parseSoftwareListQuery,
  platformTokens,
  rowTagItems,
  softwareStatus,
} from './software-list-console'

const initialQueryState = parseSoftwareListQuery(window.location.search)
const page = ref(initialQueryState.page)
const size = ref(initialQueryState.size)
const keyword = ref(initialQueryState.keyword)
const published = ref(initialQueryState.published)
const category = ref(initialQueryState.category)
const platform = ref(initialQueryState.platform)
const apps = ref<SoftwareApp[]>([])
const appStats = ref<SoftwareConsoleStats>({
  total: 0,
  published: 0,
  drafts: 0,
  downloads: 0,
  platforms: [],
})
const categories = ref<SoftwareCategory[]>([])
const tagLookup = ref<Record<string, SoftwareTag>>({})
const softwareOptions = ref<SoftwareOptions>(defaultSoftwareOptions)
const total = ref(0)
const loading = ref(false)
const statsLoading = ref(false)
const categoriesLoading = ref(false)
const deletingName = ref('')
const bulkDeleting = ref(false)
const selectedNames = ref<string[]>([])
const failedTagNames = new Set<string>()
let fetchAppsSeq = 0
let fetchAppsTimer: number | undefined
let pendingFetchDelay: number | undefined

const platformFilterOptions = computed<FilterSelectOption[]>(() => [
  { label: '平台：全部', value: '' },
  ...Array.from(new Set(appStats.value.platforms.flatMap((item) => splitOptionText(item)))).reduce(
    (options, item) => ensureCurrentOption(options, item),
    toFilterOptions(softwareOptions.value.platforms),
  ),
])
const publishedOptions: FilterSelectOption[] = [
  { label: '状态：全部', value: '' },
  { label: '已发布', value: 'true' },
  { label: '草稿', value: 'false' },
]

const hasFilters = computed(() =>
  Boolean(keyword.value || published.value || category.value || platform.value),
)
const stats = computed(() => appStats.value)
const categoryOptions = computed<FilterSelectOption[]>(() => [
  { label: '分类：全部', value: '' },
  ...categories.value.map((item) => ({
    label: item.spec.displayName || item.metadata?.name || '未命名分类',
    value: item.metadata?.name || '',
    disabled: !item.metadata?.name,
  })),
])
const tagLookupItems = computed(() => Object.values(tagLookup.value))
const selectableNames = computed(() => apps.value.map(appName).filter(Boolean))
const selectedVisibleCount = computed(
  () => selectableNames.value.filter((name) => selectedNames.value.includes(name)).length,
)
const allVisibleSelected = computed(
  () =>
    Boolean(selectableNames.value.length) &&
    selectableNames.value.every((name) => selectedNames.value.includes(name)),
)
const selectedVisibleApps = computed(() =>
  apps.value.filter((app) => selectedNames.value.includes(appName(app))),
)
const selectedPreview = computed(() =>
  selectedVisibleApps.value
    .slice(0, 3)
    .map((app) => app.spec.displayName || app.metadata?.name)
    .filter(Boolean)
    .join('、'),
)

function editorRoute(name?: string) {
  return name ? { name: 'SoftwareEditor', query: { name } } : { name: 'SoftwareEditor' }
}

function appName(app: SoftwareApp) {
  return app.metadata?.name || ''
}

function appSubtitle(app: SoftwareApp) {
  return (
    app.spec.subtitle || app.spec.vendor || app.spec.summary || app.metadata?.name || '未设置简介'
  )
}

function appIconText(app: SoftwareApp) {
  return (app.spec.displayName || app.spec.slug || '软').slice(0, 1).toUpperCase()
}

function appCategoryLabel(app: SoftwareApp) {
  return resolveCategoryLabel(app.spec.categoryName, categories.value)
}

function rowUpdatedAt(app: SoftwareApp) {
  return formatSoftwareDate(
    app.status?.latestReleaseDate || app.spec.publishedAt || app.metadata?.creationTimestamp,
  )
}

function rowTags(app: SoftwareApp) {
  return rowTagItems(app, tagLookupItems.value)
}

function rowHiddenTagCount(app: SoftwareApp) {
  return hiddenTagCount(app)
}

function openPublicPage(app: SoftwareApp) {
  if (!app.spec.slug) {
    Toast.warning('该软件缺少公开 slug')
    return
  }
  window.open(`/soft/${app.spec.slug}`, '_blank')
}

async function fetchCategories() {
  categoriesLoading.value = true
  try {
    const result = await listCategories({ page: 1, size: 200 })
    categories.value = result.items
  } finally {
    categoriesLoading.value = false
  }
}

async function fetchVisibleTags(items: SoftwareApp[]) {
  const names = Array.from(
    new Set(items.flatMap((app) => (app.spec.tagNames || []).filter(Boolean).slice(0, 2))),
  ).filter((name) => !tagLookup.value[name] && !failedTagNames.has(name))

  if (!names.length) {
    return
  }

  const resolvedTags = await Promise.all(
    names.map((name) =>
      getTag(name).catch(() => {
        failedTagNames.add(name)
        return undefined
      }),
    ),
  )
  const nextLookup = { ...tagLookup.value }
  resolvedTags.forEach((tag) => {
    const name = tag?.metadata?.name
    if (name) {
      nextLookup[name] = tag
    }
  })
  tagLookup.value = nextLookup
}

async function fetchSoftwareOptions() {
  try {
    softwareOptions.value = normalizeSoftwareOptions(await getSoftwareOptions())
  } catch {
    softwareOptions.value = defaultSoftwareOptions
  }
}

function currentQueryState() {
  return {
    page: page.value,
    size: size.value,
    keyword: keyword.value,
    published: published.value,
    category: category.value,
    platform: platform.value,
  }
}

function replaceListUrl() {
  const nextUrl = `${window.location.pathname}${buildSoftwareListQuery(currentQueryState())}`
  const currentUrl = `${window.location.pathname}${window.location.search}`
  if (nextUrl !== currentUrl) {
    window.history.replaceState(null, '', nextUrl)
  }
}

function scheduleFetchApps(delay = 0) {
  if (fetchAppsTimer) {
    window.clearTimeout(fetchAppsTimer)
  }
  fetchAppsTimer = window.setTimeout(() => {
    fetchAppsTimer = undefined
    void fetchApps()
  }, delay)
}

async function fetchStats() {
  statsLoading.value = true
  try {
    appStats.value = await getSoftwareAppStats()
  } finally {
    statsLoading.value = false
  }
}

async function fetchApps() {
  const requestId = ++fetchAppsSeq
  loading.value = true
  try {
    const result = await listApps({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      published: published.value || undefined,
      category: category.value || undefined,
      platform: platform.value || undefined,
    })
    if (requestId !== fetchAppsSeq) {
      return
    }
    apps.value = result.items
    total.value = result.total
    await fetchVisibleTags(result.items)
    if (requestId !== fetchAppsSeq) {
      return
    }
    selectedNames.value = selectedNames.value.filter((name) =>
      result.items.some((app) => appName(app) === name),
    )
  } finally {
    if (requestId === fetchAppsSeq) {
      loading.value = false
    }
  }
}

function resetFilters() {
  keyword.value = ''
  published.value = ''
  category.value = ''
  platform.value = ''
  page.value = 1
}

function toggleAllVisible(event: Event) {
  const checked = (event.target as HTMLInputElement).checked
  if (checked) {
    selectedNames.value = Array.from(new Set([...selectedNames.value, ...selectableNames.value]))
    return
  }
  selectedNames.value = selectedNames.value.filter((name) => !selectableNames.value.includes(name))
}

function clearSelection() {
  selectedNames.value = []
}

function confirmDelete(app: SoftwareApp) {
  const name = app.metadata?.name
  if (!name) {
    return
  }
  Dialog.warning({
    title: '删除软件',
    description: `确定删除「${app.spec.displayName}」吗？删除前需要先移除关联版本和下载资源。`,
    confirmType: 'danger',
    onConfirm: async () => {
      deletingName.value = name
      try {
        await deleteApp(name)
        Toast.success('删除成功')
        await Promise.all([fetchApps(), fetchStats()])
      } finally {
        deletingName.value = ''
      }
    },
  })
}

function confirmBatchDelete() {
  const names = [...selectedNames.value]
  if (!names.length) {
    return
  }
  Dialog.warning({
    title: '批量删除软件',
    description: `确定删除已选 ${names.length} 个软件${selectedPreview.value ? `（${selectedPreview.value}${names.length > 3 ? ' 等' : ''}）` : ''}吗？删除前需要先移除关联版本和下载资源。`,
    confirmType: 'danger',
    onConfirm: async () => {
      bulkDeleting.value = true
      deletingName.value = '__app_batch__'
      try {
        await Promise.all(names.map((name) => deleteApp(name)))
        clearSelection()
        Toast.success(`已删除 ${names.length} 个软件`)
        await Promise.all([fetchApps(), fetchStats()])
      } finally {
        bulkDeleting.value = false
        deletingName.value = ''
      }
    },
  })
}

watch([page, size, published, category, platform, keyword], (current, previous) => {
  const keywordChanged = Boolean(previous && current[5] !== previous[5])
  const filtersChanged = Boolean(
    previous && current.slice(1).some((value, index) => value !== previous[index + 1]),
  )
  if (filtersChanged && current[0] !== 1) {
    pendingFetchDelay = keywordChanged ? 260 : 0
    page.value = 1
    return
  }

  replaceListUrl()
  scheduleFetchApps(pendingFetchDelay ?? (keywordChanged ? 260 : 0))
  pendingFetchDelay = undefined
})

onMounted(async () => {
  await Promise.all([fetchSoftwareOptions(), fetchCategories(), fetchStats(), fetchApps()])
})

onBeforeUnmount(() => {
  if (fetchAppsTimer) {
    window.clearTimeout(fetchAppsTimer)
  }
})
</script>

<template>
  <VPageHeader title="软件管理">
    <template #actions>
      <VSpace>
        <VButton
          v-permission="['plugin:software:manage']"
          class="software-primary-action"
          type="secondary"
          :route="editorRoute()"
        >
          <template #icon>
            <IconAddCircle />
          </template>
          新建软件
        </VButton>
        <VButton :route="{ name: 'SoftwareCategories' }">
          <template #icon>
            <IconListSettings />
          </template>
          分类管理
        </VButton>
      </VSpace>
    </template>
  </VPageHeader>

  <ConsolePageScaffold class="software-console">
    <ConsoleStatsGrid :class="{ 'is-loading': statsLoading }">
      <ConsoleStatCard tone="blue" label="软件总数" :value="formatDownloadCount(stats.total)">
        <template #icon>
          <IconGrid />
        </template>
      </ConsoleStatCard>
      <ConsoleStatCard tone="green" label="已发布" :value="formatDownloadCount(stats.published)">
        <template #icon>
          <IconCheckboxCircle />
        </template>
      </ConsoleStatCard>
      <ConsoleStatCard tone="amber" label="草稿" :value="formatDownloadCount(stats.drafts)">
        <template #icon>
          <IconClipboardLine />
        </template>
      </ConsoleStatCard>
      <ConsoleStatCard tone="violet" label="下载量" :value="formatDownloadCount(stats.downloads)">
        <template #icon>
          <IconArrowDownCircleLine />
        </template>
      </ConsoleStatCard>
    </ConsoleStatsGrid>

    <ConsoleTableCard>
      <div class="list-region">
        <div class="list-headbar" :class="{ 'has-selection': selectedVisibleCount }">
          <div class="list-headbar-main">
            <label class="list-select-all">
              <input
                :checked="allVisibleSelected"
                :disabled="loading || !selectableNames.length"
                type="checkbox"
                aria-label="选择当前页软件"
                @change="toggleAllVisible"
              />
            </label>
            <template v-if="selectedVisibleCount">
              <span class="selection-summary">已选 {{ selectedVisibleCount }} 项</span>
              <span class="list-headbar-meta">
                本页 {{ formatDownloadCount(apps.length) }} 个
                <template v-if="total !== apps.length"
                  >/ 共 {{ formatDownloadCount(total) }} 个</template
                >
              </span>
            </template>
            <template v-else>
              <span class="sr-only">软件筛选</span>
              <label class="search-control">
                <IconSearch />
                <input
                  v-model.trim="keyword"
                  :placeholder="appSearchPlaceholder()"
                  name="software-keyword"
                  type="search"
                  autocomplete="off"
                  aria-label="搜索软件"
                />
              </label>
            </template>
          </div>
          <div class="list-headbar-actions">
            <template v-if="selectedVisibleCount">
              <ConsoleTextButton :disabled="bulkDeleting" @click="clearSelection">
                取消选择
              </ConsoleTextButton>
              <ConsoleTextButton danger :disabled="bulkDeleting" @click="confirmBatchDelete">
                批量删除
              </ConsoleTextButton>
            </template>
            <template v-else>
              <ConsoleFilterSelect
                v-model="published"
                class="software-select status-select"
                :options="publishedOptions"
                aria-label="筛选状态"
              />
              <ConsoleFilterSelect
                v-model="category"
                class="software-select category-select"
                :options="categoryOptions"
                :disabled="categoriesLoading"
                aria-label="筛选分类"
              />
              <ConsoleFilterSelect
                v-model="platform"
                class="software-select platform-select"
                :options="platformFilterOptions"
                aria-label="筛选平台"
              />
              <ConsoleTextButton v-if="hasFilters" @click="resetFilters"> 重置 </ConsoleTextButton>
              <ConsoleIconButton title="刷新" @click="fetchApps">
                <IconRefreshLine :class="{ spinning: loading }" />
              </ConsoleIconButton>
            </template>
          </div>
        </div>

        <VLoading v-if="loading" />
        <VEmpty
          v-else-if="!apps.length"
          title="暂无软件"
          message="新建软件后，可以在这里维护版本、下载资源和公开状态。"
        >
          <template #actions>
            <VButton type="secondary" :route="editorRoute()">新建软件</VButton>
          </template>
        </VEmpty>

        <div v-else class="table-wrap">
          <table class="software-table">
            <tbody>
              <tr v-for="app in apps" :key="app.metadata?.name">
                <td class="check-col">
                  <input
                    v-model="selectedNames"
                    :value="appName(app)"
                    :disabled="!appName(app)"
                    type="checkbox"
                    :aria-label="`选择 ${app.spec.displayName}`"
                  />
                </td>
                <td>
                  <div class="app-cell">
                    <div class="app-icon">
                      <img
                        v-if="app.spec.icon"
                        :src="app.spec.icon"
                        :alt="app.spec.displayName"
                        width="44"
                        height="44"
                        loading="lazy"
                        decoding="async"
                      />
                      <span v-else>{{ appIconText(app) }}</span>
                    </div>
                    <div class="app-meta">
                      <div class="app-name-line">
                        <strong>{{ app.spec.displayName }}</strong>
                        <VTag v-if="app.spec.featured" theme="primary">精选</VTag>
                        <span class="version-chip">{{ latestVersionText(app) }}</span>
                      </div>
                      <div class="app-subtitle">{{ appSubtitle(app) }}</div>
                      <code>/soft/{{ app.spec.slug || 'unset' }}</code>
                    </div>
                  </div>
                </td>
                <td>
                  <div class="category-cell">
                    <span class="category-pill">{{ appCategoryLabel(app) }}</span>
                    <div v-if="rowTags(app).length" class="mini-tags">
                      <span v-for="tag in rowTags(app)" :key="tag.name" :title="tag.title">
                        #{{ tag.label }}
                      </span>
                      <span v-if="rowHiddenTagCount(app)" class="mini-tag-more">
                        +{{ rowHiddenTagCount(app) }}
                      </span>
                    </div>
                  </div>
                </td>
                <td>
                  <div class="platform-cell">
                    <span
                      v-for="token in platformTokens(app.spec.platform, softwareOptions.platforms)"
                      :key="`${app.metadata?.name}-${token.label}`"
                      class="platform-token"
                      :class="token.className"
                      :title="token.label"
                    >
                      {{ token.shortLabel }}
                    </span>
                  </div>
                </td>
                <td>
                  <div class="status-cell" :class="softwareStatus(app).className">
                    <VStatusDot
                      :state="softwareStatus(app).state"
                      :text="softwareStatus(app).text"
                    />
                    <VTag v-if="app.spec.recommended" theme="secondary">推荐</VTag>
                  </div>
                </td>
                <td class="number-cell">
                  {{ formatDownloadCount(app.status?.downloadCount) }}
                </td>
                <td class="date-cell">{{ rowUpdatedAt(app) }}</td>
                <td class="actions-col">
                  <div class="row-actions">
                    <RouterLink
                      class="row-action"
                      :to="editorRoute(app.metadata?.name)"
                      title="编辑"
                      aria-label="编辑"
                    >
                      <IconRiPencilFill />
                    </RouterLink>
                    <button
                      class="row-action"
                      type="button"
                      title="查看公开页"
                      aria-label="查看公开页"
                      :disabled="!app.spec.slug"
                      @click="openPublicPage(app)"
                    >
                      <IconEye />
                    </button>
                    <VDropdown v-permission="['plugin:software:manage']" placement="bottom-end">
                      <button
                        class="row-action"
                        type="button"
                        title="更多操作"
                        aria-label="更多操作"
                        :disabled="deletingName === app.metadata?.name"
                      >
                        <IconMore />
                      </button>
                      <template #popper>
                        <VDropdownItem
                          type="danger"
                          :disabled="deletingName === app.metadata?.name"
                          @click="confirmDelete(app)"
                        >
                          <template #prefix-icon>
                            <IconDeleteBin />
                          </template>
                          删除软件
                        </VDropdownItem>
                      </template>
                    </VDropdown>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <template #footer>
        <div class="pagination-footer">
          <span>共 {{ formatDownloadCount(total) }} 个软件</span>
          <VPagination
            v-model:page="page"
            v-model:size="size"
            :total="total"
            :size-options="[20, 50, 100]"
          />
        </div>
      </template>
    </ConsoleTableCard>
  </ConsolePageScaffold>
</template>

<style scoped lang="scss">
.software-console :deep(.console-stats-grid.is-loading) {
  opacity: 0.72;
}

.software-console :deep(.console-table-card-body) {
  padding: 0;
}

.search-control {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  width: min(292px, 100%);
  min-width: 250px;
  height: 42px;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  padding: 0 13px;
  background: #fff;
  color: #8c98aa;
}

.search-control input {
  width: 100%;
  min-width: 0;
  border: 0;
  outline: none;
  background: transparent;
  color: #0f172a;
  font-size: 14px;
}

.search-control svg,
.row-action svg {
  width: 17px;
  height: 17px;
}

.search-control input::placeholder {
  color: #94a3b8;
}

.software-select {
  min-width: 124px;
}

.category-select {
  min-width: 150px;
}

.platform-select,
.status-select {
  min-width: 122px;
}

.row-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
}

.row-action:focus-visible {
  outline: 2px solid #93c5fd;
  outline-offset: 2px;
}

.spinning {
  animation: spin 0.8s linear infinite;
}

.list-region {
  background: #fff;
}

.list-headbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 76px;
  border-bottom: 1px solid #e6edf5;
  padding: 16px 24px;
  background: #fff;
}

.list-headbar.has-selection {
  background: #f8fbff;
}

.list-headbar-main,
.list-headbar-actions,
.list-select-all {
  display: flex;
  align-items: center;
}

.list-headbar-main {
  gap: 14px;
  min-width: 0;
}

.list-headbar-actions {
  flex-wrap: wrap;
  gap: 10px 12px;
  flex: 0 0 auto;
  justify-content: flex-end;
}

.list-select-all {
  justify-content: center;
  width: 24px;
  height: 42px;
  color: #0f172a;
  cursor: pointer;
}

.list-select-all input {
  width: 16px;
  height: 16px;
  accent-color: #475569;
}

.selection-summary {
  color: #0f172a;
  font-size: 14px;
  font-weight: 650;
  white-space: nowrap;
}

.list-headbar-meta {
  overflow: hidden;
  color: #64748b;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table-wrap {
  overflow-x: auto;
}

.software-table {
  width: 100%;
  min-width: 1120px;
  border-collapse: collapse;
  background: #fff;
}

.software-table td {
  border-bottom: 1px solid #eef2f7;
  padding: 14px 16px;
  color: #334155;
  font-size: 13px;
  vertical-align: middle;
}

.software-table tr:hover td {
  background: #fbfdff;
}

.check-col {
  width: 46px;
  padding-right: 6px !important;
  text-align: center !important;
}

.check-col input {
  width: 15px;
  height: 15px;
  accent-color: #475569;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
}

.app-cell {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  min-width: 310px;
}

.app-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: linear-gradient(135deg, #f8fafc, #e2e8f0);
  color: #0f172a;
  font-weight: 700;
}

.app-icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.app-meta {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.app-name-line {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.app-name-line strong {
  overflow: hidden;
  color: #0f172a;
  font-size: 14px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-subtitle {
  overflow: hidden;
  max-width: 320px;
  color: #64748b;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-chip {
  flex: 0 0 auto;
  border-radius: 999px;
  padding: 2px 7px;
  background: #eef6ff;
  color: #2563eb;
  font-size: 12px;
  line-height: 18px;
}

code {
  width: fit-content;
  border-radius: 6px;
  padding: 2px 6px;
  background: #f1f5f9;
  color: #2563eb;
  font-size: 12px;
  line-height: 18px;
}

.category-cell,
.status-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
}

.category-pill {
  display: inline-flex;
  align-items: center;
  max-width: 138px;
  border-radius: 999px;
  padding: 4px 9px;
  background: #f1f5f9;
  color: #334155;
  font-size: 12px;
  font-weight: 600;
  line-height: 1;
}

.mini-tags {
  display: flex;
  max-width: 180px;
  flex-wrap: wrap;
  gap: 4px;
  font-size: 12px;
}

.mini-tags span {
  display: inline-flex;
  max-width: 132px;
  overflow: hidden;
  border-radius: 999px;
  padding: 2px 6px;
  background: #f8fafc;
  color: #64748b;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mini-tags .mini-tag-more {
  background: #eff6ff;
  color: #2563eb;
  font-weight: 650;
}

.platform-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 118px;
}

.platform-token {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 28px;
  border: 1px solid transparent;
  border-radius: 7px;
  padding: 0 6px;
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
}

.platform-token.macos {
  border-color: #c7d2fe;
  background: #eef2ff;
  color: #4f46e5;
}

.platform-token.windows {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #2563eb;
}

.platform-token.linux {
  border-color: #fde68a;
  background: #fffbeb;
  color: #b45309;
}

.platform-token.ios {
  border-color: #ddd6fe;
  background: #f5f3ff;
  color: #7c3aed;
}

.platform-token.android {
  border-color: #bbf7d0;
  background: #f0fdf4;
  color: #16a34a;
}

.platform-token.web {
  border-color: #bae6fd;
  background: #f0f9ff;
  color: #0284c7;
}

.platform-token.generic,
.platform-token.unknown {
  border-color: #e2e8f0;
  background: #f8fafc;
  color: #64748b;
}

.number-cell {
  color: #0f172a;
  font-weight: 650;
  white-space: nowrap;
}

.date-cell {
  color: #64748b;
  white-space: nowrap;
}

.actions-col {
  width: 138px;
  text-align: right !important;
}

.row-actions {
  display: flex;
  justify-content: flex-end;
  gap: 6px;
}

.row-action {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  color: #64748b;
}

.row-action:hover:not(:disabled) {
  background: #f1f5f9;
  color: #0f172a;
}

.row-action.danger:hover:not(:disabled) {
  background: #fef2f2;
  color: #dc2626;
}

.row-action:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.pagination-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  border-top: 1px solid #eef2f7;
  padding: 12px 16px;
  background: #fbfcfe;
  color: #64748b;
  font-size: 13px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 720px) {
  .list-headbar {
    align-items: stretch;
    flex-direction: column;
    padding: 14px 16px;
  }

  .list-headbar-main {
    align-items: center;
    width: 100%;
  }

  .list-headbar-actions {
    justify-content: flex-start;
  }

  .pagination-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .search-control,
  .software-select {
    width: 100%;
    min-width: 0;
  }
}
</style>
