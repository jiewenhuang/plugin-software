<script setup lang="ts">
import { IconAddCircle, IconClose, IconSearch } from '@halo-dev/components'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

export interface TokenSelectorOption {
  label: string
  value: string
  description?: string
  disabled?: boolean
}

const props = withDefaults(
  defineProps<{
    modelValue?: string[]
    options: TokenSelectorOption[]
    placeholder?: string
    emptyText?: string
    createText?: string
    allowCreate?: boolean
    creating?: boolean
    loading?: boolean
    optionLimit?: number
    disabled?: boolean
  }>(),
  {
    modelValue: () => [],
    placeholder: '输入关键词搜索',
    emptyText: '没有匹配项',
    createText: '创建',
    allowCreate: false,
    creating: false,
    loading: false,
    optionLimit: 20,
    disabled: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
  create: [keyword: string]
  search: [keyword: string]
}>()

const rootRef = ref<HTMLElement>()
const inputRef = ref<HTMLInputElement>()
const keyword = ref('')
const opened = ref(false)
let searchTimer: number | undefined

const normalizedValue = computed(() => props.modelValue || [])
const selectedSet = computed(() => new Set(normalizedValue.value))
const normalizedKeyword = computed(() => keyword.value.trim().toLowerCase())
const optionMap = computed(() => new Map(props.options.map((option) => [option.value, option])))
const selectedOptions = computed(() =>
  normalizedValue.value.map((value) => optionMap.value.get(value) || { label: value, value }),
)
const availableOptions = computed(() => {
  const query = normalizedKeyword.value
  return props.options
    .filter((option) => option.value && !selectedSet.value.has(option.value))
    .filter((option) => {
      if (!query) {
        return true
      }
      return [option.label, option.value, option.description]
        .filter(Boolean)
        .some((text) => String(text).toLowerCase().includes(query))
    })
    .slice(0, props.optionLimit)
})
const canCreate = computed(() => {
  if (!props.allowCreate || props.loading || !normalizedKeyword.value) {
    return false
  }
  return !props.options.some((option) => option.label.toLowerCase() === normalizedKeyword.value)
})

function focusInput() {
  if (props.disabled) {
    return
  }
  opened.value = true
  emit('search', keyword.value.trim())
  inputRef.value?.focus()
}

function selectOption(option: TokenSelectorOption) {
  if (props.disabled || option.disabled || selectedSet.value.has(option.value)) {
    return
  }
  emit('update:modelValue', [...normalizedValue.value, option.value])
  keyword.value = ''
  opened.value = true
  inputRef.value?.focus()
}

function removeValue(value: string) {
  if (props.disabled) {
    return
  }
  emit(
    'update:modelValue',
    normalizedValue.value.filter((item) => item !== value),
  )
}

function handleKeydown(event: KeyboardEvent) {
  if (props.disabled) {
    return
  }
  if (event.key === 'Backspace' && !keyword.value && normalizedValue.value.length) {
    removeValue(normalizedValue.value[normalizedValue.value.length - 1])
    return
  }
  if (event.key !== 'Enter') {
    return
  }

  event.preventDefault()
  const firstOption = availableOptions.value[0]
  if (firstOption) {
    selectOption(firstOption)
    return
  }
  if (canCreate.value && !props.creating) {
    emit('create', keyword.value.trim())
  }
}

function handleDocumentClick(event: MouseEvent) {
  if (!rootRef.value?.contains(event.target as Node)) {
    opened.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
  if (searchTimer) {
    window.clearTimeout(searchTimer)
  }
})

watch(keyword, (value) => {
  if (searchTimer) {
    window.clearTimeout(searchTimer)
  }
  searchTimer = window.setTimeout(() => {
    emit('search', value.trim())
  }, 260)
})

watch(
  () => normalizedValue.value.length,
  () => {
    keyword.value = ''
  },
)
</script>

<template>
  <div ref="rootRef" class="console-token-selector" :class="{ disabled, opened }">
    <div class="token-control" role="combobox" :aria-expanded="opened" @click="focusInput">
      <span v-for="option in selectedOptions" :key="option.value" class="selected-token">
        {{ option.label }}
        <button type="button" :aria-label="`移除 ${option.label}`" @click.stop="removeValue(option.value)">
          <IconClose />
        </button>
      </span>
      <input
        ref="inputRef"
        v-model="keyword"
        :disabled="disabled"
        :placeholder="selectedOptions.length ? '' : placeholder"
        @focus="opened = true"
        @keydown="handleKeydown"
      />
    </div>

    <div v-if="opened && !disabled" class="token-menu">
      <button
        v-for="option in availableOptions"
        :key="option.value"
        class="token-option"
        type="button"
        :disabled="option.disabled"
        @click="selectOption(option)"
      >
        <IconSearch />
        <span>
          <strong>{{ option.label }}</strong>
          <small v-if="option.description">{{ option.description }}</small>
        </span>
      </button>

      <button
        v-if="canCreate"
        class="token-option create-option"
        type="button"
        :disabled="creating"
        @click="emit('create', keyword.trim())"
      >
        <IconAddCircle />
        <span>
          <strong>{{ creating ? '创建中...' : `${createText}「${keyword.trim()}」` }}</strong>
          <small>创建后会自动选中</small>
        </span>
      </button>

      <div v-if="loading" class="token-empty">
        正在搜索...
      </div>

      <div v-else-if="!availableOptions.length && !canCreate" class="token-empty">
        {{ emptyText }}
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.console-token-selector {
  position: relative;
  min-width: 0;
}

.token-control {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  min-height: 38px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  padding: 4px 7px;
  background: #fff;
  cursor: text;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}

.console-token-selector.opened .token-control {
  border-color: #60a5fa;
  box-shadow: 0 0 0 3px rgba(96, 165, 250, 0.16);
}

.console-token-selector.disabled .token-control {
  cursor: not-allowed;
  opacity: 0.6;
}

.selected-token {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  max-width: 100%;
  min-height: 26px;
  border: 1px solid #bfdbfe;
  border-radius: 999px;
  padding: 0 6px 0 10px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 13px;
  line-height: 1;
}

.selected-token button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: 0;
  border-radius: 999px;
  padding: 0;
  background: transparent;
  color: #64748b;
  cursor: pointer;
}

