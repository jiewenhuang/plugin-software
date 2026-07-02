import { describe, expect, it } from 'vitest'
import {
  ensureCurrentOption,
  normalizeSoftwareOptions,
  optionLabel,
  platformTokens,
  splitOptionText,
  toFilterOptions,
} from '../software-options'

describe('software option helpers', () => {
  it('normalizes option settings and falls back to defaults', () => {
    const options = normalizeSoftwareOptions({
      platforms: [
        { label: 'HarmonyOS', value: 'HarmonyOS', shortLabel: 'HM', className: 'harmony' },
        { label: 'HarmonyOS Duplicate', value: 'HarmonyOS' },
        { label: '', value: '' },
      ],
    })

    expect(options.platforms).toEqual([{ label: 'HarmonyOS Duplicate', value: 'HarmonyOS' }])
    expect(options.downloadProviders.map((item) => item.value)).toContain('quark')
  })

  it('builds select labels and preserves existing values', () => {
    const options = toFilterOptions([{ label: '夸克网盘', value: 'quark' }])

    expect(ensureCurrentOption(options, 'custom-provider')).toEqual([
      { label: '夸克网盘', value: 'quark' },
      { label: 'custom-provider', value: 'custom-provider' },
    ])
    expect(optionLabel([{ label: '正式版', value: 'released' }], 'released')).toBe('正式版')
    expect(optionLabel([], '', '未设置')).toBe('未设置')
  })

  it('creates platform tokens from configured options', () => {
    expect(splitOptionText('macOS / Windows，Linux')).toEqual(['macOS', 'Windows', 'Linux'])
    expect(
      platformTokens('HarmonyOS', [
        {
          label: 'HarmonyOS',
          value: 'HarmonyOS',
          shortLabel: 'HM',
          className: 'harmony',
        },
      ]),
    ).toEqual([{ label: 'HarmonyOS', shortLabel: 'HM', className: 'harmony' }])
  })
})
