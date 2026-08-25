<template>
  <div class="warehouse-page">
    <section class="warehouse-hero ry-card">
      <div class="hero-main">
        <div class="hero-icon">
          <el-icon><Box /></el-icon>
        </div>
        <div>
          <h2>数据集管理</h2>
          <p>组合事实表、物理表、数据集和指标，形成面向分析的数据服务模型。</p>
        </div>
      </div>
      <div class="hero-stats">
        <div>
          <strong>{{ rows.length }}</strong
          ><span>数据集</span>
        </div>
        <div>
          <strong>{{ publishedCount }}</strong
          ><span>已发布</span>
        </div>
        <div>
          <strong>{{ fieldCount }}</strong
          ><span>字段</span>
        </div>
      </div>
    </section>
    <div class="ry-card ry-search-card">
      <el-form inline
        ><el-form-item label="业务领域"
          ><el-select v-model="domainId" style="width: 190px" @change="load"
            ><el-option
              v-for="x in domains"
              :key="x.id"
              :label="x.domainName"
              :value="x.id" /></el-select></el-form-item
        ><el-form-item label="关键词"
          ><el-input
            v-model.trim="keyword"
            :prefix-icon="Search"
            clearable
            placeholder="名称 / 编码" /></el-form-item
        ><el-form-item label="状态"
          ><el-select
            v-model="status"
            clearable
            placeholder="全部"
            style="width: 120px"
            ><el-option label="草稿" value="DRAFT" /><el-option
              label="已发布"
              value="PUBLISHED" /></el-select></el-form-item
      ></el-form>
    </div>
    <div class="ry-card ry-table-card">
      <div class="ry-toolbar">
        <div class="ry-toolbar-left">
          <el-button
            type="primary"
            :icon="Plus"
            :disabled="!domainId"
            @click="open()"
            >新增数据集</el-button
          ><span class="hint"
            >来源、关联、字段和指标引用使用独立结构维护。</span
          >
        </div>
        <div class="ry-toolbar-right">
          <span class="hint">共 {{ filtered.length }} 项</span
          ><el-button circle :icon="Refresh" @click="load" />
        </div>
      </div>
      <el-table v-loading="loading" :data="filtered" border stripe
        ><el-table-column label="数据集" min-width="230"
          ><template #default="{ row }"
            ><b>{{ row.datasetName }}</b>
            <div class="code">{{ code(row.datasetCode) }}</div></template
          ></el-table-column
        ><el-table-column
          prop="purpose"
          label="用途"
          min-width="180"
          show-overflow-tooltip
        /><el-table-column label="来源 / Join" width="120" align="center"
          ><template #default="{ row }"
            >{{ row.sources?.length || 0 }} /
            {{ row.joins?.length || 0 }}</template
          ></el-table-column
        ><el-table-column label="字段 / 指标" width="120" align="center"
          ><template #default="{ row }"
            >{{ row.fields?.length || 0 }} /
            {{ row.metrics?.length || 0 }}</template
          ></el-table-column
        ><el-table-column label="主来源" min-width="130"
          ><template #default="{ row }">{{
            primaryAlias(row) || "—"
          }}</template></el-table-column
        ><el-table-column label="状态" width="95"
          ><template #default="{ row }"
            ><el-tag
              :type="row.status === 'PUBLISHED' ? 'success' : 'warning'"
              >{{ row.status === "PUBLISHED" ? "已发布" : "草稿" }}</el-tag
            ></template
          ></el-table-column
        ><el-table-column label="操作" width="210" fixed="right"
          ><template #default="{ row }"
            ><el-button
              link
              type="primary"
              :disabled="row.status !== 'DRAFT'"
              @click="open(row)"
              >编辑</el-button
            ><el-divider direction="vertical" /><el-button
              link
              type="success"
              :disabled="row.status !== 'DRAFT'"
              @click="publish(row)"
              >发布</el-button
            ><el-divider direction="vertical" /><el-button
              link
              type="danger"
              @click="remove(row)"
              >删除</el-button
            ></template
          ></el-table-column
        ></el-table
      >
    </div>
    <el-dialog
      v-model="visible"
      :title="form.id ? '编辑数据集' : '新增数据集'"
      width="1120px"
      top="3vh"
      :close-on-click-modal="false"
      ><el-form ref="formRef" :model="form" :rules="rules" label-width="100px"
        ><el-tabs v-model="tab" class="editor-tabs"
          ><el-tab-pane label="基础信息" name="basic"
            ><el-row :gutter="18"
              ><el-col :span="12"
                ><el-form-item label="数据集名称" prop="name"
                  ><el-input
                    v-model.trim="form.name"
                    placeholder="例如：销售经营分析集" /></el-form-item></el-col
              ><el-col :span="12"
                ><el-form-item label="数据集编码" prop="code"
                  ><el-input
                    v-model.trim="form.code"
                    :disabled="!!form.id"
                    placeholder="sales_analysis" /></el-form-item></el-col></el-row
            ><el-form-item label="业务用途" prop="purpose"
              ><el-input
                v-model.trim="form.purpose"
                placeholder="描述数据集服务的分析场景" /></el-form-item
            ><el-form-item label="默认过滤"
              ><el-input
                v-model="form.defaultFilter"
                type="textarea"
                :rows="3"
                placeholder="例如：order_status = 'PAID'" /></el-form-item
            ><el-form-item label="说明"
              ><el-input
                v-model="form.description"
                type="textarea"
                :rows="3" /></el-form-item
          ></el-tab-pane>
          <el-tab-pane name="sources"
            ><template #label>数据来源 ({{ form.sources.length }})</template>
            <div class="pane-head">
              <span>数据集必须且只能设置一个主来源。</span
              ><el-button type="primary" plain :icon="Plus" @click="addSource"
                >新增来源</el-button
              >
            </div>
            <el-table :data="form.sources" border
              ><el-table-column label="来源别名" width="150"
                ><template #default="{ row }"
                  ><el-input
                    v-model.trim="row.alias"
                    placeholder="orders" /></template></el-table-column
              ><el-table-column label="来源类型" width="150"
                ><template #default="{ row }"
                  ><el-select
                    v-model="row.type"
                    @change="sourceTypeChanged(row)"
                    ><el-option label="事实表" value="FACT_TABLE" /><el-option
                      label="物理表"
                      value="PHYSICAL_TABLE" /><el-option
                      label="其他数据集"
                      value="DATASET" /></el-select></template></el-table-column
              ><el-table-column label="来源对象"
                ><template #default="{ row }"
                  ><el-select
                    v-if="row.type !== 'PHYSICAL_TABLE'"
                    v-model="row.targetId"
                    filterable
                    style="width: 100%"
                    ><el-option
                      v-for="x in sourceOptions(row.type)"
                      :key="x.id"
                      :label="x.name"
                      :value="x.id" /></el-select
                  ><el-input
                    v-else
                    v-model.trim="row.physicalTable"
                    placeholder="schema.table_name" /></template></el-table-column
              ><el-table-column label="主来源" width="90" align="center"
                ><template #default="{ row }"
                  ><el-radio v-model="primarySourceKey" :value="row.localKey"
                    ><span /></el-radio></template></el-table-column
              ><el-table-column label="操作" width="70"
                ><template #default="{ $index }"
                  ><el-button link type="danger" @click="removeSource($index)"
                    >删除</el-button
                  ></template
                ></el-table-column
              ></el-table
            ></el-tab-pane
          >
          <el-tab-pane name="joins"
            ><template #label>关联定义 ({{ form.joins.length }})</template>
            <div class="pane-head">
              <span>Join 左右别名必须引用已配置的不同来源。</span
              ><el-button
                type="primary"
                plain
                :icon="Plus"
                :disabled="form.sources.length < 2"
                @click="addJoin"
                >新增 Join</el-button
              >
            </div>
            <el-table :data="form.joins" border
              ><el-table-column label="关联编码" width="145"
                ><template #default="{ row }"
                  ><el-input
                    v-model.trim="row.code"
                    placeholder="orders_customer" /></template></el-table-column
              ><el-table-column label="关联类型" width="120"
                ><template #default="{ row }"
                  ><el-select v-model="row.type"
                    ><el-option
                      v-for="x in joins"
                      :key="x"
                      :label="x"
                      :value="x" /></el-select></template></el-table-column
              ><el-table-column label="左来源" width="140"
                ><template #default="{ row }"
                  ><el-select v-model="row.leftAlias"
                    ><el-option
                      v-for="x in validSources"
                      :key="x.alias"
                      :label="x.alias"
                      :value="
                        x.alias
                      " /></el-select></template></el-table-column
              ><el-table-column label="右来源" width="140"
                ><template #default="{ row }"
                  ><el-select v-model="row.rightAlias"
                    ><el-option
                      v-for="x in validSources"
                      :key="x.alias"
                      :label="x.alias"
                      :value="
                        x.alias
                      " /></el-select></template></el-table-column
              ><el-table-column label="关联条件"
                ><template #default="{ row }"
                  ><el-input
                    v-model.trim="row.condition"
                    placeholder="orders.customer_id = customer.id" /></template></el-table-column
              ><el-table-column label="操作" width="70"
                ><template #default="{ $index }"
                  ><el-button
                    link
                    type="danger"
                    @click="form.joins.splice($index, 1)"
                    >删除</el-button
                  ></template
                ></el-table-column
              ></el-table
            ></el-tab-pane
          >
          <el-tab-pane name="fields"
            ><template #label>字段定义 ({{ form.fields.length }})</template>
            <div class="pane-head">
              <span>字段来源别名必须存在，显示顺序用于控制输出列顺序。</span
              ><el-button type="primary" plain :icon="Plus" @click="addField"
                >新增字段</el-button
              >
            </div>
            <el-table :data="form.fields" border max-height="430"
              ><el-table-column label="字段编码"
                ><template #default="{ row }"
                  ><el-input
                    v-model.trim="row.code" /></template></el-table-column
              ><el-table-column label="字段名称"
                ><template #default="{ row }"
                  ><el-input
                    v-model.trim="row.name" /></template></el-table-column
              ><el-table-column label="类型" width="130"
                ><template #default="{ row }"
                  ><el-select v-model="row.dataType"
                    ><el-option
                      v-for="x in dataTypes"
                      :key="x"
                      :label="x"
                      :value="x" /></el-select></template></el-table-column
              ><el-table-column label="来源别名" width="130"
                ><template #default="{ row }"
                  ><el-select v-model="row.sourceAlias"
                    ><el-option
                      v-for="x in validSources"
                      :key="x.alias"
                      :label="x.alias"
                      :value="
                        x.alias
                      " /></el-select></template></el-table-column
              ><el-table-column label="来源字段"
                ><template #default="{ row }"
                  ><el-input
                    v-model.trim="
                      row.sourceField
                    " /></template></el-table-column
              ><el-table-column label="顺序" width="90"
                ><template #default="{ row }"
                  ><el-input-number
                    v-model="row.orderNum"
                    :min="1"
                    controls-position="right" /></template></el-table-column
              ><el-table-column label="可见" width="70"
                ><template #default="{ row }"
                  ><el-switch
                    v-model="row.visible" /></template></el-table-column
              ><el-table-column label="操作" width="70"
                ><template #default="{ $index }"
                  ><el-button
                    link
                    type="danger"
                    @click="form.fields.splice($index, 1)"
                    >删除</el-button
                  ></template
                ></el-table-column
              ></el-table
            ></el-tab-pane
          >
          <el-tab-pane name="metrics"
            ><template #label>指标引用 ({{ form.metrics.length }})</template>
            <div class="metric-select">
              <div>
                <b>选择同领域已发布指标</b>
                <p>保存时转换为独立的指标引用记录。</p>
              </div>
              <el-select
                v-model="selectedMetricIds"
                multiple
                filterable
                collapse-tags
                placeholder="请选择指标"
                style="width: 600px"
                ><el-option
                  v-for="x in publishedMetrics"
                  :key="x.id"
                  :label="`${x.metricName} (${code(x.metricCode)})`"
                  :value="x.id"
              /></el-select></div></el-tab-pane></el-tabs></el-form
      ><template #footer
        ><el-button @click="visible = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="submit"
          >保存草稿</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { Box, Plus, Refresh, Search } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  deleteModel,
  listDomains,
  listModels,
  publishModel,
  saveModel,
} from "@/api/warehouse";
const dataTypes = [
    "STRING",
    "INTEGER",
    "LONG",
    "DECIMAL",
    "BOOLEAN",
    "DATE",
    "DATETIME",
  ],
  joins = ["INNER", "LEFT", "RIGHT", "FULL"];
