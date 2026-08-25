<template>
  <div class="ry-page">
    <!-- 1. 搜索筛选条 -->
    <div class="ry-card ry-search-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="字典名称">
          <el-input
            v-model.trim="query.dictName"
            placeholder="请输入字典名称"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="字典类型">
          <el-input
            v-model.trim="query.dictType"
            placeholder="请输入字典类型"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="字典状态"
            clearable
            style="width: 140px"
          >
            <el-option label="正常" value="0" />
            <el-option label="停用" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="~"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 220px"
          />
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
          <el-button type="primary" :icon="Plus" @click="handleAdd"
            >新增</el-button
          >
          <el-button
            type="success"
            :icon="Edit"
            :disabled="selection.length !== 1"
            @click="handleEditSingle"
            >修改</el-button
          >
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
        border
        stripe
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="48" align="center" />
        <el-table-column label="字典编号" prop="id" width="90" align="center" />
        <el-table-column
          label="字典名称"
          prop="dictName"
          min-width="140"
          show-overflow-tooltip
        />
        <el-table-column label="字典类型" prop="dictType" min-width="160">
          <template #default="{ row }">
            <el-link type="primary" @click="handleDictData(row)">{{
              row.dictType
            }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="90" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.status === '0' ? 'success' : 'danger'"
              effect="light"
            >
              {{ row.status === "0" ? "正常" : "停用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="备注"
          prop="remark"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column label="创建时间" prop="createTime" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)"
              >修改</el-button
            >
            <el-divider direction="vertical" />
            <el-button type="danger" link @click="handleDelete(row)"
              >删除</el-button
            >
            <el-divider direction="vertical" />
            <el-button type="primary" link @click="handleDictData(row)"
              >数据</el-button
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

    <!-- 3. 新增/编辑弹窗（字典类型） -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.isEdit ? '修改字典' : '新增字典'"
      width="600px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model.trim="form.dictName" placeholder="请输入字典名称" />
        </el-form-item>
        <el-form-item label="字典类型" prop="dictType">
          <el-input v-model.trim="form.dictType" placeholder="请输入字典类型" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio value="0">正常</el-radio>
                <el-radio value="1">停用</el-radio>
              </el-radio-group>
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
        <el-button @click="dialog.visible = false">取 消</el-button>
        <el-button
          type="primary"
          :loading="dialog.submitting"
          @click="handleSubmit"
          >确 定</el-button
        >
      </template>
    </el-dialog>

    <!-- 4. 字典数据管理弹窗（完整 CRUD） -->
    <el-dialog
      v-model="dataDialog.visible"
      :title="`字典数据 - ${dataDialog.row?.dictName || ''}`"
      width="800px"
      :close-on-click-modal="false"
      @closed="resetDataForm"
    >
      <!-- 搜索栏 -->
      <div class="ry-search-card" style="margin-bottom: 16px">
        <el-form :model="dataQuery" inline @submit.prevent>
          <el-form-item label="字典名称">
            <el-select
              v-model="dataQuery.dictType"
              style="width: 180px"
              :disabled="true"
            >
              <el-option
                :value="dataDialog.row?.dictType"
                :label="dataDialog.row?.dictName"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="字典标签">
            <el-input
              v-model.trim="dataQuery.dictLabel"
              placeholder="请输入字典标签"
              clearable
              @keyup.enter="loadDictData"
            />
          </el-form-item>
          <el-form-item label="状态">
            <el-select
              v-model="dataQuery.status"
              placeholder="数据状态"
              clearable
              style="width: 120px"
            >
              <el-option label="正常" value="0" />
              <el-option label="停用" value="1" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="loadDictData"
              >搜索</el-button
            >
            <el-button :icon="Refresh" @click="resetDataQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 工具栏 -->
      <div class="ry-toolbar" style="margin-bottom: 12px">
        <div class="ry-toolbar-left">
          <el-button type="primary" :icon="Plus" @click="handleDataAdd"
            >新增</el-button
          >
          <el-button
            type="success"
            :icon="Edit"
            :disabled="dataSelection.length !== 1"
            @click="handleDataEditSingle"
            >修改</el-button
          >
          <el-button
            type="danger"
            :icon="Delete"
            :disabled="!dataSelection.length"
            @click="handleDataBatchDelete"
            >删除</el-button
          >
          <el-button :icon="Download" @click="handleDataExport">导出</el-button>
        </div>
        <div class="ry-toolbar-right">
          <el-tooltip content="刷新">
            <el-button circle :icon="Refresh" @click="loadDictData" />
          </el-tooltip>
        </div>
      </div>

      <!-- 表格 -->
      <el-table
        v-loading="dataLoading"
        :data="dataList"
        border
        stripe
        @selection-change="handleDataSelectionChange"
      >
        <el-table-column type="selection" width="48" align="center" />
        <el-table-column label="字典编码" prop="id" width="90" align="center" />
        <el-table-column label="字典标签" prop="dictLabel" min-width="140" />
        <el-table-column label="字典键值" prop="dictValue" min-width="100">
          <template #default="{ row }">
            <span class="ry-mono">{{ row.dictValue }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="字典排序"
          prop="dictSort"
          width="100"
          align="center"
        />
        <el-table-column label="状态" prop="status" width="90" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.status === '0' ? 'success' : 'danger'"
              effect="light"
            >
              {{ row.status === "0" ? "正常" : "停用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="备注"
          prop="remark"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column label="创建时间" prop="createTime" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleDataEdit(row)"
              >修改</el-button
            >
            <el-divider direction="vertical" />
            <el-button type="danger" link @click="handleDataDelete(row)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="ry-pagination">
        <el-pagination
          v-model:current-page="dataQuery.pageNum"
          v-model:page-size="dataQuery.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="dataTotal"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadDictData"
          @current-change="loadDictData"
        />
      </div>

      <!-- 底部 -->
      <template #footer>
        <el-button @click="dataDialog.visible = false">关 闭</el-button>
      </template>
    </el-dialog>

    <!-- 5. 字典数据新增/编辑弹窗 -->
    <el-dialog
      v-model="dataDialog.formVisible"
      :title="dataDialog.isEdit ? '修改字典数据' : '新增字典数据'"
      width="560px"
      class="dict-data-form-dialog"
      :close-on-click-modal="false"
      @closed="resetDataForm"
    >
      <el-form
        ref="dataFormRef"
        :model="dataForm"
        :rules="dataRules"
        label-width="88px"
        class="dict-data-edit-form"
      >
        <el-form-item label="字典标签" prop="dictLabel">
          <el-input
            v-model.trim="dataForm.dictLabel"
            placeholder="请输入字典标签"
          />
        </el-form-item>
        <el-form-item label="字典键值" prop="dictValue">
          <el-input
            v-model.trim="dataForm.dictValue"
            placeholder="请输入字典键值"
          />
        </el-form-item>
        <el-row :gutter="24" class="dict-data-compact-row">
          <el-col :span="12">
            <el-form-item label="字典排序" prop="dictSort">
              <el-input-number
                v-model="dataForm.dictSort"
                :min="0"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" class="dict-data-status-item">
              <el-radio-group v-model="dataForm.status" class="dict-data-status-group">
                <el-radio-button value="0">正常</el-radio-button>
                <el-radio-button value="1">停用</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="样式">
          <el-select v-model="dataForm.listClass" placeholder="样式类名">
            <el-option label="默认" value="info" />
            <el-option label="主色" value="primary" />
            <el-option label="成功" value="success" />
            <el-option label="警告" value="warning" />
            <el-option label="危险" value="danger" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="dataForm.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dataDialog.formVisible = false">取 消</el-button>
        <el-button
          type="primary"
          :loading="dataDialog.submitting"
          @click="handleDataSubmit"
          >确 定</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Search,
  Refresh,
  Plus,
  Edit,
  Delete,
  Download,
} from "@element-plus/icons-vue";
import {
  listDict,
  addDict,
  updateDict,
  delDict,
  listDictData,
  addDictData,
  updateDictData,
  delDictData,
} from "@/api/system";
import { exportJson } from "@/utils";

// ===== 字典类型管理 =====
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  dictName: "",
  dictType: "",
  status: "",
});
const dateRange = ref([]);

const list = ref([]);
const total = ref(0);
const loading = ref(false);
const selection = ref([]);

async function loadList() {
  loading.value = true;
  try {
    const params = { ...query };
    if (dateRange.value?.length === 2) {
      params.beginTime = dateRange.value[0];
      params.endTime = dateRange.value[1];
    }
    const res = await listDict(params);
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
  query.dictName = "";
  query.dictType = "";
  query.status = "";
  dateRange.value = [];
  query.pageNum = 1;
  loadList();
}

function handleSelectionChange(rows) {
  selection.value = rows;
}

// ===== 字典类型新增/编辑 =====
const dialog = reactive({ visible: false, isEdit: false, submitting: false });
const formRef = ref(null);
const defaultForm = () => ({
  id: undefined,
  dictName: "",
  dictType: "",
  status: "0",
  remark: "",
});
const form = reactive(defaultForm());
const rules = {
  dictName: [{ required: true, message: "请输入字典名称", trigger: "blur" }],
  dictType: [{ required: true, message: "请输入字典类型", trigger: "blur" }],
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
  Object.assign(form, row);
  dialog.isEdit = true;
  dialog.visible = true;
}

function handleEditSingle() {
  if (selection.value.length === 1) handleEdit(selection.value[0]);
}

async function handleSubmit() {
  await formRef.value?.validate();
  dialog.submitting = true;
  try {
    if (dialog.isEdit) {
      await updateDict({ ...form });
      ElMessage.success("修改成功");
    } else {
      await addDict({ ...form });
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

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除字典「${row.dictName}」？`, "提示", {
    type: "warning",
  });
  try {
    await delDict(row.id);
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
    await delDict(selection.value.map((r) => r.id));
    ElMessage.success("删除成功");
    loadList();
  } catch (err) {
    ElMessage.error("删除失败");
  }
}

// ===== 字典数据管理 =====
const dataDialog = reactive({
  visible: false,
  row: null,
  formVisible: false,
  isEdit: false,
  submitting: false,
});
const dataQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  dictType: "",
  dictLabel: "",
  status: "",
});
const dataList = ref([]);
const dataTotal = ref(0);
const dataLoading = ref(false);
const dataSelection = ref([]);

async function loadDictData() {
  const dictType = dataDialog.row?.dictType;
  if (!dictType) {
    dataList.value = [];
    dataTotal.value = 0;
    return;
  }
  dataQuery.dictType = dictType;
  dataLoading.value = true;
  try {
    const res = await listDictData({ ...dataQuery, dictType });
    dataList.value = res.rows || [];
    dataTotal.value = res.total || 0;
  } catch (err) {
    ElMessage.error("字典数据加载失败");
  } finally {
    dataLoading.value = false;
  }
}

function resetDataQuery() {
  dataQuery.dictLabel = "";
  dataQuery.status = "";
  dataQuery.pageNum = 1;
  loadDictData();
}

function handleDataSelectionChange(rows) {
  dataSelection.value = rows;
}

function handleDictData(row) {
  dataDialog.row = row;
  dataQuery.dictType = row.dictType;
  dataQuery.pageNum = 1;
  dataList.value = [];
  dataTotal.value = 0;
  dataSelection.value = [];
  loadDictData();
  dataDialog.visible = true;
}

// ===== 字典数据新增/编辑 =====
const dataFormRef = ref(null);
const defaultDataForm = () => ({
  id: undefined,
  dictType: "",
  dictLabel: "",
  dictValue: "",
  dictSort: 1,
  status: "0",
  listClass: "info",
  remark: "",
});
const dataForm = reactive(defaultDataForm());
const dataRules = {
  dictLabel: [{ required: true, message: "请输入字典标签", trigger: "blur" }],
  dictValue: [{ required: true, message: "请输入字典键值", trigger: "blur" }],
  dictSort: [{ required: true, message: "请输入字典排序", trigger: "blur" }],
};

function resetDataForm() {
  Object.assign(dataForm, defaultDataForm());
  dataFormRef.value?.clearValidate();
  dataDialog.formVisible = false;
}

function handleDataAdd() {
  resetDataForm();
  dataForm.dictType = dataDialog.row?.dictType || "";
  dataDialog.isEdit = false;
  dataDialog.formVisible = true;
}

function handleDataEdit(row) {
  Object.assign(dataForm, row);
  dataForm.dictType = dataDialog.row?.dictType || row.dictType;
  dataDialog.isEdit = true;
  dataDialog.formVisible = true;
}

function handleDataEditSingle() {
  if (dataSelection.value.length === 1) handleDataEdit(dataSelection.value[0]);
}

async function handleDataSubmit() {
  await dataFormRef.value?.validate();
  dataDialog.submitting = true;
  try {
    const payload = {
      ...dataForm,
      dictType: dataDialog.row?.dictType || dataForm.dictType,
    };
    if (dataDialog.isEdit) {
      await updateDictData(payload);
      ElMessage.success("修改成功");
    } else {
      await addDictData(payload);
      ElMessage.success("新增成功");
    }
    dataDialog.formVisible = false;
    loadDictData();
  } catch (err) {
    ElMessage.error(err?.message || "保存失败");
  } finally {
    dataDialog.submitting = false;
  }
}

async function handleDataDelete(row) {
  await ElMessageBox.confirm(`确认删除字典数据「${row.dictLabel}」？`, "提示", {
    type: "warning",
  });
  try {
    await delDictData(row.id);
    ElMessage.success("删除成功");
    loadDictData();
  } catch (err) {
    ElMessage.error("删除失败");
  }
}

async function handleDataBatchDelete() {
  if (!dataSelection.value.length) return;
  await ElMessageBox.confirm(
    `确认删除选中的 ${dataSelection.value.length} 条字典数据？`,
    "提示",
    { type: "warning" },
  );
  try {
    await delDictData(dataSelection.value.map((r) => r.id));
    ElMessage.success("删除成功");
    loadDictData();
  } catch (err) {
    ElMessage.error("删除失败");
  }
}

function handleDataExport() {
  exportJson(
    dataList.value,
    `dict-data-${dataDialog.row?.dictType}-${Date.now()}.json`,
  );
  ElMessage.success("导出成功");
}

function handleExport() {
  exportJson(list.value, `dict-${Date.now()}.json`);
  ElMessage.success("导出成功");
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

.dict-data-edit-form {
  padding: 4px 8px 0;

  :deep(.el-form-item) {
    margin-bottom: 18px;
  }

  :deep(.el-form-item__label) {
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  :deep(.el-select),
  :deep(.el-input-number) {
    width: 100%;
  }
}

.dict-data-compact-row {
  .el-col {
    min-width: 0;
  }
}

.dict-data-status-group {
  display: flex;
  width: 100%;

  :deep(.el-radio-button) {
    flex: 1;
  }

  :deep(.el-radio-button__inner) {
    width: 100%;
  }
}

@media (max-width: 640px) {
  .dict-data-compact-row {
    display: block;

    .el-col {
      max-width: 100%;
      flex: 0 0 100%;
    }
  }
}
</style>
