import { describe, expect, it } from 'vitest'
import type { SoftwareApp } from '@/api/software'
import {
  applySoftwareAiSuggestion,
  buildSoftwareEditorQuery,
  createSoftwareEditorSnapshot,
  normalizeSoftwareEditorTab,
} from '../software-editor-console'

const app: SoftwareApp = {
  metadata: { name: 'software-app-bridge' },
  spec: {
    slug: 'adobe-bridge',
    displayName: 'Adobe Bridge',
    published: true,
    tagNames: ['tag-design'],
    seo: {
      title: 'Adobe Bridge',
      keywords: ['mac', 'assets'],
    },
  },
}

describe('software editor console helpers', () => {
  it('normalizes editor tab query values', () => {
    expect(normalizeSoftwareEditorTab('media')).toBe('media')
    expect(normalizeSoftwareEditorTab('unknown')).toBe('basic')
    expect(normalizeSoftwareEditorTab(null)).toBe('basic')
  })

  it('builds editor query without dropping the active tab', () => {
    expect(buildSoftwareEditorQuery('software-app-bridge', 'media')).toBe(
      '?name=software-app-bridge&tab=media',
    )
    expect(buildSoftwareEditorQuery('software-app-bridge', 'basic')).toBe(
      '?name=software-app-bridge',
    )
    expect(buildSoftwareEditorQuery('', 'downloads')).toBe('?tab=downloads')
  })

  it('creates stable snapshots for unsaved-change detection', () => {
    const left = createSoftwareEditorSnapshot(app)
    const right = createSoftwareEditorSnapshot({
      ...app,
      spec: {
        ...app.spec,
        seo: {
          keywords: ['mac', 'assets'],
          title: 'Adobe Bridge',
        },
      },
    })
    const changed = createSoftwareEditorSnapshot({
      ...app,
      spec: {
        ...app.spec,
        displayName: 'Adobe Bridge 2026',
      },
    })

    expect(left).toBe(right)
    expect(changed).not.toBe(left)
  })

  it('applies seo suggestions without changing summary or description', () => {
    const draft: SoftwareApp = {
      ...app,
      spec: {
        ...app.spec,
        summary: 'old summary',
        description: '<p>old description</p>',
        seo: {
          title: 'old title',
          description: 'old seo',
          keywords: ['old'],
        },
      },
    }

    applySoftwareAiSuggestion(draft, 'seo', {
      summary: 'new summary',
      description: '<p>new description</p>',
      seo: {
        title: 'new title',
        description: 'new seo',
        keywords: ['new'],
      },
    })

    expect(draft.spec.summary).toBe('old summary')
    expect(draft.spec.description).toBe('<p>old description</p>')
    expect(draft.spec.seo).toEqual({
      title: 'new title',
      description: 'new seo',
      keywords: ['new'],
    })
  })

  it('applies description suggestions without changing seo', () => {
    const draft: SoftwareApp = {
      ...app,
      spec: {
        ...app.spec,
        summary: 'old summary',
        description: '<p>old description</p>',
        seo: {
          title: 'old title',
          description: 'old seo',
          keywords: ['old'],
        },
      },
    }

    applySoftwareAiSuggestion(draft, 'description', {
      summary: 'new summary',
      description: '<p>new description</p>',
      seo: {
        title: 'new title',
        description: 'new seo',
        keywords: ['new'],
      },
    })

    expect(draft.spec.summary).toBe('old summary')
    expect(draft.spec.description).toBe('<p>new description</p>')
    expect(draft.spec.seo).toEqual({
      title: 'old title',
      description: 'old seo',
      keywords: ['old'],
    })
  })

  it('applies basic suggestions to summary only', () => {
    const draft: SoftwareApp = {
      ...app,
      spec: {
        ...app.spec,
        summary: 'old summary',
        description: '<p>old description</p>',
      },
    }

    applySoftwareAiSuggestion(draft, 'basic', {
      summary: 'new summary',
      description: '<p>new description</p>',
    })

    expect(draft.spec.summary).toBe('new summary')
    expect(draft.spec.description).toBe('<p>old description</p>')
  })
})
