<template>
  <div class="page">
    <div class="ry-card search">
      <el-form class="query-form" :model="query" inline>
        <el-form-item label="模型"
          ><el-input
            v-model="query.modelName"
            placeholder="名称或编码"
            clearable
        /></el-form-item>
        <el-form-item label="提供商"
          ><el-select
            v-model="query.providerId"
            clearable
            filterable
            placeholder="全部"
            ><el-option
              v-for="p in providers"
              :key="p.id"
              :label="p.providerName"
              :value="p.id" /></el-select
        ></el-form-item>
        <el-form-item
          ><el-button type="primary" :icon="Search" @click="search"
            >搜索</el-button
          ><el-button :icon="Refresh" @click="reset"
            >重置</el-button
          ></el-form-item
        >
      </el-form>
    </div>
    <div class="ry-card table-card">
      <el-tabs
        v-model="query.modelType"
        class="model-tabs"
        @tab-change="changeModelType"
      >
        <el-tab-pane
          v-for="t in types"
          :key="t.value"
          :label="t.label"
          :name="t.value"
        />
      </el-tabs>
      <div class="toolbar">
        <el-button type="primary" :icon="Plus" @click="open()"
          >新增模型</el-button
        ><el-button
          type="danger"
          plain
          :disabled="!selected.length"
          @click="removeRows(selected)"
          >批量删除</el-button
        >
      </div>
      <el-table
        v-loading="loading"
        :data="rows"
        border
        @selection-change="selected = $event"
      >
        <el-table-column type="selection" width="46" />
        <el-table-column label="模型" min-width="180"
          ><template #default="{ row }"
            ><b>{{ row.modelName }}</b>
            <div class="muted">{{ row.modelKey }}</div></template
          ></el-table-column
        >
        <el-table-column label="类型" width="110"
          ><template #default="{ row }"
            ><el-tag :type="typeTag(row.modelType)">{{
              typeName(row.modelType)
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column prop="providerName" label="提供商" min-width="130" />
        <el-table-column
          prop="modelCode"
          label="提供商模型编码"
          min-width="180"
        />
        <el-table-column
          prop="sortNum"
          label="排序号"
          width="90"
          align="center"
        />
        <el-table-column label="关键参数" min-width="150"
          ><template #default="{ row }">{{
            summary(row)
          }}</template></el-table-column
        >
        <el-table-column label="默认" width="112" align="center" fixed="right"
          ><template #default="{ row }"
            ><el-tag v-if="row.isDefault" type="success">默认</el-tag
            ><el-button
              v-else
              link
              type="primary"
              :disabled="row.status === '1'"
              @click="makeDefault(row)"
              >设为默认</el-button
            ></template
          ></el-table-column
        >
        <el-table-column label="状态" width="80" align="center" fixed="right"
          ><template #default="{ row }"
            ><el-switch
              :model-value="row.status === '0'"
              @change="status(row, $event)" /></template
        ></el-table-column>
        <el-table-column label="操作" width="132" fixed="right"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="open(row)">编辑</el-button
            ><el-button link type="danger" @click="removeRows([row])"
              >删除</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="load"
      />
    </div>
    <el-dialog
      v-model="dialog"
      :title="form.id ? '修改模型' : '新增模型'"
      width="760px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12"
            ><el-form-item label="模型名称" prop="modelName"
              ><el-input v-model="form.modelName" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="模型类型" prop="modelType"
              ><el-select v-model="form.modelType" style="width: 100%"
                ><el-option
                  v-for="t in types"
                  :key="t.value"
                  v-bind="t" /></el-select></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="模型编码" prop="modelKey"
              ><el-input v-model="form.modelKey" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="提供商" prop="providerId"
              ><el-select
                v-model="form.providerId"
                filterable
                style="width: 100%"
                ><el-option
                  v-for="p in activeProviders"
                  :key="p.id"
                  :label="p.providerName"
                  :value="p.id" /></el-select></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="模型代码" prop="modelCode"
              ><el-input v-model="form.modelCode" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="部署名称"
              ><el-input v-model="form.deploymentName" /></el-form-item
          ></el-col>
          <el-col v-if="form.modelType === 'RERANK'" :span="12"
            ><el-form-item label="重排接口路径" prop="endpointPath"
              ><el-input
                v-model="form.endpointPath"
                placeholder="例如 /compatible-api/v1/reranks" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="排序号"
              ><el-input-number
                v-model="form.sortNum"
                :min="0"
                :max="999999" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="最大输入 Token"
              ><el-input-number
                v-model="form.contextWindow"
                :min="1" /></el-form-item
          ></el-col>
          <el-col v-if="form.modelType === 'CHAT'" :span="12"
            ><el-form-item label="最大输出 Token"
              ><el-input-number
                v-model="form.maxOutputTokens"
                :min="1" /></el-form-item
          ></el-col>
          <el-col v-if="form.modelType === 'EMBEDDING'" :span="12"
            ><el-form-item label="向量维度" prop="dimension"
              ><el-input-number
                v-model="form.dimension"
                :min="1" /></el-form-item
          ></el-col>
          <el-col v-if="form.modelType === 'RERANK'" :span="12"
            ><el-form-item label="默认 Top N" prop="defaultTopN"
              ><el-input-number
                v-model="form.defaultTopN"
                :min="1" /></el-form-item
          ></el-col>
          <el-col v-if="form.modelType === 'CHAT'" :span="24"
            ><el-form-item label="模型能力"
              ><el-checkbox v-model="form.supportStream">流式</el-checkbox
              ><el-checkbox v-model="form.supportTools">工具</el-checkbox
              ><el-checkbox v-model="form.supportVision">视觉</el-checkbox
              ><el-checkbox v-model="form.supportJson"
                >JSON</el-checkbox
              ></el-form-item
            ></el-col
          >
          <el-col :span="24"
            ><el-form-item label="模型配置" prop="modelConfig"
              ><el-input
                v-model="form.modelConfig"
                type="textarea"
                :rows="3"
                placeholder="JSON对象" /></el-form-item
          ></el-col>
          <el-col :span="24"
            ><el-form-item label="能力扩展" prop="capabilities"
              ><el-input
                v-model="form.capabilities"
                type="textarea"
                :rows="2"
                placeholder="JSON对象" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="默认模型"
              ><el-switch v-model="form.isDefault" /></el-form-item
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
import { computed, onMounted, reactive, ref } from "vue";
import { Delete, Plus, Refresh, Search } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  addModel,
  changeModelStatus,
  delModel,
  listModelProviders,
  listModels,
  setDefaultModel,
  updateModel,
} from "@/api/ai";
const types = [
  { label: "LLM大模型", value: "CHAT" },
  { label: "向量模型", value: "EMBEDDING" },
  { label: "排序模型", value: "RERANK" },
];
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  modelName: "",
  modelType: "CHAT",
  providerId: undefined,
});
const rows = ref([]),
  total = ref(0),
  loading = ref(false),
  selected = ref([]),
  providers = ref([]),
  dialog = ref(false),
  saving = ref(false),
  formRef = ref();
