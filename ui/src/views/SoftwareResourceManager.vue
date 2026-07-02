<script setup lang="ts">
import {
  Dialog,
  IconAddCircle,
  IconArrowDownCircleLine,
  IconCheckboxCircle,
  IconClipboardLine,
  IconClose,
  IconDeleteBin,
  IconGrid,
  IconLink,
  IconListSettings,
  IconRefreshLine,
  IconRiPencilFill,
  IconSave,
  IconSearch,
  Toast,
  VButton,
  VCard,
  VEmpty,
  VLoading,
  VPageHeader,
  VPagination,
  VSpace,
  VStatusDot,
  VSwitch,
  VTag,
} from '@halo-dev/components'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  createCategory,
  createTag,
  createTopic,
  deleteCategory,
  deleteDownloadAsset,
  deleteTag,
  deleteTopic,
  emptyCategory,
  emptyTag,
  emptyTopic,
  getSoftwareOptions,
  listApps,
  listCategories,
  listDownloadAssets,
  listTags,
  listTopics,
  updateCategory,
  updateDownloadAsset,
  updateTag,
  updateTopic,
  type DownloadAsset,
  type ListResult,
  type SoftwareApp,
  type SoftwareCategory,
  type SoftwareOptions,
  type SoftwareTag,
  type SoftwareTopic,
} from '@/api/software'
import ConsoleFilterSelect, { type FilterSelectOption } from '@/components/ConsoleFilterSelect.vue'
import ConsoleIconButton from '@/components/console/ConsoleIconButton.vue'
import ConsolePageScaffold from '@/components/console/ConsolePageScaffold.vue'
import ConsoleStatCard from '@/components/console/ConsoleStatCard.vue'
import ConsoleStatsGrid from '@/components/console/ConsoleStatsGrid.vue'
import ConsoleTableCard from '@/components/console/ConsoleTableCard.vue'
import ConsoleTextButton from '@/components/console/ConsoleTextButton.vue'
import ConsoleToolbar from '@/components/console/ConsoleToolbar.vue'
import DownloadAssetManager from '@/components/downloads/DownloadAssetManager.vue'
import TagManager from '@/components/tags/TagManager.vue'
import TopicManager from '@/components/topics/TopicManager.vue'
import { defaultSoftwareOptions, normalizeSoftwareOptions } from '@/options/software-options'
import {
  buildCategoryRows,
  buildCategoryStats,
  buildCategoryTree,
  categoryMatchesFilters,
  formatCategoryDate,
  sortCategoryRows,
  type CategoryTreeNode,
  type CategorySortKey,
  type CategoryStatusFilter,
} from './category-console'
import { downloadAssetListParams } from './download-asset-console'
import { tagListParams } from './tag-console'
import { topicListParams } from './topic-console'

type ResourceType = 'categories' | 'tags' | 'topics' | 'downloads'
type ResourceItem = SoftwareCategory | SoftwareTag | SoftwareTopic
type CategoryDropPosition = 'before' | 'inside' | 'after'
type TopicSelection = {
  count: number
  names: string[]
  titles: string[]
}
type DownloadSelection = {
  count: number
  names: string[]
  titles: string[]
}
type TagSelection = {
  count: number
  names: string[]
  titles: string[]
}

