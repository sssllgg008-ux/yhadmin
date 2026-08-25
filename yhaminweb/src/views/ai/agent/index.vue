<template>
  <div class="ry-page">
    <!-- 1. 搜索筛选条 -->
    <div class="ry-card ry-search-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="智能体名称">
          <el-input
            v-model.trim="query.agentName"
            placeholder="请输入智能体名称"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="全部"
            clearable
            style="width: 120px"
          >
            <el-option label="启用" value="0" />
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
          <el-button type="primary" :icon="Plus" @click="handleAdd"
            >新增智能体</el-button
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
        <el-table-column label="编号" prop="id" width="70" align="center" />
        <el-table-column
          label="智能体名称"
          prop="agentName"
          min-width="160"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <div class="agent-name-cell">
              <el-avatar :size="28" class="agent-avatar">{{
                row.agentName.slice(0, 1)
              }}</el-avatar>
              <span>{{ row.agentName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="描述"
          prop="description"
          min-width="220"
          show-overflow-tooltip
        />
        <el-table-column label="工具数量" width="100" align="center">
          <template #default="{ row }">
            <el-tooltip
              v-if="row.tools?.length"
              :content="row.tools.join('、')"
              placement="top"
            >
              <el-tag type="info" effect="light"
                >{{ row.tools.length }} 个</el-tag
              >
            </el-tooltip>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === '0'"
              @change="(val) => handleStatusChange(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="160" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleChat(row)"
              >对话</el-button
            >
            <el-button type="primary" link @click="handleEdit(row)"
              >编辑</el-button
            >
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
      :title="dialog.isEdit ? '编辑智能体' : '新增智能体'"
      width="720px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="智能体名称" prop="agentName">
              <el-input
                v-model.trim="form.agentName"
                placeholder="如 客服助手"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model.trim="form.description"
            placeholder="一句话描述智能体用途"
          />
        </el-form-item>
        <el-form-item label="工具集合">
          <el-select
            v-model="form.tools"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入工具名"
            style="width: 100%"
          >
            <el-option
              v-for="t in toolOptions"
              :key="t"
              :label="t"
              :value="t"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="System Prompt" prop="systemPrompt">
          <el-input
            v-model="form.systemPrompt"
            type="textarea"
            :rows="5"
            placeholder="定义智能体的角色、行为与约束"
          />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio value="0">启用</el-radio>
                <el-radio value="1">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from "vue";
import { useRouter } from "vue-router";
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
  listAgent,
  addAgent,
  updateAgent,
  delAgent,
  changeAgentStatus,
} from "@/api/ai";
import { exportJson } from "@/utils";

const router = useRouter();

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  agentName: "",
  status: "",
});

const list = ref([]);
const total = ref(0);
const loading = ref(false);
const selection = ref([]);
const toolOptions = [
  "知识库检索",
  "邮件发送",
  "天气查询",
  "数据库查询",
  "代码搜索",
  "图片生成",
  "文档解析",
  "向量检索",
  "代码仓库",
  "图表生成",
  "术语库检索",
  "风险评估",
  "语音转写",
  "日历管理",
  "搜索引擎",
];

async function loadList() {
  loading.value = true;
  try {
    const res = await listAgent(query);
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
  query.agentName = "";
  query.status = "";
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
  agentName: "",
  description: "",
  tools: [],
  systemPrompt: "",
  status: "0",
  remark: "",
});
const form = reactive(defaultForm());
const rules = {
  agentName: [{ required: true, message: "请输入智能体名称", trigger: "blur" }],
  description: [{ required: true, message: "请输入描述", trigger: "blur" }],
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
  Object.assign(form, row, { tools: [...(row.tools || [])] });
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
      await updateAgent({ ...form });
      ElMessage.success("修改成功");
    } else {
      await addAgent({ ...form });
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
  await ElMessageBox.confirm(`确认删除智能体「${row.agentName}」？`, "提示", {
    type: "warning",
  });
  try {
    await delAgent(row.id);
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
    await delAgent(selection.value.map((r) => r.id));
    ElMessage.success("删除成功");
    loadList();
  } catch (err) {
    ElMessage.error("删除失败");
  }
}

// ===== 状态切换 =====
async function handleStatusChange(row, val) {
  const newStatus = val ? "0" : "1";
  try {
    await changeAgentStatus(row.id, newStatus);
    row.status = newStatus;
    ElMessage.success("状态已更新");
  } catch (err) {
    ElMessage.error("状态更新失败");
  }
}

function handleChat(row) {
  ElMessage.info(`正在打开「${row.agentName}」对话窗口...`);
  router.push(`/ai/bot?id=${row.id}`);
}

function handleExport() {
  exportJson(list.value, `agents-${Date.now()}.json`);
  ElMessage.success("导出成功");
}

onMounted(() => {
  loadList();
});
</script>

<style lang="scss" scoped>
.ry-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.agent-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.agent-avatar {
  background: var(--ry-primary);
  color: #fff;
  font-size: 13px;
}

.text-muted {
  color: var(--ry-neutral-400);
}
</style>
