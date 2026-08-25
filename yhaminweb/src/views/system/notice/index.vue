<template>
  <div class="ry-page">
    <!-- 1. 搜索筛选条 -->
    <div class="ry-card ry-search-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="公告标题">
          <el-input
            v-model.trim="query.noticeTitle"
            placeholder="请输入公告标题"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="创建人">
          <el-input
            v-model.trim="query.createBy"
            placeholder="请输入创建人"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="公告类型">
          <el-select
            v-model="query.noticeType"
            placeholder="公告类型"
            clearable
            style="width: 140px"
          >
            <el-option label="公告" value="1" />
            <el-option label="通知" value="2" />
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
        <el-table-column label="公告编号" prop="id" width="90" align="center" />
        <el-table-column
          label="公告标题"
          prop="noticeTitle"
          min-width="240"
          show-overflow-tooltip
        />
        <el-table-column
          label="公告类型"
          prop="noticeType"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.noticeType === '1' ? 'success' : 'info'"
              effect="light"
            >
              {{ row.noticeType === "1" ? "公告" : "通知" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建人" prop="createBy" width="110" />
        <el-table-column label="创建时间" prop="createTime" width="170" />
        <el-table-column label="状态" prop="status" width="90" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.status === '0' ? 'success' : 'info'"
              effect="light"
            >
              {{ row.status === "0" ? "正常" : "关闭" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)"
              >修改</el-button
            >
            <el-divider direction="vertical" />
            <el-button type="danger" link @click="handleDelete(row)"
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
      :title="dialog.isEdit ? '修改公告' : '新增公告'"
      width="680px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="14">
            <el-form-item label="公告标题" prop="noticeTitle">
              <el-input
                v-model.trim="form.noticeTitle"
                placeholder="请输入公告标题"
              />
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="公告类型" prop="noticeType">
              <el-select v-model="form.noticeType" style="width: 100%">
                <el-option label="公告" value="1" />
                <el-option label="通知" value="2" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio>
            <el-radio value="1">关闭</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="公告内容" prop="noticeContent">
          <el-input
            v-model="form.noticeContent"
            type="textarea"
            :rows="6"
            placeholder="请输入公告内容"
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
import { listNotice, addNotice, updateNotice, delNotice } from "@/api/system";
import { exportJson } from "@/utils";

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  noticeTitle: "",
  createBy: "",
  noticeType: "",
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
    const res = await listNotice(params);
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
  query.noticeTitle = "";
  query.createBy = "";
  query.noticeType = "";
  dateRange.value = [];
  query.pageNum = 1;
  loadList();
}

function handleSelectionChange(rows) {
  selection.value = rows;
}

// ===== 新增/编辑 =====
const dialog = reactive({ visible: false, isEdit: false, submitting: false });
const formRef = ref(null);
const defaultForm = () => ({
  id: undefined,
  noticeTitle: "",
  noticeType: "1",
  status: "0",
  noticeContent: "",
});
const form = reactive(defaultForm());
const rules = {
  noticeTitle: [{ required: true, message: "请输入公告标题", trigger: "blur" }],
  noticeType: [
    { required: true, message: "请选择公告类型", trigger: "change" },
  ],
  noticeContent: [
    { required: true, message: "请输入公告内容", trigger: "blur" },
  ],
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
      await updateNotice({ ...form });
      ElMessage.success("修改成功");
    } else {
      await addNotice({ ...form });
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
  await ElMessageBox.confirm(`确认删除公告「${row.noticeTitle}」？`, "提示", {
    type: "warning",
  });
  try {
    await delNotice(row.id);
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
    await delNotice(selection.value.map((r) => r.id));
    ElMessage.success("删除成功");
    loadList();
  } catch (err) {
    ElMessage.error("删除失败");
  }
}

function handleExport() {
  exportJson(list.value, `notice-${Date.now()}.json`);
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
</style>
