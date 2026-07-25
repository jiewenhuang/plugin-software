<script setup lang="ts">
import { Toast, VButton, VModal, VSpace, VSwitch } from '@halo-dev/components'
import { computed, reactive, ref, watch } from 'vue'
import {
  createDownloadAsset,
  getDownloadAssetSecret,
  updateDownloadAsset,
  type DownloadAsset,
  type DownloadAssetSecret,
  type SoftwareOptions,
  type SoftwareVersion,
} from '@/api/software'
import ConsoleFilterSelect from '@/components/ConsoleFilterSelect.vue'
import { ensureCurrentOption, toFilterOptions } from '@/options/software-options'
import {
  buildDownloadAssetPayload,
  createDownloadAssetDraft,
  hasDownloadTarget,
  type DownloadAssetDraft,
} from '@/views/software-editor-console'

const props = defineProps<{
  appName: string
  versions: SoftwareVersion[]
  softwareOptions: SoftwareOptions
  canManageSecrets: boolean
}>()

const emit = defineEmits<{
  saved: []
}>()

const visible = ref(false)
const saving = ref(false)
const secretFieldsEditable = ref(false)
const draft = reactive<DownloadAssetDraft>(
  createDownloadAssetDraft(
    props.versions,
    props.softwareOptions.downloadTypes,
    props.softwareOptions.downloadProviders,
  ),
)

const versionOptions = computed(() => [
  { label: '选择版本', value: '', disabled: true },
  ...props.versions.map((version) => ({
    label: version.spec.version,
    value: version.metadata?.name || '',
    disabled: !version.metadata?.name,
  })),
])
const typeOptions = computed(() =>
  ensureCurrentOption(toFilterOptions(props.softwareOptions.downloadTypes), draft.type),
)
const providerOptions = computed(() =>
  ensureCurrentOption(toFilterOptions(props.softwareOptions.downloadProviders), draft.provider),
)

function reset() {
  secretFieldsEditable.value = false
  Object.assign(
    draft,
    createDownloadAssetDraft(
      props.versions,
      props.softwareOptions.downloadTypes,
      props.softwareOptions.downloadProviders,
    ),
  )
}

function openCreate() {
  reset()
  secretFieldsEditable.value = props.canManageSecrets
  visible.value = true
}

