<script setup lang="ts">
import {
  Dialog,
  IconAddCircle,
  IconArrowDownCircleLine,
  IconCheckboxCircle,
  IconClipboardLine,
  IconDeleteBin,
  IconGrid,
  IconLink,
  IconListSettings,
  IconRefreshLine,
  IconRiPencilFill,
  IconSave,
  Toast,
  VButton,
  VEmpty,
  VLoading,
  VModal,
  VPagination,
  VSpace,
  VStatusDot,
  VSwitch,
} from '@halo-dev/components'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  createCategory,
  deleteCategory,
  emptyCategory,
  listAllCategories,
  listCategories,
  updateCategory,
  type SoftwareCategory,
} from '@/api/software'
import ConsoleFilterSelect, { type FilterSelectOption } from '@/components/ConsoleFilterSelect.vue'
import ConsoleIconButton from '@/components/console/ConsoleIconButton.vue'
import ConsolePageScaffold from '@/components/console/ConsolePageScaffold.vue'
import ConsoleStatCard from '@/components/console/ConsoleStatCard.vue'
import ConsoleStatsGrid from '@/components/console/ConsoleStatsGrid.vue'
import ConsoleTableCard from '@/components/console/ConsoleTableCard.vue'
import ConsoleTextButton from '@/components/console/ConsoleTextButton.vue'
import ConsoleToolbar from '@/components/console/ConsoleToolbar.vue'
import {
  buildCategoryRows,
  buildCategoryStats,
  buildCategoryTree,
  formatCategoryDate,
  sortCategoryRows,
  type CategorySortKey,
  type CategoryStatusFilter,
  type CategoryTreeNode,
} from '@/views/category-console'

type CategoryDropPosition = 'before' | 'inside' | 'after'

const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const selectedParent = ref('')
const selectedStatus = ref<CategoryStatusFilter>('')
const selectedSort = ref<CategorySortKey>('created-desc')
const items = ref<SoftwareCategory[]>([])
const catalog = ref<SoftwareCategory[]>([])
const loading = ref(false)
const saving = ref(false)
const sortSaving = ref(false)
const deletingName = ref('')
const editingName = ref('')
const composerOpen = ref(false)
const draft = ref<SoftwareCategory>(emptyCategory())
const draggingName = ref('')
const dropTargetName = ref('')
const dropPosition = ref<CategoryDropPosition>('after')
let fetchSequence = 0
let searchTimer: ReturnType<typeof setTimeout> | undefined

const rows = computed(() => buildCategoryRows(catalog.value))
const pageRows = computed(() => buildCategoryRows(items.value))
const stats = computed(() => buildCategoryStats(catalog.value))
const treeItems = computed(() => flattenTree(buildCategoryTree(catalog.value)))
const isEditing = computed(() => Boolean(editingName.value))
const composerTitle = computed(() => (isEditing.value ? '编辑分类' : '新建分类'))
const canSave = computed(() =>
  Boolean(draft.value.spec.displayName?.trim() && draft.value.spec.slug?.trim()),
)
const hasFilters = computed(() =>
  Boolean(keyword.value || selectedParent.value || selectedStatus.value),
)
const parentOptions = computed<FilterSelectOption[]>(() => [
  { label: '全部分类', value: '' },
  ...rows.value
    .filter((row) => !row.parentName)
    .sort((left, right) => left.displayName.localeCompare(right.displayName))
    .map((row) => ({ label: row.displayName, value: row.name })),
])
const statusOptions: FilterSelectOption[] = [
  { label: '全部状态', value: '' },
  { label: '启用', value: 'enabled' },
  { label: '停用', value: 'disabled' },
]
const sortOptions: FilterSelectOption[] = [
  { label: '创建时间 ↓', value: 'created-desc' },
  { label: '创建时间 ↑', value: 'created-asc' },
  { label: '优先级 ↓', value: 'priority-desc' },
  { label: '优先级 ↑', value: 'priority-asc' },
]

