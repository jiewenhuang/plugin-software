import type { SoftwareTag } from '@/api/software'

export type TagStatusFilter = '' | 'used' | 'unused' | 'hidden'
export type TagSortKey = 'usage-desc' | 'usage-asc' | 'created-desc' | 'created-asc' | 'name-asc'

export interface TagConsoleFilters {
  keyword: string
  status: TagStatusFilter
}

export interface TagStats {
  total: number
  used: number
  unused: number
  linkedApps: number
}

export interface TagStatusMeta {
  text: '已使用' | '未使用' | '隐藏'
  className: 'used' | 'unused' | 'hidden'
}

export interface TagRow {
  name: string
  slug: string
  displayName: string
  description: string
  priority: number
  visible: boolean
  usageCount: number
  linkedApps: number
  createdAt: string
  colorValue: string
  colorClass: string
  chipClass: string
  status: TagStatusMeta
  source: SoftwareTag
}

const tagColors = ['violet', 'emerald', 'orange', 'blue', 'pink', 'slate']

export function tagListParams() {
  return {
    page: 1,
    size: 1000,
  }
}

export function buildTagStats(tags: SoftwareTag[]): TagStats {
  return tags.reduce<TagStats>(
    (stats, tag) => {
      const count = tagUsageCount(tag)
      stats.total += 1
      stats.linkedApps += count
      if (count > 0) {
        stats.used += 1
      } else {
        stats.unused += 1
      }
      return stats
    },
    {
      total: 0,
      used: 0,
      unused: 0,
      linkedApps: 0,
    },
  )
}

export function buildTagRows(tags: SoftwareTag[]): TagRow[] {
  return tags.map((tag, index) => {
    const displayName = tag.spec.displayName || '未命名标签'
    const usageCount = tagUsageCount(tag)
    const colorValue = normalizeTagColor(tag.spec.color)
    const colorClass = colorValue
      ? 'custom'
      : tagColor(tag.metadata?.name || tag.spec.slug || displayName, index)
    return {
      name: tag.metadata?.name || '',
      slug: tag.spec.slug,
      displayName,
      description: tag.spec.description || '',
      priority: tag.spec.priority ?? 0,
      visible: tag.spec.visible !== false,
      usageCount,
      linkedApps: usageCount,
      createdAt: tag.metadata?.creationTimestamp || '',
      colorValue,
      colorClass,
      chipClass: usageCount > 0 ? colorClass : 'muted',
      status: tagStatus(tag),
      source: tag,
    }
  })
}

export function filterTagRows(rows: TagRow[], filters: TagConsoleFilters) {
  return rows.filter((row) => {
    if (filters.status === 'used' && row.usageCount <= 0) {
      return false
    }
    if (filters.status === 'unused' && row.usageCount > 0) {
      return false
    }
    if (filters.status === 'hidden' && row.visible) {
      return false
    }

    const keyword = filters.keyword.trim().toLowerCase()
    if (!keyword) {
      return true
    }

    return [row.name, row.slug, row.displayName, row.description]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
      .includes(keyword)
  })
}

export function sortTagRows(rows: TagRow[], sortKey: TagSortKey) {
  return [...rows].sort((left, right) => {
    if (sortKey === 'usage-asc') {
      return left.usageCount - right.usageCount || compareText(left.displayName, right.displayName)
    }
    if (sortKey === 'created-desc') {
      return compareText(right.createdAt, left.createdAt)
    }
    if (sortKey === 'created-asc') {
      return compareText(left.createdAt, right.createdAt)
    }
    if (sortKey === 'name-asc') {
      return compareText(left.displayName, right.displayName)
    }
    return right.usageCount - left.usageCount || compareText(left.displayName, right.displayName)
  })
}

export function tagMatchesConsoleFilters(tag: SoftwareTag, filters: TagConsoleFilters) {
  return filterTagRows(buildTagRows([tag]), filters).length > 0
}

export function tagStatus(tag: SoftwareTag): TagStatusMeta {
  if (tag.spec.visible === false) {
    return {
      className: 'hidden',
      text: '隐藏',
    }
  }
  return tagUsageCount(tag) > 0
    ? {
        className: 'used',
        text: '已使用',
      }
    : {
        className: 'unused',
        text: '未使用',
      }
}

export function tagUsageCount(tag: SoftwareTag) {
  return tag.status?.appCount ?? 0
}

export function formatTagDate(value?: string) {
  if (!value) {
    return '未记录'
  }
  return value.replace('T', ' ').slice(0, 16)
}

export function formatTagNumber(value?: number) {
  return new Intl.NumberFormat('zh-CN').format(value || 0)
}

export function normalizeTagColor(value?: string) {
  const color = value?.trim()
  if (!color) {
    return ''
  }
  if (/^#[0-9a-f]{6}$/i.test(color)) {
    return color.toLowerCase()
  }
  if (/^#[0-9a-f]{3}$/i.test(color)) {
    const [, red, green, blue] = color.toLowerCase()
    return `#${red}${red}${green}${green}${blue}${blue}`
  }
  return ''
}

function tagColor(seed: string, fallbackIndex: number) {
  const hash = seed.split('').reduce((total, char) => total + char.charCodeAt(0), fallbackIndex)
  return tagColors[Math.abs(hash) % tagColors.length]
}

function compareText(left: string, right: string) {
  return left.localeCompare(right)
}