async function openEdit(asset: DownloadAsset) {
  secretFieldsEditable.value = false
  let secret: DownloadAssetSecret | undefined
  if (props.canManageSecrets && asset.metadata?.name) {
    try {
      secret = await getDownloadAssetSecret(asset.metadata.name)
      secretFieldsEditable.value = true
    } catch {
      Toast.warning('敏感下载配置加载失败，本次只保存普通字段')
    }
  }
  Object.assign(
    draft,
    createDownloadAssetDraft(
      props.versions,
      props.softwareOptions.downloadTypes,
      props.softwareOptions.downloadProviders,
      asset,
      secret,
    ),
  )
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
  if (!draft.versionName) {
    Toast.warning('请选择关联版本')
    return
  }
  if (!draft.title.trim()) {
    Toast.warning('资源名称必填')
    return
  }
  if (!draft.name && !secretFieldsEditable.value) {
    Toast.warning('缺少下载源敏感配置权限')
    return
  }
  if (secretFieldsEditable.value && draft.enabled && !hasDownloadTarget(draft)) {
    Toast.warning('启用下载源前请填写下载 URL、storageKey 或密码 Secret')
    return
  }

  const payload = buildDownloadAssetPayload(props.appName, draft, secretFieldsEditable.value)
  saving.value = true
  try {
    if (draft.name) {
      await updateDownloadAsset(draft.name, payload)
    } else {
      await createDownloadAsset(payload)
    }
    Toast.success('下载资源已保存')
    visible.value = false
    reset()
    emit('saved')
  } finally {
    saving.value = false
  }
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
    :title="draft.name ? '编辑下载源' : '添加下载源'"
    :width="820"
    :body-class="['software-child-resource-modal-body']"
    @close="close"
  >
    <div class="modal-form">
      <div class="form-grid">
        <label>
          <span>关联版本 *</span>
          <ConsoleFilterSelect
            v-model="draft.versionName"
            class="editor-select"
            :options="versionOptions"
            aria-label="选择关联版本"
          />
        </label>
        <label>
          <span>资源名称 *</span>
          <input
            v-model.trim="draft.title"
            class="field"
            name="download-asset-title"
            placeholder="安装包 / 网盘链接"
            autocomplete="off"
          />
        </label>
        <label v-if="canManageSecrets" class="full-span">
          <span>下载 URL</span>
          <input
            v-model.trim="draft.url"
            class="field"
            name="download-asset-url"
            placeholder="https://..."
            type="url"
            inputmode="url"
            autocomplete="url"
            :disabled="!secretFieldsEditable"
          />
        </label>
        <label v-if="canManageSecrets" class="full-span">
          <span>Storage Key / 站内路径</span>
          <input
            v-model.trim="draft.storageKey"
            class="field"
            name="download-asset-storage-key"
            placeholder="/upload/... 或 https://..."
            autocomplete="off"
            :disabled="!secretFieldsEditable"
          />
        </label>
        <label v-if="canManageSecrets">
          <span>提取码</span>
          <input
            v-model.trim="draft.extractionCode"
            class="field"
            name="download-asset-extraction-code"
            autocomplete="off"
            :disabled="!secretFieldsEditable"
          />
        </label>
        <label v-if="canManageSecrets">
          <span>密码 Secret 名称</span>
          <input
            v-model.trim="draft.passwordSecretName"
            class="field"
            name="download-asset-password-secret"
            autocomplete="off"
            :disabled="!secretFieldsEditable"
          />
        </label>
        <label>
          <span>类型</span>
          <ConsoleFilterSelect
            v-model="draft.type"
            class="editor-select"
            :options="typeOptions"
            aria-label="选择下载类型"
          />
        </label>
        <label>
          <span>Provider</span>
          <ConsoleFilterSelect
            v-model="draft.provider"
            class="editor-select"
            :options="providerOptions"
            aria-label="选择下载源 Provider"
          />
        </label>
        <label>
          <span>Region</span>
          <input
            v-model.trim="draft.region"
            class="field"
            name="download-asset-region"
            placeholder="cn / global"
            autocomplete="off"
          />
        </label>
        <label>
          <span>优先级</span>
          <input
            v-model.number="draft.priority"
            class="field"
            name="download-asset-priority"
            type="number"
            min="0"
            inputmode="numeric"
          />
        </label>
      </div>
      <label class="switch-card modal-switch-card" :class="{ active: draft.enabled }">
        <VSwitch v-model="draft.enabled" />
        <span>
          <strong>启用下载源</strong>
          <small>停用后不会作为公开下载资源返回</small>
        </span>
      </label>
      <label class="switch-card modal-switch-card" :class="{ active: draft.requiresLogin }">
        <VSwitch v-model="draft.requiresLogin" />
        <span>
          <strong>需要登录</strong>
          <small>匿名访客无法取得下载授权</small>
        </span>
      </label>
      <label class="switch-card modal-switch-card" :class="{ active: draft.requiresCaptcha }">
        <VSwitch v-model="draft.requiresCaptcha" />
        <span>
          <strong>需要验证码</strong>
          <small>通过 Turnstile 校验后才会返回短期授权</small>
        </span>
      </label>
    </div>
    <template #footer>
      <VSpace>
        <VButton type="secondary" :loading="saving" @click="save">
          {{ draft.name ? '保存下载源' : '添加下载源' }}
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

label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  color: #374151;
  font-size: 13px;
}

.field {
  width: 100%;
  height: 34px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  padding: 0 10px;
  background: #fff;
  color: #111827;
  font-size: 14px;
}

.field:focus {
  border-color: #60a5fa;
  box-shadow: 0 0 0 3px rgba(96, 165, 250, 0.16);
  outline: none;
}

.full-span {
  grid-column: 1 / -1;
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

.switch-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  padding: 12px;
  background: #fff;
}

.switch-card.active {
  border-color: #bfdbfe;
  background: #eff6ff;
}

.switch-card :deep(.switch-inner) {
  background: #cbd5e1 !important;
}

.switch-card.active :deep(.switch-inner) {
  background: #2563eb !important;
}

.switch-card strong,
.switch-card small {
  display: block;
}

.switch-card strong {
  color: #111827;
  font-size: 13px;
}

.switch-card small {
  margin-top: 2px;
  color: #64748b;
  font-size: 12px;
}

@media (max-width: 720px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
