import type {
  DownloadAsset,
  DownloadAssetSecret,
  SoftwareApp,
  SoftwareAppAiSuggestion,
  SoftwareAiTarget,
  SoftwareOptionItem,
  SoftwareVersion,
} from '@/api/software'

export type SoftwareEditorTab = 'basic' | 'media' | 'versions' | 'downloads' | 'seo' | 'settings'

export const softwareEditorTabs: SoftwareEditorTab[] = [
  'basic',
  'media',
  'versions',
  'downloads',
  'seo',
  'settings',
]

export function normalizeSoftwareEditorTab(value: string | null | undefined): SoftwareEditorTab {
  return softwareEditorTabs.includes(value as SoftwareEditorTab)
    ? (value as SoftwareEditorTab)
    : 'basic'
}

export function buildSoftwareEditorQuery(name: string, tab: SoftwareEditorTab) {
  const params = new URLSearchParams()
  if (name) {
    params.set('name', name)
  }
  if (tab !== 'basic') {
    params.set('tab', tab)
  }
  const query = params.toString()
  return query ? `?${query}` : ''
}

export interface SoftwareVersionDraft {
  name: string
  version: string
  build: string
  releaseDate: string
  minOsVersion: string
  maxOsVersion: string
  architectures: string[]
  locale: string
  sizeBytes: string
  status: string
  changelog: string
  installGuide: string
  installWarnings: string
  sha256: string
  md5: string
}

export interface DownloadAssetDraft {
  name: string
  versionName: string
  title: string
  url: string
  storageKey: string
  extractionCode: string
  passwordSecretName: string
  type: string
  provider: string
  region: string
  priority: number
  enabled: boolean
  requiresLogin: boolean
  requiresCaptcha: boolean
}

export function createVersionDraft(
  statusOptions: SoftwareOptionItem[],
  version?: SoftwareVersion,
): SoftwareVersionDraft {
  return {
    name: version?.metadata?.name || '',
    version: version?.spec.version || '',
    build: version?.spec.build || '',
    releaseDate: dateInputValue(version?.spec.releaseDate),
    minOsVersion: version?.spec.minOsVersion || '',
    maxOsVersion: version?.spec.maxOsVersion || '',
    architectures: [...(version?.spec.architectures || [])],
    locale: version?.spec.locale || '',
    sizeBytes: version?.spec.sizeBytes == null ? '' : String(version.spec.sizeBytes),
    status: version?.spec.status || firstConfiguredValue(statusOptions, 'draft'),
    changelog: version?.spec.changelog || '',
    installGuide: version?.spec.installGuide || '',
    installWarnings: (version?.spec.installWarnings || []).join('\n'),
    sha256: version?.spec.checksum?.sha256 || '',
    md5: version?.spec.checksum?.md5 || '',
  }
}

export function buildVersionPayload(
  appName: string,
  draft: SoftwareVersionDraft,
): SoftwareVersion {
  const sha256 = draft.sha256.trim() || undefined
  const md5 = draft.md5.trim() || undefined
  return {
    spec: {
      appName,
      version: draft.version.trim(),
      build: draft.build.trim() || undefined,
      releaseDate: releaseDateValue(draft.releaseDate),
      minOsVersion: draft.minOsVersion.trim() || undefined,
      maxOsVersion: draft.maxOsVersion.trim() || undefined,
      architectures: uniqueValues(draft.architectures),
      locale: draft.locale.trim() || undefined,
      sizeBytes: draft.sizeBytes === '' ? undefined : Number(draft.sizeBytes),
      changelog: draft.changelog.trim() || undefined,
      installGuide: draft.installGuide.trim() || undefined,
      installWarnings: uniqueValues(draft.installWarnings.split('\n')),
      checksum: sha256 || md5 ? { sha256, md5 } : undefined,
      status: draft.status || undefined,
    },
  }
}