async function fetchCategories(refreshCatalog = false) {
  const sequence = ++fetchSequence
  loading.value = true
  try {
    const [result, catalogResult] = await Promise.all([
      listCategories({
        page: page.value,
        size: size.value,
        keyword: keyword.value || undefined,
        visible:
          selectedStatus.value === 'enabled'
            ? true
            : selectedStatus.value === 'disabled'
              ? false
              : undefined,
        parentName: selectedParent.value || undefined,
        sort: sortParameter(selectedSort.value),
      }),
      refreshCatalog || !catalog.value.length ? listAllCategories() : Promise.resolve(undefined),
    ])
    if (sequence !== fetchSequence) {
      return
    }
    items.value = result.items
    total.value = result.total
    if (catalogResult) {
      catalog.value = catalogResult.items
    }
    if (!result.items.length && result.total > 0 && page.value > 1) {
      page.value -= 1
    }
  } finally {
    if (sequence === fetchSequence) {
      loading.value = false
    }
  }
}

function openCreate() {
  editingName.value = ''
  draft.value = emptyCategory()
  composerOpen.value = true
}

function openEdit(category: SoftwareCategory) {
  editingName.value = category.metadata?.name || ''
  draft.value = cloneCategory(category)
  composerOpen.value = true
}

function closeComposer() {
  if (saving.value) {
    return
  }
  editingName.value = ''
  draft.value = emptyCategory()
  composerOpen.value = false
}

async function save() {
  if (!canSave.value) {
    Toast.warning('分类名称和 slug 必填')
    return
  }
  if (
    draft.value.spec.parentName &&
    editingName.value &&
    (draft.value.spec.parentName === editingName.value ||
      isDescendant(draft.value.spec.parentName, editingName.value))
  ) {
    Toast.warning('不能将分类设置为自身或子分类的下级')
    return
  }

  const payload = cloneCategory(draft.value)
  payload.spec.parentName = payload.spec.parentName || undefined
  saving.value = true
  try {
    if (editingName.value) {
      await updateCategory(editingName.value, payload)
    } else {
      await createCategory(payload)
    }
    Toast.success('保存成功')
    closeComposerAfterSave()
    await fetchCategories(true)
  } finally {
    saving.value = false
  }
}

function confirmDelete(category: SoftwareCategory) {
  const name = category.metadata?.name
  if (!name) {
    return
  }
  Dialog.warning({
    title: '删除分类',
    description: `确定删除「${category.spec.displayName}」吗？`,
    confirmType: 'danger',
    onConfirm: async () => {
      deletingName.value = name
      try {
        await deleteCategory(name)
        if (editingName.value === name) {
          closeComposerAfterSave()
        }
        Toast.success('删除成功')
        await fetchCategories(true)
      } finally {
        deletingName.value = ''
      }
    },
  })
}

function clearFilters() {
  keyword.value = ''
  selectedParent.value = ''
  selectedStatus.value = ''
  selectedSort.value = 'created-desc'
  page.value = 1
}

function selectParent(name: string) {
  selectedParent.value = name
}

