import type { SoftwareTopic } from '@/api/software'

export type TopicStatusFilter = '' | 'published' | 'draft' | 'featured'
export type TopicSortKey =
  | 'created-desc'
  | 'created-asc'
  | 'priority-asc'
  | 'apps-desc'
  | 'title-asc'

export interface TopicConsoleFilters {
  keyword: string
  status: TopicStatusFilter
}

export interface TopicStats {
  total: number
  published: number
  drafts: number
  featured: number
  linkedApps: number
}

export interface TopicStatusMeta {
  text: '已发布' | '草稿'
  className: 'published' | 'draft'
}

export interface TopicRow {
  name: string
  slug: string
  title: string
  subtitle: string
  description: string
  cover: string
  priority: number
  published: boolean
  featured: boolean
  appCount: number
  itemCount: number
  createdAt: string
  initial: string
  tone: string
  status: TopicStatusMeta
  source: SoftwareTopic
}

const topicTones = ['mint', 'blue', 'violet', 'amber', 'rose', 'slate']

export function buildTopicStats(topics: SoftwareTopic[]): TopicStats {
  return topics.reduce<TopicStats>(
    (stats, topic) => {
      stats.total += 1
      if (topic.spec.published) {
        stats.published += 1
      } else {
        stats.drafts += 1
      }
      if (topic.spec.featured) {
        stats.featured += 1
      }
      stats.linkedApps += topicAppCount(topic)
      return stats
    },
    {
      total: 0,
      published: 0,
      drafts: 0,
      featured: 0,
      linkedApps: 0,
    },
  )
}

export function buildTopicRows(topics: SoftwareTopic[]): TopicRow[] {
  return topics.map((topic, index) => {
    const title = topic.spec.title || '未命名专题'
    return {
      name: topic.metadata?.name || '',
      slug: topic.spec.slug,
      title,
      subtitle: topic.spec.subtitle || '',
      description: topic.spec.description || '',
      cover: topic.spec.cover || '',
      priority: topic.spec.priority ?? 0,
      published: Boolean(topic.spec.published),
      featured: Boolean(topic.spec.featured),
      appCount: topicAppCount(topic),
      itemCount: topic.spec.items?.length || 0,
      createdAt: topic.metadata?.creationTimestamp || '',
      initial: topicInitial(title),
      tone: topicTone(topic.metadata?.name || topic.spec.slug || title, index),
      status: topicStatus(topic),
      source: topic,
    }
  })
}

export function filterTopicRows(rows: TopicRow[], filters: TopicConsoleFilters) {
  return rows.filter((row) => {
    if (filters.status === 'published' && !row.published) {
      return false
    }
    if (filters.status === 'draft' && row.published) {
      return false
    }
    if (filters.status === 'featured' && !row.featured) {
      return false
    }

    const keyword = filters.keyword.trim().toLowerCase()
    if (!keyword) {
      return true
    }

    return [row.name, row.slug, row.title, row.subtitle, row.description]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
      .includes(keyword)
  })
}

export function sortTopicRows(rows: TopicRow[], sortKey: TopicSortKey) {
  return [...rows].sort((left, right) => {
    if (sortKey === 'created-asc') {
      return compareText(left.createdAt, right.createdAt)
    }
    if (sortKey === 'priority-asc') {
      return left.priority - right.priority || compareText(left.title, right.title)
    }
    if (sortKey === 'apps-desc') {
      return right.appCount - left.appCount || compareText(left.title, right.title)
    }
    if (sortKey === 'title-asc') {
      return compareText(left.title, right.title)
    }
    return compareText(right.createdAt, left.createdAt)
  })
}

export function topicMatchesConsoleFilters(topic: SoftwareTopic, filters: TopicConsoleFilters) {
  return filterTopicRows(buildTopicRows([topic]), filters).length > 0
}

export function topicStatus(topic: SoftwareTopic): TopicStatusMeta {
  return topic.spec.published
    ? {
        className: 'published',
        text: '已发布',
      }
    : {
        className: 'draft',
        text: '草稿',
      }
}

export function topicAppCount(topic: SoftwareTopic) {
  return topic.status?.appCount ?? topic.spec.items?.length ?? 0
}

export function formatTopicDate(value?: string) {
  if (!value) {
    return '未记录'
  }
  return value.replace('T', ' ').slice(0, 16)
}

export function formatTopicNumber(value?: number) {
  return new Intl.NumberFormat('zh-CN').format(value || 0)
}

function topicInitial(title: string) {
  const normalized = title.trim()
  return normalized ? normalized.slice(0, 1).toUpperCase() : 'T'
}

function topicTone(seed: string, fallbackIndex: number) {
  const hash = seed
    .split('')
    .reduce((total, char) => total + char.charCodeAt(0), fallbackIndex)
  return topicTones[Math.abs(hash) % topicTones.length]
}

function compareText(left: string, right: string) {
  return left.localeCompare(right)
}
