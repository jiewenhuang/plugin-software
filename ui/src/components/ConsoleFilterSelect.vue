<script setup lang="ts">
import { IconArrowDownLine, VDropdown, VDropdownItem } from '@halo-dev/components'
import { computed } from 'vue'

export interface FilterSelectOption {
  label: string
  value: string
  disabled?: boolean
}

const props = withDefaults(
  defineProps<{
    modelValue: string
    options: FilterSelectOption[]
    disabled?: boolean
    ariaLabel?: string
  }>(),
  {
    disabled: false,
    ariaLabel: '筛选',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const selectedOption = computed(
  () => props.options.find((option) => option.value === props.modelValue) || props.options[0],
)

function selectOption(option: FilterSelectOption) {
  if (props.disabled || option.disabled) {
    return
  }
  emit('update:modelValue', option.value)
}
</script>

<template>
  <VDropdown placement="bottom-start">
    <button
      class="console-filter-select"
      type="button"
      :disabled="disabled"
      :aria-label="ariaLabel"
    >
      <span>{{ selectedOption?.label || '请选择' }}</span>
      <IconArrowDownLine />
    </button>
    <template #popper>
      <div class="console-filter-select-menu">
        <VDropdownItem
          v-for="option in options"
          :key="option.value"
          :selected="option.value === modelValue"
          :disabled="option.disabled"
          @click="selectOption(option)"
        >
          {{ option.label }}
        </VDropdownItem>
      </div>
    </template>
  </VDropdown>
</template>

<style scoped lang="scss">
.console-filter-select {
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  height: 42px;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  padding: 0 11px;
  background: #fff;
  color: #334155;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  line-height: 1;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease,
    color 0.15s ease,
    background-color 0.15s ease;
}

.console-filter-select:hover,
.console-filter-select:focus-visible {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #2563eb;
}

.console-filter-select:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.console-filter-select:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.console-filter-select span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.console-filter-select svg {
  width: 16px;
  height: 16px;
  flex: 0 0 auto;
  color: #64748b;
}

.console-filter-select-menu {
  min-width: 150px;
  max-width: 260px;
  max-height: min(320px, calc(100vh - 96px));
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 4px;
  scrollbar-gutter: stable;
}
</style>
