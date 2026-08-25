<template>
  <div class="page">
    <div class="ry-card card">
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" @click="open()"
          >新增提供商</el-button
        ><el-button :icon="Refresh" circle @click="load" />
      </div>
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column label="提供商" min-width="160"
          ><template #default="{ row }"
            ><b>{{ row.providerName }}</b>
            <div class="muted">{{ row.providerCode }}</div></template
          ></el-table-column
        >
        <el-table-column prop="providerType" label="类型" width="150" />
        <el-table-column
          prop="sortNum"
          label="排序号"
          width="90"
          align="center"
        />
        <el-table-column prop="baseUrl" label="API基础地址" min-width="240" />
        <el-table-column prop="apiKeyMask" label="API Key" width="140"
          ><template #default="{ row }">{{
            row.apiKeyMask || "未配置"
          }}</template></el-table-column
        >
        <el-table-column label="超时" width="130"
          ><template #default="{ row }"
            >{{ row.connectTimeout }}s / {{ row.readTimeout }}s</template
          ></el-table-column
        >
        <el-table-column label="状态" width="80"
          ><template #default="{ row }"
            ><el-switch
              :model-value="row.status === '0'"
              @change="status(row, $event)" /></template
        ></el-table-column>
        <el-table-column label="操作" width="130"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="open(row)">编辑</el-button
            ><el-button link type="danger" @click="removeRow(row)"
              >删除</el-button
            ></template
          ></el-table-column
        >
      </el-table>
    </div>
    <el-dialog
      v-model="dialog"
      :title="form.id ? '修改提供商' : '新增提供商'"
      width="min(720px, calc(100vw - 32px))"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12"
            ><el-form-item label="配置名称" prop="providerName"
              ><el-input v-model="form.providerName" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="提供商编码" prop="providerCode"
              ><el-input v-model="form.providerCode" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="提供商类型" prop="providerType"
              ><el-select
                v-model="form.providerType"
                filterable
                allow-create
                style="width: 100%"
                ><el-option
                  v-for="t in providerTypes"
                  :key="t"
                  :label="t"
                  :value="t" /></el-select></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="API Key"
              ><el-input
                v-model="form.apiKey"
                type="password"
                show-password
                :placeholder="
                  form.id ? '留空表示不修改' : '请输入API Key'
                " /></el-form-item
          ></el-col>
          <el-col :span="24"
            ><el-form-item label="API基础地址" prop="baseUrl"
              ><el-input v-model="form.baseUrl" /></el-form-item
          ></el-col>
          <el-col :span="24"
            ><el-form-item label="排序号"
              ><el-input-number
                v-model="form.sortNum"
                :min="0"
                :max="999999" /></el-form-item
          ></el-col>
          <el-col :span="24"
            ><el-form-item label="连接超时" class="number-item"
              ><div class="number-field">
                <el-input-number v-model="form.connectTimeout" :min="1" />
                <div class="number-unit">单位：秒</div>
              </div></el-form-item
            ></el-col
          >
          <el-col :span="24"
            ><el-form-item label="读取超时" class="number-item"
              ><div class="number-field">
                <el-input-number v-model="form.readTimeout" :min="1" />
                <div class="number-unit">单位：秒</div>
              </div></el-form-item
            ></el-col
          >
          <el-col :span="24"
            ><el-form-item label="重试次数" class="number-item"
              ><div class="number-field">
                <el-input-number v-model="form.maxRetries" :min="0" />
                <div class="number-unit">单位：次</div>
              </div></el-form-item
            ></el-col
          >
          <el-col :span="24"
            ><el-form-item label="扩展配置" prop="extraConfig"
              ><el-input
                v-model="form.extraConfig"
                type="textarea"
                :rows="3"
                placeholder="JSON对象" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="状态"
              ><el-radio-group v-model="form.status"
                ><el-radio value="0">启用</el-radio
                ><el-radio value="1">停用</el-radio></el-radio-group
              ></el-form-item
            ></el-col
          >
          <el-col :span="24"
            ><el-form-item label="备注"
              ><el-input v-model="form.remark" type="textarea" /></el-form-item
          ></el-col>
        </el-row>
      </el-form>
      <template #footer
        ><el-button @click="dialog = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >确定</el-button
        ></template
      >
    </el-dialog>
  </div>
</template>
<script setup>
import { onMounted, reactive, ref } from "vue";
import { Plus, Refresh } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  addModelProvider,
  changeModelProviderStatus,
  delModelProvider,
  listModelProviders,
  updateModelProvider,
} from "@/api/ai";
const providerTypes = [
  "OPENAI",
  "AZURE_OPENAI",
  "ANTHROPIC",
  "DEEPSEEK",
  "OLLAMA",
  "DASHSCOPE",
  "OPENAI_COMPATIBLE",
];
const rows = ref([]),
  loading = ref(false),
  dialog = ref(false),
  saving = ref(false),
  formRef = ref();
const empty = () => ({
  id: undefined,
  providerName: "",
  providerCode: "",
  providerType: "OPENAI_COMPATIBLE",
  baseUrl: "",
  apiKey: "",
  sortNum: 0,
  connectTimeout: 10,
  readTimeout: 120,
  maxRetries: 2,
  extraConfig: "",
  status: "0",
  remark: "",
});
const form = reactive(empty());
const jsonRule = (_, v, d) => {
  if (!v) return d();
  try {
    const o = JSON.parse(v);
    o && !Array.isArray(o) && typeof o === "object"
      ? d()
      : d(new Error("必须是JSON对象"));
  } catch {
    d(new Error("JSON格式错误"));
  }
};
const rules = {
  providerName: [{ required: true, message: "请输入名称" }],
  providerCode: [{ required: true, message: "请输入编码" }],
  providerType: [{ required: true, message: "请选择类型" }],
  baseUrl: [{ required: true, message: "请输入API地址" }],
  extraConfig: [{ validator: jsonRule }],
};
async function load() {
  loading.value = true;
  try {
    rows.value = (await listModelProviders()).data || [];
  } finally {
    loading.value = false;
  }
}
function open(row) {
  Object.assign(form, empty(), row || {}, row ? { apiKey: "" } : {});
  dialog.value = true;
}
async function save() {
  await formRef.value.validate();
  saving.value = true;
  try {
    form.id
      ? await updateModelProvider({ ...form })
      : await addModelProvider({ ...form });
    ElMessage.success("保存成功");
    dialog.value = false;
    load();
  } finally {
    saving.value = false;
  }
}
async function status(row, val) {
  await changeModelProviderStatus(row.id, val ? "0" : "1");
  load();
}
async function removeRow(row) {
  await ElMessageBox.confirm(`确认删除“${row.providerName}”？`, "提示", {
    type: "warning",
  });
  await delModelProvider(row.id);
  ElMessage.success("删除成功");
  load();
}
onMounted(load);
</script>
<style scoped>
.page {
  padding: 16px;
}
.card {
  padding: 18px 20px;
}
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}
.muted {
  color: #909399;
  font-size: 12px;
  margin-top: 3px;
}
.number-item :deep(.el-input-number) {
  width: 220px;
  max-width: 100%;
}
.number-item :deep(.el-form-item__label) {
  white-space: nowrap;
}
.number-field {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.number-unit {
  margin-top: 5px;
  color: #909399;
  font-size: 12px;
  line-height: 18px;
}
</style>
