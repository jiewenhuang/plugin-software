<script setup lang="ts">
import {
  Dialog,
  IconAddCircle,
  IconArrowLeft,
  IconDeleteBin,
  IconMagic,
  IconRiPencilFill,
  IconSave,
  Toast,
  VButton,
  VEmpty,
  VLoading,
  VModal,
  VPageHeader,
  VSpace,
  VStatusDot,
  VSwitch,
  VTag,
} from '@halo-dev/components'
import { ExtensionsKit, RichTextEditor, VueEditor } from '@halo-dev/richtext-editor'
import '@halo-dev/richtext-editor/dist/style.css'
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import {
  createApp,
  createDownloadAsset,
  createTag,
  createVersion,
  deleteDownloadAsset,
  deleteVersion,
  emptyTag,
  emptySoftwareApp,
  getApp,
  getRelatedPost,
  getSoftwareAiCapabilities,
  getSoftwareOptions,
  getTag,
  listApps,
  listCategories,
  listDownloadAssets,
  listRelatedPosts,
  listTags,
  listVersions,
  suggestAppByAi,
  updateApp,
  updateDownloadAsset,
  updateVersion,
  type DownloadAsset,
  type RelatedPost,
  type SoftwareAiTarget,
  type SoftwareApp,
  type SoftwareCategory,
  type SoftwareOptionItem,
  type SoftwareOptions,
  type SoftwareTag,
  type SoftwareVersion,
} from '@/api/software'
import ConsoleImagePicker from '@/components/console/ConsoleImagePicker.vue'
import ConsolePageScaffold from '@/components/console/ConsolePageScaffold.vue'
import ConsoleTableCard from '@/components/console/ConsoleTableCard.vue'
import ConsoleTokenSelector from '@/components/console/ConsoleTokenSelector.vue'
import ConsoleFilterSelect from '@/components/ConsoleFilterSelect.vue'
import {
  defaultSoftwareOptions,
  ensureCurrentOption as ensureConfiguredOption,
  normalizeSoftwareOptions,
  optionLabel as configuredOptionLabel,
  splitOptionText,
  toFilterOptions,
} from '@/options/software-options'
import {
  applySoftwareAiSuggestion,
  buildSoftwareEditorQuery,
  createSoftwareEditorSnapshot,
  normalizeSoftwareEditorTab,
  type SoftwareEditorTab as EditorTab,
} from './software-editor-console'

type SelectOption = { label: string; value: string; disabled?: boolean }

const searchParams = new URLSearchParams(window.location.search)
const name = ref(searchParams.get('name') || '')
const activeTab = ref<EditorTab>(normalizeSoftwareEditorTab(searchParams.get('tab')))
const app = ref<SoftwareApp>(emptySoftwareApp())
const versions = ref<SoftwareVersion[]>([])
const downloadAssets = ref<DownloadAsset[]>([])
const categories = ref<SoftwareCategory[]>([])
const tags = ref<SoftwareTag[]>([])
const apps = ref<SoftwareApp[]>([])
const relatedPosts = ref<RelatedPost[]>([])
const softwareOptions = ref<SoftwareOptions>(defaultSoftwareOptions)
const loading = ref(false)
const saving = ref(false)
const suggestingApp = ref(false)
const aiAvailable = ref(false)
const versionSaving = ref(false)
const assetSaving = ref(false)
const tagCreating = ref(false)
const tagSearching = ref(false)
const appSearching = ref(false)
const postSearching = ref(false)
const resourceLoading = ref(false)
const versionModalVisible = ref(false)
const assetModalVisible = ref(false)
const descriptionEditor = shallowRef<VueEditor>()
const savedSnapshot = ref('')
const versionDraft = reactive({
  name: '',
  version: '',
  build: '',
  releaseDate: '',
  minOsVersion: '',
  sizeBytes: '',
  status: 'released',
  changelog: '',
})
const assetDraft = reactive({
  name: '',
  versionName: '',
  title: '',
  url: '',
  type: 'dmg',
  provider: 'external',
  region: '',
  priority: 0,
  enabled: true,
})

const tokenSearchSize = 20
let tagSearchSeq = 0
let appSearchSeq = 0
let postSearchSeq = 0

const tabs: Array<{ key: EditorTab; label: string; description: string }> = [
  { key: 'basic', label: '基本信息', description: '名称、slug、分类与标签' },
  { key: 'media', label: '媒体内容', description: '图标、封面、截图与描述' },
  { key: 'versions', label: '版本管理', description: '版本号、系统要求与更新说明' },
  { key: 'downloads', label: '下载源', description: '下载链接、类型与优先级' },
  { key: 'seo', label: 'SEO', description: '搜索标题与关键词' },
  { key: 'settings', label: '发布设置', description: '发布、推荐与关联内容' },
]

const isEdit = computed(() => Boolean(name.value))
const appName = computed(() => app.value.metadata?.name || name.value)
const canManageChildren = computed(() => Boolean(appName.value))
const currentTab = computed(() => tabs.find((tab) => tab.key === activeTab.value) || tabs[0])
const activeTabIndex = computed(() => tabs.findIndex((tab) => tab.key === activeTab.value))
const hasPreviousTab = computed(() => activeTabIndex.value > 0)
const hasNextTab = computed(() => activeTabIndex.value < tabs.length - 1)
const categoryModel = computed({
  get: () => app.value.spec.categoryName || '',
  set: (value: string) => {
    app.value.spec.categoryName = value || undefined
  },
})
const releaseChannelModel = computed({
  get: () => app.value.spec.releaseChannel || '',
  set: (value: string) => {
    app.value.spec.releaseChannel = value || undefined
  },
})
const localeModel = computed({
  get: () => app.value.spec.locale || '',
  set: (value: string) => {
    app.value.spec.locale = value || undefined
  },
})
const platformModel = computed({
  get: () => splitOptionText(app.value.spec.platform),
  set: (values: string[]) => {
    app.value.spec.platform = uniqueValues(values).join(', ') || undefined
  },
})
const architectureModel = computed({
  get: () => app.value.spec.architectures || [],
  set: (values: string[]) => {
    app.value.spec.architectures = uniqueValues(values)
  },
})
const seoKeywordsText = computed(() => toText(app.value.spec.seo?.keywords))
const selectedCategory = computed(() =>
  categories.value.find((category) => category.metadata?.name === app.value.spec.categoryName),
)
const editorTitle = computed(() =>
  isEdit.value ? `编辑软件 - ${app.value.spec.displayName || appName.value}` : '新建软件',
)
const previewImage = computed(
  () => app.value.spec.cover || app.value.spec.screenshots?.[0] || app.value.spec.icon || '',
)
const screenshotsCount = computed(() => app.value.spec.screenshots?.length || 0)
const selectedCategoryLabel = computed(() => selectedCategory.value?.spec.displayName || '未分类')
const categorySelectOptions = computed<SelectOption[]>(() => [
  { label: '未分类', value: '' },
  ...categories.value.map((category) => ({
    label: category.spec.displayName,
    value: category.metadata?.name || '',
    disabled: !category.metadata?.name,
  })),
])
const versionSelectOptions = computed<SelectOption[]>(() => [
  { label: '选择版本', value: '', disabled: true },
  ...versions.value.map((version) => ({
    label: version.spec.version,
    value: version.metadata?.name || '',
    disabled: !version.metadata?.name,
  })),
])
const platformOptions = computed(() =>
  ensureCurrentTokenOptions(softwareOptions.value.platforms, platformModel.value),
)
const architectureOptions = computed(() =>
  ensureCurrentTokenOptions(softwareOptions.value.architectures, architectureModel.value),
)
const versionStatusOptions = computed(() =>
  ensureConfiguredOption(toFilterOptions(softwareOptions.value.versionStatuses), versionDraft.status),
)
const assetTypeOptions = computed(() =>
  ensureConfiguredOption(toFilterOptions(softwareOptions.value.downloadTypes), assetDraft.type),
)
const assetProviderOptions = computed(() =>
  ensureConfiguredOption(
    toFilterOptions(softwareOptions.value.downloadProviders),
    assetDraft.provider,
  ),
)
const releaseChannelOptions = computed(() =>
  ensureConfiguredOption(
    toFilterOptions(softwareOptions.value.releaseChannels),
    app.value.spec.releaseChannel || '',
  ),
)
const localeOptions = computed(() =>
  ensureConfiguredOption(toFilterOptions(softwareOptions.value.locales), app.value.spec.locale || ''),
)
const tagOptions = computed(() =>
  tags.value
    .map((tag) => ({
      label: tag.spec.displayName || tag.metadata?.name || '',
      value: tag.metadata?.name || '',
      description: tag.spec.slug ? `/${tag.spec.slug}` : tag.metadata?.name,
      disabled: !tag.metadata?.name,
    }))
    .filter((option) => option.value),
)
const relatedAppOptions = computed(() =>
  apps.value
    .filter((item) => (item.metadata?.name || '') !== appName.value)
    .map((item) => ({
      label: item.spec.displayName || item.metadata?.name || '',
      value: item.metadata?.name || '',
      description: [item.spec.slug ? `/${item.spec.slug}` : '', item.spec.vendor || '']
        .filter(Boolean)
        .join(' · '),
      disabled: !item.metadata?.name,
    }))
    .filter((option) => option.value),
)
const relatedPostOptions = computed(() =>
  relatedPosts.value
    .map((post) => ({
      label: post.spec.title || post.metadata?.name || '',
      value: post.metadata?.name || '',
      description: post.spec.slug ? `/${post.spec.slug}` : post.status?.permalink,
      disabled: !post.metadata?.name,
    }))
    .filter((option) => option.value),
)
const editorChecklist = computed(() => [
  { label: '基础信息', done: Boolean(app.value.spec.displayName && app.value.spec.slug) },
  {
    label: '分类标签',
    done: Boolean(app.value.spec.categoryName || app.value.spec.tagNames?.length),
  },
  {
    label: '媒体素材',
    done: Boolean(app.value.spec.icon || app.value.spec.cover || screenshotsCount.value),
  },
  { label: '版本', done: versions.value.length > 0 },
  { label: '下载源', done: downloadAssets.value.length > 0 },
  { label: '发布设置', done: Boolean(app.value.spec.published || app.value.spec.releaseChannel) },
])
const editorChecklistDone = computed(() => editorChecklist.value.filter((item) => item.done).length)
const hasUnsavedChanges = computed(
  () => Boolean(savedSnapshot.value) && createSoftwareEditorSnapshot(app.value) !== savedSnapshot.value,
)

