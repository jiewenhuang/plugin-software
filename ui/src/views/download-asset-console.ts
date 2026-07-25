import type { DownloadAsset, SoftwareApp, SoftwareOptions } from '@/api/software'
import {
  defaultSoftwareOptions,
  optionLabel,
  platformTokens as buildPlatformTokens,
  type PlatformToken,
} from '@/options/software-options'

export type DownloadAssetStatusFilter = '' | 'enabled' | 'disabled' | 'protected'
export type DownloadAssetSortKey = 'updated-desc' | 'updated-asc' | 'clicks-desc' | 'priority-asc'

export interface DownloadAssetConsoleFilters {
  keyword: string
  appName: string
  type: string
  status: DownloadAssetStatusFilter
}

export interface DownloadAssetStats {
  total: number
  enabled: number
  disabled: number
  protected: number
  downloads: number
}

export interface DownloadAssetRow {
  name: string
  title: string
  appName: string
  appLabel: string
  appIcon: string
  appInitial: string
  versionName: string
  platforms: PlatformToken[]
  typeValue: string
  typeLabel: string
  providerValue: string
  providerLabel: string
  url: string
  displayUrl: string
  protectionText: string
  status: DownloadAssetStatusMeta
  downloadCount: number
  updatedAt: string
  priority: number
  region: string
  source: DownloadAsset
}

export interface DownloadAssetStatusMeta {
  text: '在线' | '失效'
  className: 'online' | 'failed'
}

export function buildDownloadAssetStats(assets: DownloadAsset[]): DownloadAssetStats {
  return assets.reduce<DownloadAssetStats>(
    (stats, asset) => {
      stats.total += 1
      if (asset.spec.enabled !== false) {
        stats.enabled += 1
      } else {
        stats.disabled += 1
      }
      if (assetHasProtection(asset)) {
        stats.protected += 1
      }
      stats.downloads += asset.status?.downloadCount || 0
      return stats
    },
    {
      total: 0,
      enabled: 0,
      disabled: 0,
      protected: 0,
      downloads: 0,
    },
  )
}

export function buildDownloadAssetRows(
  assets: DownloadAsset[],
  apps: SoftwareApp[],
  options: SoftwareOptions = defaultSoftwareOptions,
): DownloadAssetRow[] {
  return assets.map((asset) => {
    const app = appForAsset(asset.spec.appName, apps)
    const typeValue = asset.spec.type || ''
    const providerValue = asset.spec.provider || ''
    return {
      name: asset.metadata?.name || '',
      title: asset.spec.title || '未命名链接',
      appName: asset.spec.appName || '',
      appLabel: appLabel(asset.spec.appName, apps),
      appIcon: app?.spec.icon || '',
      appInitial: appInitial(app, asset.spec.appName),
      versionName: asset.spec.versionName || '未关联版本',
      platforms: platformTokens(app?.spec.platforms ?? app?.spec.platform, options.platforms),
      typeValue,
      typeLabel: downloadTypeLabel(typeValue, options),
      providerValue,
      providerLabel: downloadProviderLabel(providerValue, options),
      url: asset.spec.url || '',
      displayUrl: copyableUrl(asset),
      protectionText: assetProtectionText(asset),
      status: assetStatus(asset),
      downloadCount: asset.status?.downloadCount || 0,
      updatedAt: asset.status?.lastCheckedAt || asset.metadata?.creationTimestamp || '',
      priority: asset.spec.priority ?? 0,
      region: asset.spec.region || '未设置',
      source: asset,
    }
  })
}