function startDrag(node: CategoryTreeNode, event: DragEvent) {
  if (sortSaving.value) {
    event.preventDefault()
    return
  }
  draggingName.value = node.name
  dropTargetName.value = ''
  event.dataTransfer?.setData('text/plain', node.name)
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

function handleDragOver(node: CategoryTreeNode, event: DragEvent) {
  if (!canDropOn(node.name)) {
    return
  }
  event.preventDefault()
  const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const offsetY = event.clientY - bounds.top
  dropPosition.value =
    offsetY < bounds.height * 0.28
      ? 'before'
      : offsetY > bounds.height * 0.72
        ? 'after'
        : 'inside'
  dropTargetName.value = node.name
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

async function dropOn(node: CategoryTreeNode, event: DragEvent) {
  event.preventDefault()
  if (!canDropOn(node.name)) {
    clearDragState()
    return
  }
  const draggedRow = rowByName(draggingName.value)
  const targetRow = rowByName(node.name)
  if (!draggedRow || !targetRow) {
    clearDragState()
    return
  }

  const targetParent = dropPosition.value === 'inside' ? targetRow.name : targetRow.parentName || ''
  const siblings = sortCategoryRows(
    rows.value.filter(
      (row) => (row.parentName || '') === targetParent && row.name !== draggedRow.name,
    ),
    'priority-asc',
  )
  const targetIndex =
    dropPosition.value === 'inside'
      ? 0
      : siblings.findIndex((candidate) => candidate.name === targetRow.name)
  if (targetIndex < 0) {
    clearDragState()
    return
  }
  const insertIndex =
    dropPosition.value === 'inside'
      ? 0
      : dropPosition.value === 'before'
        ? targetIndex
        : targetIndex + 1
  const nextRows = [...siblings]
  nextRows.splice(insertIndex, 0, { ...draggedRow, parentName: targetParent })

  sortSaving.value = true
  try {
    await Promise.all(
      nextRows.map((row, index) => {
        const source = cloneCategory(row.source)
        source.spec.parentName =
          row.name === draggedRow.name ? targetParent || undefined : row.parentName || undefined
        source.spec.priority = (index + 1) * 10
        return updateCategory(row.name, source)
      }),
    )
    Toast.success(dropPosition.value === 'inside' ? '分类层级已更新' : '分类顺序已更新')
    await fetchCategories(true)
  } catch {
    Toast.warning('分类顺序更新失败，请稍后重试')
  } finally {
    sortSaving.value = false
    clearDragState()
  }
}

function canDropOn(targetName: string) {
  return Boolean(
    draggingName.value &&
      draggingName.value !== targetName &&
      !isDescendant(targetName, draggingName.value),
  )
}

function clearDragState() {
  draggingName.value = ''
  dropTargetName.value = ''
  dropPosition.value = 'after'
}

function isParentOptionDisabled(name: string) {
  return Boolean(
    editingName.value &&
      (name === editingName.value || isDescendant(name, editingName.value)),
  )
}

function isDescendant(targetName: string, ancestorName: string) {
  const rowsByName = new Map(rows.value.map((row) => [row.name, row]))
  let current = rowsByName.get(targetName)
  while (current?.parentName) {
    if (current.parentName === ancestorName) {
      return true
    }
    current = rowsByName.get(current.parentName)
  }
  return false
}

function rowByName(name: string) {
  return rows.value.find((row) => row.name === name)
}

function flattenTree(nodes: CategoryTreeNode[]): CategoryTreeNode[] {
  return nodes.flatMap((node) => [node, ...flattenTree(node.children)])
}

function closeComposerAfterSave() {
  editingName.value = ''
  draft.value = emptyCategory()
  composerOpen.value = false
}

function cloneCategory(category: SoftwareCategory) {
  return JSON.parse(JSON.stringify(category)) as SoftwareCategory
}

function sortParameter(sort: CategorySortKey) {
  return {
    'created-desc': 'metadata.creationTimestamp,desc',
    'created-asc': 'metadata.creationTimestamp,asc',
    'priority-desc': 'spec.priority,desc',
    'priority-asc': 'spec.priority,asc',
  }[sort]
}

function parentLabel(name: string) {
  return name ? rowByName(name)?.displayName || name : '全部分类'
}

function formatNumber(value?: number) {
  return new Intl.NumberFormat('zh-CN').format(value || 0)
}

function indent(depth: number) {
  return `${depth * 20}px`
}

watch(keyword, () => {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
  searchTimer = setTimeout(() => {
    if (page.value === 1) {
      void fetchCategories()
    } else {
      page.value = 1
    }
  }, 250)
})

watch([selectedParent, selectedStatus, selectedSort, size], () => {
  if (page.value === 1) {
    void fetchCategories()
  } else {
    page.value = 1
  }
})

watch(page, () => void fetchCategories())

onMounted(() => void fetchCategories(true))
onBeforeUnmount(() => {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
  fetchSequence += 1
})

defineExpose({ openCreate })
</script>

<template>
  <ConsolePageScaffold class="category-console">
    <ConsoleTableCard>
      <ConsoleToolbar>
        <template #left>
          <SearchInput v-model="keyword" placeholder="搜索分类名称" />
          <ConsoleFilterSelect
            v-model="selectedParent"
            class="category-select"
            :options="parentOptions"
            aria-label="筛选父级分类"
          />
          <ConsoleFilterSelect
            v-model="selectedStatus"
            class="category-select"
            :options="statusOptions"
            aria-label="筛选分类状态"
          />
          <ConsoleFilterSelect
            v-model="selectedSort"
            class="category-select wide"
            :options="sortOptions"
            aria-label="分类排序"
          />
        </template>
        <template #right>
          <ConsoleTextButton v-if="hasFilters" @click="clearFilters">重置</ConsoleTextButton>
          <ConsoleIconButton title="刷新" @click="fetchCategories(true)">
            <IconRefreshLine :class="{ spinning: loading }" />
          </ConsoleIconButton>
        </template>
      </ConsoleToolbar>
    </ConsoleTableCard>

    <div class="category-main-grid">
      <ConsoleTableCard>
        <div class="category-panel-heading">
          <div class="panel-title">
            <IconGrid />
            <strong>分类树</strong>
          </div>
          <button class="tree-add" type="button" title="新建分类" @click="openCreate">
            <IconAddCircle />
          </button>
        </div>

        <VLoading v-if="loading && !catalog.length" />
        <VEmpty v-else-if="!treeItems.length" title="暂无分类" />
        <div v-else class="category-tree">
          <div
            class="tree-node"
            :class="{ active: !selectedParent }"
            role="button"
            tabindex="0"
            @click="selectParent('')"
            @keydown.enter="selectParent('')"
            @keydown.space.prevent="selectParent('')"
          >
            <span class="tree-drag-placeholder" aria-hidden="true"></span>
            <span class="tree-icon"></span>
            <span>全部分类</span>
            <em>{{ stats.total }}</em>
          </div>
          <div
            v-for="node in treeItems"
            :key="node.name"
            class="tree-node"
            :class="{
              active: selectedParent === node.name,
              muted: !node.visible,
              root: node.depth === 0,
              dragging: draggingName === node.name,
              'drop-before': dropTargetName === node.name && dropPosition === 'before',
              'drop-after': dropTargetName === node.name && dropPosition === 'after',
              'drop-inside': dropTargetName === node.name && dropPosition === 'inside',
            }"
            role="button"
            tabindex="0"
            :style="{ '--tree-indent': indent(node.depth) }"
            @click="selectParent(node.name)"
            @dragover="handleDragOver(node, $event)"
            @dragleave="dropTargetName = dropTargetName === node.name ? '' : dropTargetName"
            @drop="dropOn(node, $event)"
            @keydown.enter="selectParent(node.name)"
            @keydown.space.prevent="selectParent(node.name)"
          >
            <span class="tree-line" aria-hidden="true"></span>
            <span
              class="tree-drag-handle"
              draggable="true"
              title="拖拽调整顺序"
              aria-label="拖拽调整顺序"
              @click.stop
              @dragend="clearDragState"
              @dragstart.stop="startDrag(node, $event)"
            >
              <IconListSettings />
            </span>
            <span class="tree-icon"></span>
            <span class="tree-label">{{ node.label }}</span>
            <em>{{ node.count }}</em>
          </div>
        </div>
        <div class="tree-hint">拖到节点中部设为子分类，上下边缘调整同级顺序</div>
      </ConsoleTableCard>

      <div class="category-content">
        <ConsoleTableCard>
          <div class="category-panel-heading">
            <div class="panel-title">
              <IconClipboardLine />
              <strong>分类概览</strong>
            </div>
          </div>
          <ConsoleStatsGrid class="category-stats">
            <ConsoleStatCard tone="blue" label="总分类数" :value="formatNumber(stats.total)" meta="所有分类数量">
              <template #icon><IconGrid /></template>
            </ConsoleStatCard>
            <ConsoleStatCard tone="green" label="启用数" :value="formatNumber(stats.enabled)" meta="已启用分类">
              <template #icon><IconCheckboxCircle /></template>
            </ConsoleStatCard>
            <ConsoleStatCard tone="amber" label="停用数" :value="formatNumber(stats.disabled)" meta="已停用分类">
              <template #icon><IconArrowDownCircleLine /></template>
            </ConsoleStatCard>
            <ConsoleStatCard tone="violet" label="未分类数" :value="formatNumber(stats.uncategorizedApps)" meta="未分配软件">
              <template #icon><IconLink /></template>
            </ConsoleStatCard>
          </ConsoleStatsGrid>
        </ConsoleTableCard>

        <ConsoleTableCard>
          <div class="category-table-heading">
            <strong>分类列表</strong>
            <span>当前筛选：{{ parentLabel(selectedParent) }}</span>
          </div>
          <VLoading v-if="loading" />
          <VEmpty v-else-if="!pageRows.length" title="没有匹配的分类">
            <template #actions><VButton @click="clearFilters">重置筛选</VButton></template>
          </VEmpty>
          <div v-else class="table-wrap">
            <table class="category-table">
              <thead>
                <tr>
                  <th>分类名称</th>
                  <th>软件数</th>
                  <th>状态</th>
                  <th>创建时间</th>
                  <th class="actions-col">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in pageRows" :key="row.name">
                  <td>
                    <div class="category-name-cell">
                      <span class="category-folder"></span>
                      <div>
                        <strong>{{ row.displayName }}</strong>
                        <small>/{{ row.slug }} / {{ row.name }}</small>
                      </div>
                    </div>
                  </td>
                  <td>{{ formatNumber(row.appCount) }}</td>
                  <td>
                    <VStatusDot
                      :state="row.visible ? 'success' : 'default'"
                      :text="row.visible ? '启用' : '停用'"
                    />
                  </td>
                  <td>{{ formatCategoryDate(row.createdAt) }}</td>
                  <td class="actions-col">
                    <VSpace>
                      <button class="table-icon-button" type="button" title="编辑" @click="openEdit(row.source)">
                        <IconRiPencilFill />
                      </button>
                      <button
                        class="table-icon-button danger"
                        type="button"
                        title="删除"
                        :disabled="deletingName === row.name"
                        @click="confirmDelete(row.source)"
                      >
                        <IconDeleteBin />
                      </button>
                    </VSpace>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <template #footer>
            <div class="category-pagination">
              <span>共 {{ total }} 条</span>
              <VPagination
                v-model:page="page"
                v-model:size="size"
                :total="total"
                :size-options="[10, 20, 50]"
              />
            </div>
          </template>
        </ConsoleTableCard>
      </div>
    </div>
  </ConsolePageScaffold>

  <VModal v-if="composerOpen" :title="composerTitle" :width="720" @close="closeComposer">
    <p class="modal-description">维护分类名称、公开路径、父级关系和展示状态。</p>
    <div class="category-form-grid">
      <label>
        <span>分类名称</span>
        <input v-model.trim="draft.spec.displayName" class="field" />
      </label>
      <label>
        <span>公开 slug</span>
        <input v-model.trim="draft.spec.slug" class="field" />
      </label>
      <label>
        <span>父级分类</span>
        <select v-model="draft.spec.parentName" class="field">
          <option value="">无父级</option>
          <option
            v-for="item in rows"
            :key="item.name"
            :value="item.name"
            :disabled="isParentOptionDisabled(item.name)"
          >
            {{ item.displayName }}
          </option>
        </select>
      </label>
      <label>
        <span>图标</span>
        <input v-model.trim="draft.spec.icon" class="field" />
      </label>
      <label>
        <span>排序</span>
        <input v-model.number="draft.spec.priority" class="field" type="number" min="0" />
      </label>
      <label class="switch-field category-switch">
        <VSwitch v-model="draft.spec.visible" />
        <span>启用</span>
      </label>
      <label class="category-form-full">
        <span>描述</span>
        <textarea v-model.trim="draft.spec.description" class="textarea" rows="4" />
      </label>
    </div>
    <template #footer>
      <VSpace>
        <VButton :disabled="saving" @click="closeComposer">取消</VButton>
        <VButton type="secondary" :disabled="!canSave" :loading="saving" @click="save">
          <template #icon><IconSave /></template>
          {{ isEditing ? '保存修改' : '新建分类' }}
        </VButton>
      </VSpace>
    </template>
  </VModal>
</template>

<style scoped lang="scss">
.category-select {
  min-width: 146px;
}

.category-select.wide {
  min-width: 170px;
}

.category-main-grid {
  display: grid;
  grid-template-columns: minmax(280px, 360px) minmax(0, 1fr);
  gap: 22px;
}

.category-content {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
}

.category-panel-heading,
.category-table-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 18px 20px;
}

