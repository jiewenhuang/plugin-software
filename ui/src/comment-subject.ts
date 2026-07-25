import type { Extension } from '@halo-dev/api-client'
import type { CommentSubjectRefProvider, CommentSubjectRefResult } from '@halo-dev/ui-shared'
import type { SoftwareApp } from '@/api/software'

export function createSoftwareCommentSubjectRefProvider(): CommentSubjectRefProvider {
  return {
    group: 'software.jiewen.run',
    kind: 'SoftwareApp',
    resolve: resolveSoftwareCommentSubject,
  }
}

export function resolveSoftwareCommentSubject(subject: Extension): CommentSubjectRefResult {
  const app = subject as unknown as SoftwareApp
  const resourceName = firstText(app.metadata?.name)
  const title = firstText(app.spec?.displayName, resourceName)
  const slug = firstText(app.spec?.slug, resourceName)

  return {
    label: '软件',
    title,
    externalUrl: `/soft/${slug}`,
    route: {
      name: 'SoftwareList',
    },
  }
}

function firstText(...values: Array<string | undefined>) {
  return values.find((value) => value?.trim())?.trim() ?? ''
}
