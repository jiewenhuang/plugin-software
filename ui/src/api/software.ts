import { axiosInstance, consoleApiClient, coreApiClient } from '@halo-dev/api-client'

export interface ExtensionMetadata {
  name?: string
  generateName?: string
  creationTimestamp?: string
  deletionTimestamp?: string
  version?: number
}

export interface SoftwareSeo {
  title?: string
  description?: string
  keywords?: string[]
}

export interface SoftwareAppAiSuggestion {
  summary?: string
  description?: string
  seo?: SoftwareSeo
  warnings?: string[]
}

export type SoftwareAiTarget = 'all' | 'basic' | 'description' | 'seo'

export interface SoftwareAppSpec {
  slug: string
  displayName: string
  subtitle?: string
  vendor?: string
  officialWebsite?: string
  icon?: string
  cover?: string
  screenshots?: string[]
  summary?: string
  description?: string
  categoryName?: string
  tagNames?: string[]
  platform?: string
  licenseType?: string
  releaseChannel?: string
  locale?: string
  architectures?: string[]
  editorRating?: number
  featured?: boolean
  recommended?: boolean
  published?: boolean
  publishedAt?: string
  seo?: SoftwareSeo
  relatedAppNames?: string[]
  relatedPostNames?: string[]
}

export interface SoftwareAppStatus {
  latestVersionName?: string
  latestVersion?: string
  latestReleaseDate?: string
  minOsVersion?: string
  sizeBytes?: number
  downloadCount?: number
  likeCount?: number
  commentCount?: number
  health?: string
}

export interface SoftwareApp {
  apiVersion?: string
  kind?: string
  metadata?: ExtensionMetadata
  spec: SoftwareAppSpec
  status?: SoftwareAppStatus
}

export interface SoftwareVersion {
  metadata?: ExtensionMetadata
  spec: {
    appName: string
    version: string
    build?: string
    releaseDate?: string
    sizeBytes?: number
    minOsVersion?: string
    maxOsVersion?: string
    architectures?: string[]
    locale?: string
    changelog?: string
    installGuide?: string
    installWarnings?: string[]
    status?: string
  }
  status?: {
    assetCount?: number
    downloadCount?: number
    primaryAssetName?: string
  }
}

export interface DownloadAsset {
  metadata?: ExtensionMetadata
  spec: {
    appName: string
    versionName: string
    title: string
    type?: string
    provider?: string
    url?: string
    storageKey?: string
    extractionCode?: string
    passwordSecretName?: string
    requiresLogin?: boolean
    requiresCaptcha?: boolean
    region?: string
    priority?: number
    enabled?: boolean
  }
  status?: {
    health?: string
    lastCheckedAt?: string
    downloadCount?: number
  }
}

export interface SoftwareCategory {
  metadata?: ExtensionMetadata
  spec: {
    slug: string
    displayName: string
    parentName?: string
    description?: string
    icon?: string
    priority?: number
    visible?: boolean
    seo?: SoftwareSeo
  }
  status?: {
    appCount?: number
  }
}

export interface SoftwareTag {
  metadata?: ExtensionMetadata
  spec: {
    slug: string
    displayName: string
    description?: string
    color?: string
    priority?: number
    visible?: boolean
    seo?: SoftwareSeo
  }
  status?: {
    appCount?: number
  }
}

export interface SoftwareTopicItem {
  appName: string
  reason?: string
  priority?: number
}

export interface SoftwareTopic {
  metadata?: ExtensionMetadata
  spec: {
    slug: string
    title: string
    subtitle?: string
    description?: string
    cover?: string
    items?: SoftwareTopicItem[]
    featured?: boolean
    published?: boolean
    priority?: number
    seo?: SoftwareSeo
  }
  status?: {
    appCount?: number
  }
}

export interface RelatedPost {
  metadata?: ExtensionMetadata
  spec: {
    title: string
    slug?: string
  }
  status?: {
    permalink?: string
  }
}