function splitValues(value?: string[] | string) {
  if (Array.isArray(value)) {
    return value
  }
  return String(value || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

function toText(value?: string[]) {
  return (value || []).join(', ')
}

function ensureCurrentTokenOptions(items: SoftwareOptionItem[], currentValues: string[]) {
  const options = toFilterOptions(items)
  return currentValues.reduce(
    (nextOptions, value) => ensureConfiguredOption(nextOptions, value),
    options,
  )
}

function versionStatusText(status?: string) {
  return configuredOptionLabel(softwareOptions.value.versionStatuses, status, '已发布')
}

function assetTypeText(type?: string) {
  return configuredOptionLabel(softwareOptions.value.downloadTypes, type, '未设置')
}

function assetProviderText(provider?: string) {
  return configuredOptionLabel(softwareOptions.value.downloadProviders, provider, 'external')
}

function firstConfiguredValue(items: SoftwareOptionItem[], fallback: string) {
  return items.find((item) => item.value)?.value || fallback
}

function mergeByMetadataName<T extends { metadata?: { name?: string } }>(
  current: T[],
  incoming: T[],
) {
  const map = new Map<string, T>()
  current.forEach((item) => {
    const itemName = item.metadata?.name
    if (itemName) {
      map.set(itemName, item)
    }
  })
  incoming.forEach((item) => {
    const itemName = item.metadata?.name
    if (itemName) {
      map.set(itemName, item)
    }
  })
  return Array.from(map.values())
}

function selectedByName<T extends { metadata?: { name?: string } }>(
  items: T[],
  selectedNames?: string[],
) {
  const selectedSet = new Set(selectedNames || [])
  return items.filter((item) => {
    const itemName = item.metadata?.name
    return itemName ? selectedSet.has(itemName) : false
  })
}

function goPreviousTab() {
  if (!hasPreviousTab.value) {
    return
  }
  activeTab.value = tabs[activeTabIndex.value - 1].key
}

function goNextTab() {
  if (!hasNextTab.value) {
    return
  }
  activeTab.value = tabs[activeTabIndex.value + 1].key
}

function markSavedSnapshot() {
  savedSnapshot.value = createSoftwareEditorSnapshot(app.value)
}

function replaceEditorUrl() {
  const nextUrl = `${window.location.pathname}${buildSoftwareEditorQuery(name.value, activeTab.value)}`
  const currentUrl = `${window.location.pathname}${window.location.search}`
  if (nextUrl !== currentUrl) {
    window.history.replaceState(null, '', nextUrl)
  }
}

function confirmDiscardChanges() {
  if (!hasUnsavedChanges.value) {
    return true
  }
  return window.confirm('当前软件有未保存修改，确定离开吗？')
}

function handleBeforeUnload(event: BeforeUnloadEvent) {
  if (!hasUnsavedChanges.value) {
    return
  }
  event.preventDefault()
  event.returnValue = ''
}

function setSeoKeywords(value: string) {
  app.value.spec.seo = {
    ...app.value.spec.seo,
    keywords: splitValues(value),
  }
}

function uniqueValues(values: string[]) {
  return Array.from(new Set(values.map((value) => value.trim()).filter(Boolean)))
}

function requestErrorMessage(error: unknown, fallback: string) {
  if (!error || typeof error !== 'object') {
    return fallback
  }
  const response = (error as { response?: { data?: unknown } }).response
  const data = response?.data
  if (typeof data === 'string' && data.trim()) {
    return data.trim()
  }
  if (data && typeof data === 'object') {
    const body = data as Record<string, unknown>
    const message = [body.detail, body.message, body.title].find(
      (value) => typeof value === 'string' && value.trim(),
    )
    if (typeof message === 'string') {
      return message
    }
  }
  const message = (error as { message?: unknown }).message
  return typeof message === 'string' && message.trim() ? message : fallback
}

function setTagNames(values: string[]) {
  app.value.spec.tagNames = uniqueValues(values)
}

function setRelatedApps(values: string[]) {
  app.value.spec.relatedAppNames = uniqueValues(values)
}

function setRelatedPosts(values: string[]) {
  app.value.spec.relatedPostNames = uniqueValues(values)
}

function slugify(value: string) {
  return (
    value
      .trim()
      .toLowerCase()
      .normalize('NFKD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-z0-9\u4e00-\u9fa5]+/g, '-')
      .replace(/^-+|-+$/g, '') || `tag-${Date.now()}`
  )
}

function uniqueTagSlug(keyword: string) {
  const usedSlugs = new Set(tags.value.map((tag) => tag.spec.slug).filter(Boolean))
  const baseSlug = slugify(keyword)
  if (!usedSlugs.has(baseSlug)) {
    return baseSlug
  }
  let index = 2
  let nextSlug = `${baseSlug}-${index}`
  while (usedSlugs.has(nextSlug)) {
    index += 1
    nextSlug = `${baseSlug}-${index}`
  }
  return nextSlug
}

async function createAndSelectTag(keyword: string) {
  const displayName = keyword.trim()
  if (!displayName || tagCreating.value) {
    return
  }
  const targetSlug = slugify(displayName)
  let existing = tags.value.find(
    (tag) =>
      tag.spec.displayName.toLowerCase() === displayName.toLowerCase() ||
      tag.spec.slug.toLowerCase() === targetSlug,
  )
  if (!existing) {
    const result = await listTags({
      page: 1,
      size: tokenSearchSize,
      keyword: displayName,
    })
    tags.value = mergeByMetadataName(tags.value, result.items)
    existing = result.items.find(
      (tag) =>
        tag.spec.displayName.toLowerCase() === displayName.toLowerCase() ||
        tag.spec.slug.toLowerCase() === targetSlug,
    )
  }
  if (existing?.metadata?.name) {
    setTagNames([...(app.value.spec.tagNames || []), existing.metadata.name])
    return
  }

  const payload = emptyTag()
  payload.spec.displayName = displayName
  payload.spec.slug = uniqueTagSlug(displayName)
  tagCreating.value = true
  try {
    const saved = await createTag(payload)
    tags.value = [saved, ...tags.value]
    if (saved.metadata?.name) {
      setTagNames([...(app.value.spec.tagNames || []), saved.metadata.name])
    }
    Toast.success('标签已创建')
  } finally {
    tagCreating.value = false
  }
}

async function searchTags(keyword = '') {
  const searchId = ++tagSearchSeq
  tagSearching.value = true
  try {
    const result = await listTags({
      page: 1,
      size: tokenSearchSize,
      keyword: keyword || undefined,
    })
    if (searchId !== tagSearchSeq) {
      return
    }
    tags.value = mergeByMetadataName(
      selectedByName(tags.value, app.value.spec.tagNames),
      result.items,
    )
  } finally {
    if (searchId === tagSearchSeq) {
      tagSearching.value = false
    }
  }
}

async function searchRelatedApps(keyword = '') {
  const searchId = ++appSearchSeq
  appSearching.value = true
  try {
    const result = await listApps({
      page: 1,
      size: tokenSearchSize,
      keyword: keyword || undefined,
    })
    if (searchId !== appSearchSeq) {
      return
    }
    apps.value = mergeByMetadataName(
      selectedByName(apps.value, app.value.spec.relatedAppNames),
      result.items,
    )
  } finally {
    if (searchId === appSearchSeq) {
      appSearching.value = false
    }
  }
}

async function searchRelatedPosts(keyword = '') {
  const searchId = ++postSearchSeq
  postSearching.value = true
  try {
    const result = await listRelatedPosts({
      page: 1,
      size: tokenSearchSize,
      keyword: keyword || undefined,
    }).catch(() => ({
      items: [],
      page: 1,
      size: tokenSearchSize,
      total: 0,
    }))
    if (searchId !== postSearchSeq) {
      return
    }
    relatedPosts.value = mergeByMetadataName(
      selectedByName(relatedPosts.value, app.value.spec.relatedPostNames),
      result.items,
    )
  } finally {
    if (searchId === postSearchSeq) {
      postSearching.value = false
    }
  }
}

async function fetchSelectedTokenResources() {
  const tagNames = app.value.spec.tagNames || []
  const relatedAppNames = app.value.spec.relatedAppNames || []
  const relatedPostNames = app.value.spec.relatedPostNames || []

  const [selectedTags, selectedApps, selectedPosts] = await Promise.all([
    Promise.all(
      tagNames
        .filter((tagName) => tagName && !tags.value.some((tag) => tag.metadata?.name === tagName))
        .map((tagName) => getTag(tagName).catch(() => undefined)),
    ),
    Promise.all(
      relatedAppNames
        .filter(
          (relatedName) =>
            relatedName && !apps.value.some((item) => item.metadata?.name === relatedName),
        )
        .map((relatedName) => getApp(relatedName).catch(() => undefined)),
    ),
    Promise.all(
      relatedPostNames
        .filter(
          (postName) =>
            postName && !relatedPosts.value.some((post) => post.metadata?.name === postName),
        )
        .map((postName) => getRelatedPost(postName).catch(() => undefined)),
    ),
  ])

  tags.value = mergeByMetadataName(tags.value, selectedTags.filter(Boolean) as SoftwareTag[])
  apps.value = mergeByMetadataName(apps.value, selectedApps.filter(Boolean) as SoftwareApp[])
  relatedPosts.value = mergeByMetadataName(
    relatedPosts.value,
    selectedPosts.filter(Boolean) as RelatedPost[],
  )
}

function ensureSeo() {
  if (!app.value.spec.seo) {
    app.value.spec.seo = {}
  }
}

function syncDescriptionFromEditor() {
  const editor = descriptionEditor.value
  if (!editor) {
    return
  }
  app.value.spec.description = editor.isEmpty ? '' : editor.getHTML()
}

function syncDescriptionToEditor() {
  const editor = descriptionEditor.value
  if (!editor) {
    return
  }
  const content = app.value.spec.description || ''
  if ((editor.isEmpty && !content) || editor.getHTML() === content) {
    return
  }
  editor.commands.setContent(content || '<p></p>', {
    emitUpdate: false,
    parseOptions: {
      preserveWhitespace: true,
    },
  })
}

function setupDescriptionEditor() {
  descriptionEditor.value = new VueEditor({
    content: app.value.spec.description || '<p></p>',
    extensions: [ExtensionsKit],
    parseOptions: {
      preserveWhitespace: true,
    },
    onUpdate: syncDescriptionFromEditor,
  })
}

function resetVersionDraft() {
  versionDraft.name = ''
  versionDraft.version = ''
  versionDraft.build = ''
  versionDraft.releaseDate = ''
  versionDraft.minOsVersion = ''
  versionDraft.sizeBytes = ''
  versionDraft.status = firstConfiguredValue(softwareOptions.value.versionStatuses, 'released')
  versionDraft.changelog = ''
}

function resetAssetDraft() {
  assetDraft.name = ''
  assetDraft.versionName = versions.value[0]?.metadata?.name || ''
  assetDraft.title = ''
  assetDraft.url = ''
  assetDraft.type = firstConfiguredValue(softwareOptions.value.downloadTypes, 'dmg')
  assetDraft.provider = firstConfiguredValue(softwareOptions.value.downloadProviders, 'external')
  assetDraft.region = ''
  assetDraft.priority = 0
  assetDraft.enabled = true
}

function openVersionModal() {
  if (!canManageChildren.value) {
    Toast.warning('请先保存软件基础信息')
    return
  }
  resetVersionDraft()
  versionModalVisible.value = true
}

function closeVersionModal() {
  versionModalVisible.value = false
  resetVersionDraft()
}

function openAssetModal() {
  if (!canManageChildren.value) {
    Toast.warning('请先保存软件基础信息')
    return
  }
  if (!versions.value.length) {
    Toast.warning('请先添加版本')
    activeTab.value = 'versions'
    return
  }
  resetAssetDraft()
  assetModalVisible.value = true
}

function closeAssetModal() {
  assetModalVisible.value = false
  resetAssetDraft()
}

async function fetchResourceOptions() {
  resourceLoading.value = true
  try {
    const [optionResult, categoryResult, aiCapabilities] = await Promise.all([
      getSoftwareOptions().catch(() => defaultSoftwareOptions),
      listCategories({ page: 1, size: 200 }),
      getSoftwareAiCapabilities().catch(() => ({ available: false })),
    ])
    softwareOptions.value = normalizeSoftwareOptions(optionResult)
    categories.value = categoryResult.items
    aiAvailable.value = aiCapabilities.available
  } finally {
    resourceLoading.value = false
  }
}

async function fetchEditorData() {
  ensureSeo()
  if (!name.value) {
    app.value = emptySoftwareApp()
    ensureSeo()
    syncDescriptionToEditor()
    markSavedSnapshot()
    versions.value = []
    downloadAssets.value = []
    resetVersionDraft()
    resetAssetDraft()
    return
  }
  loading.value = true
  try {
    app.value = await getApp(name.value)
    ensureSeo()
    syncDescriptionToEditor()
    const currentName = app.value.metadata?.name || name.value
    const [versionResult, assetResult] = await Promise.all([
      listVersions(currentName),
      listDownloadAssets(currentName),
    ])
    versions.value = versionResult.items
    downloadAssets.value = assetResult.items
    if (!assetDraft.versionName) {
      assetDraft.versionName = versions.value[0]?.metadata?.name || ''
    }
    markSavedSnapshot()
  } finally {
    loading.value = false
  }
}

async function saveApp() {
  syncDescriptionFromEditor()
  if (!app.value.spec.displayName || !app.value.spec.slug) {
    Toast.warning('软件名称和公开 slug 必填')
    return
  }
  saving.value = true
  try {
    const saved = name.value ? await updateApp(name.value, app.value) : await createApp(app.value)
    app.value = saved
    ensureSeo()
    name.value = saved.metadata?.name || ''
    Toast.success('保存成功')
    replaceEditorUrl()
    await fetchEditorData()
  } finally {
    saving.value = false
  }
}

async function generateAppSuggestion(target: SoftwareAiTarget) {
  syncDescriptionFromEditor()
  if (!app.value.spec.displayName) {
    Toast.warning('请先填写软件名称')
    activeTab.value = 'basic'
    return
  }
  suggestingApp.value = true
  try {
    const suggestion = await suggestAppByAi({ app: app.value, target })
    applySoftwareAiSuggestion(app.value, target, suggestion)
    if (target === 'description' && suggestion.description) {
      syncDescriptionToEditor()
    }
    Toast.success('AI 建议已填入表单')
    const warning = suggestion.warnings?.find(Boolean)
    if (warning) {
      Toast.warning(warning)
    }
  } catch (error) {
    Toast.error(requestErrorMessage(error, 'AI 生成失败'))
  } finally {
    suggestingApp.value = false
  }
}

async function saveVersion() {
  if (!appName.value) {
    Toast.warning('请先保存软件基础信息')
    return
  }
  if (!versionDraft.version) {
    Toast.warning('版本号必填')
    return
  }
  const payload: SoftwareVersion = {
    spec: {
      appName: appName.value,
      version: versionDraft.version,
      build: versionDraft.build || undefined,
      releaseDate: versionDraft.releaseDate || undefined,
      minOsVersion: versionDraft.minOsVersion || undefined,
      sizeBytes: versionDraft.sizeBytes ? Number(versionDraft.sizeBytes) : undefined,
      changelog: versionDraft.changelog || undefined,
      status: versionDraft.status || undefined,
    },
  }
  versionSaving.value = true
  try {
    if (versionDraft.name) {
      await updateVersion(versionDraft.name, payload)
    } else {
      await createVersion(payload)
    }
    Toast.success('版本已保存')
    closeVersionModal()
    await fetchEditorData()
  } finally {
    versionSaving.value = false
  }
}

function editVersion(version: SoftwareVersion) {
  activeTab.value = 'versions'
  versionDraft.name = version.metadata?.name || ''
  versionDraft.version = version.spec.version
  versionDraft.build = version.spec.build || ''
  versionDraft.releaseDate = version.spec.releaseDate || ''
  versionDraft.minOsVersion = version.spec.minOsVersion || ''
  versionDraft.sizeBytes = version.spec.sizeBytes ? String(version.spec.sizeBytes) : ''
  versionDraft.status = version.spec.status || 'released'
  versionDraft.changelog = version.spec.changelog || ''
  versionModalVisible.value = true
}

function confirmDeleteVersion(version: SoftwareVersion) {
  const versionName = version.metadata?.name
  if (!versionName) {
    return
  }
  Dialog.warning({
    title: '删除版本',
    description: `确定删除版本 ${version.spec.version} 吗？请先确认没有下载资源引用它。`,
    confirmType: 'danger',
    onConfirm: async () => {
      await deleteVersion(versionName)
      Toast.success('版本已删除')
      await fetchEditorData()
    },
  })
}

async function saveAsset() {
  if (!appName.value) {
    Toast.warning('请先保存软件基础信息')
    return
  }
  if (!assetDraft.versionName) {
    Toast.warning('请选择关联版本')
    return
  }
  if (!assetDraft.title || !assetDraft.url) {
    Toast.warning('资源名称和下载 URL 必填')
    return
  }
  const payload: DownloadAsset = {
    spec: {
      appName: appName.value,
      versionName: assetDraft.versionName,
      title: assetDraft.title,
      url: assetDraft.url,
      type: assetDraft.type || undefined,
      provider: assetDraft.provider || undefined,
      region: assetDraft.region || undefined,
      enabled: assetDraft.enabled,
      priority: assetDraft.priority || 0,
    },
  }
  assetSaving.value = true
  try {
    if (assetDraft.name) {
      await updateDownloadAsset(assetDraft.name, payload)
    } else {
      await createDownloadAsset(payload)
    }
    Toast.success('下载资源已保存')
    closeAssetModal()
    await fetchEditorData()
  } finally {
    assetSaving.value = false
  }
}

function editAsset(asset: DownloadAsset) {
  activeTab.value = 'downloads'
  assetDraft.name = asset.metadata?.name || ''
  assetDraft.versionName = asset.spec.versionName
  assetDraft.title = asset.spec.title
  assetDraft.url = asset.spec.url || ''
  assetDraft.type = asset.spec.type || ''
  assetDraft.provider = asset.spec.provider || ''
  assetDraft.region = asset.spec.region || ''
  assetDraft.priority = asset.spec.priority || 0
  assetDraft.enabled = asset.spec.enabled ?? true
  assetModalVisible.value = true
}

function confirmDeleteAsset(asset: DownloadAsset) {
  const assetName = asset.metadata?.name
  if (!assetName) {
    return
  }
  Dialog.warning({
    title: '删除下载资源',
    description: `确定删除「${asset.spec.title}」吗？`,
    confirmType: 'danger',
    onConfirm: async () => {
      await deleteDownloadAsset(assetName)
      Toast.success('下载资源已删除')
      await fetchEditorData()
    },
  })
}

function versionLabel(versionName?: string) {
  return (
    versions.value.find((version) => version.metadata?.name === versionName)?.spec.version ||
    '未关联版本'
  )
}

function formatBytes(size?: number) {
  if (!size) {
    return '未设置大小'
  }
  if (size < 1024 * 1024) {
    return `${Math.round(size / 1024)} KB`
  }
  if (size < 1024 * 1024 * 1024) {
    return `${Math.round(size / 1024 / 1024)} MB`
  }
  return `${(size / 1024 / 1024 / 1024).toFixed(1)} GB`
}

onMounted(async () => {
  setupDescriptionEditor()
  window.addEventListener('beforeunload', handleBeforeUnload)
  await Promise.all([fetchResourceOptions(), fetchEditorData()])
  await fetchSelectedTokenResources()
  markSavedSnapshot()
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
  descriptionEditor.value?.destroy()
})

watch(activeTab, replaceEditorUrl)

onBeforeRouteLeave(() => confirmDiscardChanges())
</script>

<template>
  <VPageHeader :title="editorTitle">
    <template #actions>
      <VSpace>
        <VButton :route="{ name: 'SoftwareList' }">
          <template #icon>
            <IconArrowLeft />
          </template>
          返回
        </VButton>
        <VButton type="secondary" :loading="saving" @click="saveApp">
          <template #icon>
            <IconSave />
          </template>
          保存
        </VButton>
      </VSpace>
    </template>
  </VPageHeader>

  <ConsolePageScaffold>
    <VLoading v-if="loading" />
    <div v-else class="software-editor-layout">
      <aside class="editor-sidebar">
        <section class="editor-overview-card">
          <div class="overview-cover" :class="{ empty: !previewImage }">
            <img
              v-if="previewImage"
              :src="previewImage"
              alt=""
              width="288"
              height="162"
              loading="lazy"
              decoding="async"
            />
            <span v-else>暂无封面</span>
          </div>
          <div class="overview-body">
            <div class="overview-title-row">
              <div class="overview-icon" :class="{ empty: !app.spec.icon }">
                <img
                  v-if="app.spec.icon"
                  :src="app.spec.icon"
                  alt=""
                  width="44"
                  height="44"
                  loading="lazy"
                  decoding="async"
                />
                <span v-else>{{ (app.spec.displayName || '软').slice(0, 1) }}</span>
              </div>
              <div class="overview-title">
                <strong>{{ app.spec.displayName || '未命名软件' }}</strong>
                <small>{{ app.metadata?.name || '保存后生成 metadata.name' }}</small>
              </div>
            </div>
            <div class="overview-status">
              <VStatusDot
                :state="app.spec.published ? 'success' : 'default'"
                :text="app.spec.published ? '已发布' : '草稿'"
              />
              <VTag v-if="app.spec.featured" theme="primary">精选</VTag>
              <VTag v-if="app.spec.recommended" theme="secondary">推荐</VTag>
            </div>
            <dl class="overview-meta">
              <div>
                <dt>分类</dt>
                <dd>{{ selectedCategoryLabel }}</dd>
              </div>
              <div>
                <dt>Slug</dt>
                <dd>{{ app.spec.slug || '未设置' }}</dd>
              </div>
            </dl>
            <div class="overview-metrics">
              <div>
                <strong>{{ versions.length }}</strong>
                <span>版本</span>
              </div>
              <div>
                <strong>{{ downloadAssets.length }}</strong>
                <span>下载源</span>
              </div>
              <div>
                <strong>{{ screenshotsCount }}</strong>
                <span>截图</span>
              </div>
            </div>
          </div>
        </section>

        <nav class="editor-nav-card" aria-label="软件编辑步骤">
          <button
            v-for="(tab, index) in tabs"
            :key="tab.key"
            :class="{ active: activeTab === tab.key }"
            :aria-current="activeTab === tab.key ? 'step' : undefined"
            type="button"
            @click="activeTab = tab.key"
          >
            <span class="nav-index">{{ index + 1 }}</span>
            <span class="nav-copy">
              <strong>{{ tab.label }}</strong>
              <small>{{ tab.description }}</small>
            </span>
          </button>
        </nav>

        <section class="editor-progress-card">
          <div class="progress-heading">
            <span>编辑完成度</span>
            <strong>{{ editorChecklistDone }}/{{ editorChecklist.length }}</strong>
          </div>
          <ul>
            <li v-for="item in editorChecklist" :key="item.label" :class="{ done: item.done }">
              <span />
              {{ item.label }}
            </li>
          </ul>
        </section>
      </aside>

      <ConsoleTableCard class="editor-main-card" body-class="editor-card-body">
        <div class="content-header">
          <div class="content-title">
            <span>当前步骤</span>
            <h2>{{ currentTab.label }}</h2>
            <p>{{ currentTab.description }}</p>
          </div>
          <div v-if="activeTab === 'versions' || activeTab === 'downloads'" class="content-actions">
            <VButton
              v-if="activeTab === 'versions'"
              type="secondary"
              :disabled="!canManageChildren"
              @click="openVersionModal"
            >
              <template #icon>
                <IconAddCircle />
              </template>
              添加版本
            </VButton>
            <VButton
              v-else
              type="secondary"
              :disabled="!canManageChildren || !versions.length"
              @click="openAssetModal"
            >
              <template #icon>
                <IconAddCircle />
              </template>
              添加下载源
            </VButton>
          </div>
        </div>

        <section v-if="activeTab === 'basic'" class="tab-panel">
          <div class="section-heading section-heading--with-action">
            <strong>基础资料</strong>
            <span>先把可检索、可归类的信息填完整，媒体和长描述放到下一步处理。</span>
            <VButton
              v-if="aiAvailable"
              size="sm"
              type="secondary"
              :loading="suggestingApp"
              @click="generateAppSuggestion('basic')"
            >
              <template #icon>
                <IconMagic />
              </template>
              AI 生成
            </VButton>
          </div>
          <div class="form-grid">
            <label>
              <span>软件名称 *</span>
              <input
                v-model.trim="app.spec.displayName"
                class="field"
                name="software-display-name"
                autocomplete="off"
              />
            </label>
            <label>
              <span>公开 slug *</span>
              <input
                v-model.trim="app.spec.slug"
                class="field"
                name="software-slug"
                autocomplete="off"
                inputmode="url"
              />
            </label>
            <label>
              <span>副标题</span>
              <input
                v-model.trim="app.spec.subtitle"
                class="field"
                name="software-subtitle"
                autocomplete="off"
              />
            </label>
            <label>
              <span>厂商</span>
              <input
                v-model.trim="app.spec.vendor"
                class="field"
                name="software-vendor"
                autocomplete="organization"
              />
            </label>
            <label>
              <span>官网</span>
              <input
                v-model.trim="app.spec.officialWebsite"
                class="field"
                name="software-official-website"
                type="url"
                inputmode="url"
                autocomplete="url"
              />
            </label>
            <div class="relation-field">
              <span>平台</span>
              <ConsoleTokenSelector
                v-model="platformModel"
                :options="platformOptions"
                placeholder="选择平台 / 系统"
                empty-text="没有匹配的平台"
              />
            </div>
            <label>
              <span>分类</span>
              <ConsoleFilterSelect
                v-model="categoryModel"
                class="editor-select"
                :options="categorySelectOptions"
                aria-label="选择分类"
              />
            </label>
            <label>
              <span>授权类型</span>
              <input
                v-model.trim="app.spec.licenseType"
                class="field"
                name="software-license-type"
                autocomplete="off"
              />
            </label>
          </div>

          <p v-if="selectedCategory" class="muted-line">
            当前分类公开路径：/{{ selectedCategory.spec.slug }}
          </p>
          <p v-else-if="resourceLoading" class="muted-line">正在加载分类和标签...</p>

          <div class="block-field">
            <span>标签</span>
            <ConsoleTokenSelector
              :model-value="app.spec.tagNames"
              :options="tagOptions"
              :creating="tagCreating"
              :loading="tagSearching"
              allow-create
              placeholder="输入标签名称搜索或创建"
              empty-text="没有匹配的标签"
              create-text="创建标签"
              @update:model-value="setTagNames"
              @create="createAndSelectTag"
              @search="searchTags"
            />
          </div>

          <label class="block-field">
            <span>摘要</span>
            <textarea v-model.trim="app.spec.summary" class="textarea" rows="3" />
          </label>
        </section>

        <section v-else-if="activeTab === 'media'" class="tab-panel">
          <div class="media-workbench">
            <div class="media-assets-grid">
              <section class="media-asset-card media-asset-card--icon">
                <header class="media-card-header">
                  <div>
                    <strong>软件图标</strong>
                    <span>列表、下载源和详情页的主识别图。</span>
                  </div>
                  <span class="media-card-badge">1:1</span>
                </header>
                <ConsoleImagePicker
                  class="icon-picker"
                  v-model="app.spec.icon"
                  label="软件图标"
                  description="用于列表、下载源和详情页展示。"
                  empty-text="选择图标"
                  preview-variant="square"
                />
              </section>

              <section class="media-asset-card media-asset-card--cover">
                <header class="media-card-header">
                  <div>
                    <strong>封面图</strong>
                    <span>公开详情页的顶部主视觉，建议使用横向图片。</span>
                  </div>
                  <span class="media-card-badge">16:9</span>
                </header>
                <ConsoleImagePicker
                  class="cover-picker"
                  v-model="app.spec.cover"
                  label="封面图"
                  description="建议使用横向截图或产品主视觉。"
                  empty-text="选择封面"
                  preview-variant="cover"
                />
              </section>
            </div>

            <section class="media-asset-card media-asset-card--screenshots">
              <header class="media-card-header">
                <div>
                  <strong>截图组</strong>
                  <span>支持多张图片，拖拽调整顺序后会写入 screenshots 数组。</span>
                </div>
                <span class="media-card-badge">{{ screenshotsCount }}/12</span>
              </header>
              <ConsoleImagePicker
                class="screenshots-picker"
                v-model="app.spec.screenshots"
                label="截图"
                description="支持多张图片，按当前顺序保存到 screenshots 数组。"
                empty-text="添加截图"
                multiple
                :max="12"
              />
            </section>

            <section class="media-asset-card media-asset-card--description">
              <header class="media-card-header">
                <div>
                  <strong>详情正文</strong>
                  <span>用于软件公开详情页正文，可插入说明、安装步骤和版本提示。</span>
                </div>
                <VButton
                  v-if="aiAvailable"
                  size="sm"
                  type="secondary"
                  :loading="suggestingApp"
                  @click="generateAppSuggestion('description')"
                >
                  <template #icon>
                    <IconMagic />
                  </template>
                  AI 生成
                </VButton>
              </header>
              <div class="richtext-field">
                <RichTextEditor v-if="descriptionEditor" :editor="descriptionEditor" />
                <VLoading v-else />
              </div>
            </section>
          </div>
        </section>

        <section v-else-if="activeTab === 'seo'" class="tab-panel">
          <div class="section-heading section-heading--with-action">
            <strong>SEO 信息</strong>
            <span>用于软件公开详情页和搜索索引。</span>
            <VButton
              v-if="aiAvailable"
              size="sm"
              type="secondary"
              :loading="suggestingApp"
              @click="generateAppSuggestion('seo')"
            >
              <template #icon>
                <IconMagic />
              </template>
              AI 生成
            </VButton>
          </div>
          <div class="form-grid">
            <label>
              <span>SEO 标题</span>
              <input
                v-model.trim="app.spec.seo!.title"
                class="field"
                name="software-seo-title"
                autocomplete="off"
              />
            </label>
            <label>
              <span>关键词</span>
              <input
                :value="seoKeywordsText"
                class="field"
                name="software-seo-keywords"
                placeholder="mac, utility, cleaner"
                autocomplete="off"
                @input="setSeoKeywords(($event.target as HTMLInputElement).value)"
              />
            </label>
          </div>
          <label class="block-field">
            <span>SEO 描述</span>
            <textarea v-model.trim="app.spec.seo!.description" class="textarea" rows="5" />
          </label>
        </section>

        <section v-else-if="activeTab === 'versions'" class="tab-panel">
          <div class="list-toolbar">
            <div>
              <strong>版本列表</strong>
              <span>{{
                canManageChildren ? '每个下载源都需要关联一个版本。' : '保存软件后才能添加版本。'
              }}</span>
            </div>
          </div>

          <VEmpty v-if="!versions.length" title="暂无版本" message="保存软件后即可添加版本。" />
          <div v-else class="table-wrap">
            <table class="data-table">
              <thead>
                <tr>
                  <th>版本号</th>
                  <th>发布日期</th>
                  <th>系统要求</th>
                  <th>大小</th>
                  <th>状态</th>
                  <th class="actions-col">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="version in versions" :key="version.metadata?.name">
                  <td>
                    <strong>{{ version.spec.version }}</strong>
                    <small v-if="version.spec.build">build {{ version.spec.build }}</small>
                  </td>
                  <td>{{ version.spec.releaseDate || '未设置' }}</td>
                  <td>{{ version.spec.minOsVersion || '未设置' }}</td>
                  <td>{{ formatBytes(version.spec.sizeBytes) }}</td>
                  <td>
                    <VStatusDot
                      :state="version.spec.status === 'released' ? 'success' : 'default'"
                      :text="versionStatusText(version.spec.status)"
                    />
                  </td>
                  <td class="actions-col">
                    <VSpace>
                      <VButton size="sm" @click="editVersion(version)">
                        <template #icon>
                          <IconRiPencilFill />
                        </template>
                        编辑
                      </VButton>
                      <VButton size="sm" type="danger" @click="confirmDeleteVersion(version)">
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

        <section v-else-if="activeTab === 'downloads'" class="tab-panel">
          <div class="list-toolbar">
            <div>
              <strong>下载源列表</strong>
              <span>{{
                versions.length ? '优先级越高越靠前，公开接口会返回可用资源。' : '请先添加版本。'
              }}</span>
            </div>
          </div>

          <VEmpty
            v-if="!downloadAssets.length"
            title="暂无下载资源"
            message="公开下载接口会返回优先级最高的启用资源。"
          />
          <div v-else class="table-wrap">
            <table class="data-table">
              <thead>
                <tr>
                  <th>资源名称</th>
                  <th>版本</th>
                  <th>类型</th>
                  <th>Provider</th>
                  <th>优先级</th>
                  <th>状态</th>
                  <th class="actions-col">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="asset in downloadAssets" :key="asset.metadata?.name">
                  <td>
                    <strong>{{ asset.spec.title }}</strong>
                    <small>{{ asset.spec.url || '未设置 URL' }}</small>
                  </td>
                  <td>{{ versionLabel(asset.spec.versionName) }}</td>
                  <td>{{ assetTypeText(asset.spec.type) }}</td>
                  <td>{{ assetProviderText(asset.spec.provider) }}</td>
                  <td>{{ asset.spec.priority || 0 }}</td>
                  <td>
                    <VStatusDot
                      :state="asset.spec.enabled === false ? 'default' : 'success'"
                      :text="asset.spec.enabled === false ? '停用' : '启用'"
                    />
                  </td>
                  <td class="actions-col">
                    <VSpace>
                      <VButton size="sm" @click="editAsset(asset)">
                        <template #icon>
                          <IconRiPencilFill />
                        </template>
                        编辑
                      </VButton>
                      <VButton size="sm" type="danger" @click="confirmDeleteAsset(asset)">
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

        <section v-else class="tab-panel">
          <div class="section-heading">
            <strong>发布设置</strong>
            <span>控制公开状态、推荐位、发布频道和关联内容。</span>
          </div>
          <div class="switch-card-grid">
            <label class="switch-card" :class="{ active: app.spec.published }">
              <VSwitch v-model="app.spec.published" />
              <span>
                <strong>发布</strong>
                <small>允许公开访问</small>
              </span>
            </label>
            <label class="switch-card" :class="{ active: app.spec.featured }">
              <VSwitch v-model="app.spec.featured" />
              <span>
                <strong>精选</strong>
                <small>进入精选位</small>
              </span>
            </label>
            <label class="switch-card" :class="{ active: app.spec.recommended }">
              <VSwitch v-model="app.spec.recommended" />
              <span>
                <strong>推荐</strong>
                <small>进入推荐位</small>
              </span>
            </label>
          </div>
          <div class="form-grid settings-grid">
            <label>
              <span>发布时间</span>
              <input
                v-model.trim="app.spec.publishedAt"
                class="field"
                name="software-published-at"
                placeholder="2026-06-01T10:00:00Z"
                autocomplete="off"
              />
            </label>
            <label>
              <span>发布频道</span>
              <ConsoleFilterSelect
                v-model="releaseChannelModel"
                class="editor-select"
                :options="releaseChannelOptions"
                aria-label="选择发布频道"
              />
            </label>
            <label>
              <span>语言区域</span>
              <ConsoleFilterSelect
                v-model="localeModel"
                class="editor-select"
                :options="localeOptions"
                aria-label="选择语言区域"
              />
            </label>
            <label>
              <span>编辑评分</span>
              <input
                v-model.number="app.spec.editorRating"
                class="field"
                name="software-editor-rating"
                max="10"
                min="0"
                type="number"
                inputmode="decimal"
              />
            </label>
            <div class="relation-field">
              <span>架构</span>
              <ConsoleTokenSelector
                v-model="architectureModel"
                :options="architectureOptions"
                placeholder="选择架构"
                empty-text="没有匹配的架构"
              />
            </div>
            <div class="relation-field">
              <span>关联软件</span>
              <ConsoleTokenSelector
                :model-value="app.spec.relatedAppNames"
                :options="relatedAppOptions"
                :loading="appSearching"
                placeholder="输入软件名称搜索"
                empty-text="没有匹配的软件"
                @update:model-value="setRelatedApps"
                @search="searchRelatedApps"
              />
            </div>
            <div class="relation-field">
              <span>关联文章</span>
              <ConsoleTokenSelector
                :model-value="app.spec.relatedPostNames"
                :options="relatedPostOptions"
                :loading="postSearching"
                placeholder="输入文章标题搜索"
                empty-text="没有匹配的文章"
                @update:model-value="setRelatedPosts"
                @search="searchRelatedPosts"
              />
            </div>
          </div>
        </section>
        <div class="editor-flow-footer">
          <VButton :disabled="!hasPreviousTab" @click="goPreviousTab">上一步</VButton>
          <VSpace>
            <VButton type="secondary" :loading="saving" @click="saveApp">
              <template #icon>
                <IconSave />
              </template>
              保存修改
            </VButton>
            <VButton :disabled="!hasNextTab" @click="goNextTab">下一步</VButton>
          </VSpace>
        </div>
      </ConsoleTableCard>
    </div>
  </ConsolePageScaffold>

  <VModal
    v-if="versionModalVisible"
    :title="versionDraft.name ? '编辑版本' : '添加版本'"
    :width="760"
    :body-class="['software-editor-modal-body']"
    @close="closeVersionModal"
  >
    <div class="modal-form">
      <div class="form-grid">
        <label>
          <span>版本号 *</span>
          <input
            v-model.trim="versionDraft.version"
            class="field"
            name="software-version"
            placeholder="1.0.0"
            autocomplete="off"
          />
        </label>
        <label>
          <span>构建号</span>
          <input
            v-model.trim="versionDraft.build"
            class="field"
            name="software-version-build"
            placeholder="build 123"
            autocomplete="off"
          />
        </label>
        <label>
          <span>发布日期</span>
          <input
            v-model="versionDraft.releaseDate"
            class="field"
            name="software-version-release-date"
            type="date"
          />
        </label>
        <label>
          <span>最低系统</span>
          <input
            v-model.trim="versionDraft.minOsVersion"
            class="field"
            name="software-version-min-os"
            placeholder="macOS 13.0"
            autocomplete="off"
          />
        </label>
        <label>
          <span>大小 bytes</span>
          <input
            v-model.trim="versionDraft.sizeBytes"
            class="field"
            name="software-version-size-bytes"
            placeholder="104857600"
            type="number"
            min="0"
            inputmode="numeric"
          />
        </label>
        <label>
          <span>状态</span>
          <ConsoleFilterSelect
            v-model="versionDraft.status"
            class="editor-select"
            :options="versionStatusOptions"
            aria-label="选择版本状态"
          />
        </label>
      </div>
      <label class="block-field">
        <span>更新说明</span>
        <textarea v-model.trim="versionDraft.changelog" class="textarea" rows="5" />
      </label>
    </div>
    <template #footer>
      <VSpace>
        <VButton type="secondary" :loading="versionSaving" @click="saveVersion">
          {{ versionDraft.name ? '保存版本' : '添加版本' }}
        </VButton>
        <VButton @click="closeVersionModal">取消</VButton>
      </VSpace>
    </template>
  </VModal>

  <VModal
    v-if="assetModalVisible"
    :title="assetDraft.name ? '编辑下载源' : '添加下载源'"
    :width="820"
    :body-class="['software-editor-modal-body']"
    @close="closeAssetModal"
  >
    <div class="modal-form">
      <div class="form-grid">
        <label>
          <span>关联版本 *</span>
          <ConsoleFilterSelect
            v-model="assetDraft.versionName"
            class="editor-select"
            :options="versionSelectOptions"
            aria-label="选择关联版本"
          />
        </label>
        <label>
          <span>资源名称 *</span>
          <input
            v-model.trim="assetDraft.title"
            class="field"
            name="download-asset-title"
            placeholder="安装包 / 网盘链接"
            autocomplete="off"
          />
        </label>
        <label class="full-span">
          <span>下载 URL *</span>
          <input
            v-model.trim="assetDraft.url"
            class="field"
            name="download-asset-url"
            placeholder="https://..."
            type="url"
            inputmode="url"
            autocomplete="url"
          />
        </label>
        <label>
          <span>类型</span>
          <ConsoleFilterSelect
            v-model="assetDraft.type"
            class="editor-select"
            :options="assetTypeOptions"
            aria-label="选择下载类型"
          />
        </label>
        <label>
          <span>Provider</span>
          <ConsoleFilterSelect
            v-model="assetDraft.provider"
            class="editor-select"
            :options="assetProviderOptions"
            aria-label="选择下载源 Provider"
          />
        </label>
        <label>
          <span>Region</span>
          <input
            v-model.trim="assetDraft.region"
            class="field"
            name="download-asset-region"
            placeholder="cn / global"
            autocomplete="off"
          />
        </label>
        <label>
          <span>优先级</span>
          <input
            v-model.number="assetDraft.priority"
            class="field"
            name="download-asset-priority"
            type="number"
            inputmode="numeric"
          />
        </label>
      </div>
      <label class="switch-card modal-switch-card" :class="{ active: assetDraft.enabled }">
        <VSwitch v-model="assetDraft.enabled" />
        <span>
          <strong>启用下载源</strong>
          <small>停用后不会作为公开下载资源返回</small>
        </span>
      </label>
    </div>
    <template #footer>
      <VSpace>
        <VButton type="secondary" :loading="assetSaving" @click="saveAsset">
          {{ assetDraft.name ? '保存下载源' : '添加下载源' }}
        </VButton>
        <VButton @click="closeAssetModal">取消</VButton>
      </VSpace>
    </template>
  </VModal>
</template>

<style scoped lang="scss">
:deep(.editor-card-body) {
  padding: 0;
}

.software-editor-layout {
  display: grid;
  grid-template-columns: 304px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.editor-sidebar {
  position: sticky;
  top: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.editor-overview-card,
.editor-nav-card,
.editor-progress-card {
  overflow: hidden;
  border: 1px solid #e3e9f1;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 10px 28px rgba(18, 30, 45, 0.035);
}

.overview-cover {
  display: grid;
  min-height: 128px;
  overflow: hidden;
  place-items: center;
  background: #eef2f7;
  color: #94a3b8;
  font-size: 13px;
}

.overview-cover img {
  width: 100%;
  height: 128px;
  display: block;
  object-fit: cover;
}

.overview-cover.empty {
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.08), transparent), #f8fafc;
}

.overview-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 14px;
}

.overview-title-row {
  display: grid;
  grid-template-columns: 46px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
}

.overview-icon {
  display: grid;
  width: 46px;
  height: 46px;
  overflow: hidden;
  border: 1px solid #dbe3ee;
  border-radius: 10px;
  place-items: center;
  background: #f8fafc;
  color: #2563eb;
  font-weight: 700;
}

.overview-icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.overview-title {
  min-width: 0;
}

.overview-title strong,
.overview-title small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.overview-title strong {
  color: #111827;
  font-size: 15px;
  line-height: 1.35;
}

.overview-title small,
.overview-meta dt,
.overview-metrics span,
.progress-heading span,
.editor-progress-card li {
  color: #64748b;
  font-size: 12px;
}

.overview-status {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.overview-meta {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
  margin: 0;
}

.overview-meta div {
  display: grid;
  grid-template-columns: 46px minmax(0, 1fr);
  gap: 8px;
  align-items: baseline;
}

.overview-meta dt,
.overview-meta dd {
  margin: 0;
}

.overview-meta dd {
  overflow: hidden;
  color: #334155;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.overview-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  overflow: hidden;
  border: 1px solid #edf2f7;
  border-radius: 8px;
}

.overview-metrics div {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 8px;
  border-right: 1px solid #edf2f7;
  background: #fbfcfd;
}

.overview-metrics div:last-child {
  border-right: 0;
}

.overview-metrics strong {
  color: #111827;
  font-size: 16px;
}

.editor-nav-card {
  display: flex;
  flex-direction: column;
  padding: 6px;
}

.editor-nav-card button {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  min-height: 58px;
  border: 0;
  border-radius: 7px;
  padding: 8px;
  background: transparent;
  color: #334155;
  text-align: left;
  cursor: pointer;
}

.editor-nav-card button:hover {
  background: #f8fafc;
}

.editor-nav-card button:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.16);
}

.editor-nav-card button.active {
  background: #eff6ff;
  color: #1d4ed8;
}

.nav-index {
  display: grid;
  width: 28px;
  height: 28px;
  border: 1px solid #dbe3ee;
  border-radius: 7px;
  place-items: center;
  background: #fff;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.editor-nav-card button.active .nav-index {
  border-color: #bfdbfe;
  background: #2563eb;
  color: #fff;
}

.nav-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.nav-copy strong,
.nav-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-copy strong {
  font-size: 13px;
}

.nav-copy small {
  color: #64748b;
  font-size: 12px;
}

.editor-progress-card {
  padding: 14px;
}

.progress-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.progress-heading strong {
  color: #1d4ed8;
  font-size: 13px;
}

.editor-progress-card ul {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.editor-progress-card li {
  display: flex;
  align-items: center;
  gap: 8px;
}

.editor-progress-card li span {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #cbd5e1;
}

.editor-progress-card li.done {
  color: #334155;
}

.editor-progress-card li.done span {
  background: #2563eb;
}

.editor-main-card {
  min-width: 0;
}

.content-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  border-bottom: 1px solid #eef0f3;
  padding: 18px 20px;
  background: #fff;
}

.content-title {
  min-width: 0;
}

.content-title span {
  color: #64748b;
  font-size: 12px;
}

.content-title h2 {
  margin: 3px 0 2px;
  color: #111827;
  font-size: 20px;
  line-height: 1.25;
}

.content-title p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.content-actions {
  flex-shrink: 0;
}

.list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
  border: 1px solid #e3e9f1;
  border-radius: 8px;
  padding: 12px 14px;
  background: #fbfcfd;
}

