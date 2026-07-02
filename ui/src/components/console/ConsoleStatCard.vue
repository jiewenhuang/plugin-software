<script setup lang="ts">
withDefaults(
  defineProps<{
    label: string
    value: string | number
    meta?: string
    tone?: 'blue' | 'green' | 'amber' | 'orange' | 'violet' | 'mint'
  }>(),
  {
    meta: '',
    tone: 'blue',
  },
)
</script>

<template>
  <article class="console-stat-card" :class="tone">
    <span class="console-stat-icon">
      <slot name="icon" />
    </span>
    <div class="console-stat-content">
      <small>{{ label }}</small>
      <strong>{{ value }}</strong>
      <p v-if="meta">{{ meta }}</p>
    </div>
  </article>
</template>

<style scoped lang="scss">
.console-stat-card {
  display: flex;
  align-items: center;
  gap: 20px;
  min-height: 118px;
  border: 1px solid #e3e9f1;
  border-radius: 8px;
  padding: 20px 24px;
  background: #fff;
  box-shadow: 0 14px 34px rgba(18, 30, 45, 0.045);
}

.console-stat-icon {
  display: grid;
  place-items: center;
  width: 62px;
  height: 62px;
  border-radius: 999px;
  flex: 0 0 auto;
}

.console-stat-icon :deep(svg) {
  width: 29px;
  height: 29px;
}

.console-stat-content {
  min-width: 0;
}

.console-stat-card small,
.console-stat-card p {
  color: #64748b;
  font-size: 13px;
}

.console-stat-card strong {
  display: block;
  margin-top: 6px;
  color: #111827;
  font-size: 27px;
  font-weight: 700;
  line-height: 1;
}

.console-stat-card p {
  margin: 8px 0 0;
}

.console-stat-card.blue .console-stat-icon {
  background: #e8edff;
  color: #2458d3;
}

.console-stat-card.green .console-stat-icon,
.console-stat-card.mint .console-stat-icon {
  background: #ddf7eb;
  color: #0b9c65;
}

.console-stat-card.amber .console-stat-icon,
.console-stat-card.orange .console-stat-icon {
  background: #fff0dc;
  color: #f07815;
}

.console-stat-card.violet .console-stat-icon {
  background: #efe7ff;
  color: #7c3aed;
}

@media (max-width: 720px) {
  .console-stat-card {
    min-height: 102px;
    padding: 18px;
  }

  .console-stat-icon {
    width: 54px;
    height: 54px;
  }

  .console-stat-card strong {
    font-size: 24px;
  }
}
</style>
