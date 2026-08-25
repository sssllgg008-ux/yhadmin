<template>
  <div class="ry-page">
    <!-- 1. 搜索筛选条 -->
    <div class="ry-card ry-search-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="模块名称">
          <el-input
            v-model.trim="query.moduleName"
            placeholder="请输入模块名称"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="模块编码">
          <el-input
            v-model.trim="query.moduleCode"
            placeholder="请输入模块编码"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="模块状态"
            clearable
            style="width: 140px"
          >
            <el-option label="正常" value="0" />
            <el-option label="停用" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch"
            >搜索</el-button
          >
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 2. 表格卡片 -->
    <div class="ry-card ry-table-card">
      <div class="ry-toolbar">
        <div class="ry-toolbar-left">
          <el-button
            v-permission="'system:module:add'"
            type="primary"
            :icon="Plus"
            @click="handleAdd"
            >新增</el-button
          >
          <el-button
            v-permission="'system:module:remove'"
            type="danger"
            :icon="Delete"
            :disabled="!selection.length"
            @click="handleBatchDelete"
            >批量删除</el-button
          >
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
        border
        stripe
        :default-sort="{ prop: 'orderNum', order: 'ascending' }"
        :selectable="isModuleDeletable"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="48" align="center" />
        <el-table-column
          label="ID"
          prop="id"
          width="70"
          align="center"
          sortable
        />
        <el-table-column
          label="显示名称"
          prop="moduleName"
          min-width="140"
          show-overflow-tooltip
        />
        <el-table-column
          label="模块编码"
          prop="moduleCode"
          min-width="130"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span class="ry-mono">{{ row.moduleCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="图标" prop="icon" width="80" align="center">
          <template #default="{ row }">
            <div
              v-if="row.icon"
              class="ry-color-icon"
              :style="{ background: iconColor(row.icon) }"
            >
              <el-icon><component :is="iconComp(mapIcon(row.icon))" /></el-icon>
            </div>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column
          label="排序"
          prop="orderNum"
          width="90"
          align="center"
          sortable
        />
        <el-table-column
          label="创建时间"
          prop="createTime"
          width="170"
          align="center"
          sortable
        >
          <template #default="{ row }">
            <span class="ry-mono">{{ formatTime(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              active-value="0"
              inactive-value="1"
              :disabled="!userStore.hasPermission('system:module:edit')"
              @change="(val) => handleStatusChange(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="备注"
          prop="remark"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'system:module:edit'"
              type="primary"
              link
              @click="handleEdit(row)"
              >修改</el-button
            >
            <el-divider direction="vertical" />
            <el-button
              v-permission="'system:module:remove'"
              type="danger"
              link
              :disabled="!isModuleDeletable(row)"
              @click="handleDelete(row)"
              >删除</el-button
            >
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
      :title="dialog.isEdit ? '修改模块' : '新增模块'"
      width="680px"
      class="module-edit-dialog"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="88px"
        class="module-form"
      >
        <el-row :gutter="24">
          <el-col :span="12">
            <el-form-item label="模块名称" prop="moduleName">
              <el-input
                v-model.trim="form.moduleName"
                placeholder="请输入模块名称"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模块编码" prop="moduleCode">
              <el-input
                v-model.trim="form.moduleCode"
                placeholder="请输入模块编码"
                :disabled="dialog.isEdit && isBuiltInId(form.id)"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="24">
          <el-col :span="16">
            <el-form-item label="图标" prop="icon">
              <div class="icon-picker">
                <el-input
                  v-model.trim="form.icon"
                  placeholder="图标名"
                  class="icon-input"
                >
                  <template #prepend>
                    <div
                      class="icon-preview-block"
                      :style="{ background: iconColor(form.icon) }"
                    >
                      <el-icon
                        ><component :is="iconComp(mapIcon(form.icon))"
                      /></el-icon>
                    </div>
                  </template>
                </el-input>
                <el-popover
                  placement="bottom"
                  :width="380"
                  trigger="click"
                  popper-class="icon-popover-wrap"
                >
                  <template #reference>
                    <el-button>选择</el-button>
                  </template>
                  <div class="icon-popover">
                    <el-input
                      v-model="iconSearch"
                      placeholder="搜索图标名"
                      clearable
                      size="small"
                      :prefix-icon="Search"
                    />
                    <div class="icon-grid">
                      <div
                        v-for="name in filteredIcons"
                        :key="name"
                        class="icon-cell"
                        :class="{ 'is-active': name === form.icon }"
                        :title="name"
                        @click="selectIcon(name)"
                      >
                        <el-icon
                          ><component :is="ElementPlusIconsVue[name]"
                        /></el-icon>
                      </div>
                      <div v-if="!filteredIcons.length" class="icon-empty">
                        无匹配图标
                      </div>
                    </div>
                  </div>
                </el-popover>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="排序" prop="orderNum">
              <el-input-number
                v-model="form.orderNum"
                :min="0"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-actions">
          <el-button @click="dialog.visible = false">取 消</el-button>
          <el-button
            type="primary"
            :loading="dialog.submitting"
            @click="handleSubmit"
            >确 定</el-button
          >
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Search, Refresh, Plus, Delete } from "@element-plus/icons-vue";
import * as ElementPlusIconsVue from "@element-plus/icons-vue";
import {
  listModule,
  addModule,
  updateModule,
  delModule,
  changeModuleStatus,
} from "@/api/system";
import { mapIcon } from "@/store/modules/permission";
import { useUserStore } from "@/store/modules/user";

const userStore = useUserStore();

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  moduleName: "",
  moduleCode: "",
  status: "",
});

const list = ref([]);
const total = ref(0);
const loading = ref(false);
const selection = ref([]);

async function loadList() {
  loading.value = true;
  try {
    const res = await listModule({ ...query });
    // 后端返回 PageResponse（rows + total 在顶层）
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
  query.moduleName = "";
  query.moduleCode = "";
  query.status = "";
  query.pageNum = 1;
  loadList();
}

function handleSelectionChange(rows) {
  selection.value = rows;
}

function isBuiltInId(id) {
  return Number(id) > 0 && Number(id) <= 3;
}

function isModuleDeletable(row) {
  return (
    !isBuiltInId(row?.id) && userStore.hasPermission("system:module:remove")
  );
}

// ===== 图标辅助 =====
function iconComp(name) {
  return ElementPlusIconsVue[name] || ElementPlusIconsVue.Menu;
}
/**
 * 模块图标彩色方块背景色（按 icon 名 hash 生成稳定颜色）
 */
function iconColor(icon) {
  if (!icon || icon === "#") return "var(--ry-primary)";
  const palette = [
    "#409eff",
    "#67c23a",
    "#e6a23c",
    "#f56c6c",
    "#909399",
    "#9c27b0",
    "#00bcd4",
    "#3f51b5",
  ];
  let hash = 0;
  for (let i = 0; i < icon.length; i++) {
    hash = (hash * 31 + icon.charCodeAt(i)) & 0x7fffffff;
  }
  return palette[hash % palette.length];
}

function formatTime(t) {
  if (!t) return "—";
  return String(t).replace("T", " ").substring(0, 19);
}

// ===== 图标选择器 =====
const iconNames = Object.keys(ElementPlusIconsVue).filter(
  (k) => k !== "default" && typeof ElementPlusIconsVue[k] === "object",
);
const iconSearch = ref("");
const filteredIcons = computed(() => {
  const kw = iconSearch.value.trim().toLowerCase();
  if (!kw) return iconNames;
  return iconNames.filter((n) => n.toLowerCase().includes(kw));
});
function selectIcon(name) {
  form.icon = name;
}

// ===== 状态切换 =====
async function handleStatusChange(row, val) {
  try {
    await changeModuleStatus(row.id, val);
    ElMessage.success("状态修改成功");
  } catch (err) {
    // 失败回滚
    row.status = val === "0" ? "1" : "0";
    ElMessage.error("状态修改失败");
  }
}

// ===== 新增/编辑 =====
const dialog = reactive({ visible: false, isEdit: false, submitting: false });
const formRef = ref(null);
const defaultForm = () => ({
  id: undefined,
  moduleName: "",
  moduleCode: "",
  icon: "",
  orderNum: 0,
  status: "0",
  remark: "",
});
const form = reactive(defaultForm());
const rules = {
  moduleName: [{ required: true, message: "请输入模块名称", trigger: "blur" }],
  moduleCode: [{ required: true, message: "请输入模块编码", trigger: "blur" }],
};

function resetForm() {
  Object.assign(form, defaultForm());
  formRef.value?.clearValidate();
}

function handleAdd() {
  resetForm();
  dialog.isEdit = false;
  dialog.visible = true;
}

function handleEdit(row) {
  Object.assign(form, defaultForm(), {
    id: row.id,
    moduleName: row.moduleName,
    moduleCode: row.moduleCode,
    icon: row.icon || "",
    orderNum: row.orderNum ?? 0,
    status: row.status || "0",
    remark: row.remark || "",
  });
  dialog.isEdit = true;
  dialog.visible = true;
}

async function handleSubmit() {
  await formRef.value?.validate();
  dialog.submitting = true;
  try {
    if (dialog.isEdit) {
      await updateModule({ ...form });
      ElMessage.success("修改成功");
    } else {
      await addModule({ ...form });
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
  await ElMessageBox.confirm(`确认删除模块「${row.moduleName}」？`, "提示", {
    type: "warning",
  });
  try {
    await delModule(row.id);
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
    await delModule(selection.value.map((r) => r.id));
    ElMessage.success("删除成功");
    loadList();
  } catch (err) {
    ElMessage.error("删除失败");
  }
}

onMounted(() => loadList());
</script>

<style lang="scss" scoped>
.ry-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.ry-mono {
  font-family: var(--ry-font-mono);
  font-size: 13px;
}

/* 模块彩色方块图标 */
.ry-color-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  color: #fff;
  font-size: 16px;
}

/* ===== 图标选择器 ===== */
.icon-picker {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.icon-input {
  flex: 1;
}
.icon-preview-block {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  color: #fff;
  font-size: 16px;
  margin: -10px -12px;
}

.module-form {
  padding: 4px 8px 0;

  :deep(.el-form-item) {
    margin-bottom: 22px;
  }

  :deep(.el-textarea__inner) {
    min-height: 88px !important;
  }
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

:global(.module-edit-dialog) {
  max-width: calc(100vw - 32px);
  border-radius: 14px;
}

:global(.module-edit-dialog .el-dialog__body) {
  padding: 20px 24px 8px;
}

:global(.module-edit-dialog .el-dialog__footer) {
  padding: 12px 24px 20px;
}

@media (max-width: 720px) {
  .module-form {
    padding-inline: 0;

    :deep(.el-col) {
      max-width: 100%;
      flex: 0 0 100%;
    }
  }
}

/* popover 内容样式（popper-class 渲染到 body，需用全局样式） */
</style>

<style lang="scss">
.icon-popover-wrap {
  .icon-popover {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  .icon-grid {
    display: grid;
    grid-template-columns: repeat(8, 1fr);
    gap: 4px;
    max-height: 280px;
    overflow-y: auto;
  }
  .icon-cell {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 36px;
    border-radius: var(--ry-radius-small);
    cursor: pointer;
    color: var(--ry-neutral-500);
    font-size: 18px;
    transition: all 0.15s ease;
    &:hover {
      background: var(--ry-primary-50);
      color: var(--ry-primary);
    }
    &.is-active {
      background: var(--ry-primary);
      color: #fff;
    }
  }
  .icon-empty {
    grid-column: 1 / -1;
    text-align: center;
    padding: 16px;
    color: var(--ry-neutral-500);
    font-size: 13px;
  }
}
</style>