.category-panel-heading {
  border-bottom: 1px solid #eef2f7;
}

.category-table-heading {
  padding-bottom: 12px;
}

.category-table-heading strong,
.panel-title strong {
  color: #0f172a;
  font-size: 16px;
  font-weight: 700;
}

.category-table-heading span {
  color: #64748b;
  font-size: 12px;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.panel-title svg {
  width: 18px;
  height: 18px;
  color: #45617f;
}

.tree-add,
.table-icon-button {
  display: inline-grid;
  place-items: center;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #48637f;
  cursor: pointer;
}

.tree-add {
  width: 30px;
  height: 30px;
}

.tree-add:hover,
.table-icon-button:hover {
  background: #eef6ff;
  color: #0c7ff2;
}

.category-tree {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px 16px;
}

.tree-node {
  position: relative;
  display: grid;
  grid-template-columns: 20px auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  border: 0;
  border-radius: 8px;
  padding: 0 10px 0 calc(8px + var(--tree-indent, 0px));
  background: transparent;
  color: #243247;
  font-size: 14px;
  cursor: pointer;
}

.tree-node:hover,
.tree-node.active {
  background: #eef6ff;
  color: #0c7ff2;
}

.tree-node.muted {
  color: #7b8798;
}

.tree-node.dragging {
  opacity: 0.48;
}

.tree-node.drop-inside {
  background: #eaf3ff;
  box-shadow: inset 0 0 0 1px rgba(37, 99, 235, 0.22);
}

.tree-node.drop-before::before,
.tree-node.drop-after::after {
  content: '';
  position: absolute;
  left: calc(8px + var(--tree-indent, 0px));
  right: 10px;
  height: 2px;
  border-radius: 999px;
  background: #2563eb;
}

.tree-node.drop-before::before {
  top: -3px;
}

.tree-node.drop-after::after {
  bottom: -3px;
}

.tree-drag-handle,
.tree-drag-placeholder {
  display: inline-grid;
  place-items: center;
  width: 20px;
  height: 28px;
  color: #9aa8ba;
}

.tree-drag-handle {
  border-radius: 6px;
  cursor: grab;
}

.tree-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-node em {
  min-width: 28px;
  border-radius: 999px;
  padding: 3px 8px;
  background: #f1f5f9;
  color: #4f6279;
  font-size: 12px;
  font-style: normal;
  text-align: center;
}

.tree-icon,
.category-folder {
  position: relative;
  width: 16px;
  height: 13px;
  border: 1.6px solid currentColor;
  border-radius: 3px;
}

.tree-icon::before,
.category-folder::before {
  content: '';
  position: absolute;
  top: -4px;
  left: 1px;
  width: 8px;
  height: 5px;
  border: 1.6px solid currentColor;
  border-bottom: 0;
  border-radius: 3px 3px 0 0;
  background: inherit;
}

.tree-line {
  position: absolute;
  top: -8px;
  bottom: 18px;
  left: calc(18px + var(--tree-indent, 0px));
  width: 1px;
  background: #dbe3ee;
}

.tree-node.root .tree-line,
.tree-node:first-child .tree-line {
  display: none;
}

.tree-hint {
  border-top: 1px solid #eef2f7;
  padding: 14px 16px;
  color: #94a3b8;
  font-size: 12px;
  text-align: center;
}

.category-stats {
  grid-template-columns: repeat(2, minmax(220px, 1fr));
  gap: 12px;
  padding: 0 20px 20px;
}

.category-table {
  width: 100%;
  min-width: 860px;
  border-collapse: separate;
  border-spacing: 0;
  border: 1px solid #e5ebf3;
  border-radius: 8px;
  background: #fff;
}

.category-table th,
.category-table td {
  border-bottom: 1px solid #edf2f7;
  padding: 0 16px;
  text-align: left;
}

.category-table th {
  height: 46px;
  background: #fbfcfe;
  color: #64748b;
  font-size: 12px;
}

.category-table td {
  height: 52px;
  color: #26364c;
  font-size: 13px;
}

.table-wrap {
  overflow-x: auto;
}

.category-name-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #0c7ff2;
}

