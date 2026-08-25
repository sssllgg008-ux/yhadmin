<template>
  <div class="warehouse-page">
    <section class="warehouse-hero ry-card">
      <div class="hero-main">
        <div class="hero-icon">
          <el-icon><DataAnalysis /></el-icon>
        </div>
        <div>
          <h2>指标管理</h2>
          <p>结构化维护指标口径、计算表达式、统计周期与模型依赖。</p>
        </div>
      </div>
      <div class="hero-stats">
        <div>
          <strong>{{ rows.length }}</strong
          ><span>指标</span>
        </div>
        <div>
          <strong>{{ publishedCount }}</strong
          ><span>已发布</span>
        </div>
        <div>
          <strong>{{ dependencyCount }}</strong
          ><span>依赖</span>
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
            >新增指标</el-button
          ><span class="hint"
            >指标表达式与依赖分别维护，不提交 JSON 定义。</span
          >
        </div>
        <div class="ry-toolbar-right">
          <span class="hint">共 {{ filtered.length }} 项</span
          ><el-button circle :icon="Refresh" @click="load" />
        </div>
      </div>
      <el-table v-loading="loading" :data="filtered" border stripe
        ><el-table-column label="指标" min-width="230"
          ><template #default="{ row }"
            ><b>{{ row.metricName }}</b>
            <div class="code">{{ code(row.metricCode) }}</div></template
          ></el-table-column
        ><el-table-column label="类型" width="110"
          ><template #default="{ row }"
            ><el-tag effect="plain">{{
              typeText(row.metricType)
            }}</el-tag></template
          ></el-table-column
        ><el-table-column label="事实表" min-width="160"
          ><template #default="{ row }">{{
            factName(row.factTableId) || "—"
          }}</template></el-table-column
        ><el-table-column
          prop="expression"
          label="表达式"
          min-width="200"
          show-overflow-tooltip
        /><el-table-column label="聚合" width="105"
          ><template #default="{ row }">{{
            row.aggregationType || "—"
          }}</template></el-table-column
        ><el-table-column label="依赖" width="75" align="center"
          ><template #default="{ row }">{{
            row.dependencies?.length || 0
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
      :title="form.id ? '编辑指标' : '新增指标'"
      width="1000px"
      top="4vh"
      :close-on-click-modal="false"
      ><el-form ref="formRef" :model="form" :rules="rules" label-width="105px"
        ><el-tabs v-model="tab" class="editor-tabs"
          ><el-tab-pane label="指标定义" name="basic"
            ><el-row :gutter="18"
              ><el-col :span="12"
                ><el-form-item label="指标名称" prop="name"
                  ><el-input
                    v-model.trim="form.name"
                    placeholder="例如：销售金额" /></el-form-item></el-col
              ><el-col :span="12"
                ><el-form-item label="指标编码" prop="code"
                  ><el-input
                    v-model.trim="form.code"
                    :disabled="!!form.id"
                    placeholder="sales_amount" /></el-form-item></el-col></el-row
            ><el-row :gutter="18"
              ><el-col :span="8"
                ><el-form-item label="指标类型" prop="type"
                  ><el-select v-model="form.type" style="width: 100%"
                    ><el-option
                      v-for="x in metricTypes"
                      :key="x.value"
                      :label="x.label"
                      :value="x.value" /></el-select></el-form-item></el-col
              ><el-col :span="8"
                ><el-form-item label="所属事实表"
                  ><el-select
                    v-model="form.factTableId"
                    clearable
                    filterable
                    style="width: 100%"
                    ><el-option
                      v-for="x in publishedFacts"
                      :key="x.id"
                      :label="x.factName"
                      :value="x.id" /></el-select></el-form-item></el-col
              ><el-col :span="8"
                ><el-form-item label="聚合方式"
                  ><el-select
                    v-model="form.aggregationType"
                    clearable
                    style="width: 100%"
                    ><el-option
                      v-for="x in aggregations"
                      :key="x"
                      :label="x"
                      :value="x" /></el-select></el-form-item></el-col></el-row
            ><el-row :gutter="18"
              ><el-col :span="8"
                ><el-form-item label="单位"
                  ><el-input
                    v-model.trim="form.unit"
                    placeholder="元 / 人 / %" /></el-form-item></el-col
              ><el-col :span="8"
                ><el-form-item label="显示格式"
                  ><el-input
                    v-model.trim="form.format"
                    placeholder="#,##0.00" /></el-form-item></el-col
              ><el-col :span="8"
                ><el-form-item label="统计周期"
                  ><el-input
                    v-model.trim="form.statisticalPeriod"
                    placeholder="DAY / MONTH / YEAR" /></el-form-item></el-col></el-row
            ><el-form-item label="时间维度"
              ><el-select
                v-model="form.timeDimensionId"
                clearable
                filterable
                style="width: 100%"
                ><el-option
                  v-for="x in timeDimensions"
                  :key="x.id"
                  :label="x.dimensionName"
                  :value="x.id" /></el-select></el-form-item
            ><el-form-item label="说明"
              ><el-input
                v-model="form.description"
                type="textarea"
                :rows="3" /></el-form-item
          ></el-tab-pane>
          <el-tab-pane label="计算口径" name="formula"
            ><div class="formula-tip">
              直接填写 SQL/DSL
              计算表达式；依赖模型请在“依赖关系”页签选择，系统将保存明确的模型引用。
            </div>
            <el-form-item label="计算表达式" prop="expression"
              ><el-input
                v-model="form.expression"
                type="textarea"
                :rows="8"
                placeholder="例如：SUM(order_amount)"
                class="formula-input" /></el-form-item
            ><el-form-item label="过滤条件"
              ><el-input
                v-model="form.filterExpression"
                type="textarea"
                :rows="4"
                placeholder="例如：order_status = 'PAID'" /></el-form-item
          ></el-tab-pane>
          <el-tab-pane name="dependencies"
            ><template #label
              >依赖关系 ({{ form.dependencies.length }})</template
            >
            <div class="pane-head">
              <span>选择实际参与计算的事实度量、维度属性或其他指标。</span
              ><el-button
                type="primary"
                plain
                :icon="Plus"
                @click="addDependency"
                >新增依赖</el-button
              >
            </div>
            <el-table :data="form.dependencies" border
              ><el-table-column label="依赖类型" width="160"
                ><template #default="{ row }"
                  ><el-select
                    v-model="row.type"
                    @change="dependencyTypeChanged(row)"
                    ><el-option
                      label="事实度量"
                      value="FACT_MEASURE" /><el-option
                      label="维度属性"
                      value="DIMENSION_ATTRIBUTE" /><el-option
                      label="其他指标"
                      value="METRIC" /></el-select></template></el-table-column
              ><el-table-column label="依赖对象"
                ><template #default="{ row }"
                  ><el-select
                    v-model="row.selector"
                    filterable
                    style="width: 100%"
                    @change="dependencySelected(row)"
                    ><el-option
                      v-for="x in dependencyOptions(row.type)"
                      :key="x.key"
                      :label="x.label"
                      :value="x.key" /></el-select></template></el-table-column
              ><el-table-column label="目标编码"
                ><template #default="{ row }"
                  ><el-input
                    v-model="row.targetCode"
                    disabled /></template></el-table-column
              ><el-table-column label="操作" width="70"
                ><template #default="{ $index }"
                  ><el-button
                    link
                    type="danger"
                    @click="form.dependencies.splice($index, 1)"
                    >删除</el-button
                  ></template
                ></el-table-column
              ></el-table
            ></el-tab-pane
          ></el-tabs
        ></el-form
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
import { DataAnalysis, Plus, Refresh, Search } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  deleteModel,
  listDomains,
  listModels,
  publishModel,
  saveModel,
} from "@/api/warehouse";
const metricTypes = [
    { label: "原子指标", value: "ATOMIC" },
    { label: "派生指标", value: "DERIVED" },
    { label: "复合指标", value: "COMPOSITE" },
    { label: "累计指标", value: "CUMULATIVE" },
  ],
  aggregations = ["SUM", "COUNT", "AVG", "MIN", "MAX", "DISTINCT_COUNT"];
