<template>
  <div class="ry-page">
    <!-- 1. 搜索筛选条 -->
    <div v-if="searchFields?.length" class="ry-card ry-search-card">
      <div class="filter-header">
        <div>
          <span class="filter-title">筛选条件</span>
          <span class="filter-desc">快速定位需要处理的{{ entityName }}</span>
        </div>
        <el-button
          link
          type="primary"
          :icon="searchExpanded ? ArrowUp : ArrowDown"
          @click="searchExpanded = !searchExpanded"
        >
          {{ searchExpanded ? "收起" : "展开" }}
        </el-button>
      </div>
      <el-collapse-transition>
        <el-form
          v-show="searchExpanded"
          :model="query"
          inline
          class="filter-form"
          @submit.prevent
        >
          <el-form-item
            v-for="f in searchFields"
            :key="f.prop"
            :label="f.label"
          >
            <el-input
              v-if="f.type === 'input'"
              v-model.trim="query[f.prop]"
              :placeholder="f.placeholder || `请输入${f.label}`"
              clearable
              @keyup.enter="handleSearch"
            />
            <el-select
              v-else-if="f.type === 'select'"
              v-model="query[f.prop]"
              :placeholder="f.placeholder || '全部'"
              clearable
              :style="{ width: f.width || '160px' }"
            >
              <el-option
                v-for="o in f.options"
                :key="o.value"
                :label="o.label"
                :value="o.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item class="filter-actions">
            <el-button type="primary" :icon="Search" @click="handleSearch"
              >查询</el-button
            >
            <el-button :icon="Refresh" @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </el-collapse-transition>
    </div>

    <!-- 2. 表格卡片 -->
    <div class="ry-card ry-table-card">
      <div class="ry-toolbar">
        <div class="ry-toolbar-left">
          <slot name="toolbar-left" :selection="selection" :reload="loadList" />
          <el-button type="primary" :icon="Plus" @click="handleAdd">{{
            addText
          }}</el-button>
          <el-button
            type="danger"
            :icon="Delete"
            :disabled="!selection.length"
            @click="handleBatchDelete"
            >批量删除</el-button
          >
          <el-button :icon="Download" @click="handleExport">导出</el-button>
        </div>
        <div class="ry-toolbar-right">
          <el-tooltip content="刷新">
            <el-button circle :icon="Refresh" @click="loadList" />
          </el-tooltip>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="list"
        class="data-table"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="48" align="center" />
        <el-table-column
          v-for="c in columns"
          :key="c.prop"
          :label="c.label"
          :prop="c.prop"
          :width="c.width"
          :min-width="c.minWidth || 120"
          :align="c.align || 'left'"
          :show-overflow-tooltip="c.showOverflow !== false"
          :fixed="c.fixed"
        >
          <template v-if="c.slot" #default="{ row, $index }">
            <slot :name="c.slot" :row="row" :$index="$index" />
          </template>
          <template v-else-if="c.formatter" #default="{ row }">
            {{ c.formatter(row[c.prop], row) }}
          </template>
        </el-table-column>
        <el-table-column
          label="状态"
          v-if="showStatus"
          width="90"
          align="center"
        >
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === '0'"
              @change="(val) => handleStatusChange(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="创建时间"
          prop="createTime"
          :width="createTimeWidth"
          class-name="create-time-column"
        />
        <el-table-column
          label="操作"
          :width="actionWidth"
          fixed="right"
          class-name="action-column"
        >
          <template #default="{ row }">
            <div class="table-actions">
              <slot name="actions" :row="row" :reload="loadList" />
              <el-button type="primary" link @click="handleEdit(row)"
                >编辑</el-button
              >
              <el-button type="danger" link @click="handleDelete(row)"
                >删除</el-button
              >
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="ry-pagination">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </div>

    <!-- 3. 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.isEdit ? `编辑${entityName}` : `新增${entityName}`"
      :width="dialogWidth"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <div class="dialog-intro">请完善以下信息，带星号的字段为必填项。</div>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        class="entity-form"
      >
        <el-row :gutter="16">
          <el-col v-for="f in formFields" :key="f.prop" :span="f.span || 12">
            <el-form-item :label="f.label" :prop="f.prop">
              <el-input
                v-if="f.type === 'input'"
                v-model.trim="form[f.prop]"
                :placeholder="f.placeholder || `请输入${f.label}`"
                :disabled="f.disabled && dialog.isEdit"
              />
              <el-input-number
                v-else-if="f.type === 'number'"
                v-model="form[f.prop]"
                :min="f.min"
                :max="f.max"
                :step="f.step || 1"
                controls-position="right"
                style="width: 100%"
              />
              <el-select
                v-else-if="f.type === 'select'"
                v-model="form[f.prop]"
                :placeholder="f.placeholder || '请选择'"
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="o in f.options"
                  :key="o.value"
                  :label="o.label"
                  :value="o.value"
                />
              </el-select>
              <el-radio-group
                v-else-if="f.type === 'radio'"
                v-model="form[f.prop]"
              >
                <el-radio
                  v-for="o in f.options"
                  :key="o.value"
                  :value="o.value"
                  >{{ o.label }}</el-radio
                >
              </el-radio-group>
              <el-input
                v-else-if="f.type === 'textarea'"
                v-model="form[f.prop]"
                type="textarea"
                :rows="f.rows || 3"
                :placeholder="f.placeholder || `请输入${f.label}`"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取 消</el-button>
        <el-button
          type="primary"
          :loading="dialog.submitting"
          @click="handleSubmit"
          >确 定</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Search,
  Refresh,
  Plus,
  Delete,
  Download,
  ArrowUp,
  ArrowDown,
} from "@element-plus/icons-vue";
import { exportJson } from "@/utils";

