<template>
  <div class="ry-page">
    <!-- 1. 搜索筛选条 -->
    <div class="ry-card ry-search-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="参数名称">
          <el-input
            v-model.trim="query.configName"
            placeholder="请输入参数名称"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="参数键名">
          <el-input
            v-model.trim="query.configKey"
            placeholder="请输入参数键名"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="系统内置">
          <el-select
            v-model="query.configType"
            placeholder="系统内置"
            clearable
            style="width: 140px"
          >
            <el-option label="是" value="Y" />
            <el-option label="否" value="N" />
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
        <el-table-column label="参数主键" prop="id" width="90" align="center" />
        <el-table-column
          label="参数名称"
          prop="configName"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column label="参数键名" prop="configKey" min-width="200">
          <template #default="{ row }">
            <span class="ry-mono">{{ row.configKey }}</span>
          </template>
        </el-table-column>
        <el-table-column label="参数键值" prop="configValue" min-width="160">
          <template #default="{ row }">
            <el-switch
              v-if="row.configKey === CAPTCHA_CONFIG_KEY"
              :model-value="isTrue(row.configValue)"
              inline-prompt
              active-text="启用"
              inactive-text="关闭"
              :loading="captchaSwitchLoading === row.id"
              @change="(value) => changeCaptchaEnabled(row, value)"
            />
            <span v-else>{{ row.configValue }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="系统内置"
          prop="configType"
          width="100"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.configType === 'Y' ? 'primary' : 'info'"
              effect="light"
            >
              {{ row.configType === "Y" ? "是" : "否" }}
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
      :title="dialog.isEdit ? '修改参数' : '新增参数'"
      width="600px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="参数名称" prop="configName">
          <el-input
            v-model.trim="form.configName"
            placeholder="请输入参数名称"
          />
        </el-form-item>
        <el-form-item label="参数键名" prop="configKey">
          <el-input
            v-model.trim="form.configKey"
            placeholder="请输入参数键名"
          />
        </el-form-item>
        <el-form-item label="参数键值" prop="configValue">
          <el-switch
            v-if="form.configKey === CAPTCHA_CONFIG_KEY"
            v-model="form.configValue"
            active-value="true"
            inactive-value="false"
            inline-prompt
            active-text="启用"
            inactive-text="关闭"
          />
          <el-input
            v-else
            v-model.trim="form.configValue"
            placeholder="请输入参数键值"
          />
        </el-form-item>
        <el-form-item label="系统内置">
          <el-radio-group v-model="form.configType">
            <el-radio value="Y">是</el-radio>
            <el-radio value="N">否</el-radio>
          </el-radio-group>
        </el-form-item>
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
import { listConfig, addConfig, updateConfig, delConfig } from "@/api/system";
import { exportJson } from "@/utils";

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  configName: "",
  configKey: "",
  configType: "",
});

const list = ref([]);
const total = ref(0);
const loading = ref(false);
const selection = ref([]);
const CAPTCHA_CONFIG_KEY = "sys.account.captchaEnabled";
const captchaSwitchLoading = ref(null);

function isTrue(value) {
  return ["true", "1", "y", "yes", "on"].includes(
    String(value ?? "").trim().toLowerCase(),
  );
}

async function changeCaptchaEnabled(row, enabled) {
  captchaSwitchLoading.value = row.id;
  try {
    await updateConfig({ ...row, configValue: enabled ? "true" : "false" });
    row.configValue = enabled ? "true" : "false";
    ElMessage.success(enabled ? "登录验证码已启用" : "登录验证码已关闭");
  } catch (err) {
    ElMessage.error(err?.message || "验证码开关更新失败");
  } finally {
    captchaSwitchLoading.value = null;
  }
}

async function loadList() {
  loading.value = true;
  try {
    const res = await listConfig({ ...query });
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
  query.configName = "";
  query.configKey = "";
  query.configType = "";
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
  configName: "",
  configKey: "",
  configValue: "",
  configType: "N",
  remark: "",
});
const form = reactive(defaultForm());
const rules = {
  configName: [{ required: true, message: "请输入参数名称", trigger: "blur" }],
  configKey: [{ required: true, message: "请输入参数键名", trigger: "blur" }],
  configValue: [{ required: true, message: "请输入参数键值", trigger: "blur" }],
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
      await updateConfig({ ...form });
      ElMessage.success("修改成功");
    } else {
      await addConfig({ ...form });
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
  await ElMessageBox.confirm(`确认删除参数「${row.configName}」？`, "提示", {
    type: "warning",
  });
  try {
    await delConfig(row.id);
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
    await delConfig(selection.value.map((r) => r.id));
    ElMessage.success("删除成功");
    loadList();
  } catch (err) {
    ElMessage.error("删除失败");
  }
}

function handleExport() {
  exportJson(list.value, `config-${Date.now()}.json`);
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
</style>