.list-toolbar div {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.list-toolbar strong {
  color: #111827;
  font-size: 14px;
}

.list-toolbar span {
  color: #64748b;
  font-size: 12px;
}

.modal-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

:global(.software-editor-modal-body) {
  max-height: calc(100vh - 220px);
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-right: 18px;
}

:global(.software-editor-modal-body::-webkit-scrollbar) {
  width: 8px;
}

:global(.software-editor-modal-body::-webkit-scrollbar-track) {
  background: transparent;
}

:global(.software-editor-modal-body::-webkit-scrollbar-thumb) {
  border: 2px solid #fff;
  border-radius: 999px;
  background: #cbd5e1;
}

.full-span {
  grid-column: 1 / -1;
}

.switch-card-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.switch-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  padding: 12px;
  background: #fff;
}

.switch-card.active {
  border-color: #bfdbfe;
  background: #eff6ff;
}

.switch-card :deep(.switch-inner) {
  background: #cbd5e1 !important;
}

.switch-card.active :deep(.switch-inner) {
  background: #2563eb !important;
}

.switch-card strong,
.switch-card small {
  display: block;
}

.switch-card strong {
  color: #111827;
  font-size: 13px;
}

.switch-card small {
  margin-top: 2px;
  color: #64748b;
  font-size: 12px;
}