.category-name-cell strong,
.category-name-cell small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-name-cell strong {
  color: #27364d;
}

.category-name-cell small {
  margin-top: 2px;
  color: #64748b;
  font-size: 12px;
}

.table-icon-button {
  width: 30px;
  height: 30px;
}

.table-icon-button.danger {
  color: #ef4444;
}

.actions-col {
  width: 150px;
  text-align: right !important;
}

.actions-col :deep(.space) {
  justify-content: flex-end;
}

.category-pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 18px;
  width: 100%;
  padding: 14px 18px;
  color: #64748b;
  font-size: 13px;
}

.modal-description {
  margin: 0 0 16px;
  color: #6d7a8c;
  font-size: 13px;
}

.category-form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.category-form-grid label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: #374151;
  font-size: 13px;
}

.category-form-full {
  grid-column: 1 / -1;
}

.field,
.textarea {
  width: 100%;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  background: #fff;
  color: #111827;
  font-size: 14px;
}

.field {
  height: 40px;
  padding: 0 12px;
}

.textarea {
  min-height: 104px;
  padding: 10px 11px;
  resize: vertical;
}

.switch-field {
  flex-direction: row !important;
  align-items: center;
  justify-content: center;
}

.spinning {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1100px) {
  .category-main-grid {
    grid-template-columns: 1fr;
  }

  .category-form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .category-form-grid,
  .category-stats {
    grid-template-columns: 1fr;
  }

  .category-panel-heading,
  .category-table-heading,
  .category-pagination {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
