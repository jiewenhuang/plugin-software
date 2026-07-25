<script setup lang="ts">
import {
  Dialog,
  IconAddCircle,
  Toast,
  VButton,
  VPageHeader,
  VSpace,
} from '@halo-dev/components'
import { utils } from '@halo-dev/ui-shared'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  createTag,
  createTopic,
  deleteDownloadAsset,
  deleteTag,
  deleteTopic,
  emptyTag,
  emptyTopic,
  getDownloadAssetSecret,
  getDownloadAssetStats,
  getSoftwareOptions,
  listApps,
  listDownloadAssets,
  listTags,
  listTopics,
  updateDownloadAsset,
  updateTag,
  updateTopic,
  type DownloadAsset,
  type DownloadAssetStats,
  type ListResult,
  type SoftwareApp,
  type SoftwareOptions,
  type SoftwareTag,
  type SoftwareTopic,
} from '@/api/software'
import CategoryManager from '@/components/categories/CategoryManager.vue'
import DownloadAssetManager from '@/components/downloads/DownloadAssetManager.vue'
import TagManager from '@/components/tags/TagManager.vue'
import TopicManager from '@/components/topics/TopicManager.vue'
import { defaultSoftwareOptions, normalizeSoftwareOptions } from '@/options/software-options'
import type {
  DownloadAssetSortKey,
  DownloadAssetStatusFilter,
} from './download-asset-console'
import type { TagSortKey, TagStatusFilter } from './tag-console'
import type { TopicSortKey, TopicStatusFilter } from './topic-console'

type ResourceType = 'categories' | 'tags' | 'topics' | 'downloads'
type ResourceItem = SoftwareTag | SoftwareTopic
type ResourceSelection = {
  count: number
  names: string[]
  titles: string[]
}
type TagQuery = {
  page: number
  size: number
  keyword: string
  status: TagStatusFilter
  sort: TagSortKey
}
type TopicQuery = {
  page: number
  size: number
  keyword: string
  status: TopicStatusFilter
  sort: TopicSortKey
}
type DownloadQuery = {
  page: number
  size: number
  keyword: string
  appName: string
  type: string
  status: DownloadAssetStatusFilter
  sort: DownloadAssetSortKey
}