const route = useRoute()
const router = useRouter()
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const items = ref<ResourceItem[]>([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const categorySortSaving = ref(false)
const deletingName = ref('')
const editingName = ref('')
const categoryComposerOpen = ref(false)
const categoryDraft = ref<SoftwareCategory>(emptyCategory())
const draggingCategoryName = ref('')
const categoryDropTargetName = ref('')
const categoryDropPosition = ref<CategoryDropPosition>('after')
const tagDraft = ref<SoftwareTag>(emptyTag())
const topicDraft = ref<SoftwareTopic>(emptyTopic())
const topicCreateRequestKey = ref(0)
const softwareOptions = ref<SoftwareOptions>(defaultSoftwareOptions)
const apps = ref<SoftwareApp[]>([])
const appsLoading = ref(false)
const topicManagerRef = ref<InstanceType<typeof TopicManager> | null>(null)
const downloadManagerRef = ref<InstanceType<typeof DownloadAssetManager> | null>(null)
const tagManagerRef = ref<InstanceType<typeof TagManager> | null>(null)
const topicSelection = ref<TopicSelection>({
  count: 0,
  names: [],
  titles: [],
})
const downloadSelection = ref<DownloadSelection>({
  count: 0,
  names: [],
  titles: [],
})
const tagSelection = ref<TagSelection>({
  count: 0,
  names: [],
  titles: [],
})
const selectedCategoryParent = ref('')
const selectedCategoryStatus = ref<CategoryStatusFilter>('')
const selectedCategorySort = ref<CategorySortKey>('created-desc')
const downloadAssets = ref<DownloadAsset[]>([])
const downloadsLoading = ref(false)
const togglingAssetName = ref('')
let appsSearchSeq = 0

const routeTypeMap: Record<string, ResourceType> = {
  SoftwareCategories: 'categories',
  SoftwareTags: 'tags',
  SoftwareTopics: 'topics',
  SoftwareDownloadAssets: 'downloads',
}

const activeType = computed<ResourceType>(() => routeTypeMap[String(route.name)] || 'categories')
const isCategoryView = computed(() => activeType.value === 'categories')
const currentLabel = computed(() => {
  if (activeType.value === 'categories') {
    return '分类管理'
  }
  if (activeType.value === 'tags') {
    return '标签管理'
  }
  if (activeType.value === 'topics') {
    return '专题管理'
  }
  return '下载源管理'
})
const hasFilters = computed(() => Boolean(keyword.value))
const hasCategoryFilters = computed(() =>
  Boolean(keyword.value || selectedCategoryParent.value || selectedCategoryStatus.value),
)
const isEditing = computed(() => Boolean(editingName.value))
const canSaveResource = computed(() => ['categories', 'tags', 'topics'].includes(activeType.value))
const categoryItems = computed(() => items.value as SoftwareCategory[])
const tagItems = computed(() => items.value as SoftwareTag[])
const topicItems = computed(() => items.value as SoftwareTopic[])
const categoryRows = computed(() => buildCategoryRows(categoryItems.value))
const categoryStats = computed(() => buildCategoryStats(categoryItems.value))
const categoryTree = computed(() => buildCategoryTree(categoryItems.value))
const categoryTreeItems = computed(() => flattenCategoryTree(categoryTree.value))
const categoryParentOptions = computed(() =>
  categoryRows.value
    .filter((row) => !row.parentName)
    .sort((left, right) => left.displayName.localeCompare(right.displayName)),
)
const categoryParentFilterOptions = computed<FilterSelectOption[]>(() => [
  { label: '全部分类', value: '' },
  ...categoryParentOptions.value.map((item) => ({
    label: item.displayName,
    value: item.name,
  })),
])
const categoryStatusOptions: FilterSelectOption[] = [
  { label: '全部状态', value: '' },
  { label: '启用', value: 'enabled' },
  { label: '停用', value: 'disabled' },
]
const categorySortOptions: FilterSelectOption[] = [
  { label: '创建时间 ↓', value: 'created-desc' },
  { label: '创建时间 ↑', value: 'created-asc' },
  { label: '优先级 ↓', value: 'priority-desc' },
  { label: '优先级 ↑', value: 'priority-asc' },
]
const filteredCategoryRows = computed(() => {
  const filtered = categoryRows.value.filter((row) =>
    categoryMatchesFilters(row, {
      keyword: keyword.value,
      parentName: selectedCategoryParent.value,
      status: selectedCategoryStatus.value,
    }),
  )
  return sortCategoryRows(filtered, selectedCategorySort.value)
})
const pagedCategoryRows = computed(() => {
  const start = (page.value - 1) * size.value
  return filteredCategoryRows.value.slice(start, start + size.value)
})
const categoryComposerTitle = computed(() => (isEditing.value ? '编辑分类' : '新建分类'))
const canSaveCategoryDraft = computed(() =>
  Boolean(categoryDraft.value.spec.displayName?.trim() && categoryDraft.value.spec.slug?.trim()),
)

function resourceName(item: ResourceItem) {
  return item.metadata?.name || ''
}

function resourceSlug(item: ResourceItem) {
  return item.spec.slug
}

function resourceTitle(item: ResourceItem) {
  return 'title' in item.spec ? item.spec.title : item.spec.displayName
}

function resourceDescription(item: ResourceItem) {
  return item.spec.description || ('subtitle' in item.spec ? item.spec.subtitle : '')
}

function resourcePriority(item: ResourceItem) {
  return item.spec.priority ?? 0
}

function resourceVisible(item: ResourceItem) {
  if (activeType.value === 'topics') {
    return (item as SoftwareTopic).spec.published
  }
  return (item as SoftwareCategory | SoftwareTag).spec.visible
}

function resourceCount(item: ResourceItem) {
  return item.status?.appCount || 0
}

function flattenCategoryTree(nodes: CategoryTreeNode[]): CategoryTreeNode[] {
  return nodes.flatMap((node) => [node, ...flattenCategoryTree(node.children)])
}

function categoryParentLabel(parentName: string) {
  if (!parentName) {
    return '全部分类'
  }
  return categoryRows.value.find((row) => row.name === parentName)?.displayName || parentName
}

function categoryStatusText(visible: boolean) {
  return visible ? '启用' : '停用'
}

function categoryStatusState(visible: boolean) {
  return visible ? 'success' : 'default'
}

function categoryIndent(depth: number) {
  return `${depth * 20}px`
}

function categoryRowByName(name: string) {
  return categoryRows.value.find((row) => row.name === name)
}

function isCategoryDescendant(targetName: string, ancestorName: string) {
  if (!targetName || !ancestorName) {
    return false
  }
  const rowsByName = new Map(categoryRows.value.map((row) => [row.name, row]))
  let current = rowsByName.get(targetName)
  while (current?.parentName) {
    if (current.parentName === ancestorName) {
      return true
    }
    current = rowsByName.get(current.parentName)
  }
  return false
}

function isCategoryParentOptionDisabled(row: CategoryTreeNode | { name: string }) {
  return Boolean(
    editingName.value &&
    (row.name === editingName.value || isCategoryDescendant(row.name, editingName.value)),
  )
}

function cloneResource<T extends ResourceItem>(item: T): T {
  return JSON.parse(JSON.stringify(item))
}

function cloneCategory(category: SoftwareCategory) {
  return JSON.parse(JSON.stringify(category)) as SoftwareCategory
}

function categoryPayload(category: SoftwareCategory) {
  const payload = cloneCategory(category)
  payload.spec.parentName = payload.spec.parentName || undefined
  return payload
}

function resetDraft() {
  editingName.value = ''
  categoryComposerOpen.value = false
  categoryDraft.value = emptyCategory()
  tagDraft.value = emptyTag()
  topicDraft.value = emptyTopic()
}

function normalizeListResult(result: ListResult<ResourceItem>) {
  items.value = result.items
  total.value = result.total
}

async function fetchResources() {
  if (!canSaveResource.value) {
    return
  }
  loading.value = true
  try {
    if (activeType.value === 'categories') {
      normalizeListResult(
        (await listCategories({ page: 1, size: 1000 })) as ListResult<ResourceItem>,
      )
      return
    }
    if (activeType.value === 'tags') {
      normalizeListResult((await listTags(tagListParams())) as ListResult<ResourceItem>)
      return
    }
    normalizeListResult((await listTopics(topicListParams())) as ListResult<ResourceItem>)
  } finally {
    loading.value = false
  }
}

async function fetchAppsForResource(keyword = '') {
  const seq = ++appsSearchSeq
  appsLoading.value = true
  try {
    const result = await listApps({
      page: 1,
      size: 50,
      keyword: keyword || undefined,
    })
    if (seq === appsSearchSeq) {
      apps.value = result.items
    }
  } finally {
    if (seq === appsSearchSeq) {
      appsLoading.value = false
    }
  }
}

async function fetchSoftwareOptions() {
  try {
    softwareOptions.value = normalizeSoftwareOptions(await getSoftwareOptions())
  } catch {
    softwareOptions.value = defaultSoftwareOptions
  }
}

async function fetchDownloadAssets() {
  downloadsLoading.value = true
  try {
    const result = await listDownloadAssets(downloadAssetListParams())
    downloadAssets.value = result.items
  } finally {
    downloadsLoading.value = false
  }
}

async function refreshActiveView() {
  if (activeType.value === 'downloads') {
    await Promise.all([fetchSoftwareOptions(), fetchAppsForResource(), fetchDownloadAssets()])
    return
  }
  if (activeType.value === 'topics') {
    await Promise.all([fetchResources(), fetchAppsForResource()])
    return
  }
  if (canSaveResource.value) {
    await fetchResources()
  }
}

function clearFilters() {
  keyword.value = ''
  selectedCategoryParent.value = ''
  selectedCategoryStatus.value = ''
  selectedCategorySort.value = 'created-desc'
  page.value = 1
  if (!isCategoryView.value) {
    fetchResources()
  }
}

function editResource(item: ResourceItem) {
  editingName.value = resourceName(item)
  if (activeType.value === 'categories') {
    categoryDraft.value = cloneResource(item as SoftwareCategory)
    categoryComposerOpen.value = true
    return
  }
  if (activeType.value === 'tags') {
    tagDraft.value = cloneResource(item as SoftwareTag)
    return
  }
  topicDraft.value = cloneResource(item as SoftwareTopic)
}

function startCategoryCreate() {
  editingName.value = ''
  categoryDraft.value = emptyCategory()
  categoryComposerOpen.value = true
}

function closeCategoryComposer() {
  if (saving.value) {
    return
  }
  resetDraft()
}

function startTopicCreate() {
  topicCreateRequestKey.value += 1
}

function startTagCreate() {
  tagManagerRef.value?.openCreate()
}

function updateTopicSelection(selection: TopicSelection) {
  topicSelection.value = selection
}

function updateDownloadSelection(selection: DownloadSelection) {
  downloadSelection.value = selection
}

function updateTagSelection(selection: TagSelection) {
  tagSelection.value = selection
}

async function saveCategory() {
  if (!categoryDraft.value.spec.displayName || !categoryDraft.value.spec.slug) {
    Toast.warning('分类名称和 slug 必填')
    return false
  }
  if (
    categoryDraft.value.spec.parentName &&
    editingName.value &&
    (categoryDraft.value.spec.parentName === editingName.value ||
      isCategoryDescendant(categoryDraft.value.spec.parentName, editingName.value))
  ) {
    Toast.warning('不能将分类设置为自身或子分类的下级')
    return false
  }
  const payload = categoryPayload(categoryDraft.value)
  if (editingName.value) {
    await updateCategory(editingName.value, payload)
  } else {
    await createCategory(payload)
  }
  return true
}

async function saveTag() {
  if (!tagDraft.value.spec.displayName || !tagDraft.value.spec.slug) {
    Toast.warning('标签名称和 slug 必填')
    return false
  }
  const payload = tagDraft.value
  if (editingName.value) {
    await updateTag(editingName.value, payload)
  } else {
    await createTag(payload)
  }
  return true
}

async function saveTopic(topic = topicDraft.value) {
  if (!topic.spec.title || !topic.spec.slug) {
    Toast.warning('专题标题和 slug 必填')
    return false
  }
  if (editingName.value) {
    await updateTopic(editingName.value, topic)
  } else {
    await createTopic(topic)
  }
  return true
}

async function saveResource() {
  if (!canSaveResource.value) {
    return
  }
  saving.value = true
  try {
    if (activeType.value === 'categories') {
      const saved = await saveCategory()
      if (!saved) {
        return
      }
    } else if (activeType.value === 'tags') {
      const saved = await saveTag()
      if (!saved) {
        return
      }
    } else {
      const saved = await saveTopic()
      if (!saved) {
        return
      }
    }
    Toast.success('保存成功')
    resetDraft()
    await fetchResources()
  } finally {
    saving.value = false
  }
}

function startCategoryDrag(node: CategoryTreeNode, event: DragEvent) {
  if (categorySortSaving.value) {
    event.preventDefault()
    return
  }
  draggingCategoryName.value = node.name
  categoryDropTargetName.value = ''
  event.dataTransfer?.setData('text/plain', node.name)
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

function canDropCategoryOn(targetName: string) {
  if (!draggingCategoryName.value || draggingCategoryName.value === targetName) {
    return false
  }
  return !isCategoryDescendant(targetName, draggingCategoryName.value)
}

function handleCategoryDragOver(node: CategoryTreeNode, event: DragEvent) {
  if (!canDropCategoryOn(node.name)) {
    return
  }
  event.preventDefault()
  const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const offsetY = event.clientY - bounds.top
  if (offsetY < bounds.height * 0.28) {
    categoryDropPosition.value = 'before'
  } else if (offsetY > bounds.height * 0.72) {
    categoryDropPosition.value = 'after'
  } else {
    categoryDropPosition.value = 'inside'
  }
  categoryDropTargetName.value = node.name
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

function clearCategoryDragState() {
  draggingCategoryName.value = ''
  categoryDropTargetName.value = ''
  categoryDropPosition.value = 'after'
}

async function dropCategoryOn(node: CategoryTreeNode, event: DragEvent) {
  event.preventDefault()
  if (!canDropCategoryOn(node.name)) {
    clearCategoryDragState()
    return
  }

  const draggedRow = categoryRowByName(draggingCategoryName.value)
  const targetRow = categoryRowByName(node.name)
  if (!draggedRow || !targetRow) {
    clearCategoryDragState()
    return
  }

  const targetParentName =
    categoryDropPosition.value === 'inside' ? targetRow.name : targetRow.parentName || ''
  const siblings = sortCategoryRows(
    categoryRows.value.filter(
      (row) => (row.parentName || '') === targetParentName && row.name !== draggedRow.name,
    ),
    'priority-desc',
  )
  const targetIndex =
    categoryDropPosition.value === 'inside'
      ? 0
      : siblings.findIndex((row) => row.name === targetRow.name)
  if (targetIndex < 0) {
    clearCategoryDragState()
    return
  }

  const nextRows = [...siblings]
  const insertIndex =
    categoryDropPosition.value === 'inside'
      ? 0
      : categoryDropPosition.value === 'before'
        ? targetIndex
        : targetIndex + 1
  nextRows.splice(insertIndex, 0, {
    ...draggedRow,
    parentName: targetParentName,
  })

  categorySortSaving.value = true
  try {
    await Promise.all(
      nextRows.map((row, index) => {
        const priority = (nextRows.length - index) * 10
        const source = cloneCategory(row.source)
        source.spec.parentName =
          row.name === draggedRow.name ? targetParentName || undefined : row.parentName || undefined
        source.spec.priority = priority
        return updateCategory(row.name, source)
      }),
    )
    Toast.success(categoryDropPosition.value === 'inside' ? '分类层级已更新' : '分类顺序已更新')
    await fetchResources()
  } catch {
    Toast.warning('分类顺序更新失败，请稍后重试')
  } finally {
    categorySortSaving.value = false
    clearCategoryDragState()
  }
}

async function saveTopicFromManager(topic: SoftwareTopic) {
  saving.value = true
  try {
    topicDraft.value = topic
    const saved = await saveTopic(topic)
    if (!saved) {
      return
    }
    Toast.success('保存成功')
    resetDraft()
    await fetchResources()
  } finally {
    saving.value = false
  }
}

async function saveTagFromManager(tag: SoftwareTag) {
  saving.value = true
  try {
    tagDraft.value = tag
    const saved = await saveTag()
    if (!saved) {
      return
    }
    Toast.success('保存成功')
    resetDraft()
    await fetchResources()
  } finally {
    saving.value = false
  }
}

function confirmDelete(item: ResourceItem) {
  const name = resourceName(item)
  if (!name) {
    return
  }
  Dialog.warning({
    title: `删除${currentLabel.value}`,
    description: `确定删除「${resourceTitle(item)}」吗？`,
    confirmType: 'danger',
    onConfirm: async () => {
      deletingName.value = name
      try {
        if (activeType.value === 'categories') {
          await deleteCategory(name)
        } else if (activeType.value === 'tags') {
          await deleteTag(name)
        } else {
          await deleteTopic(name)
        }
        if (editingName.value === name) {
          resetDraft()
        }
        Toast.success('删除成功')
        await fetchResources()
      } finally {
        deletingName.value = ''
      }
    },
  })
}

function confirmBatchDeleteTopics() {
  const names = [...topicSelection.value.names]
  if (!names.length) {
    return
  }
  const preview = topicSelection.value.titles.slice(0, 3).join('、')
  Dialog.warning({
    title: '批量删除专题',
    description: `确定删除已选 ${names.length} 个专题${preview ? `（${preview}${names.length > 3 ? ' 等' : ''}）` : ''}吗？`,
    confirmType: 'danger',
    onConfirm: async () => {
      deletingName.value = '__topic_batch__'
      try {
        await Promise.all(names.map((name) => deleteTopic(name)))
        topicManagerRef.value?.clearSelection()
        resetDraft()
        Toast.success(`已删除 ${names.length} 个专题`)
        await fetchResources()
      } finally {
        deletingName.value = ''
      }
    },
  })
}

function confirmBatchDeleteTags() {
  const names = [...tagSelection.value.names]
  if (!names.length) {
    return
  }
  const preview = tagSelection.value.titles.slice(0, 3).join('、')
  Dialog.warning({
    title: '批量删除标签',
    description: `确定删除已选 ${names.length} 个标签${preview ? `（${preview}${names.length > 3 ? ' 等' : ''}）` : ''}吗？`,
    confirmType: 'danger',
    onConfirm: async () => {
      deletingName.value = '__tag_batch__'
      try {
        await Promise.all(names.map((name) => deleteTag(name)))
        tagManagerRef.value?.clearSelection()
        resetDraft()
        Toast.success(`已删除 ${names.length} 个标签`)
        await fetchResources()
      } finally {
        deletingName.value = ''
      }
    },
  })
}

function startDownloadCreate() {
  if (downloadManagerRef.value) {
    downloadManagerRef.value.createForCurrentApp()
    return
  }
  createDownloadAsset()
}

function confirmBatchDeleteDownloads() {
  const names = [...downloadSelection.value.names]
  if (!names.length) {
    return
  }
  const preview = downloadSelection.value.titles.slice(0, 3).join('、')
  Dialog.warning({
    title: '批量删除下载源',
    description: `确定删除已选 ${names.length} 个下载源${preview ? `（${preview}${names.length > 3 ? ' 等' : ''}）` : ''}吗？`,
    confirmType: 'danger',
    onConfirm: async () => {
      deletingName.value = '__download_batch__'
      try {
        await Promise.all(names.map((name) => deleteDownloadAsset(name)))
        downloadManagerRef.value?.clearSelection()
        Toast.success(`已删除 ${names.length} 个下载源`)
        await fetchDownloadAssets()
      } finally {
        deletingName.value = ''
      }
    },
  })
}

function formatNumber(value?: number) {
  return new Intl.NumberFormat('zh-CN').format(value || 0)
}

async function copyAssetUrl(asset: DownloadAsset) {
  if (!asset.spec.url) {
    Toast.warning('该下载源未设置 URL')
    return
  }
  await navigator.clipboard.writeText(asset.spec.url)
  Toast.success('下载地址已复制')
}

async function setAssetEnabled(asset: DownloadAsset, enabled: boolean) {
  const name = asset.metadata?.name
  if (!name) {
    return
  }
  togglingAssetName.value = name
  try {
    await updateDownloadAsset(name, {
      ...asset,
      spec: {
        ...asset.spec,
        enabled,
      },
    })
    await fetchDownloadAssets()
  } finally {
    togglingAssetName.value = ''
  }
}

function confirmDeleteAsset(asset: DownloadAsset) {
  const name = asset.metadata?.name
  if (!name) {
    return
  }
  Dialog.warning({
    title: '删除下载源',
    description: `确定删除「${asset.spec.title}」吗？`,
    confirmType: 'danger',
    onConfirm: async () => {
      deletingName.value = name
      try {
        await deleteDownloadAsset(name)
        Toast.success('删除成功')
        await fetchDownloadAssets()
      } finally {
        deletingName.value = ''
      }
    },
  })
}

watch(keyword, () => {
  if (activeType.value === 'downloads') {
    fetchDownloadAssets()
    return
  }
  if (isCategoryView.value) {
    page.value = 1
    return
  }
})

watch([selectedCategoryParent, selectedCategoryStatus, selectedCategorySort], () => {
  page.value = 1
})

watch(
  () => route.name,
  async () => {
    page.value = 1
    keyword.value = ''
    selectedCategoryParent.value = ''
    selectedCategoryStatus.value = ''
    selectedCategorySort.value = 'created-desc'
    items.value = []
    total.value = 0
    resetDraft()
    await refreshActiveView()
  },
)

function editDownloadAsset(asset: DownloadAsset) {
  void router.push({
    name: 'SoftwareEditor',
    query: {
      name: asset.spec.appName || undefined,
      tab: 'downloads',
    },
  })
}

function createDownloadAsset(appName?: string) {
  void router.push({
    name: 'SoftwareEditor',
    query: {
      name: appName || undefined,
      tab: 'downloads',
    },
  })
}

onMounted(refreshActiveView)
</script>

<template>
  <VPageHeader :title="currentLabel">
    <template #actions>
      <VSpace>
        <VButton
          v-if="isCategoryView"
          class="category-primary-action"
          type="secondary"
          @click="startCategoryCreate"
        >
          <template #icon>
            <IconAddCircle />
          </template>
          新建分类
        </VButton>
        <template v-else-if="activeType === 'tags'">
          <VButton class="console-header-primary-action" type="secondary" @click="startTagCreate">
            <template #icon>
              <IconAddCircle />
            </template>
            新建标签
          </VButton>
        </template>
        <template v-else-if="activeType === 'topics'">
          <VButton class="console-header-primary-action" type="secondary" @click="startTopicCreate">
            <template #icon>
              <IconAddCircle />
            </template>
            新建专题
          </VButton>
        </template>
        <template v-else-if="activeType === 'downloads'">
          <VButton
            class="console-header-primary-action"
            type="secondary"
            @click="startDownloadCreate"
          >
            <template #icon>
              <IconAddCircle />
            </template>
            新建链接
          </VButton>
        </template>
        <VButton
          v-else-if="canSaveResource"
          type="secondary"
          :loading="saving"
          @click="saveResource"
        >
          <template #icon>
            <IconSave />
          </template>
          {{ isEditing ? '保存修改' : `新建${currentLabel.replace('管理', '')}` }}
        </VButton>
      </VSpace>
    </template>
  </VPageHeader>

  <ConsolePageScaffold v-if="isCategoryView" class="category-console">
    <ConsoleTableCard>
      <ConsoleToolbar>
        <template #left>
          <label class="category-search">
            <IconSearch />
            <input v-model.trim="keyword" placeholder="搜索分类名称" type="search" />
          </label>
          <ConsoleFilterSelect
            v-model="selectedCategoryParent"
            class="category-select"
            :options="categoryParentFilterOptions"
            aria-label="筛选父级分类"
          />
          <ConsoleFilterSelect
            v-model="selectedCategoryStatus"
            class="category-select"
            :options="categoryStatusOptions"
            aria-label="筛选分类状态"
          />
          <ConsoleFilterSelect
            v-model="selectedCategorySort"
            class="category-select wide"
            :options="categorySortOptions"
            aria-label="分类排序"
          />
        </template>
        <template #right>
          <ConsoleTextButton v-if="hasCategoryFilters" @click="clearFilters"
            >重置</ConsoleTextButton
          >
          <ConsoleIconButton title="刷新" @click="fetchResources">
            <IconRefreshLine :class="{ spinning: loading }" />
          </ConsoleIconButton>
        </template>
      </ConsoleToolbar>
    </ConsoleTableCard>

    <div class="category-main-grid">
      <ConsoleTableCard>
        <div class="category-panel-heading">
          <div class="panel-title">
            <IconGrid />
            <strong>分类树</strong>
          </div>
          <button class="tree-add" type="button" title="新建分类" @click="startCategoryCreate">
            <IconAddCircle />
          </button>
        </div>

        <VLoading v-if="loading" />
        <VEmpty v-else-if="!categoryTreeItems.length" title="暂无分类" />
        <div v-else class="category-tree">
          <div
            class="tree-node"
            :class="{ active: !selectedCategoryParent }"
            role="button"
            tabindex="0"
            @click="selectedCategoryParent = ''"
            @keydown.enter="selectedCategoryParent = ''"
            @keydown.space.prevent="selectedCategoryParent = ''"
          >
            <span class="tree-drag-placeholder" aria-hidden="true"></span>
            <span class="tree-icon"></span>
            <span>全部分类</span>
            <em>{{ categoryStats.total }}</em>
          </div>
          <div
            v-for="node in categoryTreeItems"
            :key="node.name"
            class="tree-node"
            :class="{
              active: selectedCategoryParent === node.name,
              muted: !node.visible,
              root: node.depth === 0,
              dragging: draggingCategoryName === node.name,
              'drop-before':
                categoryDropTargetName === node.name && categoryDropPosition === 'before',
              'drop-after':
                categoryDropTargetName === node.name && categoryDropPosition === 'after',
              'drop-inside':
                categoryDropTargetName === node.name && categoryDropPosition === 'inside',
            }"
            role="button"
            tabindex="0"
            :style="{ '--tree-indent': categoryIndent(node.depth) }"
            @click="selectedCategoryParent = node.name"
            @dragover="handleCategoryDragOver(node, $event)"
            @dragleave="
              categoryDropTargetName =
                categoryDropTargetName === node.name ? '' : categoryDropTargetName
            "
            @drop="dropCategoryOn(node, $event)"
            @keydown.enter="selectedCategoryParent = node.name"
            @keydown.space.prevent="selectedCategoryParent = node.name"
          >
            <span class="tree-line" aria-hidden="true"></span>
            <span
              class="tree-drag-handle"
              draggable="true"
              title="拖拽调整顺序"
              aria-label="拖拽调整顺序"
              @click.stop
              @dragend="clearCategoryDragState"
              @dragstart.stop="startCategoryDrag(node, $event)"
            >
              <IconListSettings />
            </span>
            <span class="tree-icon"></span>
            <span class="tree-label">{{ node.label }}</span>
            <em>{{ node.count }}</em>
          </div>
        </div>

        <div class="tree-hint">拖到节点中部设为子分类，上下边缘调整同级顺序</div>
      </ConsoleTableCard>

      <div class="category-content">
        <ConsoleTableCard>
          <div class="category-panel-heading">
            <div class="panel-title">
              <IconClipboardLine />
              <strong>分类概览</strong>
            </div>
          </div>
          <ConsoleStatsGrid class="category-stats">
            <ConsoleStatCard
              tone="blue"
              label="总分类数"
              :value="formatNumber(categoryStats.total)"
              meta="所有分类数量"
            >
              <template #icon>
                <IconGrid />
              </template>
            </ConsoleStatCard>
            <ConsoleStatCard
              tone="green"
              label="启用数"
              :value="formatNumber(categoryStats.enabled)"
              meta="已启用分类"
            >
              <template #icon>
                <IconCheckboxCircle />
              </template>
            </ConsoleStatCard>
            <ConsoleStatCard
              tone="amber"
              label="停用数"
              :value="formatNumber(categoryStats.disabled)"
              meta="已停用分类"
            >
              <template #icon>
                <IconArrowDownCircleLine />
              </template>
            </ConsoleStatCard>
            <ConsoleStatCard
              tone="violet"
              label="未分类数"
              :value="formatNumber(categoryStats.uncategorizedApps)"
              meta="未分配软件"
            >
              <template #icon>
                <IconLink />
              </template>
            </ConsoleStatCard>
          </ConsoleStatsGrid>
        </ConsoleTableCard>

        <ConsoleTableCard>
          <div class="category-table-heading">
            <strong>分类列表</strong>
            <span> 当前筛选：{{ categoryParentLabel(selectedCategoryParent) }} </span>
          </div>

          <VLoading v-if="loading" />
          <VEmpty v-else-if="!filteredCategoryRows.length" title="没有匹配的分类">
            <template #actions>
              <VButton @click="clearFilters">重置筛选</VButton>
            </template>
          </VEmpty>
          <div v-else class="table-wrap">
            <table class="category-table">
              <thead>
                <tr>
                  <th>分类名称</th>
                  <th>软件数</th>
                  <th>状态</th>
                  <th>创建时间</th>
                  <th class="actions-col">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in pagedCategoryRows" :key="row.name">
                  <td>
                    <div class="category-name-cell">
                      <span class="category-folder"></span>
                      <div>
                        <strong>{{ row.displayName }}</strong>
                        <small>/{{ row.slug }} / {{ row.name }}</small>
                      </div>
                    </div>
                  </td>
                  <td>{{ formatNumber(row.appCount) }}</td>
                  <td>
                    <VStatusDot
                      :state="categoryStatusState(row.visible)"
                      :text="categoryStatusText(row.visible)"
                    />
                  </td>
                  <td>{{ formatCategoryDate(row.createdAt) }}</td>
                  <td class="actions-col">
                    <VSpace>
                      <button
                        class="table-icon-button"
                        type="button"
                        title="编辑"
                        @click="editResource(row.source)"
                      >
                        <IconRiPencilFill />
                      </button>
                      <button
                        class="table-icon-button danger"
                        type="button"
                        title="删除"
                        :disabled="deletingName === row.name"
                        @click="confirmDelete(row.source)"
                      >
                        <IconDeleteBin />
                      </button>
                    </VSpace>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <template #footer>
            <div class="category-pagination">
              <span>共 {{ filteredCategoryRows.length }} 条</span>
              <VPagination
                v-model:page="page"
                v-model:size="size"
                :total="filteredCategoryRows.length"
                :size-options="[10, 20, 50]"
              />
            </div>
          </template>
        </ConsoleTableCard>

        <div v-if="!categoryItems.length && !loading" class="category-empty-zone">
          <span class="category-empty-icon"></span>
          <strong>暂无分类</strong>
          <p>当前没有创建任何分类，点击右上角“新建分类”开始创建</p>
          <VButton class="category-primary-action" type="secondary" @click="startCategoryCreate">
            <template #icon>
              <IconAddCircle />
            </template>
            新建分类
          </VButton>
        </div>
      </div>
    </div>

    <div
      v-if="categoryComposerOpen"
      class="category-modal-backdrop"
      @click.self="closeCategoryComposer"
    >
      <section
        class="category-modal-card"
        role="dialog"
        aria-modal="true"
        :aria-label="categoryComposerTitle"
      >
        <button class="modal-close" type="button" title="关闭编辑" @click="closeCategoryComposer">
          <IconClose />
        </button>
        <div class="modal-heading">
          <strong>{{ categoryComposerTitle }}</strong>
          <span>维护分类名称、公开路径、父级关系和展示状态。</span>
        </div>

        <div class="category-form-grid">
          <label>
            <span>分类名称</span>
            <input v-model.trim="categoryDraft.spec.displayName" class="field" />
          </label>
          <label>
            <span>公开 slug</span>
            <input v-model.trim="categoryDraft.spec.slug" class="field" />
          </label>
          <label>
            <span>父级分类</span>
            <select v-model="categoryDraft.spec.parentName" class="field">
              <option value="">无父级</option>
              <option
                v-for="item in categoryRows"
                :key="item.name"
                :value="item.name"
                :disabled="isCategoryParentOptionDisabled(item)"
              >
                {{ item.displayName }}
              </option>
            </select>
          </label>
          <label>
            <span>图标</span>
            <input v-model.trim="categoryDraft.spec.icon" class="field" />
          </label>
          <label>
            <span>排序</span>
            <input v-model.number="categoryDraft.spec.priority" class="field" type="number" />
          </label>
          <label class="switch-field category-switch">
            <VSwitch v-model="categoryDraft.spec.visible" />
            <span>启用</span>
          </label>
          <label class="category-form-full">
            <span>描述</span>
            <textarea v-model.trim="categoryDraft.spec.description" class="textarea" rows="4" />
          </label>
        </div>

        <div class="modal-actions">
          <VButton @click="closeCategoryComposer">取消</VButton>
          <VButton
            type="secondary"
            :disabled="!canSaveCategoryDraft"
            :loading="saving"
            @click="saveResource"
          >
            <template #icon>
              <IconSave />
            </template>
            {{ isEditing ? '保存修改' : '新建分类' }}
          </VButton>
        </div>
      </section>
    </div>
  </ConsolePageScaffold>

  <DownloadAssetManager
    ref="downloadManagerRef"
    v-else-if="activeType === 'downloads'"
    :assets="downloadAssets"
    :apps="apps"
    :options="softwareOptions"
    :loading="downloadsLoading"
    :deleting-name="deletingName"
    :toggling-name="togglingAssetName"
    @refresh="refreshActiveView"
    @copy="copyAssetUrl"
    @edit="editDownloadAsset"
    @delete="confirmDeleteAsset"
    @toggle="setAssetEnabled"
    @create="createDownloadAsset"
    @selection-change="updateDownloadSelection"
    @batch-delete="confirmBatchDeleteDownloads"
  />

  <TagManager
    ref="tagManagerRef"
    v-else-if="activeType === 'tags'"
    :tags="tagItems"
    :loading="loading"
    :saving="saving"
    :deleting-name="deletingName"
    :editing-name="editingName"
    :draft="tagDraft"
    @refresh="refreshActiveView"
    @reset="resetDraft"
    @edit="editResource"
    @delete="confirmDelete"
    @save="saveTagFromManager"
    @selection-change="updateTagSelection"
    @batch-delete="confirmBatchDeleteTags"
  />

  <TopicManager
    ref="topicManagerRef"
    v-else-if="activeType === 'topics'"
    :topics="topicItems"
    :loading="loading"
    :saving="saving"
    :deleting-name="deletingName"
    :editing-name="editingName"
    :draft="topicDraft"
    :apps="apps"
    :apps-loading="appsLoading"
    :create-request-key="topicCreateRequestKey"
    @refresh="refreshActiveView"
    @reset="resetDraft"
    @edit="editResource"
    @delete="confirmDelete"
    @save="saveTopicFromManager"
    @search-apps="fetchAppsForResource"
    @selection-change="updateTopicSelection"
    @batch-delete="confirmBatchDeleteTopics"
  />

  <VCard v-else :body-class="['resource-card-body']">
    <template #header>
      <div class="resource-toolbar">
        <div v-if="canSaveResource" class="toolbar-actions">
          <input
            v-model.trim="keyword"
            class="resource-search"
            placeholder="搜索名称或 slug"
            type="search"
          />
          <VButton v-if="hasFilters" size="sm" @click="clearFilters">重置</VButton>
          <button class="icon-action" type="button" title="刷新" @click="fetchResources">
            <IconRefreshLine :class="{ spinning: loading }" />
          </button>
        </div>
      </div>
    </template>

    <div v-if="canSaveResource" class="resource-layout">
      <section class="resource-form">
        <div class="form-title">
          <strong>{{
            isEditing
              ? `编辑${currentLabel.replace('管理', '')}`
              : `新建${currentLabel.replace('管理', '')}`
          }}</strong>
          <VButton v-if="isEditing" size="sm" @click="resetDraft">取消编辑</VButton>
        </div>

        <template v-if="activeType === 'categories'">
          <label>
            <span>分类名称</span>
            <input v-model.trim="categoryDraft.spec.displayName" class="field" />
          </label>
          <label>
            <span>公开 slug</span>
            <input v-model.trim="categoryDraft.spec.slug" class="field" />
          </label>
          <label>
            <span>父级 name</span>
            <input v-model.trim="categoryDraft.spec.parentName" class="field" />
          </label>
          <label>
            <span>图标</span>
            <input v-model.trim="categoryDraft.spec.icon" class="field" />
          </label>
          <label>
            <span>排序</span>
            <input v-model.number="categoryDraft.spec.priority" class="field" type="number" />
          </label>
          <label class="switch-field">
            <VSwitch v-model="categoryDraft.spec.visible" />
            <span>可见</span>
          </label>
          <label class="full">
            <span>描述</span>
            <textarea v-model.trim="categoryDraft.spec.description" class="textarea" rows="4" />
          </label>
        </template>

        <template v-else-if="activeType === 'tags'">
          <label>
            <span>标签名称</span>
            <input v-model.trim="tagDraft.spec.displayName" class="field" />
          </label>
          <label>
            <span>公开 slug</span>
            <input v-model.trim="tagDraft.spec.slug" class="field" />
          </label>
          <label>
            <span>排序</span>
            <input v-model.number="tagDraft.spec.priority" class="field" type="number" />
          </label>
          <label class="switch-field">
            <VSwitch v-model="tagDraft.spec.visible" />
            <span>可见</span>
          </label>
          <label class="full">
            <span>描述</span>
            <textarea v-model.trim="tagDraft.spec.description" class="textarea" rows="4" />
          </label>
        </template>

        <template v-else>
          <label>
            <span>专题标题</span>
            <input v-model.trim="topicDraft.spec.title" class="field" />
          </label>
          <label>
            <span>公开 slug</span>
            <input v-model.trim="topicDraft.spec.slug" class="field" />
          </label>
          <label>
            <span>副标题</span>
            <input v-model.trim="topicDraft.spec.subtitle" class="field" />
          </label>
          <label>
            <span>封面 URL</span>
            <input v-model.trim="topicDraft.spec.cover" class="field" />
          </label>
          <label>
            <span>排序</span>
            <input v-model.number="topicDraft.spec.priority" class="field" type="number" />
          </label>
          <label class="switch-field">
            <VSwitch v-model="topicDraft.spec.published" />
            <span>发布</span>
          </label>
          <label class="switch-field">
            <VSwitch v-model="topicDraft.spec.featured" />
            <span>精选</span>
          </label>
          <label class="full">
            <span>描述</span>
            <textarea v-model.trim="topicDraft.spec.description" class="textarea" rows="4" />
          </label>
        </template>
      </section>

      <section class="resource-list">
        <VLoading v-if="loading" />
        <VEmpty v-else-if="!items.length" :title="`暂无${currentLabel.replace('管理', '')}`" />
        <div v-else class="table-wrap">
          <table class="resource-table">
            <thead>
              <tr>
                <th>名称</th>
                <th>Slug</th>
                <th>排序</th>
                <th>软件数</th>
                <th>状态</th>
                <th class="actions-col">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in items" :key="resourceName(item)">
                <td>
                  <strong>{{ resourceTitle(item) }}</strong>
                  <small>{{ resourceDescription(item) || resourceName(item) }}</small>
                </td>
                <td>
                  <code>/{{ resourceSlug(item) }}</code>
                </td>
                <td>{{ resourcePriority(item) }}</td>
                <td>
                  <VTag theme="secondary">{{ resourceCount(item) }} 软件</VTag>
                </td>
                <td>
                  <VStatusDot
                    :state="resourceVisible(item) ? 'success' : 'default'"
                    :text="resourceVisible(item) ? '可见' : '隐藏'"
                  />
                </td>
                <td class="actions-col">
                  <VSpace>
                    <VButton size="sm" type="secondary" @click="editResource(item)">
                      <template #icon>
                        <IconRiPencilFill />
                      </template>
                      编辑
                    </VButton>
                    <VButton
                      v-permission="['plugin:software:manage']"
                      size="sm"
                      type="danger"
                      :loading="deletingName === resourceName(item)"
                      @click="confirmDelete(item)"
                    >
                      <template #icon>
                        <IconDeleteBin />
                      </template>
                      删除
                    </VButton>
                  </VSpace>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>

    <template v-if="canSaveResource" #footer>
      <div class="pagination-footer">
        <span>共 {{ total }} 条资源</span>
        <VPagination
          v-model:page="page"
          v-model:size="size"
          :total="total"
          :size-options="[20, 50, 100]"
        />
      </div>
    </template>
  </VCard>
</template>

<style scoped lang="scss">
:deep(.resource-card-body) {
  padding: 0;
}

.category-search {
  height: 46px;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: #fff;
  color: #0f172a;
  font-size: 14px;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}

.category-search {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 0 14px;
  color: #8a96a8;
}

.category-search:focus-within {
  border-color: #60a5fa;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.category-search input {
  width: 100%;
  min-width: 0;
  border: 0;
  padding: 0;
  background: transparent;
  color: #0f172a;
  outline: none;
}

.category-search svg {
  width: 18px;
  height: 18px;
}

.category-select {
  min-width: 146px;
}

.category-select.wide {
  min-width: 170px;
}

.category-main-grid {
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
  gap: 22px;
}

.category-content {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
}

.category-panel-heading,
.category-table-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 18px 20px;
}

.category-panel-heading {
  border-bottom: 1px solid #eef2f7;
}

.category-table-heading {
  padding-bottom: 12px;
}

.category-table-heading strong,
.panel-title strong {
  color: #0f172a;
  font-size: 16px;
  font-weight: 700;
}

.category-table-heading span {
  color: #64748b;
  font-size: 12px;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.panel-title svg {
  width: 18px;
  height: 18px;
  color: #45617f;
}

.tree-add {
  display: inline-grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #48637f;
  cursor: pointer;
}

.tree-add:hover {
  background: #eef6ff;
  color: #0c7ff2;
}

.category-tree {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px 16px;
}

.tree-node {
  position: relative;
  display: grid;
  grid-template-columns: 20px auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  border: 0;
  border-radius: 8px;
  padding: 0 10px 0 calc(8px + var(--tree-indent, 0px));
  background: transparent;
  color: #243247;
  font-size: 14px;
  text-align: left;
  cursor: pointer;
}

.tree-node:hover,
.tree-node.active {
  background: #eef6ff;
  color: #0c7ff2;
}

.tree-node.muted {
  color: #7b8798;
}

.tree-node.dragging {
  opacity: 0.48;
}

.tree-node.drop-inside {
  background: #eaf3ff;
  color: #0c7ff2;
  box-shadow: inset 0 0 0 1px rgba(37, 99, 235, 0.22);
}

.tree-node.drop-before::before,
.tree-node.drop-after::after {
  content: '';
  position: absolute;
  left: calc(8px + var(--tree-indent, 0px));
  right: 10px;
  height: 2px;
  border-radius: 999px;
  background: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.14);
}

.tree-node.drop-before::before {
  top: -3px;
}

.tree-node.drop-after::after {
  bottom: -3px;
}

.tree-drag-handle,
.tree-drag-placeholder {
  display: inline-grid;
  place-items: center;
  width: 20px;
  height: 28px;
  color: #9aa8ba;
}

.tree-drag-handle {
  border-radius: 6px;
  cursor: grab;
}

.tree-drag-handle:active {
  cursor: grabbing;
}

.tree-drag-handle:hover {
  background: rgba(37, 99, 235, 0.08);
  color: #2563eb;
}

.tree-drag-handle svg {
  width: 15px;
  height: 15px;
}

.tree-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-node em {
  min-width: 28px;
  border-radius: 999px;
  padding: 3px 8px;
  background: #f1f5f9;
  color: #4f6279;
  font-size: 12px;
  font-style: normal;
  text-align: center;
}

.tree-node.active em {
  background: #dbeafe;
  color: #0c7ff2;
}

.tree-icon,
.category-folder {
  position: relative;
  width: 16px;
  height: 13px;
  border: 1.6px solid currentColor;
  border-radius: 3px;
}

.tree-icon::before,
.category-folder::before {
  content: '';
  position: absolute;
  top: -4px;
  left: 1px;
  width: 8px;
  height: 5px;
  border: 1.6px solid currentColor;
  border-bottom: 0;
  border-radius: 3px 3px 0 0;
  background: inherit;
}

.tree-line {
  position: absolute;
  top: -8px;
  bottom: 18px;
  left: calc(18px + var(--tree-indent, 0px));
  width: 1px;
  background: #dbe3ee;
}

.tree-node.root .tree-line,
.tree-node:first-child .tree-line {
  display: none;
}

.tree-hint {
  border-top: 1px solid #eef2f7;
  padding: 14px 16px;
  color: #94a3b8;
  font-size: 12px;
  text-align: center;
}

.category-stats {
  grid-template-columns: repeat(2, minmax(220px, 1fr));
  gap: 12px;
  padding: 0 20px 20px;
}

.category-stats :deep(.console-stat-card) {
  min-height: 104px;
  gap: 16px;
  padding: 16px 18px;
  box-shadow: 0 10px 24px rgba(18, 30, 45, 0.035);
}

.category-stats :deep(.console-stat-icon) {
  width: 50px;
  height: 50px;
}

.category-stats :deep(.console-stat-icon svg) {
  width: 23px;
  height: 23px;
}

.category-stats :deep(.console-stat-content) {
  flex: 1 1 auto;
  min-width: 0;
}

.category-stats :deep(.console-stat-card small),
.category-stats :deep(.console-stat-card p) {
  line-height: 1.35;
  white-space: nowrap;
}

.category-stats :deep(.console-stat-card strong) {
  margin-top: 4px;
  font-size: 25px;
}

.category-stats :deep(.console-stat-card p) {
  margin-top: 6px;
}

.category-table-body {
  overflow: hidden;
}

.category-table {
  width: 100%;
  min-width: 860px;
  border-collapse: separate;
  border-spacing: 0;
  border: 1px solid #e5ebf3;
  border-radius: 8px;
  background: #fff;
}

.category-table th {
  height: 46px;
  border-bottom: 1px solid #e5ebf3;
  padding: 0 16px;
  background: #fbfcfe;
  color: #64748b;
  font-size: 12px;
  font-weight: 600;
  text-align: left;
}

.category-table td {
  height: 52px;
  border-bottom: 1px solid #edf2f7;
  padding: 0 16px;
  color: #26364c;
  font-size: 13px;
  vertical-align: middle;
}

.category-table tr:last-child td {
  border-bottom: 0;
}

.category-table tbody tr:hover td {
  background: #fbfdff;
}

.category-name-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  color: #0c7ff2;
}

.category-name-cell div {
  min-width: 0;
}

.category-name-cell strong,
.category-name-cell small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-name-cell strong {
  color: #27364d;
  font-weight: 600;
}

.category-name-cell small {
  margin-top: 2px;
  color: #64748b;
  font-size: 12px;
}

.table-icon-button {
  display: inline-grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #465a73;
  cursor: pointer;
}

.table-icon-button:hover {
  background: #eef6ff;
  color: #0c7ff2;
}

.table-icon-button.danger {
  color: #ef4444;
}

.table-icon-button.danger:hover {
  background: #fff1f2;
  color: #dc2626;
}

.category-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 18px;
  width: 100%;
  padding: 14px 18px;
  color: #64748b;
  font-size: 13px;
}