const domains = ref([]),
  domainId = ref(),
  rows = ref([]),
  facts = ref([]),
  dimensions = ref([]),
  keyword = ref(""),
  status = ref(""),
  loading = ref(false),
  saving = ref(false),
  visible = ref(false),
  tab = ref("basic"),
  formRef = ref();
const empty = () => ({
    id: undefined,
    domainId: domainId.value,
    code: "",
    name: "",
    type: "ATOMIC",
    factTableId: null,
    expression: "",
    aggregationType: "SUM",
    filterExpression: "",
    unit: "",
    format: "",
    timeDimensionId: null,
    statisticalPeriod: "",
    description: "",
    dependencies: [],
  }),
  form = reactive(empty());
const rules = {
  name: [{ required: true, message: "请输入指标名称" }],
  code: [
    { required: true, message: "请输入指标编码" },
    { pattern: /^[a-z][a-z0-9_]*$/, message: "仅支持小写字母、数字和下划线" },
  ],
  type: [{ required: true, message: "请选择指标类型" }],
  expression: [{ required: true, message: "请输入计算表达式" }],
};
const code = (v) => (typeof v === "object" ? v?.value : v || "");
const publishedCount = computed(
    () => rows.value.filter((x) => x.status === "PUBLISHED").length,
  ),
  dependencyCount = computed(() =>
    rows.value.reduce((n, x) => n + (x.dependencies?.length || 0), 0),
  ),
  filtered = computed(() =>
    rows.value.filter(
      (x) =>
        (!keyword.value ||
          `${x.metricName} ${code(x.metricCode)}`
            .toLowerCase()
            .includes(keyword.value.toLowerCase())) &&
        (!status.value || x.status === status.value),
    ),
  ),
  publishedFacts = computed(() =>
    facts.value.filter((x) => x.status === "PUBLISHED"),
  ),
  timeDimensions = computed(() =>
    dimensions.value.filter(
      (x) => x.status === "PUBLISHED" && x.dimensionType === "TIME",
    ),
  );