const props = defineProps({
  // 实体名（如 "向量模型"），用于弹窗标题与提示
  entityName: { type: String, default: "记录" },
  // 名称字段（用于删除确认文案）
  nameField: { type: String, default: "id" },
  // 弹窗宽度
  dialogWidth: { type: String, default: "640px" },
  // 是否展示状态列与状态切换
  showStatus: { type: Boolean, default: true },
  // 新增按钮文案
  addText: { type: String, default: "新增" },
  // 创建时间列宽
  createTimeWidth: { type: [String, Number], default: 160 },
  // 操作列宽
  actionWidth: { type: [String, Number], default: 180 },
  // 搜索字段
  searchFields: { type: Array, default: () => [] },
  // 表格列：{ prop, label, width, minWidth, align, fixed, formatter, slot }
  columns: { type: Array, default: () => [] },
  // 表单字段：{ prop, label, type, span, options, ... }
  formFields: { type: Array, default: () => [] },
  // 表单校验规则
  rules: { type: Object, default: () => ({}) },
  // API：{ list, add, update, remove, changeStatus }
  api: { type: Object, required: true },
  // 表单默认值工厂
  defaultForm: { type: Function, required: true },
});

const query = reactive({ pageNum: 1, pageSize: 10 });
const searchExpanded = ref(true);
// 初始化查询字段
props.searchFields.forEach((f) => (query[f.prop] = ""));

const list = ref([]);
const total = ref(0);
const loading = ref(false);
const selection = ref([]);

