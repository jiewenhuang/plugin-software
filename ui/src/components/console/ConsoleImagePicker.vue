<script setup lang="ts">
import {
  IconArrowUpDownLine,
  IconDeleteBin,
  IconImageAddLine,
  IconLink,
  IconUpload,
  Toast,
  VButton,
  VModal,
  VSpace,
} from '@halo-dev/components'
import { consoleApiClient } from '@halo-dev/api-client'
import type { AttachmentLike } from '@halo-dev/ui-shared'
import { computed, onBeforeUnmount, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue?: string | string[]
    label: string
    description?: string
    emptyText?: string
    multiple?: boolean
    max?: number
    accepts?: string[]
    previewVariant?: 'wide' | 'cover' | 'square'
  }>(),
  {
    modelValue: undefined,
    description: '',
    emptyText: '未选择图片',
    multiple: false,
    max: undefined,
    accepts: () => ['image/*'],
    previewVariant: 'wide',
  },
)

const emit = defineEmits<{
  (event: 'update:modelValue', value: string | string[]): void
}>()

const sourceVisible = ref(false)
const selectorVisible = ref(false)
const urlVisible = ref(false)
const urlDraft = ref('')
const uploading = ref(false)
const fileInputRef = ref<HTMLInputElement>()
const draggingIndex = ref<number | null>(null)
const dragOverIndex = ref<number | null>(null)
const pointerDragIndex = ref<number | null>(null)

const imageUrls = computed(() => {
  const value = props.modelValue
  const urls = Array.isArray(value) ? value : value ? [value] : []
  return props.multiple ? urls.filter(Boolean) : urls.filter(Boolean).slice(0, 1)
})

const remainingSlots = computed(() => {
  if (!props.multiple || props.max === undefined) {
    return undefined
  }
  return Math.max(props.max - imageUrls.value.length, 0)
})

const selectorMax = computed(() => {
  if (!props.multiple) {
    return 1
  }
  return remainingSlots.value || undefined
})

const canChooseMore = computed(() => {
  if (!props.multiple || props.max === undefined) {
    return true
  }
  return imageUrls.value.length < props.max
})
const sourceModalTitle = computed(() => {
  if (props.multiple) {
    return `添加${props.label}`
  }
  return `${imageUrls.value.length ? '更换' : '选择'}${props.label}`
})

function normalizeAttachmentUrl(attachment: AttachmentLike) {
  if (typeof attachment === 'string') {
    return attachment
  }
  if ('url' in attachment && typeof attachment.url === 'string') {
    return attachment.url
  }
  if ('status' in attachment) {
    const status = attachment.status as { permalink?: string } | undefined
    return status?.permalink || ''
  }
  return ''
}

function emitUrls(urls: string[]) {
  const normalized = Array.from(new Set(urls.map((url) => url.trim()).filter(Boolean)))
  emit('update:modelValue', props.multiple ? normalized : normalized[0] || '')
}

function handleSelect(attachments: AttachmentLike[]) {
  const selectedUrls = attachments.map(normalizeAttachmentUrl).filter(Boolean)
  if (!selectedUrls.length) {
    return
  }

  if (!props.multiple) {
    emitUrls([selectedUrls[0]])
    return
  }

  const nextUrls = [...imageUrls.value, ...selectedUrls]
  if (props.max !== undefined && nextUrls.length > props.max) {
    Toast.warning(`最多选择 ${props.max} 张图片`)
  }
  emitUrls(props.max === undefined ? nextUrls : nextUrls.slice(0, props.max))
}

function appendUrls(urls: string[]) {
  if (!urls.length) {
    return
  }

  if (!props.multiple) {
    emitUrls([urls[0]])
    return
  }

  const nextUrls = [...imageUrls.value, ...urls]
  if (props.max !== undefined && nextUrls.length > props.max) {
    Toast.warning(`最多选择 ${props.max} 张图片`)
  }
  emitUrls(props.max === undefined ? nextUrls : nextUrls.slice(0, props.max))
}

function removeImage(index: number) {
  const nextUrls = imageUrls.value.filter((_, currentIndex) => currentIndex !== index)
  emitUrls(nextUrls)
}

function openSelector() {
  if (!canChooseMore.value) {
    return
  }
  sourceVisible.value = true
}

function chooseFromAttachment() {
  sourceVisible.value = false
  selectorVisible.value = true
}

function triggerUpload() {
  if (!canChooseMore.value || uploading.value) {
    return
  }
  sourceVisible.value = false
  fileInputRef.value?.click()
}