const domains = ref([]),
  domainId = ref(),
  rows = ref([]),
  facts = ref([]),
  metrics = ref([]),
  keyword = ref(""),
  status = ref(""),
  loading = ref(false),
  saving = ref(false),
  visible = ref(false),
  tab = ref("basic"),
  formRef = ref(),
  primarySourceKey = ref(),
  selectedMetricIds = ref([]);
const key = () => `${Date.now()}_${Math.random()}`;
const empty = () => ({
    id: undefined,
    domainId: domainId.value,
    code: "",
    name: "",
    purpose: "",
    description: "",
    defaultFilter: "",
    sources: [],
    joins: [],
    fields: [],
    metrics: [],
  }),
  form = reactive(empty());
const rules = {
  name: [{ required: true, message: "请输入数据集名称" }],
  code: [
    { required: true, message: "请输入数据集编码" },
    { pattern: /^[a-z][a-z0-9_]*$/, message: "仅支持小写字母、数字和下划线" },
  ],
  purpose: [{ required: true, message: "请输入业务用途" }],
};
const code = (v) => (typeof v === "object" ? v?.value : v || "");
const publishedCount = computed(
    () => rows.value.filter((x) => x.status === "PUBLISHED").length,
  ),
  fieldCount = computed(() =>
    rows.value.reduce((n, x) => n + (x.fields?.length || 0), 0),
  ),
  filtered = computed(() =>
    rows.value.filter(
      (x) =>
        (!keyword.value ||
          `${x.datasetName} ${code(x.datasetCode)}`
            .toLowerCase()
            .includes(keyword.value.toLowerCase())) &&
        (!status.value || x.status === status.value),
    ),
  ),
  validSources = computed(() => form.sources.filter((x) => x.alias)),
  publishedMetrics = computed(() =>
    metrics.value.filter((x) => x.status === "PUBLISHED"),
  );
