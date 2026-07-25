import { describe, expect, it } from 'vitest'
import type { SoftwareApp } from '@/api/software'
import {
  applySoftwareAiSuggestion,
  buildDownloadAssetPayload,
  buildSoftwareEditorQuery,
  buildVersionPayload,
  createDownloadAssetDraft,
  createSoftwareEditorSnapshot,
  createVersionDraft,
  hasDownloadTarget,
  normalizeSoftwareEditorTab,
  stableAsciiSlug,
} from '../software-editor-console'
import { defaultSoftwareOptions } from '@/options/software-options'

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

  it('creates deterministic backend-safe slugs for non-Latin inline tags', () => {
    expect(stableAsciiSlug('系统清理')).toMatch(/^tag-[a-z0-9]+$/)
    expect(stableAsciiSlug('系统清理')).toBe(stableAsciiSlug('系统清理'))
    expect(stableAsciiSlug('Crème Brûlée')).toBe('creme-brulee')
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

  it('maps version API values to a LocalDate payload', () => {
    const draft = createVersionDraft(defaultSoftwareOptions.versionStatuses, {
      metadata: { name: 'version-1' },
      spec: {
        appName: 'app-1',
        version: '2.0.0',
        releaseDate: '2026-07-26T12:34:56Z',
        sizeBytes: 0,
        installWarnings: ['Backup first'],
      },
    })
    draft.architectures = ['arm64', 'arm64', ' x64 ']
    draft.installWarnings = 'Backup first\n\nBackup first\nClose the app'
    draft.sha256 = 'a'.repeat(64)

    expect(draft.releaseDate).toBe('2026-07-26')
    expect(draft.sizeBytes).toBe('0')
    expect(buildVersionPayload('app-1', draft).spec).toMatchObject({
      appName: 'app-1',
      releaseDate: '2026-07-26',
      architectures: ['arm64', 'x64'],
      installWarnings: ['Backup first', 'Close the app'],
      sizeBytes: 0,
      checksum: { sha256: 'a'.repeat(64) },
    })
  })

  it('keeps sensitive download fields behind the explicitly authorized payload boundary', () => {
    const draft = createDownloadAssetDraft(
      [{ metadata: { name: 'version-1' }, spec: { appName: 'app-1', version: '1.0.0' } }],
      defaultSoftwareOptions.downloadTypes,
      defaultSoftwareOptions.downloadProviders,
    )
    draft.title = 'Primary mirror'
    draft.url = ' https://example.com/app.dmg '
    draft.extractionCode = ' 1234 '

    const ordinaryPayload = buildDownloadAssetPayload('app-1', draft, false)
    const secretPayload = buildDownloadAssetPayload('app-1', draft, true)

    expect(ordinaryPayload.spec).not.toHaveProperty('url')
    expect(ordinaryPayload.spec).not.toHaveProperty('extractionCode')
    expect(secretPayload.spec.url).toBe('https://example.com/app.dmg')
    expect(secretPayload.spec.extractionCode).toBe('1234')
  })

  it('accepts a password Secret as the only configured download target', () => {
    expect(
      hasDownloadTarget({ url: ' ', storageKey: '', passwordSecretName: ' download-secret ' }),
    ).toBe(true)
    expect(hasDownloadTarget({ url: ' ', storageKey: '', passwordSecretName: ' ' })).toBe(false)
  })
})
