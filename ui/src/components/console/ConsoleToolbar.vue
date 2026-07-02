<template>
  <div class="console-toolbar">
    <div class="console-toolbar-left">
      <slot name="left" />
    </div>
    <div class="console-toolbar-right">
      <slot name="right" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.console-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  min-height: 74px;
  border-bottom: 1px solid #eef2f7;
  padding: 14px 16px;
  background: #fbfcfe;
}

.console-toolbar-left,
.console-toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.console-toolbar-left {
  flex: 1 1 auto;
  flex-wrap: wrap;
}

.console-toolbar-right {
  flex: 0 0 auto;
  justify-content: flex-end;
}

@media (max-width: 720px) {
  .console-toolbar,
  .console-toolbar-left,
  .console-toolbar-right {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
