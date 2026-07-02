<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string
    ariaLabel?: string
    disabled?: boolean
    active?: boolean
    danger?: boolean
  }>(),
  {
    title: '',
    ariaLabel: '',
    disabled: false,
    active: false,
    danger: false,
  },
)

const emit = defineEmits<{
  click: [event: MouseEvent]
}>()
</script>

<template>
  <button
    class="console-icon-button"
    :class="{ active, danger }"
    type="button"
    :title="title"
    :aria-label="ariaLabel || title || undefined"
    :disabled="disabled"
    @click="emit('click', $event)"
  >
    <slot />
  </button>
</template>

<style scoped lang="scss">
.console-icon-button {
  display: inline-grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: #fff;
  color: #64748b;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    color 0.15s ease,
    background-color 0.15s ease;
}

.console-icon-button :deep(svg) {
  width: 17px;
  height: 17px;
}

.console-icon-button:hover,
.console-icon-button.active {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #2563eb;
}

.console-icon-button:focus-visible {
  outline: 2px solid #93c5fd;
  outline-offset: 2px;
}

.console-icon-button.danger {
  color: #ef4444;
}

.console-icon-button.danger:hover {
  border-color: #fecdd3;
  background: #fff1f2;
  color: #dc2626;
}

.console-icon-button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
</style>
