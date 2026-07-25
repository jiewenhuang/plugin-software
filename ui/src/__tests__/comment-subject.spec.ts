import { describe, expect, it } from 'vitest'
import type { Extension } from '@halo-dev/api-client'
import type { SoftwareApp } from '@/api/software'
import { createSoftwareCommentSubjectRefProvider } from '@/comment-subject'

describe('software comment subject ref provider', () => {
  it('registers SoftwareApp as a comment source for Halo console', () => {
    const provider = createSoftwareCommentSubjectRefProvider()

    expect(provider.group).toBe('software.jiewen.run')
    expect(provider.kind).toBe('SoftwareApp')
  })

  it('resolves a software app to title and frontend detail URL', () => {
    const provider = createSoftwareCommentSubjectRefProvider()

    expect(
      provider.resolve(app('software-generated-name', 'cleanmymac-x', 'CleanMyMac X')),
    ).toEqual({
      label: '软件',
      title: 'CleanMyMac X',
      externalUrl: '/soft/cleanmymac-x',
      route: {
        name: 'SoftwareList',
      },
    })
  })

  it('falls back to metadata name when display fields are missing', () => {
    const provider = createSoftwareCommentSubjectRefProvider()

    expect(provider.resolve(app('software-generated-name'))).toEqual({
      label: '软件',
      title: 'software-generated-name',
      externalUrl: '/soft/software-generated-name',
      route: {
        name: 'SoftwareList',
      },
    })
  })
})

function app(name: string, slug?: string, displayName?: string): SoftwareApp & Extension {
  return {
    apiVersion: 'software.jiewen.run/v1alpha1',
    kind: 'SoftwareApp',
    metadata: { name },
    spec: {
      slug: slug ?? '',
      displayName: displayName ?? '',
    },
  } as SoftwareApp & Extension
}