.selected-token button:hover {
  background: rgba(37, 99, 235, 0.1);
  color: #1d4ed8;
}

.selected-token svg {
  width: 13px;
  height: 13px;
}

.token-control input {
  flex: 1 1 160px;
  min-width: 120px;
  height: 26px;
  border: 0;
  padding: 0 4px;
  background: transparent;
  color: #111827;
  font-size: 14px;
  outline: none;
}

.token-control input::placeholder {
  color: #9ca3af;
}

.token-menu {
  position: absolute;
  z-index: 30;
  top: calc(100% + 6px);
  right: 0;
  left: 0;
  max-height: min(320px, 42vh);
  overflow-y: auto;
  overscroll-behavior: contain;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 16px 34px rgba(15, 23, 42, 0.14);
}

.token-menu::-webkit-scrollbar {
  width: 8px;
}

.token-menu::-webkit-scrollbar-track {
  background: transparent;
}

.token-menu::-webkit-scrollbar-thumb {
  border: 2px solid #fff;
  border-radius: 999px;
  background: #cbd5e1;
}

.token-option {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 9px;
  align-items: center;
  width: 100%;
  border: 0;
  border-bottom: 1px solid #f1f5f9;
  padding: 10px 12px;
  background: #fff;
  color: #334155;
  text-align: left;
  cursor: pointer;
}

.token-option:last-child {
  border-bottom: 0;
}

.token-option:hover {
  background: #f8fafc;
}

.token-option:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.token-option svg {
  width: 16px;
  height: 16px;
  color: #64748b;
}

.token-option span,
.token-option strong,
.token-option small {
  min-width: 0;
}

.token-option strong,
.token-option small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.token-option strong {
  color: #111827;
  font-size: 13px;
}

.token-option small {
  margin-top: 2px;
  color: #64748b;
  font-size: 12px;
}

.create-option {
  background: #f8fbff;
}

.create-option svg {
  color: #2563eb;
}

.token-empty {
  padding: 12px;
  color: #64748b;
  font-size: 13px;
}
</style>