.modal-switch-card {
  margin-top: 2px;
}

.editor-select {
  min-width: 0;
}

.editor-select :deep(.console-filter-select) {
  height: 34px;
  border-color: #d1d5db;
  border-radius: 6px;
  color: #111827;
  font-size: 14px;
  font-weight: 400;
}

.media-workbench {
  display: grid;
  gap: 14px;
}

.media-assets-grid {
  display: grid;
  grid-template-columns: minmax(180px, 244px) minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.media-asset-card {
  min-width: 0;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 14px;
  background: #fff;
}

.media-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.media-card-header > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.media-card-header strong {
  color: #111827;
  font-size: 14px;
}

.media-card-header span:not(.media-card-badge) {
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
}

.media-card-badge {
  flex: 0 0 auto;
  border: 1px solid #dbeafe;
  border-radius: 999px;
  padding: 3px 8px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.25;
}

.media-asset-card :deep(.picker-heading) {
  display: none;
}

.icon-picker :deep(.image-grid),
.icon-picker :deep(.empty-picker) {
  max-width: 172px;
}

.icon-picker :deep(.image-grid) {
  margin-inline: auto;
}

.icon-picker :deep(.image-preview),
.icon-picker :deep(.empty-picker) {
  aspect-ratio: 1;
}

.cover-picker :deep(.image-preview),
.cover-picker :deep(.empty-picker) {
  aspect-ratio: 16 / 9;
}

.screenshots-picker :deep(.image-grid) {
  grid-template-columns: repeat(auto-fill, minmax(178px, 1fr));
}

.screenshots-picker :deep(.image-preview),
.screenshots-picker :deep(.add-card) {
  aspect-ratio: 16 / 10;
}

.screenshots-picker :deep(.add-card) {
  min-height: auto;
}

.media-asset-card--description .richtext-field {
  min-height: 320px;
  margin-top: 4px;
}

.section-heading span,
.muted-line {
  color: #6b7280;
  font-size: 12px;
}

.tab-panel {
  padding: 20px;
}

.section-heading {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 16px;
}

.section-heading--with-action {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  column-gap: 12px;
}

.section-heading--with-action strong,
.section-heading--with-action span {
  grid-column: 1;
}

.section-heading--with-action > :deep(button) {
  grid-column: 2;
  grid-row: 1 / span 2;
}

.section-heading strong {
  color: #111827;
  font-size: 15px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

label,
.block-field,
.relation-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  color: #374151;
  font-size: 13px;
}

.field,
.textarea {
  width: 100%;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #111827;
  font-size: 14px;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}

.field {
  height: 34px;
  padding: 0 10px;
}

.textarea {
  padding: 10px;
  resize: vertical;
}

.field:focus,
.textarea:focus {
  border-color: #60a5fa;
  box-shadow: 0 0 0 3px rgba(96, 165, 250, 0.16);
  outline: none;
}

.block-field,
.muted-line {
  margin-top: 14px;
}

.relation-field {
  grid-column: 1 / -1;
}

.richtext-field {
  min-height: 280px;
  overflow: hidden;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
}

.richtext-field :deep(.ProseMirror) {
  min-height: 220px;
}

.settings-grid {
  margin-top: 12px;
}

.table-wrap {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  min-width: 780px;
  border-collapse: collapse;
  border-top: 1px solid #eef2f7;
  background: #fff;
}

.data-table th {
  height: 40px;
  border-bottom: 1px solid #eef2f7;
  padding: 0 12px;
  background: #fbfcfd;
  color: #6b7280;
  font-size: 12px;
  font-weight: 600;
  text-align: left;
}

.data-table td {
  border-bottom: 1px solid #f1f5f9;
  padding: 12px;
  color: #1f2937;
  font-size: 13px;
  vertical-align: middle;
}

.data-table td strong,
.data-table td small {
  display: block;
}

.data-table td small {
  max-width: 360px;
  overflow: hidden;
  color: #6b7280;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.actions-col {
  width: 170px;
  text-align: right;
}

.actions-col :deep(.space) {
  justify-content: flex-end;
}

.editor-flow-footer {
  display: flex;
  position: sticky;
  bottom: 0;
  z-index: 2;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-top: 1px solid #eef0f3;
  padding: 14px 20px;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(8px);
}

@media (max-width: 1120px) {
  .software-editor-layout {
    grid-template-columns: 1fr;
  }

  .editor-sidebar {
    position: static;
  }

  .editor-nav-card {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .media-assets-grid {
    grid-template-columns: minmax(0, 220px) minmax(0, 1fr);
  }
}

@media (max-width: 720px) {
  .content-header,
  .list-toolbar,
  .editor-flow-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .editor-nav-card,
  .switch-card-grid {
    grid-template-columns: 1fr;
  }

  .section-heading--with-action {
    grid-template-columns: minmax(0, 1fr);
  }

  .section-heading--with-action > :deep(button) {
    grid-column: 1;
    grid-row: auto;
    justify-self: flex-start;
    margin-top: 4px;
  }

  .form-grid,
  .media-assets-grid {
    grid-template-columns: 1fr;
  }

  .icon-picker :deep(.image-grid),
  .icon-picker :deep(.empty-picker) {
    max-width: 180px;
  }

  .tab-panel {
    padding: 16px;
  }
}
</style>