.category-form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  border-top: 1px solid #edf2f7;
  margin-top: 16px;
  padding-top: 16px;
}

.category-form-full {
  grid-column: 1 / -1;
}

.category-form-grid .field,
.category-form-grid .textarea {
  width: 100%;
}

.category-form-grid .field:focus,
.category-form-grid .textarea:focus {
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
  outline: none;
}

.category-form-grid .textarea {
  min-height: 104px;
  padding: 10px 11px;
}

.category-switch {
  justify-content: center;
}

.category-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.36);
  backdrop-filter: blur(3px);
}

.category-modal-card {
  position: relative;
  width: min(720px, calc(100vw - 48px));
  max-height: calc(100vh - 72px);
  overflow: auto;
  border: 1px solid #e3e9f1;
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

.modal-close svg {
  width: 17px;
  height: 17px;
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
  color: #6d7a8c;
  font-size: 13px;
  line-height: 1.5;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  border-top: 1px solid #edf2f7;
  margin-top: 18px;
  padding-top: 16px;
}

.category-empty-zone {
  display: flex;
  align-items: center;
  flex-direction: column;
  justify-content: center;
  min-height: 150px;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  padding: 22px;
  background: linear-gradient(180deg, #f8fbff, #fff);
  color: #64748b;
  text-align: center;
}

.category-empty-icon {
  display: block;
  width: 44px;
  height: 34px;
  margin-bottom: 10px;
  border: 1px solid #93c5fd;
  border-radius: 8px;
  background: #dbeafe;
  box-shadow: inset 0 0 0 7px #bfdbfe;
}

.category-empty-zone strong {
  color: #0f172a;
  font-size: 15px;
}

.category-empty-zone p {
  margin: 8px 0 14px;
  font-size: 13px;
}

.resource-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 16px;
  background: #fff;
}

.toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  min-width: 0;
}