const factName = (id) => facts.value.find((x) => x.id === id)?.factName,
  typeText = (v) => metricTypes.find((x) => x.value === v)?.label || v;
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
    [rows.value, facts.value, dimensions.value] = await Promise.all(
      ["metrics", "facts", "dimensions"].map((x) =>
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
    code: code(r.metricCode),
    name: r.metricName || "",
    type: r.metricType || "ATOMIC",
    factTableId: r.factTableId || null,
    expression: r.expression === "{}" ? "" : r.expression || "",
    aggregationType: r.aggregationType || "SUM",
    filterExpression: r.filterExpression || "",
    unit: r.unit || "",
    format: r.format || "",
    timeDimensionId: r.timeDimensionId || null,
    statisticalPeriod: r.statisticalPeriod || "",
    description: r.description || "",
    dependencies: (r.dependencies || []).map((x) => ({
      id: x.id,
      type: x.dependencyType,
      targetId: x.targetId,
      targetCode: x.targetCode,
      selector: `${x.targetId}|${x.targetCode}`,
    })),
  };
}
function open(r) {
  Object.assign(form, empty(), normalize(r));
  tab.value = "basic";
  visible.value = true;
}
function dependencyOptions(type) {
  if (type === "FACT_MEASURE")
    return facts.value.flatMap((f) =>
      (f.measures || []).map((m) => ({
        key: `${m.id}|${code(m.measureCode)}`,
        targetId: m.id,
        targetCode: code(m.measureCode),
        label: `${f.factName} / ${m.measureName}`,
      })),
    );
  if (type === "DIMENSION_ATTRIBUTE")
    return dimensions.value.flatMap((d) =>
      (d.attributes || []).map((a) => ({
        key: `${a.id}|${code(a.attributeCode)}`,
        targetId: a.id,
        targetCode: code(a.attributeCode),
        label: `${d.dimensionName} / ${a.attributeName}`,
      })),
    );
  return rows.value
    .filter((x) => x.id !== form.id)
    .map((x) => ({
      key: `${x.id}|${code(x.metricCode)}`,
      targetId: x.id,
      targetCode: code(x.metricCode),
      label: x.metricName,
    }));
}
function addDependency() {
  form.dependencies.push({
    type: "FACT_MEASURE",
    selector: "",
    targetId: null,
    targetCode: "",
  });
}
function dependencyTypeChanged(r) {
  r.selector = "";
  r.targetId = null;
  r.targetCode = "";
}
function dependencySelected(r) {
  const x = dependencyOptions(r.type).find((x) => x.key === r.selector);
  if (x) {
    r.targetId = x.targetId;
    r.targetCode = x.targetCode;
  }
}
function error() {
  if (
    ["DERIVED", "COMPOSITE", "CUMULATIVE"].includes(form.type) &&
    !form.dependencies.length
  )
    return "派生、复合或累计指标必须配置依赖";
  if (form.dependencies.some((x) => !x.targetId || !x.targetCode))
    return "请完整选择依赖对象";
  const keys = form.dependencies.map(
    (x) => `${x.type}:${x.targetId}:${x.targetCode}`,
  );
  if (new Set(keys).size !== keys.length) return "指标依赖不能重复";
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
    await saveModel("metrics", {
      ...form,
      dependencies: form.dependencies.map(({ selector, ...x }) => x),
    });
    ElMessage.success("指标草稿已保存");
    visible.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function publish(r) {
  await ElMessageBox.confirm(`确认发布“${r.metricName}”？`, "发布确认", {
    type: "warning",
  });
  await publishModel("metrics", r.id);
  await load();
}
async function remove(r) {
  await ElMessageBox.confirm(`确认删除“${r.metricName}”？`, "删除确认", {
    type: "warning",
  });
  await deleteModel("metrics", r.id);
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
  min-height: 470px;
}
.pane-head,
.formula-tip {
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
.formula-input :deep(textarea) {
  font-family: var(--ry-font-mono);
}
</style>