export interface SoftwareOptionItem {
  label: string
  value: string
  shortLabel?: string
  className?: string
}

export interface SoftwareOptions {
  platforms: SoftwareOptionItem[]
  architectures: SoftwareOptionItem[]
  versionStatuses: SoftwareOptionItem[]
  downloadTypes: SoftwareOptionItem[]
  downloadProviders: SoftwareOptionItem[]
  releaseChannels: SoftwareOptionItem[]
  locales: SoftwareOptionItem[]
}

export interface SoftwareConsoleStats {
  total: number
  published: number
  drafts: number
  downloads: number
  platforms: string[]
}

export interface ListResult<T> {
  page: number
  size: number
  total: number
  items: T[]
  first?: boolean
  last?: boolean
  hasNext?: boolean
  hasPrevious?: boolean
  totalPages?: number
}

const base = '/apis/console.api.software.jiewen.run/v1alpha1'

export function getSoftwareOptions() {
  return axiosInstance.get<SoftwareOptions>(`${base}/options`).then((response) => response.data)
}

export function getSoftwareAppStats() {
  return axiosInstance
    .get<SoftwareConsoleStats>(`${base}/apps/stats`)
    .then((response) => response.data)
}

export function emptySoftwareApp(): SoftwareApp {
  return {
    spec: {
      slug: '',
      displayName: '',
      published: false,
      featured: false,
      recommended: false,
      tagNames: [],
      architectures: [],
      seo: {},
    },
  }
}

export function listApps(params: {
  page: number
  size: number
  keyword?: string
  published?: string
  category?: string
  platform?: string
}) {
  return axiosInstance
    .get<ListResult<SoftwareApp>>(`${base}/apps`, { params })
    .then((response) => response.data)
}

export function listRelatedPosts(params: { page: number; size: number; keyword?: string }) {
  return consoleApiClient.content.post
    .listPosts({
      page: params.page,
      size: params.size,
      keyword: params.keyword,
      sort: ['metadata.creationTimestamp,desc'],
    })
    .then((response) => response.data as unknown as ListResult<RelatedPost>)
}

export function getRelatedPost(name: string) {
  return coreApiClient.content.post
    .getPost({ name })
    .then((response) => response.data as unknown as RelatedPost)
}

export function getApp(name: string) {
  return axiosInstance.get<SoftwareApp>(`${base}/apps/${name}`).then((response) => response.data)
}

export function createApp(app: SoftwareApp) {
  return axiosInstance.post<SoftwareApp>(`${base}/apps`, app).then((response) => response.data)
}

export function updateApp(name: string, app: SoftwareApp) {
  return axiosInstance
    .put<SoftwareApp>(`${base}/apps/${name}`, app)
    .then((response) => response.data)
}

export function deleteApp(name: string) {
  return axiosInstance.delete<SoftwareApp>(`${base}/apps/${name}`).then((response) => response.data)
}

export function suggestAppByAi(payload: {
  app: SoftwareApp
  instruction?: string
  target?: SoftwareAiTarget
}) {
  return axiosInstance
    .post<SoftwareAppAiSuggestion>(`${base}/ai/apps/suggestions`, payload)
    .then((response) => response.data)
}

export function listVersions(appName: string) {
  return axiosInstance
    .get<ListResult<SoftwareVersion>>(`${base}/versions`, {
      params: { page: 1, size: 1000, appName },
    })
    .then((response) => response.data)
}

export function createVersion(version: SoftwareVersion) {
  return axiosInstance
    .post<SoftwareVersion>(`${base}/versions`, version)
    .then((response) => response.data)
}

export function updateVersion(name: string, version: SoftwareVersion) {
  return axiosInstance
    .put<SoftwareVersion>(`${base}/versions/${name}`, version)
    .then((response) => response.data)
}

export function deleteVersion(name: string) {
  return axiosInstance
    .delete<SoftwareVersion>(`${base}/versions/${name}`)
    .then((response) => response.data)
}

