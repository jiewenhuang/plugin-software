import { describe, expect, it } from 'vitest'
import type { DownloadAsset, SoftwareApp } from '@/api/software'
import {
  appLabel,
  assetMatchesConsoleFilters,
  assetStatus,
  buildDownloadAssetRows,
  buildDownloadAssetStats,
  filterDownloadAssetRows,
  copyableUrl,
  downloadAssetListParams,
  downloadProviderLabel,
  downloadTypeLabel,
  linkFingerprintCells,
  platformTokens,
  sortDownloadAssetRows,
} from '../download-asset-console'

const apps: SoftwareApp[] = [
  {
    metadata: { name: 'halo-app' },
    spec: {
      slug: 'halo',
      displayName: 'Halo 开源建站工具',
      platform: 'Windows macOS Linux',
    },
  },
  {
    metadata: { name: 'assistant-app' },
    spec: {
      slug: 'halo-assistant',
      displayName: 'Halo 小助手',
    },
  },
]

const assets: DownloadAsset[] = [
  {
    metadata: { name: 'asset-1', creationTimestamp: '2024-05-18T10:30:00Z' },
    spec: {
      appName: 'halo-app',
      versionName: 'version-214',
      title: 'Halo DMG',
      type: 'dmg',
      provider: 'halo-cdn',
      url: 'https://d.halo.run/abc123',
      priority: 12,
      enabled: true,
      region: '华东',
    },
    status: {
      downloadCount: 120,
      health: 'healthy',
      lastCheckedAt: '2024-05-20T14:30:00Z',
    },
  },
  {
    metadata: { name: 'asset-2' },
    spec: {
      appName: 'assistant-app',
      versionName: 'version-132',
      title: 'Assistant Zip',
      type: 'zip',
      provider: 'external',
      requiresLogin: true,
      priority: 4,
      enabled: false,
    },
    status: {
      downloadCount: 30,
      health: 'unknown',
    },
  },
]

describe('download asset console helpers', () => {
  it('builds dashboard stats from all download assets', () => {
    expect(
      buildDownloadAssetStats([
        ...assets,
        {
          metadata: { name: 'asset-3' },
          spec: {
            appName: 'halo-app',
            versionName: 'version-214',
            title: 'Halo 提取码链接',
            extractionCode: 'abcd',
          },
        },
      ]),
    ).toEqual({
      total: 3,
      enabled: 2,
      disabled: 1,
      protected: 2,
      downloads: 150,
    })
  })

  it('matches console filters by keyword, app, type, and status', () => {
    expect(
      assetMatchesConsoleFilters(assets[0], apps, {
        keyword: '建站',
        appName: 'halo-app',
        type: 'dmg',
        status: 'enabled',
      }),
    ).toBe(true)

    expect(
      assetMatchesConsoleFilters(assets[1], apps, {
        keyword: 'abc123',
        appName: '',
        type: '',
        status: 'enabled',
      }),
    ).toBe(false)

    expect(
      assetMatchesConsoleFilters(
        {
          metadata: { name: 'asset-3' },
          spec: {
            appName: 'halo-app',
            versionName: 'version-214',
            title: 'Halo 提取码链接',
            extractionCode: 'abcd',
          },
        },
        apps,
        {
          keyword: '',
          appName: '',
          type: '',
          status: 'protected',
        },
      ),
    ).toBe(true)
  })

  it('formats display labels for table cells', () => {
    expect(appLabel('halo-app', apps)).toBe('Halo 开源建站工具')
    expect(appLabel('missing-app', apps)).toBe('missing-app')
    expect(downloadTypeLabel('')).toBe('未设置')
    expect(downloadProviderLabel('')).toBe('external')
    expect(copyableUrl(assets[1])).toBe('未设置 URL')
    expect(assetStatus(assets[0])).toEqual({
      className: 'online',
      text: '在线',
    })
    expect(assetStatus(assets[1])).toEqual({
      className: 'failed',
      text: '失效',
    })
  })

  it('builds table rows with app, platform, status, and compact link fields', () => {
    const rows = buildDownloadAssetRows(assets, apps)

    expect(rows[0]).toMatchObject({
      name: 'asset-1',
      title: 'Halo DMG',
      appLabel: 'Halo 开源建站工具',
      appInitial: 'H',
      typeLabel: 'DMG',
      providerLabel: 'halo-cdn',
      displayUrl: 'https://d.halo.run/abc123',
      protectionText: '公开',
      downloadCount: 120,
      region: '华东',
      status: {
        className: 'online',
        text: '在线',
      },
    })
    expect(rows[0].platforms.map((item) => item.shortLabel)).toEqual(['W', 'M', 'L'])
  })

  it('filters and sorts table rows without mutating the original list', () => {
    const rows = buildDownloadAssetRows(assets, apps)
    const filtered = filterDownloadAssetRows(rows, {
      keyword: 'assistant',
      appName: '',
      type: '',
      status: 'disabled',
    })

    expect(filtered.map((row) => row.name)).toEqual(['asset-2'])
    expect(sortDownloadAssetRows(rows, 'clicks-desc').map((row) => row.name)).toEqual([
      'asset-1',
      'asset-2',
    ])
    expect(rows.map((row) => row.name)).toEqual(['asset-1', 'asset-2'])
  })

  it('normalizes platform tokens and creates deterministic link cells', () => {
    expect(platformTokens('Windows macOS Linux').map((item) => item.className)).toEqual([
      'windows',
      'macos',
      'linux',
    ])
    expect(linkFingerprintCells('https://d.halo.run/abc123')).toHaveLength(81)
    expect(linkFingerprintCells('https://d.halo.run/abc123')).toEqual(
      linkFingerprintCells('https://d.halo.run/abc123'),
    )
  })

  it('keeps console list requests broad so local filters can match app names and urls', () => {
    expect(downloadAssetListParams()).toEqual({
      page: 1,
      size: 1000,
    })
  })
})
