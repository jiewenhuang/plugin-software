import type { SoftwareApp, SoftwareAppAiSuggestion, SoftwareAiTarget } from '@/api/software'

export type SoftwareEditorTab = 'basic' | 'media' | 'versions' | 'downloads' | 'seo' | 'settings'

export const softwareEditorTabs: SoftwareEditorTab[] = [
  'basic',
  'media',
  'versions',
  'downloads',
  'seo',
  'settings',
]

export function normalizeSoftwareEditorTab(value: string | null | undefined): SoftwareEditorTab {
  return softwareEditorTabs.includes(value as SoftwareEditorTab)
    ? (value as SoftwareEditorTab)
    : 'basic'
}

export function buildSoftwareEditorQuery(name: string, tab: SoftwareEditorTab) {
  const params = new URLSearchParams()
  if (name) {
    params.set('name', name)
  }
  if (tab !== 'basic') {
    params.set('tab', tab)
  }
  const query = params.toString()
  return query ? `?${query}` : ''
}

function stableValue(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(stableValue)
  }
  if (!value || typeof value !== 'object') {
    return value
  }
  return Object.keys(value)
    .sort()
    .reduce<Record<string, unknown>>((nextValue, key) => {
      nextValue[key] = stableValue((value as Record<string, unknown>)[key])
      return nextValue
    }, {})
}

export function createSoftwareEditorSnapshot(app: SoftwareApp) {
  return JSON.stringify(stableValue(app))
}

export function applySoftwareAiSuggestion(
  app: SoftwareApp,
  target: SoftwareAiTarget,
  suggestion: SoftwareAppAiSuggestion,
) {
  if ((target === 'all' || target === 'basic') && suggestion.summary) {
    app.spec.summary = suggestion.summary
  }

  if ((target === 'all' || target === 'description') && suggestion.description) {
    app.spec.description = suggestion.description
  }

  if ((target === 'all' || target === 'seo') && suggestion.seo) {
    app.spec.seo = {
      ...app.spec.seo,
      ...suggestion.seo,
      keywords: suggestion.seo.keywords || app.spec.seo?.keywords,
    }
  }
}