export function filterDownloadAssetRows(
  rows: DownloadAssetRow[],
  filters: DownloadAssetConsoleFilters,
) {
  return rows.filter((row) => {
    if (filters.appName && row.appName !== filters.appName) {
      return false
    }
    if (filters.type && row.typeValue !== filters.type) {
      return false
    }
    if (filters.status === 'enabled' && row.source.spec.enabled === false) {
      return false
    }
    if (filters.status === 'disabled' && row.source.spec.enabled !== false) {
      return false
    }
    if (filters.status === 'protected' && !assetHasProtection(row.source)) {
      return false
    }

    const keyword = filters.keyword.trim().toLowerCase()
    if (!keyword) {
      return true
    }

    const searchText = [
      row.name,
      row.title,
      row.url,
      row.providerLabel,
      row.typeLabel,
      row.versionName,
      row.region,
      row.appLabel,
      row.appName,
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()

    return searchText.includes(keyword)
  })
}

export function sortDownloadAssetRows(rows: DownloadAssetRow[], sortKey: DownloadAssetSortKey) {
  return [...rows].sort((left, right) => {
    if (sortKey === 'updated-asc') {
      return compareText(left.updatedAt, right.updatedAt)
    }
    if (sortKey === 'clicks-desc') {
      return right.downloadCount - left.downloadCount || compareText(left.title, right.title)
    }
    if (sortKey === 'priority-asc') {
      return left.priority - right.priority || compareText(left.title, right.title)
    }
    return compareText(right.updatedAt, left.updatedAt)
  })
}

export function assetMatchesConsoleFilters(
  asset: DownloadAsset,
  apps: SoftwareApp[],
  filters: DownloadAssetConsoleFilters,
) {
  return filterDownloadAssetRows(buildDownloadAssetRows([asset], apps), filters).length > 0
}

export function appLabel(appName: string | undefined, apps: SoftwareApp[]) {
  if (!appName) {
    return '未关联软件'
  }
  return appForAsset(appName, apps)?.spec.displayName || appName
}

export function appForAsset(appName: string | undefined, apps: SoftwareApp[]) {
  return apps.find((app) => app.metadata?.name === appName)
}

export function downloadTypeLabel(
  type: string | undefined,
  options: SoftwareOptions = defaultSoftwareOptions,
) {
  return optionLabel(options.downloadTypes, type, '未设置')
}

export function downloadProviderLabel(
  provider: string | undefined,
  options: SoftwareOptions = defaultSoftwareOptions,
) {
  return optionLabel(options.downloadProviders, provider, 'external')
}

export function copyableUrl(asset: DownloadAsset) {
  if (asset.spec.url?.trim()) {
    return asset.spec.url.trim()
  }
  return asset.spec.hasUrl ? '••••••（需敏感配置权限）' : '未设置 URL'
}

export function assetStatus(asset: DownloadAsset): DownloadAssetStatusMeta {
  return asset.spec.enabled === false
    ? {
        className: 'failed',
        text: '失效',
      }
    : {
        className: 'online',
        text: '在线',
      }
}

export function assetProtectionText(asset: DownloadAsset) {
  const protections = []
  if (asset.spec.requiresLogin) {
    protections.push('登录')
  }
  if (asset.spec.requiresCaptcha) {
    protections.push('验证码')
  }
  if (
    asset.spec.passwordSecretName ||
    asset.spec.extractionCode ||
    asset.spec.hasPasswordSecret ||
    asset.spec.hasExtractionCode
  ) {
    protections.push('提取码')
  }
  return protections.length ? protections.join(' / ') : '公开'
}

export function formatAssetDate(value?: string) {
  if (!value) {
    return '未记录'
  }
  return value.replace('T', ' ').slice(0, 16)
}

export function formatDownloadNumber(value?: number) {
  return new Intl.NumberFormat('zh-CN').format(value || 0)
}

export function platformTokens(
  platform?: string[] | string,
  platformOptions = defaultSoftwareOptions.platforms,
): PlatformToken[] {
  return buildPlatformTokens(platform, platformOptions)
}

function appInitial(app: SoftwareApp | undefined, fallback?: string) {
  return (app?.spec.displayName || app?.spec.slug || fallback || '软').slice(0, 1).toUpperCase()
}

function compareText(left: string, right: string) {
  return left.localeCompare(right)
}

function assetHasProtection(asset: DownloadAsset) {
  return Boolean(
    asset.spec.requiresLogin ||
      asset.spec.requiresCaptcha ||
      asset.spec.passwordSecretName ||
      asset.spec.extractionCode ||
      asset.spec.hasPasswordSecret ||
      asset.spec.hasExtractionCode,
  )
}
