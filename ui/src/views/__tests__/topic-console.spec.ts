import { describe, expect, it } from 'vitest'
import type { SoftwareTopic } from '@/api/software'
import {
  buildTopicRows,
  buildTopicStats,
  filterTopicRows,
  formatTopicDate,
  sortTopicRows,
  topicListParams,
  topicMatchesConsoleFilters,
  topicStatus,
} from '../topic-console'

const topics: SoftwareTopic[] = [
  {
    metadata: {
      name: 'topic-starter',
      creationTimestamp: '2024-06-01T10:30:00Z',
    },
    spec: {
      slug: 'starter',
      title: '装机必备',
      subtitle: '新电脑软件组合',
      published: true,
      featured: true,
      priority: 20,
      items: [{ appName: 'halo' }, { appName: 'pixpin' }],
    },
    status: {
      appCount: 6,
    },
  },
  {
    metadata: {
      name: 'topic-dev',
      creationTimestamp: '2024-06-03T10:30:00Z',
    },
    spec: {
      slug: 'developer-tools',
      title: '开发工具',
      description: '编辑器和构建工具',
      published: false,
      featured: false,
      priority: 8,
      items: [{ appName: 'vscode' }],
    },
  },
  {
    metadata: {
      name: 'topic-design',
      creationTimestamp: '2024-06-02T10:30:00Z',
    },
    spec: {
      slug: 'design',
      title: '设计精选',
      published: true,
      featured: false,
      priority: 12,
    },
    status: {
      appCount: 3,
    },
  },
]

describe('topic console helpers', () => {
  it('builds topic dashboard stats from published, draft, featured, and linked apps', () => {
    expect(buildTopicStats(topics)).toEqual({
      total: 3,
      published: 2,
      drafts: 1,
      featured: 1,
      linkedApps: 10,
    })
  })

  it('normalizes topic table rows with status and fallback app counts', () => {
    const rows = buildTopicRows(topics)

    expect(rows[0]).toMatchObject({
      name: 'topic-starter',
      slug: 'starter',
      title: '装机必备',
      subtitle: '新电脑软件组合',
      appCount: 6,
      itemCount: 2,
      status: {
        className: 'published',
        text: '已发布',
      },
    })
    expect(rows[1]).toMatchObject({
      appCount: 1,
      status: {
        className: 'draft',
        text: '草稿',
      },
    })
  })

  it('filters topics by status and keyword without API-specific fields', () => {
    const rows = buildTopicRows(topics)

    expect(
      filterTopicRows(rows, {
        keyword: '构建',
        status: 'draft',
      }).map((row) => row.name),
    ).toEqual(['topic-dev'])

    expect(
      topicMatchesConsoleFilters(topics[0], {
        keyword: 'starter',
        status: 'featured',
      }),
    ).toBe(true)
  })

  it('sorts topics by common console sort modes', () => {
    const rows = buildTopicRows(topics)

    expect(sortTopicRows(rows, 'created-desc').map((row) => row.name)).toEqual([
      'topic-dev',
      'topic-design',
      'topic-starter',
    ])
    expect(sortTopicRows(rows, 'apps-desc').map((row) => row.name)).toEqual([
      'topic-starter',
      'topic-design',
      'topic-dev',
    ])
    expect(sortTopicRows(rows, 'priority-desc').map((row) => row.name)).toEqual([
      'topic-starter',
      'topic-design',
      'topic-dev',
    ])
  })

  it('formats dates and keeps list requests broad for local console filters', () => {
    expect(topicStatus(topics[2])).toEqual({
      className: 'published',
      text: '已发布',
    })
    expect(formatTopicDate('2024-06-02T10:30:00Z')).toBe('2024-06-02 10:30')
    expect(formatTopicDate()).toBe('未记录')
    expect(topicListParams()).toEqual({
      page: 1,
      size: 1000,
    })
  })
})