export function createDownloadAssetDraft(
  versions: SoftwareVersion[],
  typeOptions: SoftwareOptionItem[],
  providerOptions: SoftwareOptionItem[],
  asset?: DownloadAsset,
  secret?: DownloadAssetSecret,
): DownloadAssetDraft {
  const sensitive = secret || asset?.spec
  return {
    name: asset?.metadata?.name || '',
    versionName: asset?.spec.versionName || versions[0]?.metadata?.name || '',
    title: asset?.spec.title || '',
    url: sensitive?.url || '',
    storageKey: sensitive?.storageKey || '',
    extractionCode: sensitive?.extractionCode || '',
    passwordSecretName: sensitive?.passwordSecretName || '',
    type: asset?.spec.type || firstConfiguredValue(typeOptions, 'dmg'),
    provider: asset?.spec.provider || firstConfiguredValue(providerOptions, 'external'),
    region: asset?.spec.region || '',
    priority: asset?.spec.priority || 0,
    enabled: asset?.spec.enabled ?? false,
    requiresLogin: asset?.spec.requiresLogin ?? false,
    requiresCaptcha: asset?.spec.requiresCaptcha ?? false,
  }
}

export function buildDownloadAssetPayload(
  appName: string,
  draft: DownloadAssetDraft,
  includeSecrets: boolean,
): DownloadAsset {
  return {
    spec: {
      appName,
      versionName: draft.versionName,
      title: draft.title.trim(),
      ...(includeSecrets
        ? {
            url: draft.url.trim() || undefined,
            storageKey: draft.storageKey.trim() || undefined,
            extractionCode: draft.extractionCode.trim() || undefined,
            passwordSecretName: draft.passwordSecretName.trim() || undefined,
          }
        : {}),
      type: draft.type || undefined,
      provider: draft.provider || undefined,
      region: draft.region.trim() || undefined,
      enabled: draft.enabled,
      requiresLogin: draft.requiresLogin,
      requiresCaptcha: draft.requiresCaptcha,
      priority: Math.max(0, Number(draft.priority) || 0),
    },
  }
}

export function hasDownloadTarget(
  draft: Pick<DownloadAssetDraft, 'url' | 'storageKey' | 'passwordSecretName'>,
) {
  return [draft.url, draft.storageKey, draft.passwordSecretName].some((value) => value.trim())
}

function firstConfiguredValue(items: SoftwareOptionItem[], fallback: string) {
  return items.find((item) => item.value)?.value || fallback
}

function dateInputValue(value?: string) {
  return value && /^\d{4}-\d{2}-\d{2}/.test(value) ? value.slice(0, 10) : ''
}

function releaseDateValue(value: string) {
  return dateInputValue(value) || undefined
}

function uniqueValues(values: string[]) {
  return [...new Set(values.map((value) => value.trim()).filter(Boolean))]
}

function stableValue(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(stableValue)
  }
  if (!value || typeof value !== 'object') {
    return value
  }
  return Object.keys(value)
    .sort()
    .reduce<Record<string, unknown>>((nextValue, key) => {
      nextValue[key] = stableValue((value as Record<string, unknown>)[key])
      return nextValue
    }, {})
}

export function createSoftwareEditorSnapshot(app: SoftwareApp) {
  return JSON.stringify(stableValue(app))
}

export function stableAsciiSlug(value: string, prefix = 'tag') {
  const normalized = value
    .trim()
    .toLowerCase()
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
  if (normalized) {
    return normalized
  }

  // FNV-1a gives non-Latin labels a deterministic ASCII fallback without a transliteration
  // dependency. Collision handling remains the caller's responsibility.
  let hash = 0x811c9dc5
  for (const character of value.trim()) {
    hash ^= character.codePointAt(0) || 0
    hash = Math.imul(hash, 0x01000193)
  }
  return `${prefix}-${(hash >>> 0).toString(36)}`
}

export function applySoftwareAiSuggestion(
  app: SoftwareApp,
  target: SoftwareAiTarget,
  suggestion: SoftwareAppAiSuggestion,
) {
  if ((target === 'all' || target === 'basic') && suggestion.summary) {
    app.spec.summary = suggestion.summary
  }

  if ((target === 'all' || target === 'description') && suggestion.description) {
    app.spec.description = suggestion.description
  }

  if ((target === 'all' || target === 'seo') && suggestion.seo) {
    app.spec.seo = {
      ...app.spec.seo,
      ...suggestion.seo,
      keywords: suggestion.seo.keywords || app.spec.seo?.keywords,
    }
  }
}
