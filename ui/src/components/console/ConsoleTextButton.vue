<script setup lang="ts">
withDefaults(
  defineProps<{
    disabled?: boolean
    danger?: boolean
  }>(),
  {
    disabled: false,
    danger: false,
  },
)

const emit = defineEmits<{
  click: [event: MouseEvent]
}>()
</script>

<template>
  <button
    class="console-text-button"
    :class="{ danger }"
    type="button"
    :disabled="disabled"
    @click="emit('click', $event)"
  >
    <slot />
  </button>
</template>

<style scoped lang="scss">
.console-text-button {
  display: inline-grid;
  place-items: center;
  width: auto;
  height: 42px;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  padding: 0 13px;
  background: #fff;
  color: #334155;
  cursor: pointer;
  font-size: 14px;
  transition:
    border-color 0.15s ease,
    color 0.15s ease,
    background-color 0.15s ease;
}

.console-text-button:hover {
  border-color: #bfdbfe;
  background: #eff6ff;
  color: #2563eb;
}

.console-text-button.danger {
  border-color: #fecdd3;
  color: #d92d20;
}

.console-text-button.danger:hover {
  border-color: #fda4af;
  background: #fff1f2;
  color: #b42318;
}

.console-text-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
</style>
