<template>
  <section class="console-stats-grid">
    <slot />
  </section>
</template>

<style scoped lang="scss">
.console-stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

@media (max-width: 860px) {
  .console-stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .console-stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