const activeProviders = computed(() =>
  providers.value.filter((p) => p.status === "0"),
);
const empty = () => ({
  id: undefined,
  providerId: undefined,
  modelName: "",
  modelKey: "",
  modelCode: "",
  modelType: "CHAT",
  sortNum: 0,
  deploymentName: "",
  endpointPath: "",
  contextWindow: 8192,
  maxOutputTokens: 2048,
  dimension: 1024,
  defaultTopN: 10,
  supportStream: true,
  supportTools: false,
  supportVision: false,
  supportJson: false,
  priceUnit: "MILLION_TOKEN",
  currency: "CNY",
  modelConfig: "",
  capabilities: "",
  isDefault: false,
  status: "0",
  remark: "",
});
const form = reactive(empty());
const jsonRule = (_, v, done) => {
  if (!v) return done();
  try {
    const o = JSON.parse(v);
    o && !Array.isArray(o) && typeof o === "object"
      ? done()
      : done(new Error("必须是JSON对象"));
  } catch {
    done(new Error("JSON格式错误"));
  }
};
const rules = {
  modelName: [{ required: true, message: "请输入模型名称" }],
  modelKey: [{ required: true, message: "请输入模型编码" }],
  modelCode: [{ required: true, message: "请输入模型代码" }],
  modelType: [{ required: true, message: "请选择类型" }],
  providerId: [{ required: true, message: "请选择提供商" }],
  dimension: [
    {
      validator: (_, v, d) =>
        form.modelType !== "EMBEDDING" || v > 0
          ? d()
          : d(new Error("请输入向量维度")),
    },
  ],
  defaultTopN: [
    {
      validator: (_, v, d) =>
        form.modelType !== "RERANK" || v > 0
          ? d()
          : d(new Error("请输入Top N")),
    },
  ],
  modelConfig: [{ validator: jsonRule }],
  capabilities: [{ validator: jsonRule }],
};
rules.endpointPath = [
  {
    validator: (_, v, d) =>
      form.modelType !== "RERANK" || String(v || "").trim()
        ? d()
        : d(new Error("请输入重排接口路径")),
  },
];
async function loadProviders() {
  providers.value = (await listModelProviders()).data || [];
}
async function load() {
  loading.value = true;
  try {
    const r = await listModels({ ...query });
    rows.value = r.rows || [];
    total.value = r.total || 0;
  } finally {
    loading.value = false;
  }
}
function search() {
  query.pageNum = 1;
  load();
}
function reset() {
  Object.assign(query, { pageNum: 1, modelName: "", providerId: undefined });
  load();
}
function changeModelType() {
  query.pageNum = 1;
  selected.value = [];
  load();
}
function open(row) {
  Object.assign(
    form,
    empty(),
    row || (!row ? { modelType: query.modelType } : {}),
  );
  dialog.value = true;
}
async function save() {
  await formRef.value.validate();
  saving.value = true;
  try {
    form.id ? await updateModel({ ...form }) : await addModel({ ...form });
    ElMessage.success("保存成功");
    dialog.value = false;
    load();
  } finally {
    saving.value = false;
  }
}
async function status(row, val) {
  try {
    await changeModelStatus(row.id, val ? "0" : "1");
    ElMessage.success(val ? "模型已启用" : "模型已停用");
  } finally {
    await load();
  }
}
async function makeDefault(row) {
  await setDefaultModel(row.id);
  ElMessage.success("设置成功");
  load();
}
async function removeRows(list) {
  await ElMessageBox.confirm(`确认删除${list.length}条模型？`, "提示", {
    type: "warning",
  });
  await delModel(list.map((x) => x.id));
  ElMessage.success("删除成功");
  load();
}
const typeName = (v) => types.find((t) => t.value === v)?.label || v;
const typeTag = (v) =>
  v === "CHAT" ? "primary" : v === "EMBEDDING" ? "success" : "warning";
