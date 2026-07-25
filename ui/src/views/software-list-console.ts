import type { SoftwareApp, SoftwareCategory, SoftwareOptionItem, SoftwareTag } from '@/api/software'
import {
  platformTokens as buildPlatformTokens,
  type PlatformToken,
} from '@/options/software-options'

export interface SoftwareListStats {
  total: number
  published: number
  drafts: number
  downloads: number
}

export interface SoftwareStatusMeta {
  state: 'success' | 'default'
  text: '已发布' | '草稿'
  className: 'published' | 'draft'
}

export interface SoftwareRowTagItem {
  name: string
  label: string
  title: string
}

export interface SoftwareListQueryState {
  page: number
  size: number
  keyword: string
  published: string
  category: string
  platform: string
}

const defaultQueryState: SoftwareListQueryState = {
  page: 1,
  size: 20,
  keyword: '',
  published: '',
  category: '',
  platform: '',
}

function positiveInt(value: string | null, fallback: number) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback
}

export function parseSoftwareListQuery(search: string | URLSearchParams): SoftwareListQueryState {
  const params = search instanceof URLSearchParams ? search : new URLSearchParams(search)
  return {
    page: positiveInt(params.get('page'), defaultQueryState.page),
    size: positiveInt(params.get('size'), defaultQueryState.size),
    keyword: params.get('keyword')?.trim() || '',
    published: params.get('published') || '',
    category: params.get('category') || '',
    platform: params.get('platform') || '',
  }
}

export function buildSoftwareListQuery(state: SoftwareListQueryState) {
  const params = new URLSearchParams()
  if (state.page > defaultQueryState.page) {
    params.set('page', String(state.page))
  }
  if (state.size !== defaultQueryState.size) {
    params.set('size', String(state.size))
  }
  if (state.keyword.trim()) {
    params.set('keyword', state.keyword.trim())
  }
  if (state.published) {
    params.set('published', state.published)
  }
  if (state.category) {
    params.set('category', state.category)
  }
  if (state.platform) {
    params.set('platform', state.platform)
  }
  const query = params.toString()
  return query ? `?${query}` : ''
}

export function buildSoftwareStats(apps: SoftwareApp[], total = apps.length): SoftwareListStats {
  return apps.reduce<SoftwareListStats>(
    (stats, app) => {
      if (app.spec.published) {
        stats.published += 1
      } else {
        stats.drafts += 1
      }
      stats.downloads += app.status?.downloadCount || 0
      return stats
    },
    {
      total,
      published: 0,
      drafts: 0,
      downloads: 0,
    },
  )
}

export function categoryLabel(name: string | undefined, categories: SoftwareCategory[]) {
  if (!name) {
    return '未分类'
  }
  return categories.find((item) => item.metadata?.name === name)?.spec.displayName || name
}

export function rowTagItems(
  app: SoftwareApp,
  tags: SoftwareTag[],
  limit = 2,
): SoftwareRowTagItem[] {
  const tagLookup = new Map(tags.map((tag) => [tag.metadata?.name, tag]))
  return (app.spec.tagNames || [])
    .filter(Boolean)
    .slice(0, limit)
    .map((name) => {
      const tag = tagLookup.get(name)
      return {
        name,
        label: tag?.spec.displayName || name,
        title: name,
      }
    })
}

export function hiddenTagCount(app: SoftwareApp, limit = 2) {
  return Math.max((app.spec.tagNames || []).filter(Boolean).length - limit, 0)
}

export function formatSoftwareDate(value?: string) {
  if (!value) {
    return '未更新'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '未更新'
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
    .format(date)
    .replace(/\//g, '-')
}

export function formatDownloadCount(value?: number) {
  return new Intl.NumberFormat('zh-CN').format(value || 0)
}

export function softwareStatus(app: SoftwareApp): SoftwareStatusMeta {
  return app.spec.published
    ? {
        state: 'success',
        text: '已发布',
        className: 'published',
      }
    : {
        state: 'default',
        text: '草稿',
        className: 'draft',
      }
}

export function platformTokens(
  platform?: string[] | string,
  platformOptions?: SoftwareOptionItem[],
): PlatformToken[] {
  return buildPlatformTokens(platform, platformOptions)
}

export function latestVersionText(app: SoftwareApp) {
  return app.status?.latestVersion || app.status?.latestVersionName || '暂无版本'
}

export function appSearchPlaceholder() {
  return '搜索软件名称 / slug'
}