async function loadList() {
  loading.value = true;
  try {
    const res = await props.api.list({ ...query });
    list.value = res.rows || [];
    total.value = res.total || 0;
  } catch (err) {
    ElMessage.error("列表加载失败");
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  query.pageNum = 1;
  loadList();
}

function handleReset() {
  props.searchFields.forEach((f) => (query[f.prop] = ""));
  query.pageNum = 1;
  loadList();
}

function handleSelectionChange(rows) {
  selection.value = rows;
}

// ===== 新增/编辑 =====
const dialog = reactive({ visible: false, isEdit: false, submitting: false });
const formRef = ref(null);
const form = reactive(props.defaultForm());

watch(
  () => props.defaultForm,
  () => Object.assign(form, props.defaultForm()),
);

function resetForm() {
  Object.assign(form, props.defaultForm());
  formRef.value?.clearValidate();
}

function handleAdd() {
  resetForm();
  dialog.isEdit = false;
  dialog.visible = true;
}

function handleEdit(row) {
  Object.assign(form, row);
  dialog.isEdit = true;
  dialog.visible = true;
}

async function handleSubmit() {
  await formRef.value?.validate();
  dialog.submitting = true;
  try {
    if (dialog.isEdit) {
      await props.api.update({ ...form });
      ElMessage.success("修改成功");
    } else {
      await props.api.add({ ...form });
      ElMessage.success("新增成功");
    }
    dialog.visible = false;
    loadList();
  } catch (err) {
    ElMessage.error(err?.message || "保存失败");
  } finally {
    dialog.submitting = false;
  }
}

// ===== 删除 =====
async function handleDelete(row) {
  const name = row[props.nameField] || row.id;
  await ElMessageBox.confirm(`确认删除「${name}」？`, "提示", {
    type: "warning",
  });
  try {
    await props.api.remove(row.id);
    ElMessage.success("删除成功");
    loadList();
  } catch (err) {
    ElMessage.error("删除失败");
  }
}

async function handleBatchDelete() {
  if (!selection.value.length) return;
  await ElMessageBox.confirm(
    `确认删除选中的 ${selection.value.length} 条记录？`,
    "提示",
    { type: "warning" },
  );
  try {
    await props.api.remove(selection.value.map((r) => r.id));
    ElMessage.success("删除成功");
    loadList();
  } catch (err) {
    ElMessage.error("删除失败");
  }
}

async function handleStatusChange(row, val) {
  const newStatus = val ? "0" : "1";
  try {
    if (props.api.changeStatus) {
      await props.api.changeStatus(row.id, newStatus);
    }
    row.status = newStatus;
    ElMessage.success("状态已更新");
  } catch (err) {
    ElMessage.error("状态更新失败");
  }
}

function handleExport() {
  exportJson(list.value, `${props.entityName}-${Date.now()}.json`);
  ElMessage.success("导出成功");
}

defineExpose({ loadList });

onMounted(() => loadList());
</script>

<style lang="scss" scoped>
.ry-page {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.filter-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}
.filter-title {
  display: block;
  color: var(--ry-foreground);
  font-size: 14px;
  font-weight: 600;
}
.filter-desc {
  display: block;
  margin-top: 3px;
  color: var(--ry-muted-foreground);
  font-size: 12px;
}
.filter-form {
  padding-top: 12px;
  border-top: 1px solid var(--ry-border-light);
}
.filter-form :deep(.el-form-item) {
  margin-bottom: 0;
}
.filter-actions {
  margin-left: auto;
}
.data-table :deep(th.el-table__cell) {
  height: 42px;
  background: var(--ry-neutral-50);
  border-bottom: 1px solid var(--ry-border);
}
.data-table :deep(td.el-table__cell) {
  border-bottom-color: var(--ry-border-light);
}
.data-table :deep(.create-time-column .cell) {
  white-space: nowrap;
}
.table-actions {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  white-space: nowrap;
}
.table-actions :deep(.el-button) {
  flex-shrink: 0;
}
.dialog-intro {
  margin: -4px 0 20px;
  padding: 10px 12px;
  color: var(--ry-muted-foreground);
  background: var(--ry-neutral-50);
  border: 1px solid var(--ry-border-light);
  border-radius: var(--ry-radius-medium);
  font-size: 12px;
}
.entity-form :deep(.el-form-item__label) {
  color: var(--ry-neutral-600);
  font-weight: 500;
}

@media (max-width: 768px) {
  :deep(.ry-search-card .el-form) {
    display: flex;
    flex-direction: column;
  }
  .filter-header {
    margin-bottom: 14px;
  }
  .filter-form {
    padding-top: 14px;
  }
  .filter-actions {
    margin-left: 0;
  }
  :deep(.ry-search-card .el-form-item) {
    width: 100%;
    margin-right: 0;
  }
  :deep(.ry-search-card .el-form-item__content),
  :deep(.ry-search-card .el-input),
  :deep(.ry-search-card .el-select) {
    width: 100% !important;
  }
  :deep(.el-dialog .el-col) {
    max-width: 100%;
    flex: 0 0 100%;
  }
}
</style>
