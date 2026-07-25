import { axiosInstance, consoleApiClient, coreApiClient } from '@halo-dev/api-client'
import type {
  DownloadAssetConsoleSpec as GeneratedDownloadAssetConsoleSpec,
  DownloadAssetSecretVo as GeneratedDownloadAssetSecret,
  DownloadAssetStatus as GeneratedDownloadAssetStatus,
  Metadata as GeneratedMetadata,
  SoftwareAppSeo as GeneratedSoftwareSeo,
  SoftwareAppSpec as GeneratedSoftwareAppSpec,
  SoftwareAppStatus as GeneratedSoftwareAppStatus,
  SoftwareCategorySpec as GeneratedSoftwareCategorySpec,
  SoftwareCategoryStatus as GeneratedSoftwareCategoryStatus,
  SoftwareTagSpec as GeneratedSoftwareTagSpec,
  SoftwareTagStatus as GeneratedSoftwareTagStatus,
  SoftwareTopicItem as GeneratedSoftwareTopicItem,
  SoftwareTopicSpec as GeneratedSoftwareTopicSpec,
  SoftwareTopicStatus as GeneratedSoftwareTopicStatus,
  SoftwareVersionSpec as GeneratedSoftwareVersionSpec,
  SoftwareVersionStatus as GeneratedSoftwareVersionStatus,
} from './generated/models'

// Generated models are the server contract. Console drafts intentionally relax only metadata,
// because resources do not have a server-assigned name/version before they are persisted.
export type ExtensionMetadata = Omit<
  Partial<GeneratedMetadata>,
  'creationTimestamp' | 'deletionTimestamp' | 'version'
> & {
  creationTimestamp?: string
  deletionTimestamp?: string
  version?: number
}

export type SoftwareSeo = GeneratedSoftwareSeo

export interface SoftwareAppAiSuggestion {
  summary?: string
  description?: string
  seo?: SoftwareSeo
  warnings?: string[]
}

export type SoftwareAiTarget = 'all' | 'basic' | 'description' | 'seo'

export interface SoftwareAiCapabilities {
  available: boolean
}

export type SoftwareAppSpec = GeneratedSoftwareAppSpec

export type SoftwareAppStatus = GeneratedSoftwareAppStatus

export interface SoftwareApp {
  apiVersion?: string
  kind?: string
  metadata?: ExtensionMetadata
  spec: SoftwareAppSpec
  status?: SoftwareAppStatus
}

export interface SoftwareVersion {
  metadata?: ExtensionMetadata
  spec: GeneratedSoftwareVersionSpec
  status?: GeneratedSoftwareVersionStatus
}

// Console responses and secret responses are separate server contracts. The editor combines them
// only after the dedicated secret endpoint succeeds, keeping the authorization boundary explicit.
export type DownloadAssetSpec = GeneratedDownloadAssetConsoleSpec &
  Omit<GeneratedDownloadAssetSecret, 'name'>

export interface DownloadAsset {
  metadata?: ExtensionMetadata
  spec: DownloadAssetSpec
  status?: GeneratedDownloadAssetStatus
}

export type DownloadAssetSecret = GeneratedDownloadAssetSecret

export interface SoftwareCategory {
  metadata?: ExtensionMetadata
  spec: GeneratedSoftwareCategorySpec
  status?: GeneratedSoftwareCategoryStatus
}

export interface SoftwareTag {
  metadata?: ExtensionMetadata
  spec: GeneratedSoftwareTagSpec
  status?: GeneratedSoftwareTagStatus
}

export type SoftwareTopicItem = GeneratedSoftwareTopicItem

