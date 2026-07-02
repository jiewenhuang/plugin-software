<template>
  <section class="console-page-scaffold">
    <slot />
  </section>
</template>

<style scoped lang="scss">
.console-page-scaffold {
  display: flex;
  box-sizing: border-box;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
  padding: 16px 16px 20px;
}
</style>
