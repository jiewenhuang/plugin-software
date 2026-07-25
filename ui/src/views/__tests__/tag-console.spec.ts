import { describe, expect, it } from 'vitest'
import type { SoftwareTag } from '@/api/software'
import {
  buildTagRows,
  buildTagStats,
  filterTagRows,
  formatTagDate,
  sortTagRows,
  tagMatchesConsoleFilters,
  tagStatus,
} from '../tag-console'

const tags: SoftwareTag[] = [
  {
    metadata: {
      name: 'tag-halo',
      creationTimestamp: '2024-05-20T14:30:00Z',
    },
    spec: {
      slug: 'halo',
      displayName: 'Halo',
      color: '#e11d48',
      visible: true,
      priority: 10,
    },
    status: {
      appCount: 128,
    },
  },
  {
    metadata: {
      name: 'tag-unused',
      creationTimestamp: '2024-05-01T08:00:00Z',
    },
    spec: {
      slug: 'unused-tag',
      displayName: '未使用标签',
      visible: true,
      priority: 1,
    },
  },
  {
    metadata: {
      name: 'tag-hidden',
      creationTimestamp: '2024-05-18T09:15:00Z',
    },
    spec: {
      slug: 'hidden',
      displayName: '隐藏标签',
      visible: false,
      priority: 5,
    },
    status: {
      appCount: 8,
    },
  },
]

describe('tag console helpers', () => {
  it('builds dashboard stats from used and unused tags', () => {
    expect(buildTagStats(tags)).toEqual({
      total: 3,
      used: 2,
      unused: 1,
      linkedApps: 136,
    })
  })

  it('normalizes tag rows with usage and status metadata', () => {
    const rows = buildTagRows(tags)

    expect(rows[0]).toMatchObject({
      name: 'tag-halo',
      slug: 'halo',
      displayName: 'Halo',
      colorValue: '#e11d48',
      colorClass: 'custom',
      chipClass: 'custom',
      usageCount: 128,
      status: {
        className: 'used',
        text: '已使用',
      },
    })
    expect(rows[1]).toMatchObject({
      usageCount: 0,
      status: {
        className: 'unused',
        text: '未使用',
      },
    })
    expect(rows[2].status).toEqual({
      className: 'hidden',
      text: '隐藏',
    })
  })

  it('filters tags by keyword and usage state', () => {
    const rows = buildTagRows(tags)

    expect(
      filterTagRows(rows, {
        keyword: 'unused',
        status: 'unused',
      }).map((row) => row.name),
    ).toEqual(['tag-unused'])

    expect(
      tagMatchesConsoleFilters(tags[2], {
        keyword: '隐藏',
        status: 'hidden',
      }),
    ).toBe(true)
  })

  it('sorts tags by usage, creation time, and name', () => {
    const rows = buildTagRows(tags)

    expect(sortTagRows(rows, 'usage-desc').map((row) => row.name)).toEqual([
      'tag-halo',
      'tag-hidden',
      'tag-unused',
    ])
    expect(sortTagRows(rows, 'created-desc').map((row) => row.name)).toEqual([
      'tag-halo',
      'tag-hidden',
      'tag-unused',
    ])
    expect(sortTagRows(rows, 'name-asc').map((row) => row.name)).toEqual([
      'tag-halo',
      'tag-unused',
      'tag-hidden',
    ])
  })

  it('formats dates and status values', () => {
    expect(tagStatus(tags[0])).toEqual({
      className: 'used',
      text: '已使用',
    })
    expect(formatTagDate('2024-05-20T14:30:00Z')).toBe('2024-05-20 14:30')
    expect(formatTagDate()).toBe('未记录')
  })
})