export function listDownloadAssets(
  appNameOrParams:
    | string
    | {
        page?: number
        size?: number
        keyword?: string
        appName?: string
        versionName?: string
      },
) {
  const params =
    typeof appNameOrParams === 'string'
      ? { page: 1, size: 1000, appName: appNameOrParams }
      : {
          page: 1,
          size: 1000,
          ...appNameOrParams,
        }

  return axiosInstance
    .get<ListResult<DownloadAsset>>(`${base}/download-assets`, {
      params,
    })
    .then((response) => response.data)
}

export function createDownloadAsset(asset: DownloadAsset) {
  return axiosInstance
    .post<DownloadAsset>(`${base}/download-assets`, asset)
    .then((response) => response.data)
}

export function updateDownloadAsset(name: string, asset: DownloadAsset) {
  return axiosInstance
    .put<DownloadAsset>(`${base}/download-assets/${name}`, asset)
    .then((response) => response.data)
}

export function deleteDownloadAsset(name: string) {
  return axiosInstance
    .delete<DownloadAsset>(`${base}/download-assets/${name}`)
    .then((response) => response.data)
}

export function emptyCategory(): SoftwareCategory {
  return {
    spec: {
      slug: '',
      displayName: '',
      visible: true,
      priority: 0,
      seo: {},
    },
  }
}

export function listCategories(params: { page: number; size: number; keyword?: string }) {
  return axiosInstance
    .get<ListResult<SoftwareCategory>>(`${base}/categories`, { params })
    .then((response) => response.data)
}

export function createCategory(category: SoftwareCategory) {
  return axiosInstance
    .post<SoftwareCategory>(`${base}/categories`, category)
    .then((response) => response.data)
}

export function updateCategory(name: string, category: SoftwareCategory) {
  return axiosInstance
    .put<SoftwareCategory>(`${base}/categories/${name}`, category)
    .then((response) => response.data)
}

export function deleteCategory(name: string) {
  return axiosInstance
    .delete<SoftwareCategory>(`${base}/categories/${name}`)
    .then((response) => response.data)
}

export function emptyTag(): SoftwareTag {
  return {
    spec: {
      slug: '',
      displayName: '',
      visible: true,
      priority: 0,
      seo: {},
    },
  }
}

export function listTags(params: { page: number; size: number; keyword?: string }) {
  return axiosInstance
    .get<ListResult<SoftwareTag>>(`${base}/tags`, { params })
    .then((response) => response.data)
}

export function getTag(name: string) {
  return axiosInstance.get<SoftwareTag>(`${base}/tags/${name}`).then((response) => response.data)
}

export function createTag(tag: SoftwareTag) {
  return axiosInstance.post<SoftwareTag>(`${base}/tags`, tag).then((response) => response.data)
}

export function updateTag(name: string, tag: SoftwareTag) {
  return axiosInstance
    .put<SoftwareTag>(`${base}/tags/${name}`, tag)
    .then((response) => response.data)
}

export function deleteTag(name: string) {
  return axiosInstance.delete<SoftwareTag>(`${base}/tags/${name}`).then((response) => response.data)
}

export function emptyTopic(): SoftwareTopic {
  return {
    spec: {
      slug: '',
      title: '',
      published: false,
      featured: false,
      priority: 0,
      items: [],
      seo: {},
    },
  }
}

export function listTopics(params: { page: number; size: number; keyword?: string }) {
  return axiosInstance
    .get<ListResult<SoftwareTopic>>(`${base}/topics`, { params })
    .then((response) => response.data)
}

export function createTopic(topic: SoftwareTopic) {
  return axiosInstance
    .post<SoftwareTopic>(`${base}/topics`, topic)
    .then((response) => response.data)
}

export function updateTopic(name: string, topic: SoftwareTopic) {
  return axiosInstance
    .put<SoftwareTopic>(`${base}/topics/${name}`, topic)
    .then((response) => response.data)
}

export function deleteTopic(name: string) {
  return axiosInstance
    .delete<SoftwareTopic>(`${base}/topics/${name}`)
    .then((response) => response.data)
}
