<script setup lang="ts">
import { Toast, VButton, VModal, VSpace } from '@halo-dev/components'
import { computed, reactive, ref, watch } from 'vue'
import {
  createVersion,
  updateVersion,
  type SoftwareOptionItem,
  type SoftwareOptions,
  type SoftwareVersion,
} from '@/api/software'
import ConsoleFilterSelect from '@/components/ConsoleFilterSelect.vue'
import ConsoleTokenSelector from '@/components/console/ConsoleTokenSelector.vue'
import { ensureCurrentOption, toFilterOptions } from '@/options/software-options'
import {
  buildVersionPayload,
  createVersionDraft,
  type SoftwareVersionDraft,
} from '@/views/software-editor-console'

const props = defineProps<{
  appName: string
  softwareOptions: SoftwareOptions
}>()

const emit = defineEmits<{
  saved: []
}>()

const visible = ref(false)
const saving = ref(false)
const draft = reactive<SoftwareVersionDraft>(
  createVersionDraft(props.softwareOptions.versionStatuses),
)

const statusOptions = computed(() =>
  ensureCurrentOption(toFilterOptions(props.softwareOptions.versionStatuses), draft.status),
)
const architectureOptions = computed(() =>
  ensureCurrentTokenOptions(props.softwareOptions.architectures, draft.architectures),
)
const localeOptions = computed(() =>
  ensureCurrentOption(toFilterOptions(props.softwareOptions.locales), draft.locale),
)

function reset(version?: SoftwareVersion) {
  Object.assign(draft, createVersionDraft(props.softwareOptions.versionStatuses, version))
}

function openCreate() {
  reset()
  visible.value = true
}

function openEdit(version: SoftwareVersion) {
  reset(version)
  visible.value = true
}

function close() {
  if (saving.value) {
    return
  }
  visible.value = false
  reset()
}

async function save() {
  if (!props.appName) {
    Toast.warning('请先保存软件基础信息')
    return
  }
  if (!draft.version.trim()) {
    Toast.warning('版本号必填')
    return
  }
  if (draft.sizeBytes && (!Number.isFinite(Number(draft.sizeBytes)) || Number(draft.sizeBytes) < 0)) {
    Toast.warning('安装包大小必须是非负数字')
    return
  }

  const payload = buildVersionPayload(props.appName, draft)
  saving.value = true
  try {
    if (draft.name) {
      await updateVersion(draft.name, payload)
    } else {
      await createVersion(payload)
    }
    Toast.success('版本已保存')
    visible.value = false
    reset()
    emit('saved')
  } finally {
    saving.value = false
  }
}

function ensureCurrentTokenOptions(items: SoftwareOptionItem[], currentValues: string[]) {
  return currentValues.reduce(
    (options, value) => ensureCurrentOption(options, value),
    toFilterOptions(items),
  )
}

watch(
  () => props.appName,
  () => {
    if (!saving.value) {
      visible.value = false
      reset()
    }
  },
)

defineExpose({ openCreate, openEdit })
</script>

<template>
  <VModal
    v-if="visible"
    :title="draft.name ? '编辑版本' : '添加版本'"
    :width="760"
    :body-class="['software-child-resource-modal-body']"
    @close="close"
  >
    <div class="modal-form">
      <div class="form-grid">
        <label>
          <span>版本号 *</span>
          <input
            v-model.trim="draft.version"
            class="field"
            name="software-version"
            placeholder="1.0.0"
            autocomplete="off"
          />
        </label>
        <label>
          <span>构建号</span>
          <input
            v-model.trim="draft.build"
            class="field"
            name="software-version-build"
            placeholder="build 123"
            autocomplete="off"
          />
        </label>
        <label>
          <span>发布日期</span>
          <input
            v-model="draft.releaseDate"
            class="field"
            name="software-version-release-date"
            type="date"
          />
        </label>
        <label>
          <span>最低系统</span>
          <input
            v-model.trim="draft.minOsVersion"
            class="field"
            name="software-version-min-os"
            placeholder="macOS 13.0"
            autocomplete="off"
          />
        </label>
        <label>
          <span>最高系统</span>
          <input
            v-model.trim="draft.maxOsVersion"
            class="field"
            name="software-version-max-os"
            placeholder="macOS 15.x"
            autocomplete="off"
          />
        </label>
        <div class="relation-field full-span">
          <span>支持架构</span>
          <ConsoleTokenSelector
            v-model="draft.architectures"
            :options="architectureOptions"
            placeholder="选择 arm64 / x86_64 等架构"
            empty-text="没有匹配的架构"
          />
        </div>
        <label>
          <span>语言</span>
          <ConsoleFilterSelect
            v-model="draft.locale"
            class="editor-select"
            :options="localeOptions"
            aria-label="选择版本语言"
          />
        </label>
        <label>
          <span>大小 bytes</span>
          <input
            v-model.trim="draft.sizeBytes"
            class="field"
            name="software-version-size-bytes"
            placeholder="104857600"
            type="number"
            min="0"
            inputmode="numeric"
          />
        </label>
        <label>
          <span>状态</span>
          <ConsoleFilterSelect
            v-model="draft.status"
            class="editor-select"
            :options="statusOptions"
            aria-label="选择版本状态"
          />
        </label>
        <label class="full-span">
          <span>SHA-256</span>
          <input
            v-model.trim="draft.sha256"
            class="field checksum-field"
            name="software-version-sha256"
            minlength="64"
            maxlength="64"
            spellcheck="false"
            autocomplete="off"
          />
        </label>
        <label class="full-span">
          <span>MD5（兼容旧来源）</span>
          <input
            v-model.trim="draft.md5"
            class="field checksum-field"
            name="software-version-md5"
            minlength="32"
            maxlength="32"
            spellcheck="false"
            autocomplete="off"
          />
        </label>
      </div>
      <label class="block-field">
        <span>更新说明</span>
        <textarea v-model.trim="draft.changelog" class="textarea" rows="5" />
      </label>
      <label class="block-field">
        <span>安装说明</span>
        <textarea v-model.trim="draft.installGuide" class="textarea" rows="4" />
      </label>
      <label class="block-field">
        <span>安装警告（每行一条）</span>
        <textarea v-model.trim="draft.installWarnings" class="textarea" rows="3" />
      </label>
    </div>
    <template #footer>
      <VSpace>
        <VButton type="secondary" :loading="saving" @click="save">
          {{ draft.name ? '保存版本' : '添加版本' }}
        </VButton>
        <VButton :disabled="saving" @click="close">取消</VButton>
      </VSpace>
    </template>
  </VModal>
</template>

<style scoped lang="scss">
.modal-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

:global(.software-child-resource-modal-body) {
  max-height: calc(100vh - 220px);
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-right: 18px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

label,
.block-field,
.relation-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  color: #374151;
  font-size: 13px;
}

.field,
.textarea {
  width: 100%;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #111827;
  font-size: 14px;
}

.field {
  height: 34px;
  padding: 0 10px;
}

.textarea {
  padding: 10px;
  resize: vertical;
}

.field:focus,
.textarea:focus {
  border-color: #60a5fa;
  box-shadow: 0 0 0 3px rgba(96, 165, 250, 0.16);
  outline: none;
}

.full-span,
.relation-field {
  grid-column: 1 / -1;
}

.block-field {
  margin-top: 0;
}

.editor-select {
  min-width: 0;
}

.editor-select :deep(.console-filter-select) {
  height: 34px;
  border-color: #d1d5db;
  border-radius: 6px;
  color: #111827;
  font-size: 14px;
  font-weight: 400;
}

.checksum-field {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

@media (max-width: 720px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
