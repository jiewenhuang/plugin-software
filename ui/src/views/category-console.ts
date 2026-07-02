import type { SoftwareCategory } from '@/api/software'

export type CategoryStatusFilter = '' | 'enabled' | 'disabled'
export type CategorySortKey = 'created-desc' | 'created-asc' | 'priority-desc' | 'priority-asc'

export interface CategoryFilters {
  keyword: string
  parentName: string
  status: CategoryStatusFilter
}

export interface CategoryStats {
  total: number
  enabled: number
  disabled: number
  uncategorizedApps: number
}

export interface CategoryRow {
  name: string
  slug: string
  displayName: string
  parentName: string
  description: string
  priority: number
  visible: boolean
  appCount: number
  createdAt: string
  source: SoftwareCategory
}

export interface CategoryTreeNode {
  name: string
  label: string
  count: number
  depth: number
  visible: boolean
  children: CategoryTreeNode[]
}

export function buildCategoryStats(categories: SoftwareCategory[]): CategoryStats {
  return categories.reduce<CategoryStats>(
    (stats, category) => {
      stats.total += 1
      if (category.spec.visible === false) {
        stats.disabled += 1
      } else {
        stats.enabled += 1
      }
      if (category.spec.slug === 'uncategorized' || category.spec.displayName === '未分类') {
        stats.uncategorizedApps += category.status?.appCount || 0
      }
      return stats
    },
    {
      total: 0,
      enabled: 0,
      disabled: 0,
      uncategorizedApps: 0,
    },
  )
}

export function buildCategoryRows(categories: SoftwareCategory[]): CategoryRow[] {
  return categories.map((category) => ({
    name: category.metadata?.name || '',
    slug: category.spec.slug,
    displayName: category.spec.displayName,
    parentName: category.spec.parentName || '',
    description: category.spec.description || '',
    priority: category.spec.priority ?? 0,
    visible: category.spec.visible !== false,
    appCount: category.status?.appCount || 0,
    createdAt: category.metadata?.creationTimestamp || '',
    source: category,
  }))
}

export function buildCategoryTree(categories: SoftwareCategory[]): CategoryTreeNode[] {
  const rows = sortCategoryRows(buildCategoryRows(categories), 'priority-desc')
  const childrenByParent = new Map<string, CategoryRow[]>()
  const rowByName = new Map(rows.map((row) => [row.name, row]))

  rows.forEach((row) => {
    const parentKey = row.parentName && rowByName.has(row.parentName) ? row.parentName : ''
    childrenByParent.set(parentKey, [...(childrenByParent.get(parentKey) || []), row])
  })

  const toNode = (row: CategoryRow, depth: number): CategoryTreeNode => {
    const children = (childrenByParent.get(row.name) || []).map((child) => toNode(child, depth + 1))
    return {
      name: row.name,
      label: row.displayName,
      count: row.appCount + children.reduce((total, child) => total + child.count, 0),
      depth,
      visible: row.visible,
      children,
    }
  }

  return (childrenByParent.get('') || []).map((row) => toNode(row, 0))
}

export function categoryMatchesFilters(row: CategoryRow, filters: CategoryFilters) {
  if (filters.parentName && row.parentName !== filters.parentName && row.name !== filters.parentName) {
    return false
  }
  if (filters.status === 'enabled' && !row.visible) {
    return false
  }
  if (filters.status === 'disabled' && row.visible) {
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
}

export function sortCategoryRows(rows: CategoryRow[], sortKey: CategorySortKey) {
  return [...rows].sort((left, right) => {
    if (sortKey === 'created-asc') {
      return compareText(left.createdAt, right.createdAt)
    }
    if (sortKey === 'priority-desc') {
      return right.priority - left.priority || compareText(left.displayName, right.displayName)
    }
    if (sortKey === 'priority-asc') {
      return left.priority - right.priority || compareText(left.displayName, right.displayName)
    }
    return compareText(right.createdAt, left.createdAt)
  })
}

export function formatCategoryDate(value: string) {
  if (!value) {
    return '未记录'
  }
  return value.replace('T', ' ').slice(0, 16)
}

function compareText(left: string, right: string) {
  return left.localeCompare(right)
}