const summary = (r) =>
  r.modelType === "EMBEDDING"
    ? `${r.dimension || "-"}维`
    : r.modelType === "RERANK"
      ? `Top ${r.defaultTopN || "-"}`
      : `${r.contextWindow || "-"} Token`;
onMounted(async () => {
  await loadProviders();
  await load();
});
</script>
<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
}
.search,
.table-card {
  padding: 18px 20px;
}
.query-form {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  column-gap: 24px;
  row-gap: 12px;
}
.query-form :deep(.el-form-item) {
  margin: 0;
}
.query-form :deep(.el-form-item__label) {
  white-space: nowrap;
  padding-right: 10px;
}
.query-form :deep(.el-form-item:nth-child(1) .el-input) {
  width: 216px;
}
.query-form :deep(.el-form-item:nth-child(2) .el-select) {
  width: 240px;
}
.model-tabs {
  margin: -6px 0 14px;
}
.model-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}
.toolbar {
  margin-bottom: 14px;
  display: flex;
  gap: 10px;
}
.muted {
  color: #909399;
  font-size: 12px;
  margin-top: 3px;
}
.el-pagination {
  justify-content: flex-end;
  margin-top: 16px;
}
.el-input-number {
  width: 100%;
}
@media (max-width: 900px) {
  .query-form {
    column-gap: 16px;
  }
  .query-form :deep(.el-form-item:nth-child(1) .el-input),
  .query-form :deep(.el-form-item:nth-child(2) .el-select) {
    width: 200px;
  }
}
</style>
