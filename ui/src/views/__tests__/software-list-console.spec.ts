import { describe, expect, it } from 'vitest'
import type { SoftwareApp, SoftwareCategory, SoftwareTag } from '@/api/software'
import {
  buildSoftwareStats,
  buildSoftwareListQuery,
  categoryLabel,
  formatDownloadCount,
  formatSoftwareDate,
  hiddenTagCount,
  latestVersionText,
  parseSoftwareListQuery,
  platformTokens,
  rowTagItems,
  softwareStatus,
} from '../software-list-console'

const categories: SoftwareCategory[] = [
  {
    metadata: { name: 'dev-tools' },
    spec: {
      slug: 'dev-tools',
      displayName: '开发工具',
    },
  },
]

const apps: SoftwareApp[] = [
  {
    metadata: { name: 'halo-app' },
    spec: {
      slug: 'halo',
      displayName: 'Halo',
      categoryName: 'dev-tools',
      tagNames: ['tag-design', 'tag-productivity', 'missing-tag'],
      platform: 'macOS Windows Linux',
      published: true,
    },
    status: {
      latestVersion: '2.21.4',
      latestVersionName: 'version-214',
      latestReleaseDate: '2026-06-08T10:20:00Z',
      downloadCount: 1280,
    },
  },
  {
    metadata: { name: 'draft-app' },
    spec: {
      slug: 'draft-app',
      displayName: 'Draft App',
      platform: '',
      published: false,
    },
    status: {
      downloadCount: 20,
    },
  },
]

const tags: SoftwareTag[] = [
  {
    metadata: { name: 'tag-design' },
    spec: {
      slug: 'design',
      displayName: '设计辅助',
    },
  },
  {
    metadata: { name: 'tag-productivity' },
    spec: {
      slug: 'productivity',
      displayName: '效率工具',
    },
  },
]

describe('software list console helpers', () => {
  it('builds dashboard stats from listed software apps', () => {
    expect(buildSoftwareStats(apps, 24)).toEqual({
      total: 24,
      published: 1,
      drafts: 1,
      downloads: 1300,
    })
  })

  it('formats software row metadata', () => {
    expect(categoryLabel('dev-tools', categories)).toBe('开发工具')
    expect(categoryLabel('unknown', categories)).toBe('unknown')
    expect(categoryLabel('', categories)).toBe('未分类')
    expect(formatSoftwareDate('2026-06-08T10:20:00')).toBe('2026-06-08 10:20')
    expect(formatSoftwareDate()).toBe('未更新')
    expect(formatSoftwareDate('not-a-date')).toBe('未更新')
    expect(formatDownloadCount(1280)).toBe('1,280')
    expect(latestVersionText(apps[0])).toBe('2.21.4')
  })

  it('parses and builds software list query state', () => {
    expect(
      parseSoftwareListQuery(
        '?page=3&size=50&keyword=bridge&published=true&category=dev-tools&platform=macOS',
      ),
    ).toEqual({
      page: 3,
      size: 50,
      keyword: 'bridge',
      published: 'true',
      category: 'dev-tools',
      platform: 'macOS',
    })

    expect(parseSoftwareListQuery('?page=-1&size=abc')).toEqual({
      page: 1,
      size: 20,
      keyword: '',
      published: '',
      category: '',
      platform: '',
    })

    expect(
      buildSoftwareListQuery({
        page: 2,
        size: 50,
        keyword: 'bridge',
        published: '',
        category: 'dev-tools',
        platform: '',
      }),
    ).toBe('?page=2&size=50&keyword=bridge&category=dev-tools')

    expect(
      buildSoftwareListQuery({
        page: 1,
        size: 20,
        keyword: '',
        published: '',
        category: '',
        platform: '',
      }),
    ).toBe('')
  })

  it('resolves software tag metadata names to display names for list rows', () => {
    expect(rowTagItems(apps[0], tags)).toEqual([
      {
        name: 'tag-design',
        label: '设计辅助',
        title: 'tag-design',
      },
      {
        name: 'tag-productivity',
        label: '效率工具',
        title: 'tag-productivity',
      },
    ])
    expect(rowTagItems(apps[0], tags, 3)[2]).toEqual({
      name: 'missing-tag',
      label: 'missing-tag',
      title: 'missing-tag',
    })
    expect(hiddenTagCount(apps[0])).toBe(1)
  })

  it('derives status and compact platform chips', () => {
    expect(softwareStatus(apps[0])).toEqual({
      state: 'success',
      text: '已发布',
      className: 'published',
    })
    expect(softwareStatus(apps[1])).toEqual({
      state: 'default',
      text: '草稿',
      className: 'draft',
    })

    expect(platformTokens('macOS Windows Linux')).toEqual([
      { label: 'macOS', shortLabel: 'M', className: 'macos' },
      { label: 'Windows', shortLabel: 'W', className: 'windows' },
      { label: 'Linux', shortLabel: 'L', className: 'linux' },
    ])
    expect(platformTokens('')).toEqual([
      { label: '未设置', shortLabel: '-', className: 'unknown' },
    ])
  })
})