export interface SoftwareTopic {
  metadata?: ExtensionMetadata
  spec: GeneratedSoftwareTopicSpec
  status?: GeneratedSoftwareTopicStatus
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

export interface DownloadAssetStats {
  total: number
  enabled: number
  disabled: number
  protectedCount: number
  downloads: number
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
  return axiosInstance
    .post<SoftwareApp>(`${base}/apps`, { spec: app.spec })
    .then((response) => response.data)
}

export function updateApp(name: string, app: SoftwareApp) {
  return axiosInstance
    .put<SoftwareApp>(`${base}/apps/${name}`, { spec: app.spec })
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

export function getSoftwareAiCapabilities() {
  return axiosInstance
    .get<SoftwareAiCapabilities>(`${base}/ai/capabilities`)
    .then((response) => response.data)
}

export function listVersions(appName: string) {
  return listAllPages<SoftwareVersion>(`${base}/versions`, { appName })
}

export function createVersion(version: SoftwareVersion) {
  return axiosInstance
    .post<SoftwareVersion>(`${base}/versions`, { spec: version.spec })
    .then((response) => response.data)
}

export function updateVersion(name: string, version: SoftwareVersion) {
  return axiosInstance
    .put<SoftwareVersion>(`${base}/versions/${name}`, { spec: version.spec })
    .then((response) => response.data)
}

export function deleteVersion(name: string) {
  return axiosInstance
    .delete<SoftwareVersion>(`${base}/versions/${name}`)
    .then((response) => response.data)
}

export interface DownloadAssetListParams {
  page?: number
  size?: number
  keyword?: string
  appName?: string
  versionName?: string
  type?: string
  enabled?: boolean
  protection?: 'protected'
  sort?: string
}

export function listDownloadAssets(appName: string): Promise<ListResult<DownloadAsset>>
export function listDownloadAssets(
  params: DownloadAssetListParams,
): Promise<ListResult<DownloadAsset>>
export function listDownloadAssets(appNameOrParams: string | DownloadAssetListParams) {
  if (typeof appNameOrParams === 'string') {
    return listAllPages<DownloadAsset>(`${base}/download-assets`, {
      appName: appNameOrParams,
    })
  }
  return axiosInstance
    .get<ListResult<DownloadAsset>>(`${base}/download-assets`, { params: appNameOrParams })
    .then((response) => response.data)
}

export function getDownloadAssetStats() {
  return axiosInstance
    .get<DownloadAssetStats>(`${base}/download-assets/stats`)
    .then((response) => response.data)
}

export async function createDownloadAsset(asset: DownloadAsset) {
  const desiredEnabled = asset.spec.enabled === true
  const response = await axiosInstance.post<DownloadAsset>(`${base}/download-assets`, {
    spec: publicDownloadSpec(asset.spec, false),
  })
  const created = response.data
  const name = created.metadata?.name
  if (!name) {
    return created
  }
  if (hasExplicitDownloadSecret(asset.spec)) {
    await updateDownloadAssetSecret(name, asset.spec)
  }
  if (!desiredEnabled) {
    return created
  }
  return axiosInstance
    .put<DownloadAsset>(`${base}/download-assets/${name}`, {
      spec: publicDownloadSpec({ ...asset.spec, enabled: true }, true),
    })
    .then((result) => result.data)
}

export async function updateDownloadAsset(name: string, asset: DownloadAsset) {
  // Disable before clearing the last usable target. This avoids a transient enabled asset with
  // neither URL nor storage key, which the server correctly rejects.
  if (asset.spec.enabled === false) {
    const response = await axiosInstance.put<DownloadAsset>(`${base}/download-assets/${name}`, {
      spec: publicDownloadSpec(asset.spec, false),
    })
    if (hasExplicitDownloadSecret(asset.spec)) {
      await updateDownloadAssetSecret(name, asset.spec)
    }
    return response.data
  }
  if (hasExplicitDownloadSecret(asset.spec)) {
    await updateDownloadAssetSecret(name, asset.spec)
  }
  return axiosInstance
    .put<DownloadAsset>(`${base}/download-assets/${name}`, {
      spec: publicDownloadSpec(asset.spec, asset.spec.enabled),
    })
    .then((response) => response.data)
}

export function getDownloadAssetSecret(name: string) {
  return axiosInstance
    .get<DownloadAssetSecret>(`${base}/download-assets/${name}/secrets`)
    .then((response) => response.data)
}

export function updateDownloadAssetSecret(
  name: string,
  secret: Pick<
    DownloadAsset['spec'],
    'url' | 'storageKey' | 'extractionCode' | 'passwordSecretName'
  >,
) {
  return axiosInstance
    .put<DownloadAssetSecret>(`${base}/download-assets/${name}/secrets`, {
      url: secret.url,
      storageKey: secret.storageKey,
      extractionCode: secret.extractionCode,
      passwordSecretName: secret.passwordSecretName,
    })
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

export function listCategories(params: {
  page: number
  size: number
  keyword?: string
  visible?: boolean
  parentName?: string
  sort?: string
}) {
  return axiosInstance
    .get<ListResult<SoftwareCategory>>(`${base}/categories`, { params })
    .then((response) => response.data)
}

export function listAllCategories() {
  return listAllPages<SoftwareCategory>(`${base}/categories`, {}, 100)
}

export function createCategory(category: SoftwareCategory) {
  return axiosInstance
    .post<SoftwareCategory>(`${base}/categories`, { spec: category.spec })
    .then((response) => response.data)
}

export function updateCategory(name: string, category: SoftwareCategory) {
  return axiosInstance
    .put<SoftwareCategory>(`${base}/categories/${name}`, { spec: category.spec })
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

export function listTags(params: {
  page: number
  size: number
  keyword?: string
  visible?: boolean
  usage?: 'used' | 'unused'
  sort?: string
}) {
  return axiosInstance
    .get<ListResult<SoftwareTag>>(`${base}/tags`, { params })
    .then((response) => response.data)
}

export function getTag(name: string) {
  return axiosInstance.get<SoftwareTag>(`${base}/tags/${name}`).then((response) => response.data)
}

export function createTag(tag: SoftwareTag) {
  return axiosInstance
    .post<SoftwareTag>(`${base}/tags`, { spec: tag.spec })
    .then((response) => response.data)
}

export function updateTag(name: string, tag: SoftwareTag) {
  return axiosInstance
    .put<SoftwareTag>(`${base}/tags/${name}`, { spec: tag.spec })
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

export function listTopics(params: {
  page: number
  size: number
  keyword?: string
  published?: boolean
  featured?: boolean
  sort?: string
}) {
  return axiosInstance
    .get<ListResult<SoftwareTopic>>(`${base}/topics`, { params })
    .then((response) => response.data)
}

export function createTopic(topic: SoftwareTopic) {
  return axiosInstance
    .post<SoftwareTopic>(`${base}/topics`, { spec: topic.spec })
    .then((response) => response.data)
}

export function updateTopic(name: string, topic: SoftwareTopic) {
  return axiosInstance
    .put<SoftwareTopic>(`${base}/topics/${name}`, { spec: topic.spec })
    .then((response) => response.data)
}

export function deleteTopic(name: string) {
  return axiosInstance
    .delete<SoftwareTopic>(`${base}/topics/${name}`)
    .then((response) => response.data)
}

function publicDownloadSpec(spec: DownloadAsset['spec'], enabled = spec.enabled) {
  return {
    appName: spec.appName,
    versionName: spec.versionName,
    title: spec.title,
    type: spec.type,
    provider: spec.provider,
    requiresLogin: spec.requiresLogin,
    requiresCaptcha: spec.requiresCaptcha,
    region: spec.region,
    priority: spec.priority,
    enabled,
  }
}

function hasExplicitDownloadSecret(spec: DownloadAsset['spec']) {
  return ['url', 'storageKey', 'extractionCode', 'passwordSecretName'].some((key) =>
    Object.prototype.hasOwnProperty.call(spec, key),
  )
}

async function listAllPages<T>(
  path: string,
  params: Record<string, unknown>,
  pageSize = 100,
): Promise<ListResult<T>> {
  const items: T[] = []
  let page = 1
  let total = 0
  do {
    const response = await axiosInstance.get<ListResult<T>>(path, {
      params: { ...params, page, size: pageSize },
    })
    const result = response.data
    total = result.total
    items.push(...result.items)
    if (!result.items.length || items.length >= total) {
      break
    }
    page += 1
  } while (page < 10_000)

  return {
    page: 1,
    size: items.length,
    total,
    items,
    first: true,
    last: true,
    hasNext: false,
    hasPrevious: false,
    totalPages: 1,
  }
}