const route = useRoute()
const router = useRouter()
const items = ref<ResourceItem[]>([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const deletingName = ref('')
const editingName = ref('')
const tagDraft = ref<SoftwareTag>(emptyTag())
const topicDraft = ref<SoftwareTopic>(emptyTopic())
const topicCreateRequestKey = ref(0)
const softwareOptions = ref<SoftwareOptions>(defaultSoftwareOptions)
const apps = ref<SoftwareApp[]>([])
const appsLoading = ref(false)
const downloadAssets = ref<DownloadAsset[]>([])
const downloadStats = ref<DownloadAssetStats>({
  total: 0,
  enabled: 0,
  disabled: 0,
  protectedCount: 0,
  downloads: 0,
})
const downloadsLoading = ref(false)
const togglingAssetName = ref('')
const categoryManagerRef = ref<InstanceType<typeof CategoryManager> | null>(null)
const tagManagerRef = ref<InstanceType<typeof TagManager> | null>(null)
const topicManagerRef = ref<InstanceType<typeof TopicManager> | null>(null)
const downloadManagerRef = ref<InstanceType<typeof DownloadAssetManager> | null>(null)
const tagSelection = ref<ResourceSelection>({ count: 0, names: [], titles: [] })
const topicSelection = ref<ResourceSelection>({ count: 0, names: [], titles: [] })
const downloadSelection = ref<ResourceSelection>({ count: 0, names: [], titles: [] })
const tagQuery = ref<TagQuery>({
  page: 1,
  size: 20,
  keyword: '',
  status: '',
  sort: 'usage-desc',
})
const topicQuery = ref<TopicQuery>({
  page: 1,
  size: 20,
  keyword: '',
  status: '',
  sort: 'created-desc',
})
const downloadQuery = ref<DownloadQuery>({
  page: 1,
  size: 20,
  keyword: '',
  appName: '',
  type: '',
  status: '',
  sort: 'updated-desc',
})
let appsSearchSequence = 0
let resourceFetchSequence = 0
let downloadFetchSequence = 0

const routeTypeMap: Record<string, ResourceType> = {
  SoftwareCategories: 'categories',
  SoftwareTags: 'tags',
  SoftwareTopics: 'topics',
  SoftwareDownloadAssets: 'downloads',
}

const activeType = computed<ResourceType>(() => routeTypeMap[String(route.name)] || 'categories')
const isCategoryView = computed(() => activeType.value === 'categories')
const tagItems = computed(() => items.value as SoftwareTag[])
const topicItems = computed(() => items.value as SoftwareTopic[])
const currentLabel = computed(() => {
  const labels: Record<ResourceType, string> = {
    categories: '分类管理',
    tags: '标签管理',
    topics: '专题管理',
    downloads: '下载源管理',
  }
  return labels[activeType.value]
})

function resourceName(item: ResourceItem) {
  return item.metadata?.name || ''
}

function resourceTitle(item: ResourceItem) {
  return 'title' in item.spec ? item.spec.title : item.spec.displayName
}

function cloneResource<T extends ResourceItem>(item: T): T {
  return JSON.parse(JSON.stringify(item)) as T
}

function resetDraft() {
  editingName.value = ''
  tagDraft.value = emptyTag()
  topicDraft.value = emptyTopic()
}

function resetQueries() {
  tagQuery.value = {
    page: 1,
    size: 20,
    keyword: '',
    status: '',
    sort: 'usage-desc',
  }
  topicQuery.value = {
    page: 1,
    size: 20,
    keyword: '',
    status: '',
    sort: 'created-desc',
  }
  downloadQuery.value = {
    page: 1,
    size: 20,
    keyword: '',
    appName: '',
    type: '',
    status: '',
    sort: 'updated-desc',
  }
}

function normalizeListResult(result: ListResult<ResourceItem>) {
  items.value = result.items
  total.value = result.total
}

function tagSortParameter(sort: TagSortKey) {
  return {
    'usage-desc': 'status.appCount,desc',
    'usage-asc': 'status.appCount,asc',
    'created-desc': 'metadata.creationTimestamp,desc',
    'created-asc': 'metadata.creationTimestamp,asc',
    'name-asc': 'spec.displayName,asc',
  }[sort]
}

function topicSortParameter(sort: TopicSortKey) {
  return {
    'created-desc': 'metadata.creationTimestamp,desc',
    'created-asc': 'metadata.creationTimestamp,asc',
    'priority-asc': 'spec.priority,asc',
    'apps-desc': 'status.appCount,desc',
    'title-asc': 'spec.title,asc',
  }[sort]
}

function downloadSortParameter(sort: DownloadAssetSortKey) {
  return {
    'updated-desc': 'status.lastCheckedAt,desc',
    'updated-asc': 'status.lastCheckedAt,asc',
    'clicks-desc': 'status.downloadCount,desc',
    'priority-asc': 'spec.priority,asc',
  }[sort]
}

async function fetchResources() {
  if (activeType.value !== 'tags' && activeType.value !== 'topics') {
    return
  }
  const sequence = ++resourceFetchSequence
  loading.value = true
  try {
    if (activeType.value === 'tags') {
      const query = tagQuery.value
      const result = await listTags({
        page: query.page,
        size: query.size,
        keyword: query.keyword || undefined,
        visible: query.status === 'hidden' ? false : undefined,
        usage: query.status === 'used' || query.status === 'unused' ? query.status : undefined,
        sort: tagSortParameter(query.sort),
      })
      if (sequence === resourceFetchSequence) {
        normalizeListResult(result as ListResult<ResourceItem>)
      }
      return
    }

    const query = topicQuery.value
    const result = await listTopics({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      published:
        query.status === 'published' ? true : query.status === 'draft' ? false : undefined,
      featured: query.status === 'featured' ? true : undefined,
      sort: topicSortParameter(query.sort),
    })
    if (sequence === resourceFetchSequence) {
      normalizeListResult(result as ListResult<ResourceItem>)
    }
  } finally {
    if (sequence === resourceFetchSequence) {
      loading.value = false
    }
  }
}

async function fetchAppsForResource(keyword = '') {
  const sequence = ++appsSearchSequence
  appsLoading.value = true
  try {
    const result = await listApps({
      page: 1,
      size: 50,
      keyword: keyword || undefined,
    })
    if (sequence === appsSearchSequence) {
      apps.value = result.items
    }
  } finally {
    if (sequence === appsSearchSequence) {
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

async function fetchDownloadAssets(includeStats = false) {
  const sequence = ++downloadFetchSequence
  downloadsLoading.value = true
  try {
    const query = downloadQuery.value
    const [result, stats] = await Promise.all([
      listDownloadAssets({
        page: query.page,
        size: query.size,
        keyword: query.keyword || undefined,
        appName: query.appName || undefined,
        type: query.type || undefined,
        enabled:
          query.status === 'enabled' ? true : query.status === 'disabled' ? false : undefined,
        protection: query.status === 'protected' ? 'protected' : undefined,
        sort: downloadSortParameter(query.sort),
      }),
      includeStats ? getDownloadAssetStats() : Promise.resolve(undefined),
    ])
    if (sequence === downloadFetchSequence) {
      downloadAssets.value = result.items
      total.value = result.total
      if (stats) {
        downloadStats.value = stats
      }
    }
  } finally {
    if (sequence === downloadFetchSequence) {
      downloadsLoading.value = false
    }
  }
}

async function refreshActiveView() {
  if (activeType.value === 'categories') {
    return
  }
  if (activeType.value === 'downloads') {
    await Promise.all([fetchSoftwareOptions(), fetchAppsForResource(), fetchDownloadAssets(true)])
    return
  }
  if (activeType.value === 'topics') {
    await Promise.all([fetchResources(), fetchAppsForResource()])
    return
  }
  await fetchResources()
}

function updateTagQuery(query: TagQuery) {
  tagQuery.value = query
  void fetchResources()
}

function updateTopicQuery(query: TopicQuery) {
  topicQuery.value = query
  void fetchResources()
}

function updateDownloadQuery(query: DownloadQuery) {
  downloadQuery.value = query
  void fetchDownloadAssets()
}

function editResource(item: ResourceItem) {
  editingName.value = resourceName(item)
  if (activeType.value === 'tags') {
    tagDraft.value = cloneResource(item as SoftwareTag)
    return
  }
  if (activeType.value === 'topics') {
    topicDraft.value = cloneResource(item as SoftwareTopic)
  }
}

function startCategoryCreate() {
  categoryManagerRef.value?.openCreate()
}

function startTagCreate() {
  tagManagerRef.value?.openCreate()
}

function startTopicCreate() {
  topicCreateRequestKey.value += 1
}

function startDownloadCreate() {
  if (downloadManagerRef.value) {
    downloadManagerRef.value.createForCurrentApp()
    return
  }
  createDownloadAsset()
}

async function saveTag(tag: SoftwareTag) {
  if (!tag.spec.displayName || !tag.spec.slug) {
    Toast.warning('标签名称和 slug 必填')
    return false
  }
  if (editingName.value) {
    await updateTag(editingName.value, tag)
  } else {
    await createTag(tag)
  }
  return true
}

async function saveTopic(topic: SoftwareTopic) {
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

async function saveTagFromManager(tag: SoftwareTag) {
  saving.value = true
  try {
    if (!(await saveTag(tag))) {
      return
    }
    Toast.success('保存成功')
    resetDraft()
    await fetchResources()
  } finally {
    saving.value = false
  }
}

async function saveTopicFromManager(topic: SoftwareTopic) {
  saving.value = true
  try {
    if (!(await saveTopic(topic))) {
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
  if (!name || (activeType.value !== 'tags' && activeType.value !== 'topics')) {
    return
  }
  Dialog.warning({
    title: '删除' + currentLabel.value,
    description: '确定删除「' + resourceTitle(item) + '」吗？',
    confirmType: 'danger',
    onConfirm: async () => {
      deletingName.value = name
      try {
        if (activeType.value === 'tags') {
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

function confirmBatchDeleteTags() {
  const names = [...tagSelection.value.names]
  if (!names.length) {
    return
  }
  const preview = tagSelection.value.titles.slice(0, 3).join('、')
  Dialog.warning({
    title: '批量删除标签',
    description:
      '确定删除已选 ' +
      names.length +
      ' 个标签' +
      (preview ? '（' + preview + (names.length > 3 ? ' 等' : '') + '）' : '') +
      '吗？',
    confirmType: 'danger',
    onConfirm: async () => {
      deletingName.value = '__tag_batch__'
      try {
        await Promise.all(names.map((name) => deleteTag(name)))
        tagManagerRef.value?.clearSelection()
        resetDraft()
        Toast.success('已删除 ' + names.length + ' 个标签')
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
    description:
      '确定删除已选 ' +
      names.length +
      ' 个专题' +
      (preview ? '（' + preview + (names.length > 3 ? ' 等' : '') + '）' : '') +
      '吗？',
    confirmType: 'danger',
    onConfirm: async () => {
      deletingName.value = '__topic_batch__'
      try {
        await Promise.all(names.map((name) => deleteTopic(name)))
        topicManagerRef.value?.clearSelection()
        resetDraft()
        Toast.success('已删除 ' + names.length + ' 个专题')
        await fetchResources()
      } finally {
        deletingName.value = ''
      }
    },
  })
}

function confirmBatchDeleteDownloads() {
  const names = [...downloadSelection.value.names]
  if (!names.length) {
    return
  }
  const preview = downloadSelection.value.titles.slice(0, 3).join('、')
  Dialog.warning({
    title: '批量删除下载源',
    description:
      '确定删除已选 ' +
      names.length +
      ' 个下载源' +
      (preview ? '（' + preview + (names.length > 3 ? ' 等' : '') + '）' : '') +
      '吗？',
    confirmType: 'danger',
    onConfirm: async () => {
      deletingName.value = '__download_batch__'
      try {
        await Promise.all(names.map((name) => deleteDownloadAsset(name)))
        downloadManagerRef.value?.clearSelection()
        Toast.success('已删除 ' + names.length + ' 个下载源')
        await fetchDownloadAssets(true)
      } finally {
        deletingName.value = ''
      }
    },
  })
}

async function copyAssetUrl(asset: DownloadAsset) {
  if (!utils.permission.has(['plugin:software:secret'])) {
    Toast.warning('缺少“软件敏感下载配置”权限')
    return
  }
  const name = asset.metadata?.name
  const secret = name ? await getDownloadAssetSecret(name) : asset.spec
  if (!secret.url) {
    Toast.warning('该下载源未设置 URL')
    return
  }
  await navigator.clipboard.writeText(secret.url)
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
    await fetchDownloadAssets(true)
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
    description: '确定删除「' + asset.spec.title + '」吗？',
    confirmType: 'danger',
    onConfirm: async () => {
      deletingName.value = name
      try {
        await deleteDownloadAsset(name)
        Toast.success('删除成功')
        await fetchDownloadAssets(true)
      } finally {
        deletingName.value = ''
      }
    },
  })
}

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

function updateTagSelection(selection: ResourceSelection) {
  tagSelection.value = selection
}

function updateTopicSelection(selection: ResourceSelection) {
  topicSelection.value = selection
}

function updateDownloadSelection(selection: ResourceSelection) {
  downloadSelection.value = selection
}

watch(
  () => route.name,
  async () => {
    resourceFetchSequence += 1
    downloadFetchSequence += 1
    appsSearchSequence += 1
    resetQueries()
    items.value = []
    total.value = 0
    resetDraft()
    await refreshActiveView()
  },
)

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
        <VButton
          v-else-if="activeType === 'tags'"
          class="console-header-primary-action"
          type="secondary"
          @click="startTagCreate"
        >
          <template #icon>
            <IconAddCircle />
          </template>
          新建标签
        </VButton>
        <VButton
          v-else-if="activeType === 'topics'"
          class="console-header-primary-action"
          type="secondary"
          @click="startTopicCreate"
        >
          <template #icon>
            <IconAddCircle />
          </template>
          新建专题
        </VButton>
        <VButton
          v-else
          class="console-header-primary-action"
          type="secondary"
          @click="startDownloadCreate"
        >
          <template #icon>
            <IconAddCircle />
          </template>
          新建链接
        </VButton>
      </VSpace>
    </template>
  </VPageHeader>

  <CategoryManager v-if="isCategoryView" ref="categoryManagerRef" />

  <DownloadAssetManager
    v-else-if="activeType === 'downloads'"
    ref="downloadManagerRef"
    :assets="downloadAssets"
    :apps="apps"
    :deleting-name="deletingName"
    :loading="downloadsLoading"
    :options="softwareOptions"
    :stats="downloadStats"
    :toggling-name="togglingAssetName"
    :total="total"
    remote
    @batch-delete="confirmBatchDeleteDownloads"
    @copy="copyAssetUrl"
    @create="createDownloadAsset"
    @delete="confirmDeleteAsset"
    @edit="editDownloadAsset"
    @query-change="updateDownloadQuery"
    @refresh="refreshActiveView"
    @selection-change="updateDownloadSelection"
    @toggle="setAssetEnabled"
  />

  <TagManager
    v-else-if="activeType === 'tags'"
    ref="tagManagerRef"
    :deleting-name="deletingName"
    :draft="tagDraft"
    :editing-name="editingName"
    :loading="loading"
    :saving="saving"
    :tags="tagItems"
    :total="total"
    remote
    @batch-delete="confirmBatchDeleteTags"
    @delete="confirmDelete"
    @edit="editResource"
    @query-change="updateTagQuery"
    @refresh="refreshActiveView"
    @reset="resetDraft"
    @save="saveTagFromManager"
    @selection-change="updateTagSelection"
  />

  <TopicManager
    v-else
    ref="topicManagerRef"
    :apps="apps"
    :apps-loading="appsLoading"
    :create-request-key="topicCreateRequestKey"
    :deleting-name="deletingName"
    :draft="topicDraft"
    :editing-name="editingName"
    :loading="loading"
    :saving="saving"
    :topics="topicItems"
    :total="total"
    remote
    @batch-delete="confirmBatchDeleteTopics"
    @delete="confirmDelete"
    @edit="editResource"
    @query-change="updateTopicQuery"
    @refresh="refreshActiveView"
    @reset="resetDraft"
    @save="saveTopicFromManager"
    @search-apps="fetchAppsForResource"
    @selection-change="updateTopicSelection"
  />
</template>
