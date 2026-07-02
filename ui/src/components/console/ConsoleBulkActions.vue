<script setup lang="ts">
import ConsoleTextButton from './ConsoleTextButton.vue'

withDefaults(
  defineProps<{
    count: number
    actionLabel?: string
  }>(),
  {
    actionLabel: '批量删除',
  },
)

const emit = defineEmits<{
  action: []
}>()
</script>

<template>
  <div v-if="count" class="console-bulk-actions" aria-live="polite">
    <span>已选 {{ count }} 项</span>
    <ConsoleTextButton danger @click="emit('action')">
      {{ actionLabel }}
    </ConsoleTextButton>
  </div>
</template>

<style scoped lang="scss">
.console-bulk-actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 42px;
  border-left: 1px solid #e3e9f1;
  padding-left: 12px;
  color: #64748b;
  font-size: 13px;
  white-space: nowrap;
}
</style>