.resource-search,
.resource-select,
.field,
.textarea {
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  background: #fff;
  color: #111827;
  font-size: 14px;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}

.resource-search,
.resource-select,
.field {
  height: 40px;
  padding: 0 12px;
}

.resource-search {
  width: min(320px, 100%);
}

.resource-select {
  min-width: 150px;
}

.compact-select {
  min-width: 112px;
}

.textarea {
  padding: 10px;
  resize: vertical;
}

.icon-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  color: #4b5563;
  background: #fff;
  cursor: pointer;
}

.icon-action:hover {
  color: #0969da;
  background: #f8fbff;
}

.spinning {
  animation: spin 0.8s linear infinite;
}

.resource-layout {
  display: grid;
  grid-template-columns: minmax(300px, 360px) minmax(0, 1fr);
  min-height: 440px;
}

.resource-form {
  display: grid;
  align-self: start;
  grid-template-columns: 1fr;
  gap: 14px;
  padding: 18px;
  border-right: 1px solid #eef0f3;
  background: #fcfdfd;
}

.form-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: #374151;
  font-size: 13px;
}

.switch-field {
  flex-direction: row;
  align-items: center;
}

.resource-list {
  min-width: 0;
  padding: 18px;
}

.table-wrap {
  overflow-x: auto;
}

