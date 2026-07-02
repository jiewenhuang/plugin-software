<script setup lang="ts">
import { VCard } from '@halo-dev/components'
import { computed, useSlots } from 'vue'

const props = withDefaults(
  defineProps<{
    bodyClass?: string | string[]
  }>(),
  {
    bodyClass: '',
  },
)

const slots = useSlots()
const bodyClasses = computed(() =>
  ['console-table-card-body', ...(Array.isArray(props.bodyClass) ? props.bodyClass : [props.bodyClass])].filter(
    Boolean,
  ),
)
</script>

<template>
  <VCard class="console-table-card" :body-class="bodyClasses">
    <template v-if="slots.header" #header>
      <slot name="header" />
    </template>

    <slot />

    <template v-if="slots.footer" #footer>
      <slot name="footer" />
    </template>
  </VCard>
</template>

<style scoped lang="scss">
.console-table-card {
  overflow: hidden;
  border: 1px solid #e3e9f1;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 10px 28px rgba(18, 30, 45, 0.035);
}

:deep(.console-table-card-body) {
  padding: 0;
}
</style>
