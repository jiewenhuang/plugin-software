import { describe, expect, it } from 'vitest'
import type { SoftwareCategory } from '@/api/software'
import {
  buildCategoryRows,
  buildCategoryStats,
  buildCategoryTree,
  categoryMatchesFilters,
  sortCategoryRows,
} from '../category-console'

const categories: SoftwareCategory[] = [
  {
    metadata: {
      name: 'os',
      creationTimestamp: '2024-06-01T10:30:00Z',
    },
    spec: {
      slug: 'os',
      displayName: '操作系统',
      visible: true,
      priority: 10,
    },
    status: {
      appCount: 128,
    },
  },
  {
    metadata: {
      name: 'windows',
      creationTimestamp: '2024-06-02T10:30:00Z',
    },
    spec: {
      slug: 'windows',
      displayName: 'Windows',
      parentName: 'os',
      visible: true,
      priority: 3,
    },
    status: {
      appCount: 2,
    },
  },
  {
    metadata: {
      name: 'macos',
      creationTimestamp: '2024-06-03T10:30:00Z',
    },
    spec: {
      slug: 'macos',
      displayName: 'macOS',
      parentName: 'os',
      visible: true,
      priority: 2,
    },
    status: {
      appCount: 1,
    },
  },
  {
    metadata: {
      name: 'uncategorized',
      creationTimestamp: '2024-06-04T10:30:00Z',
    },
    spec: {
      slug: 'uncategorized',
      displayName: '未分类',
      visible: false,
      priority: 1,
    },
    status: {
      appCount: 12,
    },
  },
]

describe('category console helpers', () => {
  it('builds overview stats for the category dashboard', () => {
    expect(buildCategoryStats(categories)).toEqual({
      total: 4,
      enabled: 3,
      disabled: 1,
      uncategorizedApps: 12,
    })
  })

  it('builds a parent-child tree with aggregate counts', () => {
    expect(buildCategoryTree(categories)).toEqual([
      expect.objectContaining({
        name: 'uncategorized',
        label: '未分类',
        count: 12,
        depth: 0,
        children: [],
      }),
      expect.objectContaining({
        name: 'os',
        label: '操作系统',
        count: 131,
        depth: 0,
        children: [
          expect.objectContaining({ name: 'macos', label: 'macOS', count: 1, depth: 1 }),
          expect.objectContaining({ name: 'windows', label: 'Windows', count: 2, depth: 1 }),
        ],
      }),
    ])
  })

  it('filters and sorts visible category table rows', () => {
    const rows = buildCategoryRows(categories)
    const filtered = rows.filter((row) =>
      categoryMatchesFilters(row, {
        keyword: 'win',
        parentName: 'os',
        status: 'enabled',
      }),
    )

    expect(filtered.map((row) => row.name)).toEqual(['windows'])
    expect(sortCategoryRows(rows, 'created-desc').map((row) => row.name)).toEqual([
      'uncategorized',
      'macos',
      'windows',
      'os',
    ])
    expect(sortCategoryRows(rows, 'priority-desc').map((row) => row.name)).toEqual([
      'os',
      'windows',
      'macos',
      'uncategorized',
    ])
    expect(sortCategoryRows(rows, 'priority-asc').map((row) => row.name)).toEqual([
      'uncategorized',
      'macos',
      'windows',
      'os',
    ])
  })
})