async function handleUploadFiles(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!files.length) {
    return
  }

  const allowedFiles = props.multiple ? files.slice(0, selectorMax.value) : files.slice(0, 1)
  if (props.multiple && files.length > allowedFiles.length && props.max !== undefined) {
    Toast.warning(`最多选择 ${props.max} 张图片`)
  }

  uploading.value = true
  try {
    const uploadedUrls: string[] = []
    for (const file of allowedFiles) {
      const { data } = await consoleApiClient.storage.attachment.uploadAttachmentForConsole({
        file,
      })
      const url = normalizeAttachmentUrl(data)
      if (url) {
        uploadedUrls.push(url)
      }
    }
    appendUrls(uploadedUrls)
    if (uploadedUrls.length) {
      Toast.success('图片上传成功')
    }
  } catch {
    Toast.error('图片上传失败')
  } finally {
    uploading.value = false
  }
}

function openUrlInput() {
  if (!canChooseMore.value) {
    return
  }
  urlDraft.value = ''
  sourceVisible.value = false
  urlVisible.value = true
}

function confirmUrlInput() {
  const url = urlDraft.value.trim()
  if (!url) {
    Toast.warning('请输入图片地址')
    return
  }
  try {
    const parsed = new URL(url, window.location.origin)
    if (!['http:', 'https:', 'data:', 'blob:'].includes(parsed.protocol)) {
      Toast.warning('请输入有效的图片地址')
      return
    }
    appendUrls([parsed.href])
    urlVisible.value = false
    urlDraft.value = ''
  } catch {
    Toast.warning('请输入有效的图片地址')
  }
}

function reorderImage(fromIndex: number, toIndex: number) {
  if (!props.multiple || fromIndex === toIndex) {
    return
  }
  if (
    fromIndex < 0 ||
    toIndex < 0 ||
    fromIndex >= imageUrls.value.length ||
    toIndex >= imageUrls.value.length
  ) {
    return
  }
  const nextUrls = [...imageUrls.value]
  const [movedUrl] = nextUrls.splice(fromIndex, 1)
  nextUrls.splice(toIndex, 0, movedUrl)
  emitUrls(nextUrls)
}

