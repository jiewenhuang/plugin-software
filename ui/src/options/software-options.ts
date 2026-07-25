import type { SoftwareOptionItem, SoftwareOptions } from '@/api/software'
import type { FilterSelectOption } from '@/components/ConsoleFilterSelect.vue'

export interface PlatformToken {
  label: string
  shortLabel: string
  className: string
}

export const defaultSoftwareOptions: SoftwareOptions = {
  platforms: [
    { label: 'macOS', value: 'macOS', shortLabel: 'M', className: 'macos' },
    { label: 'Windows', value: 'Windows', shortLabel: 'W', className: 'windows' },
    { label: 'Linux', value: 'Linux', shortLabel: 'L', className: 'linux' },
    { label: 'iOS', value: 'iOS', shortLabel: 'i', className: 'ios' },
    { label: 'Android', value: 'Android', shortLabel: 'A', className: 'android' },
    { label: 'Web', value: 'Web', shortLabel: 'Web', className: 'web' },
  ],
  architectures: [
    { label: 'Apple Silicon', value: 'arm64' },
    { label: 'Intel x64', value: 'x64' },
    { label: 'Universal', value: 'universal' },
  ],
  versionStatuses: [
    { label: '已发布', value: 'released' },
    { label: '草稿', value: 'draft' },
    { label: 'Beta', value: 'beta' },
    { label: '已废弃', value: 'deprecated' },
  ],
  downloadTypes: [
    { label: 'DMG', value: 'dmg' },
    { label: 'ZIP', value: 'zip' },
    { label: 'PKG', value: 'pkg' },
    { label: 'EXE', value: 'exe' },
    { label: 'MSI', value: 'msi' },
    { label: 'APK', value: 'apk' },
    { label: 'IPA', value: 'ipa' },
    { label: '其他', value: 'other' },
  ],
  downloadProviders: [
    { label: '外部链接', value: 'external' },
    { label: '官方源', value: 'official' },
    { label: '附件存储', value: 'attachment' },
    { label: 'GitHub', value: 'github' },
    { label: '百度网盘', value: 'baidu' },
    { label: '阿里云盘', value: 'aliyun' },
    { label: '夸克网盘', value: 'quark' },
    { label: '其他', value: 'other' },
  ],
  releaseChannels: [
    { label: '未设置', value: '' },
    { label: 'Stable', value: 'stable' },
    { label: 'Beta', value: 'beta' },
    { label: 'Alpha', value: 'alpha' },
    { label: 'Nightly', value: 'nightly' },
  ],
  locales: [
    { label: '未设置', value: '' },
    { label: '简体中文', value: 'zh-CN' },
    { label: '繁体中文', value: 'zh-TW' },
    { label: 'English', value: 'en-US' },
    { label: '日本語', value: 'ja-JP' },
    { label: '通用', value: 'global' },
  ],
}

export function normalizeSoftwareOptions(input?: Partial<SoftwareOptions>): SoftwareOptions {
  return {
    platforms: normalizeItems(input?.platforms, defaultSoftwareOptions.platforms),
    architectures: normalizeItems(input?.architectures, defaultSoftwareOptions.architectures),
    versionStatuses: normalizeItems(
      input?.versionStatuses,
      defaultSoftwareOptions.versionStatuses,
    ),
    downloadTypes: normalizeItems(input?.downloadTypes, defaultSoftwareOptions.downloadTypes),
    downloadProviders: normalizeItems(
      input?.downloadProviders,
      defaultSoftwareOptions.downloadProviders,
    ),
    releaseChannels: normalizeItems(input?.releaseChannels, defaultSoftwareOptions.releaseChannels),
    locales: normalizeItems(input?.locales, defaultSoftwareOptions.locales),
  }
}

export function toFilterOptions(items: SoftwareOptionItem[]): FilterSelectOption[] {
  return items.map((item) => ({
    label: item.label || item.value,
    value: item.value,
  }))
}

export function ensureCurrentOption(
  options: FilterSelectOption[],
  currentValue?: string,
): FilterSelectOption[] {
  if (!currentValue || options.some((option) => option.value === currentValue)) {
    return options
  }
  return [...options, { label: currentValue, value: currentValue }]
}

export function optionLabel(
  items: SoftwareOptionItem[],
  value?: string,
  fallback = '未设置',
) {
  if (!value) {
    return fallback
  }
  return items.find((item) => item.value === value)?.label || value
}

export function splitOptionText(value?: string[] | string) {
  if (Array.isArray(value)) {
    return value
  }
  return String(value || '')
    .split(/[,\s/|，、]+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

export function platformTokens(
  platform?: string[] | string,
  platformOptions: SoftwareOptionItem[] = defaultSoftwareOptions.platforms,
): PlatformToken[] {
  const tokens = splitOptionText(platform)

  if (!tokens.length) {
    return [
      {
        label: '未设置',
        shortLabel: '-',
        className: 'unknown',
      },
    ]
  }

  return tokens.slice(0, 4).map((value) => {
    const option = findOption(platformOptions, value)
    const label = option?.label || value
    return {
      label,
      shortLabel: option?.shortLabel || platformShortLabel(label),
      className: sanitizeClassName(option?.className) || platformClassName(label),
    }
  })
}

function normalizeItems(
  configured: SoftwareOptionItem[] | undefined,
  fallback: SoftwareOptionItem[],
) {
  if (!Array.isArray(configured)) {
    return [...fallback]
  }

  const optionMap = new Map<string, SoftwareOptionItem>()
  configured.forEach((item) => {
    const label = String(item?.label || '').trim()
    const value = String(item?.value ?? '').trim()
    if (!label && !value) {
      return
    }
    optionMap.set(value, {
      label: label || value,
      value,
      shortLabel: item.shortLabel?.trim() || undefined,
      className: item.className?.trim() || undefined,
    })
  })

  return [...optionMap.values()]
}

function findOption(options: SoftwareOptionItem[], value: string) {
  const normalized = value.toLowerCase()
  return options.find(
    (option) =>
      option.value.toLowerCase() === normalized || option.label.toLowerCase() === normalized,
  )
}

function sanitizeClassName(className?: string) {
  const normalized = className?.trim().toLowerCase()
  if (!normalized) {
    return ''
  }
  return /^[a-z0-9-]+$/.test(normalized) ? normalized : ''
}

function platformClassName(label: string) {
  const normalized = label.toLowerCase()
  if (normalized === 'macos' || normalized === 'mac') {
    return 'macos'
  }
  if (normalized === 'windows' || normalized === 'win') {
    return 'windows'
  }
  if (normalized === 'linux') {
    return 'linux'
  }
  if (normalized === 'ios') {
    return 'ios'
  }
  if (normalized === 'android') {
    return 'android'
  }
  if (normalized === 'web') {
    return 'web'
  }
  return 'generic'
}

function platformShortLabel(label: string) {
  const normalized = label.toLowerCase()
  if (normalized === 'macos' || normalized === 'mac') {
    return 'M'
  }
  if (normalized === 'windows' || normalized === 'win') {
    return 'W'
  }
  if (normalized === 'linux') {
    return 'L'
  }
  if (normalized === 'android') {
    return 'A'
  }
  if (normalized === 'ios') {
    return 'i'
  }
  if (normalized === 'web') {
    return 'Web'
  }
  return label.slice(0, 2).toUpperCase()
}