const primaryAlias = (r) =>
  code(r.sources?.find((x) => x.primary)?.sourceAlias);
async function init() {
  domains.value = (await listDomains()) || [];
  if (domains.value.length) {
    domainId.value = domains.value[0].id;
    await load();
  }
}
async function load() {
  loading.value = true;
  try {
    [rows.value, facts.value, metrics.value] = await Promise.all(
      ["datasets", "facts", "metrics"].map((x) =>
        listModels(x, domainId.value),
      ),
    );
  } finally {
    loading.value = false;
  }
}
function normalize(r = {}) {
  return {
    id: r.id,
    domainId: domainId.value,
    code: code(r.datasetCode),
    name: r.datasetName || "",
    purpose: r.purpose || "",
    description: r.description || "",
    defaultFilter: r.defaultFilter || "",
    sources: (r.sources || []).map((x) => ({
      id: x.id,
      localKey: key(),
      alias: code(x.sourceAlias),
      type: x.sourceType,
      targetId: x.targetId,
      physicalTable: x.physicalTable || "",
      primary: x.primary,
    })),
    joins: (r.joins || []).map((x) => ({
      id: x.id,
      code: code(x.joinCode),
      type: x.joinType,
      leftAlias: code(x.leftAlias),
      rightAlias: code(x.rightAlias),
      condition: x.condition || "",
    })),
    fields: (r.fields || []).map((x) => ({
      id: x.id,
      code: code(x.fieldCode),
      name: x.fieldName,
      dataType: x.dataType,
      sourceAlias: code(x.sourceAlias),
      sourceField: x.sourceField,
      visible: x.visible,
      orderNum: x.orderNum,
    })),
    metrics: (r.metrics || []).map((x) => ({ id: x.id, metricId: x.metricId })),
  };
}
function open(r) {
  Object.assign(form, empty(), normalize(r));
  primarySourceKey.value = form.sources.find((x) => x.primary)?.localKey;
  selectedMetricIds.value = form.metrics.map((x) => x.metricId);
  tab.value = "basic";
  visible.value = true;
}
function sourceOptions(type) {
  if (type === "FACT_TABLE")
    return facts.value
      .filter((x) => x.status === "PUBLISHED")
      .map((x) => ({ id: x.id, name: x.factName }));
  return rows.value
    .filter((x) => x.status === "PUBLISHED" && x.id !== form.id)
    .map((x) => ({ id: x.id, name: x.datasetName }));
}
function addSource() {
  form.sources.push({
    localKey: key(),
    alias: "",
    type: "FACT_TABLE",
    targetId: null,
    physicalTable: "",
    primary: false,
  });
}
function sourceTypeChanged(r) {
  r.targetId = null;
  r.physicalTable = "";
}
function removeSource(i) {
  const s = form.sources[i];
  form.sources.splice(i, 1);
  if (primarySourceKey.value === s.localKey) primarySourceKey.value = null;
  form.joins = form.joins.filter(
    (x) => x.leftAlias !== s.alias && x.rightAlias !== s.alias,
  );
  form.fields = form.fields.filter((x) => x.sourceAlias !== s.alias);
}
function addJoin() {
  form.joins.push({
    code: "",
    type: "LEFT",
    leftAlias: "",
    rightAlias: "",
    condition: "",
  });
}
function addField() {
  form.fields.push({
    code: "",
    name: "",
    dataType: "STRING",
    sourceAlias: "",
    sourceField: "",
    visible: true,
    orderNum: form.fields.length + 1,
  });
}
function error() {
  if (!form.sources.length) return "请至少配置一个数据来源";
  const aliases = form.sources.map((x) => x.alias);
  if (
    form.sources.some(
      (x) =>
        !x.alias ||
        (x.type === "PHYSICAL_TABLE" ? !x.physicalTable : !x.targetId),
    )
  )
    return "请完整填写数据来源";
  if (new Set(aliases).size !== aliases.length) return "来源别名不能重复";
  if (!primarySourceKey.value) return "请选择一个主来源";
  for (const j of form.joins) {
    if (!j.code || !j.leftAlias || !j.rightAlias || !j.condition)
      return "请完整填写关联定义";
    if (j.leftAlias === j.rightAlias) return "Join 左右来源不能相同";
    if (!aliases.includes(j.leftAlias) || !aliases.includes(j.rightAlias))
      return "Join 引用了不存在的来源别名";
  }
  if (
    form.fields.some(
      (x) =>
        !x.code ||
        !x.name ||
        !x.dataType ||
        !aliases.includes(x.sourceAlias) ||
        !x.sourceField,
    )
  )
    return "请完整填写字段定义并选择有效来源";
  if (new Set(form.fields.map((x) => x.code)).size !== form.fields.length)
    return "字段编码不能重复";
  return "";
}
async function submit() {
  try {
    await formRef.value.validate();
  } catch {
    return;
  }
  const e = error();
  if (e) return ElMessage.warning(e);
  saving.value = true;
  try {
    const payload = {
      ...form,
      sources: form.sources.map(({ localKey, ...x }) => ({
        ...x,
        primary: localKey === primarySourceKey.value,
      })),
      metrics: selectedMetricIds.value.map((id) => ({
        id: form.metrics.find((x) => x.metricId === id)?.id,
        metricId: id,
      })),
    };
    await saveModel("datasets", payload);
    ElMessage.success("数据集草稿已保存");
    visible.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function publish(r) {
  await ElMessageBox.confirm(`确认发布“${r.datasetName}”？`, "发布确认", {
    type: "warning",
  });
  await publishModel("datasets", r.id);
  await load();
}
async function remove(r) {
  await ElMessageBox.confirm(`确认删除“${r.datasetName}”？`, "删除确认", {
    type: "warning",
  });
  await deleteModel("datasets", r.id);
  await load();
}
onMounted(init);
</script>
<style lang="scss" scoped>
.warehouse-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.warehouse-hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 22px 24px;
}
.hero-main {
  display: flex;
  align-items: center;
  gap: 16px;
}
.hero-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 10px;
  background: var(--ry-gradient-primary);
  color: #fff;
  font-size: 23px;
}
h2 {
  margin: 0 0 6px;
  font-size: 20px;
}
p,
.hint {
  margin: 0;
  color: var(--ry-muted-foreground);
  font-size: 12px;
}
.hero-stats {
  display: flex;
}
.hero-stats div {
  min-width: 90px;
  padding: 0 22px;
  text-align: center;
  border-left: 1px solid var(--ry-border-light);
}
.hero-stats strong {
  display: block;
  font-size: 22px;
}
.ry-search-card {
  margin-bottom: 0;
}
.ry-search-card :deep(.el-form-item) {
  margin-bottom: 0;
}
.code {
  margin-top: 4px;
  color: var(--ry-muted-foreground);
  font: 12px var(--ry-font-mono);
}
.editor-tabs {
  min-height: 480px;
}
.pane-head,
.metric-select {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  padding: 12px;
  border: 1px solid var(--ry-border-light);
  border-radius: 6px;
  background: var(--ry-neutral-50);
  color: var(--ry-muted-foreground);
  font-size: 12px;
}
.metric-select p {
  margin-top: 4px;
}
.el-input-number {
  width: 100%;
}
</style>