.resource-table {
  width: 100%;
  min-width: 760px;
  border-collapse: collapse;
  border-top: 1px solid #eef2f7;
}

.resource-table th {
  height: 46px;
  border-bottom: 1px solid #e5e7eb;
  padding: 0 16px;
  background: #fbfcfe;
  color: #6b7280;
  font-size: 12px;
  font-weight: 600;
  text-align: left;
}

.resource-table td {
  border-bottom: 1px solid #eef2f7;
  padding: 14px 16px;
  color: #1f2937;
  font-size: 13px;
  vertical-align: middle;
}

.resource-table tbody tr:hover td {
  background: #fbfdff;
}

.resource-table td strong,
.resource-table td small {
  display: block;
}

.resource-table td small {
  max-width: 520px;
  overflow: hidden;
  margin-top: 3px;
  color: #6b7280;
  text-overflow: ellipsis;
  white-space: nowrap;
}

code {
  border-radius: 4px;
  padding: 2px 5px;
  background: #f3f4f6;
  color: #2563eb;
  font-size: 12px;
}

.actions-col {
  width: 220px;
  text-align: right;
}

.actions-col :deep(.space) {
  justify-content: flex-end;
}

.pagination-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  padding: 12px 16px;
  color: #6b7280;
  font-size: 13px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1100px) {
  .category-main-grid {
    grid-template-columns: 1fr;
  }

  .category-form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .resource-toolbar {
    align-items: stretch;
  }

  .toolbar-actions,
  .resource-search,
  .resource-select {
    width: 100%;
  }

  .resource-layout {
    grid-template-columns: 1fr;
  }

  .resource-form {
    border-right: 0;
    border-bottom: 1px solid #eef0f3;
  }
}

@media (max-width: 720px) {
  .category-form-grid {
    grid-template-columns: 1fr;
  }

  .category-panel-heading,
  .category-table-heading,
  .category-pagination,
  .modal-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .category-content {
    gap: 14px;
  }

  .category-stats {
    grid-template-columns: 1fr;
  }

  .pagination-footer {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