function handleDragStart(event: DragEvent, index: number) {
  if (!props.multiple) {
    return
  }
  draggingIndex.value = index
  event.dataTransfer?.setData('text/plain', String(index))
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

function handleDragOver(event: DragEvent, index: number) {
  if (!props.multiple || draggingIndex.value === null) {
    return
  }
  event.preventDefault()
  dragOverIndex.value = index
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

function handleDrop(event: DragEvent, index: number) {
  if (!props.multiple) {
    return
  }
  event.preventDefault()
  const fromIndex = Number(event.dataTransfer?.getData('text/plain') || draggingIndex.value)
  reorderImage(fromIndex, index)
  draggingIndex.value = null
  dragOverIndex.value = null
}

function handleDragEnd() {
  draggingIndex.value = null
  dragOverIndex.value = null
}

function cardIndexFromPoint(x: number, y: number) {
  const card = document
    .elementsFromPoint(x, y)
    .find(
      (element) => element instanceof HTMLElement && element.classList.contains('image-card'),
    ) as HTMLElement | undefined
  const index = Number(card?.dataset.index)
  return Number.isFinite(index) ? index : null
}

function handlePointerMove(event: PointerEvent) {
  if (pointerDragIndex.value === null) {
    return
  }
  dragOverIndex.value = cardIndexFromPoint(event.clientX, event.clientY)
}

function cancelPointerDrag() {
  pointerDragIndex.value = null
  draggingIndex.value = null
  dragOverIndex.value = null
  window.removeEventListener('pointermove', handlePointerMove)
  window.removeEventListener('pointerup', handlePointerUp)
  window.removeEventListener('pointercancel', cancelPointerDrag)
}

function handlePointerUp(event: PointerEvent) {
  const fromIndex = pointerDragIndex.value
  if (fromIndex !== null) {
    const toIndex = cardIndexFromPoint(event.clientX, event.clientY)
    if (toIndex !== null) {
      reorderImage(fromIndex, toIndex)
    }
  }
  cancelPointerDrag()
}

function handlePointerDown(event: PointerEvent, index: number) {
  if (!props.multiple || imageUrls.value.length < 2) {
    return
  }
  event.preventDefault()
  pointerDragIndex.value = index
  draggingIndex.value = index
  dragOverIndex.value = null
  window.addEventListener('pointermove', handlePointerMove)
  window.addEventListener('pointerup', handlePointerUp)
  window.addEventListener('pointercancel', cancelPointerDrag)
}

onBeforeUnmount(cancelPointerDrag)

function imageLabel(url: string, index: number) {
  if (url.startsWith('data:image')) {
    return `内联图片 ${index + 1}`
  }
  try {
    const pathname = new URL(url, window.location.origin).pathname
    const filename = pathname.split('/').filter(Boolean).pop()
    return filename || `图片 ${index + 1}`
  } catch {
    return url || `图片 ${index + 1}`
  }
}
</script>

<template>
  <div
    class="console-image-picker"
    :class="[`preview-${previewVariant}`, { 'is-multiple': multiple }]"
  >
    <div class="picker-heading">
      <div>
        <span>{{ label }}</span>
        <small v-if="description">{{ description }}</small>
      </div>
      <small v-if="multiple && max" class="picker-count">{{ imageUrls.length }}/{{ max }}</small>
    </div>

    <div v-if="imageUrls.length" class="image-grid">
      <article
        v-for="(url, index) in imageUrls"
        :key="`${url}-${index}`"
        class="image-card"
        :class="{
          dragging: draggingIndex === index,
          'drag-over': dragOverIndex === index && draggingIndex !== index,
        }"
        :data-index="index"
        :draggable="multiple && imageUrls.length > 1"
        @dragstart="handleDragStart($event, index)"
        @dragover="handleDragOver($event, index)"
        @drop="handleDrop($event, index)"
        @dragend="handleDragEnd"
      >
        <button
          v-if="!multiple"
          class="image-preview image-preview-action"
          type="button"
          :title="`更换${label}`"
          @click="openSelector"
        >
          <img :src="url" alt="" draggable="false" loading="lazy" />
          <span class="preview-overlay">
            <IconImageAddLine />
          </span>
        </button>
        <div v-else class="image-preview">
          <img :src="url" alt="" draggable="false" loading="lazy" />
          <span
            v-if="imageUrls.length > 1"
            class="drag-handle"
            title="拖拽排序"
            @pointerdown="handlePointerDown($event, index)"
          >
            <IconArrowUpDownLine />
          </span>
        </div>
        <div class="image-meta">
          <span :title="url">{{ imageLabel(url, index) }}</span>
          <button type="button" aria-label="移除图片" @click="removeImage(index)">
            <IconDeleteBin />
          </button>
        </div>
      </article>

      <button v-if="multiple && canChooseMore" class="add-card" type="button" @click="openSelector">
        <IconImageAddLine />
        <span>{{ emptyText }}</span>
      </button>
    </div>

    <button v-else class="empty-picker" type="button" @click="openSelector">
      <IconImageAddLine />
      <span>{{ emptyText }}</span>
    </button>

    <input
      ref="fileInputRef"
      class="file-input"
      type="file"
      :accept="accepts.join(',')"
      :multiple="multiple"
      @change="handleUploadFiles"
    />

    <VModal
      v-if="sourceVisible"
      :title="sourceModalTitle"
      :width="520"
      :body-class="['image-source-modal-body']"
      @close="sourceVisible = false"
    >
      <div class="source-options">
        <button class="source-option" type="button" :disabled="uploading" @click="triggerUpload">
          <span class="source-option-icon">
            <IconUpload />
          </span>
          <span>
            <strong>直接上传</strong>
            <small>从本地选择图片，上传到 Halo 附件库后使用。</small>
          </span>
        </button>
        <button class="source-option" type="button" @click="chooseFromAttachment">
          <span class="source-option-icon">
            <IconImageAddLine />
          </span>
          <span>
            <strong>从附件库选择</strong>
            <small>复用已经上传到附件库的图片资源。</small>
          </span>
        </button>
        <button class="source-option" type="button" @click="openUrlInput">
          <span class="source-option-icon">
            <IconLink />
          </span>
          <span>
            <strong>输入图片地址</strong>
            <small>使用外部图片 URL，适合已有 CDN 或远程资源。</small>
          </span>
        </button>
      </div>
      <template #footer>
        <VSpace>
          <VButton @click="sourceVisible = false">取消</VButton>
        </VSpace>
      </template>
    </VModal>

    <VModal
      v-if="urlVisible"
      title="输入图片地址"
      :width="560"
      :body-class="['image-url-modal-body']"
      @close="urlVisible = false"
    >
      <label class="url-field">
        <span>图片 URL</span>
        <input
          v-model.trim="urlDraft"
          class="url-input"
          placeholder="https://example.com/image.png"
          type="url"
          @keydown.enter.prevent="confirmUrlInput"
        />
      </label>
      <template #footer>
        <VSpace>
          <VButton type="secondary" @click="confirmUrlInput">使用图片</VButton>
          <VButton @click="urlVisible = false">取消</VButton>
        </VSpace>
      </template>
    </VModal>

    <AttachmentSelectorModal
      v-model:visible="selectorVisible"
      :accepts="accepts"
      :min="1"
      :max="selectorMax"
      @select="handleSelect"
    />
  </div>
</template>

<style scoped lang="scss">
.console-image-picker {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.picker-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  color: #374151;
  font-size: 13px;
}

.picker-heading > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.picker-heading small,
.picker-count {
  color: #6b7280;
  font-size: 12px;
  line-height: 1.35;
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 10px;
}

.console-image-picker:not(.is-multiple) .image-grid {
  grid-template-columns: minmax(0, 1fr);
}

.image-card {
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease,
    opacity 0.15s ease,
    transform 0.15s ease;
}

.image-card[draggable='true'] {
  cursor: grab;
}

.image-card.dragging {
  opacity: 0.55;
  transform: scale(0.98);
}

.image-card.drag-over {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.image-preview {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  border: 0;
  padding: 0;
  overflow: hidden;
  background:
    linear-gradient(45deg, #f8fafc 25%, transparent 25%),
    linear-gradient(-45deg, #f8fafc 25%, transparent 25%),
    linear-gradient(45deg, transparent 75%, #f8fafc 75%),
    linear-gradient(-45deg, transparent 75%, #f8fafc 75%);
  background-color: #eef2f7;
  background-position:
    0 0,
    0 8px,
    8px -8px,
    -8px 0;
  background-size: 16px 16px;
}

.image-preview-action {
  display: block;
  cursor: pointer;
}

.image-preview-action:hover .preview-overlay {
  opacity: 1;
}

.console-image-picker:not(.is-multiple) .image-preview {
  aspect-ratio: 2.4 / 1;
}

.console-image-picker.preview-cover:not(.is-multiple) .image-preview,
.console-image-picker.preview-cover .image-preview {
  aspect-ratio: 16 / 9;
}

.console-image-picker.preview-square:not(.is-multiple) .image-preview,
.console-image-picker.preview-square .image-preview {
  aspect-ratio: 1;
}

.image-preview img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.preview-overlay,
.drag-handle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.preview-overlay {
  position: absolute;
  inset: 0;
  opacity: 0;
  background: rgba(15, 23, 42, 0.48);
  transition: opacity 0.15s ease;
}

.preview-overlay svg {
  width: 22px;
  height: 22px;
}

.drag-handle {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 28px;
  height: 28px;
  border-radius: 7px;
  background: rgba(15, 23, 42, 0.64);
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.18);
}

.drag-handle svg {
  width: 16px;
  height: 16px;
}

.image-meta {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 30px;
  align-items: center;
  gap: 8px;
  height: 36px;
  border-top: 1px solid #f1f5f9;
  padding: 0 6px 0 10px;
}

.image-meta span {
  overflow: hidden;
  color: #4b5563;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.image-meta button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #9ca3af;
  cursor: pointer;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}

.image-meta button:hover {
  background: #fef2f2;
  color: #dc2626;
}

.empty-picker {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 8px;
  min-height: 112px;
  aspect-ratio: 16 / 9;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  background: #f8fafc;
  color: #64748b;
  font-size: 13px;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background 0.15s ease,
    color 0.15s ease;
}

.empty-picker:hover {
  border-color: #94a3b8;
  background: #f1f5f9;
  color: #334155;
}

.empty-picker svg,
.add-card svg {
  width: 18px;
  height: 18px;
}

.console-image-picker.preview-cover .empty-picker {
  aspect-ratio: 16 / 9;
}

.console-image-picker.preview-square .empty-picker {
  aspect-ratio: 1;
}

.console-image-picker.is-multiple .empty-picker {
  min-height: 150px;
  aspect-ratio: auto;
}

.add-card {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 8px;
  min-height: 124px;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  background: #f8fafc;
  color: #64748b;
  font-size: 13px;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background 0.15s ease,
    color 0.15s ease;
}

.add-card:hover {
  border-color: #94a3b8;
  background: #f1f5f9;
  color: #334155;
}

.file-input {
  display: none;
}

.source-options {
  display: grid;
  gap: 12px;
  padding: 4px;
}

.source-option {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 12px;
  width: 100%;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 14px;
  background: #fff;
  color: #334155;
  cursor: pointer;
  text-align: left;
  transition:
    border-color 0.15s ease,
    background 0.15s ease,
    box-shadow 0.15s ease;
}

.source-option:hover:not(:disabled) {
  border-color: #bfdbfe;
  background: #f8fbff;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.06);
}

.source-option:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.source-option-icon {
  display: inline-grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 8px;
  background: #eff6ff;
  color: #2563eb;
}

.source-option-icon svg {
  width: 19px;
  height: 19px;
}

.source-option strong,
.source-option small {
  display: block;
}

.source-option strong {
  color: #0f172a;
  font-size: 14px;
  font-weight: 650;
  line-height: 20px;
}

.source-option small {
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
  line-height: 18px;
}

.url-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.url-field span {
  color: #334155;
  font-size: 13px;
  font-weight: 600;
}

.url-input {
  width: 100%;
  height: 42px;
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  padding: 0 12px;
  background: #fff;
  color: #0f172a;
  font-size: 14px;
  outline: none;
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}

.url-input:focus {
  border-color: #93c5fd;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.url-input::placeholder {
  color: #94a3b8;
}
</style>
