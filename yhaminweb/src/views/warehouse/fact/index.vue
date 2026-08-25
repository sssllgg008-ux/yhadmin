<template>
  <div class="warehouse-page">
    <section class="warehouse-hero ry-card">
      <div class="hero-main">
        <div class="hero-icon">
          <el-icon><Coin /></el-icon>
        </div>
        <div>
          <h2>事实表管理</h2>
          <p>结构化维护事实粒度、字段、维度关联与度量定义。</p>
        </div>
      </div>
      <div class="hero-stats">
        <div>
          <strong>{{ rows.length }}</strong
          ><span>事实表</span>
        </div>
        <div>
          <strong>{{ publishedCount }}</strong
          ><span>已发布</span>
        </div>
        <div>
          <strong>{{ measureCount }}</strong
          ><span>度量</span>
        </div>
      </div>
    </section>
    <div class="ry-card ry-search-card">
      <el-form inline
        ><el-form-item label="业务领域"
          ><el-select v-model="domainId" style="width: 190px" @change="load"
            ><el-option
              v-for="d in domains"
              :key="d.id"
              :label="d.domainName"
              :value="d.id" /></el-select></el-form-item
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
            >新增事实表</el-button
          ><span class="hint"
            >先定义并发布维度，再建立事实字段与维度业务键的映射。</span
          >
        </div>
        <div class="ry-toolbar-right">
          <span class="hint">共 {{ filtered.length }} 项</span
          ><el-button circle :icon="Refresh" @click="load" />
        </div>
      </div>
      <el-table v-loading="loading" :data="filtered" border stripe
        ><el-table-column label="事实表" min-width="230"
          ><template #default="{ row }"
            ><b>{{ row.factName }}</b>
            <div class="code">{{ code(row.factCode) }}</div></template
          ></el-table-column
        ><el-table-column label="类型" width="140"
          ><template #default="{ row }"
            ><el-tag effect="plain">{{
              factTypeText(row.factType)
            }}</el-tag></template
          ></el-table-column
        ><el-table-column prop="physicalTable" label="物理表" min-width="180"
          ><template #default="{ row }">{{
            row.physicalTable || "手工定义"
          }}</template></el-table-column
        ><el-table-column label="字段 / 维度 / 度量" width="170" align="center"
          ><template #default="{ row }"
            >{{ row.columns?.length || 0 }} /
            {{ row.dimensionRefs?.length || 0 }} /
            {{ row.measures?.length || 0 }}</template
          ></el-table-column
        ><el-table-column
          prop="grain"
          label="粒度"
          min-width="180"
          show-overflow-tooltip /><el-table-column
          label="状态"
          width="95"
          align="center"
          ><template #default="{ row }"
            ><el-tag
              :type="row.status === 'PUBLISHED' ? 'success' : 'warning'"
              >{{ row.status === "PUBLISHED" ? "已发布" : "草稿" }}</el-tag
            ></template
          ></el-table-column
        ><el-table-column label="操作" width="210" fixed="right" align="center"
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
        ><template #empty><el-empty description="暂无事实表" /></template
      ></el-table>
    </div>
    <el-dialog
      v-model="visible"
      :title="form.id ? '编辑事实表' : '新增事实表'"
      width="1120px"
      top="3vh"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px"
        ><el-tabs v-model="tab" class="editor-tabs">
          <el-tab-pane label="基础信息" name="basic"
            ><el-row :gutter="18"
              ><el-col :span="12"
                ><el-form-item label="事实表名称" prop="name"
                  ><el-input
                    v-model.trim="form.name"
                    placeholder="例如：订单交易事实表" /></el-form-item></el-col
              ><el-col :span="12"
                ><el-form-item label="事实表编码" prop="code"
                  ><el-input
                    v-model.trim="form.code"
                    :disabled="!!form.id"
                    placeholder="例如：order_transaction" /></el-form-item></el-col></el-row
            ><el-row :gutter="18"
              ><el-col :span="8"
                ><el-form-item label="事实类型" prop="type"
                  ><el-select v-model="form.type" style="width: 100%"
                    ><el-option
                      v-for="x in factTypes"
                      :key="x.value"
                      :label="x.label"
                      :value="x.value" /></el-select></el-form-item></el-col
              ><el-col :span="8"
                ><el-form-item label="粒度" prop="grain"
                  ><el-input
                    v-model.trim="form.grain"
                    placeholder="一行代表一笔订单" /></el-form-item></el-col
              ><el-col :span="8"
                ><el-form-item label="时间字段"
                  ><el-select
                    v-model="form.timeColumnCode"
                    clearable
                    style="width: 100%"
                    ><el-option
                      v-for="x in form.columns"
                      :key="x.code"
                      :label="x.name || x.code"
                      :value="
                        x.code
                      " /></el-select></el-form-item></el-col></el-row
            ><el-form-item label="说明"
              ><el-input v-model="form.description" type="textarea" :rows="3"
            /></el-form-item>
            <div class="section-title">物理来源</div>
            <el-row :gutter="18"
              ><el-col :span="12"
                ><el-form-item label="数据源"
                  ><el-select
                    v-model="form.dataSourceId"
                    clearable
                    filterable
                    style="width: 100%"
                    @change="sourceChanged"
                    ><el-option
                      v-for="x in dataSources"
                      :key="x.id"
                      :label="`${x.name} · ${x.databaseName}`"
                      :value="x.id" /></el-select></el-form-item></el-col
              ><el-col :span="12"
                ><el-form-item label="物理表"
                  ><el-select
                    v-model="form.physicalTable"
                    clearable
                    filterable
                    :disabled="!form.dataSourceId"
                    :loading="tableLoading"
                    style="width: 100%"
                    ><el-option
                      v-for="x in tables"
                      :key="`${x.schema}-${x.tableName}`"
                      :label="
                        x.schema ? `${x.schema}.${x.tableName}` : x.tableName
                      "
                      :value="
                        x.tableName
                      " /></el-select></el-form-item></el-col></el-row
          ></el-tab-pane>
          <el-tab-pane name="columns"
            ><template #label>字段定义 ({{ form.columns.length }})</template>
            <div class="pane-head">
              <span>字段独立保存，可从物理表元数据导入。</span>
              <div>
                <el-button
                  :icon="Download"
                  :disabled="!form.physicalTable"
                  :loading="columnLoading"
                  @click="importColumns"
                  >导入字段</el-button
                ><el-button type="primary" plain :icon="Plus" @click="addColumn"
                  >新增字段</el-button
                >
              </div>
            </div>
            <el-table :data="form.columns" border max-height="430"
              ><el-table-column label="字段编码" min-width="140"
                ><template #default="{ row }"
                  ><el-input
                    v-model.trim="row.code" /></template></el-table-column
              ><el-table-column label="字段名称" min-width="140"
                ><template #default="{ row }"
                  ><el-input
                    v-model.trim="row.name" /></template></el-table-column
              ><el-table-column label="逻辑类型" width="140"
                ><template #default="{ row }"
                  ><el-select v-model="row.dataType"
                    ><el-option
                      v-for="x in dataTypes"
                      :key="x"
                      :label="x"
                      :value="x" /></el-select></template></el-table-column
              ><el-table-column label="精度" width="90"
                ><template #default="{ row }"
                  ><el-input-number
                    v-model="row.precision"
                    :min="0"
                    controls-position="right" /></template></el-table-column
              ><el-table-column label="小数位" width="90"
                ><template #default="{ row }"
                  ><el-input-number
                    v-model="row.scale"
                    :min="0"
                    controls-position="right" /></template></el-table-column
              ><el-table-column label="可空" width="70" align="center"
                ><template #default="{ row }"
                  ><el-switch
                    v-model="row.nullable" /></template></el-table-column
              ><el-table-column label="说明" min-width="140"
                ><template #default="{ row }"
                  ><el-input
                    v-model="row.description" /></template></el-table-column
              ><el-table-column label="操作" width="65"
                ><template #default="{ $index }"
                  ><el-button link type="danger" @click="removeColumn($index)"
                    >删除</el-button
                  ></template
                ></el-table-column
              ></el-table
            ></el-tab-pane
          >
          <el-tab-pane name="dimensions"
            ><template #label
              >维度关联 ({{ form.dimensionRefs.length }})</template
            >
            <div class="pane-head">
              <span>事实字段必须与维度业务键的数据类型兼容。</span
              ><el-button
                type="primary"
                plain
                :icon="Plus"
                @click="addDimensionRef"
                >新增关联</el-button
              >
            </div>
            <el-table :data="form.dimensionRefs" border
              ><el-table-column label="关联编码"
                ><template #default="{ row }"
                  ><el-input
                    v-model.trim="row.code"
                    placeholder="customer_ref" /></template></el-table-column
              ><el-table-column label="维度"
                ><template #default="{ row }"
                  ><el-select
                    v-model="row.dimensionId"
                    filterable
                    @change="dimensionChanged(row)"
                    ><el-option
                      v-for="x in publishedDimensions"
                      :key="x.id"
                      :label="x.dimensionName"
                      :value="x.id" /></el-select></template></el-table-column
              ><el-table-column label="事实字段"
                ><template #default="{ row }"
                  ><el-select v-model="row.factColumnCode" filterable
                    ><el-option
                      v-for="x in form.columns"
                      :key="x.code"
                      :label="`${x.name} (${x.code})`"
                      :value="x.code" /></el-select></template></el-table-column
              ><el-table-column label="维度业务键"
                ><template #default="{ row }"
                  ><el-input
                    :model-value="dimensionKey(row.dimensionId)"
                    disabled /></template></el-table-column
              ><el-table-column label="操作" width="65"
                ><template #default="{ $index }"
                  ><el-button
                    link
                    type="danger"
                    @click="form.dimensionRefs.splice($index, 1)"
                    >删除</el-button
                  ></template
                ></el-table-column
              ></el-table
            ></el-tab-pane
          >
          <el-tab-pane name="measures"
            ><template #label>度量定义 ({{ form.measures.length }})</template>
            <div class="pane-head">
              <span>度量引用事实字段，并定义默认聚合方式。</span
              ><el-button type="primary" plain :icon="Plus" @click="addMeasure"
                >新增度量</el-button
              >
            </div>
            <el-table :data="form.measures" border
              ><el-table-column label="度量编码"
                ><template #default="{ row }"
                  ><el-input
                    v-model.trim="row.code" /></template></el-table-column
              ><el-table-column label="度量名称"
                ><template #default="{ row }"
                  ><el-input
                    v-model.trim="row.name" /></template></el-table-column
              ><el-table-column label="来源字段"
                ><template #default="{ row }"
                  ><el-select v-model="row.columnCode" filterable
                    ><el-option
                      v-for="x in numericColumns"
                      :key="x.code"
                      :label="`${x.name} (${x.code})`"
                      :value="x.code" /></el-select></template></el-table-column
              ><el-table-column label="聚合方式"
                ><template #default="{ row }"
                  ><el-select v-model="row.aggregationType"
                    ><el-option
                      v-for="x in aggregations"
                      :key="x"
                      :label="x"
                      :value="x" /></el-select></template></el-table-column
              ><el-table-column label="单位"
                ><template #default="{ row }"
                  ><el-input
                    v-model.trim="row.unit" /></template></el-table-column
              ><el-table-column label="操作" width="65"
                ><template #default="{ $index }"
                  ><el-button
                    link
                    type="danger"
                    @click="form.measures.splice($index, 1)"
                    >删除</el-button
                  ></template
                ></el-table-column
              ></el-table
            ></el-tab-pane
          >
        </el-tabs></el-form
      ><template #footer
        ><el-button @click="visible = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="submit"
          >保存草稿</el-button
        ></template
      >
    </el-dialog>
  </div>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { Coin, Download, Plus, Refresh, Search } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  deleteModel,
  listDataSourceColumns,
  listDataSources,
  listDataSourceTables,
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
  numericTypes = ["INTEGER", "LONG", "DECIMAL"],
  aggregations = ["SUM", "COUNT", "AVG", "MIN", "MAX", "DISTINCT_COUNT"];
const factTypes = [
  { label: "事务事实", value: "TRANSACTION" },
  { label: "周期快照", value: "PERIODIC_SNAPSHOT" },
  { label: "累计快照", value: "ACCUMULATING_SNAPSHOT" },
  { label: "无事实度量", value: "FACTLESS" },
];
const domains = ref([]),
  domainId = ref(),
  rows = ref([]),
  dimensions = ref([]),
  dataSources = ref([]),
  tables = ref([]),
  keyword = ref(""),
  status = ref(""),
  loading = ref(false),
  saving = ref(false),
  visible = ref(false),
  tab = ref("basic"),
  formRef = ref(),
  tableLoading = ref(false),
  columnLoading = ref(false);
const empty = () => ({
  id: undefined,
  domainId: domainId.value,
  dataSourceId: null,
  code: "",
  name: "",
  physicalTable: "",
  type: "TRANSACTION",
  grain: "",
  timeColumnCode: null,
  description: "",
  columns: [],
  dimensionRefs: [],
  measures: [],
});
const form = reactive(empty());
const rules = {
  name: [{ required: true, message: "请输入事实表名称" }],
  code: [
    { required: true, message: "请输入事实表编码" },
    { pattern: /^[a-z][a-z0-9_]*$/, message: "仅支持小写字母、数字和下划线" },
  ],
  type: [{ required: true, message: "请选择事实类型" }],
  grain: [{ required: true, message: "请描述事实粒度" }],
};
const code = (v) => (typeof v === "object" ? v?.value : v || "");
const publishedCount = computed(
    () => rows.value.filter((x) => x.status === "PUBLISHED").length,
  ),
  measureCount = computed(() =>
    rows.value.reduce((n, x) => n + (x.measures?.length || 0), 0),
  ),
  filtered = computed(() =>
    rows.value.filter(
      (x) =>
        (!keyword.value ||
          `${x.factName} ${code(x.factCode)}`
            .toLowerCase()
            .includes(keyword.value.toLowerCase())) &&
        (!status.value || x.status === status.value),
    ),
  ),
  publishedDimensions = computed(() =>
    dimensions.value.filter((x) => x.status === "PUBLISHED"),
  ),
  numericColumns = computed(() =>
    form.columns.filter((x) => numericTypes.includes(x.dataType)),
  );
const factTypeText = (v) => factTypes.find((x) => x.value === v)?.label || v;
async function init() {
  const [a, b] = await Promise.all([listDomains(), listDataSources()]);
  domains.value = a || [];
  dataSources.value = (b || []).filter((x) => x.enabled);
  if (domains.value.length) {
    domainId.value = domains.value[0].id;
    await load();
  }
}
async function load() {
  if (!domainId.value) return;
  loading.value = true;
  try {
    [rows.value, dimensions.value] = await Promise.all([
      listModels("facts", domainId.value),
      listModels("dimensions", domainId.value),
    ]);
  } finally {
    loading.value = false;
  }
}
function normalize(r = {}) {
  return {
    id: r.id,
    domainId: domainId.value,
    dataSourceId: r.dataSourceId || null,
    code: code(r.factCode),
    name: r.factName || "",
    physicalTable: r.physicalTable || "",
    type: r.factType || "TRANSACTION",
    grain: r.grain || "",
    timeColumnCode: code(r.timeColumnCode) || null,
    description: r.description || "",
    columns: (r.columns || []).map((x) => ({
      id: x.id,
      code: code(x.columnCode),
      name: x.columnName,
      dataType: x.dataType,
      precision: x.precision,
      scale: x.scale,
      nullable: x.nullable,
      description: x.description || "",
    })),
    dimensionRefs: (r.dimensionRefs || []).map((x) => ({
      id: x.id,
      code: code(x.refCode),
      dimensionId: x.dimensionId,
      factColumnCode: code(x.factColumnCode),
      dimensionKeyCode: code(x.dimensionKeyCode),
    })),
    measures: (r.measures || []).map((x) => ({
      id: x.id,
      code: code(x.measureCode),
      name: x.measureName,
      columnCode: code(x.columnCode),
      aggregationType: x.aggregationType,
      unit: x.unit || "",
    })),
  };
}
function open(r) {
  Object.assign(form, empty(), normalize(r));
  tab.value = "basic";
  visible.value = true;
  tables.value = [];
  if (form.dataSourceId) sourceChanged(form.dataSourceId, false);
}
async function sourceChanged(id, clear = true) {
  if (clear) form.physicalTable = "";
  tables.value = [];
  if (!id) return;
  const s = dataSources.value.find((x) => x.id === id);
  tableLoading.value = true;
  try {
    tables.value =
      (await listDataSourceTables(id, {
        catalog: s?.databaseName,
        schema: s?.schemaName,
      })) || [];
  } finally {
    tableLoading.value = false;
  }
}
function addColumn() {
  form.columns.push({
    code: "",
    name: "",
    dataType: "STRING",
    precision: null,
    scale: null,
    nullable: true,
    description: "",
  });
}
function removeColumn(i) {
  const c = form.columns[i].code;
  form.columns.splice(i, 1);
  if (form.timeColumnCode === c) form.timeColumnCode = null;
  form.dimensionRefs = form.dimensionRefs.filter((x) => x.factColumnCode !== c);
  form.measures = form.measures.filter((x) => x.columnCode !== c);
}
async function importColumns() {
  const s = dataSources.value.find((x) => x.id === form.dataSourceId);
  columnLoading.value = true;
  try {
    const cols =
        (await listDataSourceColumns(form.dataSourceId, {
          catalog: s?.databaseName,
          schema: s?.schemaName,
          table: form.physicalTable,
        })) || [],
      seen = new Set(form.columns.map((x) => x.code));
    cols.forEach((x) => {
      const c = x.columnName.toLowerCase();
      if (!seen.has(c))
        form.columns.push({
          code: c,
          name: x.remarks || x.columnName,
          dataType: x.dataType || "STRING",
          precision: x.columnSize || null,
          scale: x.decimalDigits || null,
          nullable: x.nullable !== false,
          description: x.remarks || "",
        });
    });
    ElMessage.success(`已导入 ${cols.length} 个字段`);
  } finally {
    columnLoading.value = false;
  }
}
function dimensionKey(id) {
  const d = dimensions.value.find((x) => x.id === id);
  return code(d?.keyAttributeCode);
}
function dimensionChanged(r) {
  r.dimensionKeyCode = dimensionKey(r.dimensionId);
}
function addDimensionRef() {
  form.dimensionRefs.push({
    code: "",
    dimensionId: null,
    factColumnCode: "",
    dimensionKeyCode: "",
  });
}
function addMeasure() {
  form.measures.push({
    code: "",
    name: "",
    columnCode: "",
    aggregationType: "SUM",
    unit: "",
  });
}
function structuralError() {
  if (!form.columns.length) return "请至少定义一个事实字段";
  if (form.columns.some((x) => !x.code || !x.name || !x.dataType))
    return "请完整填写字段定义";
  if (new Set(form.columns.map((x) => x.code)).size !== form.columns.length)
    return "字段编码不能重复";
  for (const r of form.dimensionRefs) {
    if (!r.code || !r.dimensionId || !r.factColumnCode)
      return "请完整填写维度关联";
    const d = dimensions.value.find((x) => x.id === r.dimensionId),
      a = d?.attributes?.find(
        (x) => code(x.attributeCode) === dimensionKey(r.dimensionId),
      ),
      c = form.columns.find((x) => x.code === r.factColumnCode);
    if (
      a &&
      c &&
      a.dataType !== c.dataType &&
      !(numericTypes.includes(a.dataType) && numericTypes.includes(c.dataType))
    )
      return `维度“${d.dimensionName}”业务键与事实字段类型不兼容`;
  }
  if (
    form.measures.some(
      (x) => !x.code || !x.name || !x.columnCode || !x.aggregationType,
    )
  )
    return "请完整填写度量定义";
  return "";
}
async function submit() {
  try {
    await formRef.value.validate();
  } catch {
    tab.value = "basic";
    return;
  }
  const e = structuralError();
  if (e) {
    ElMessage.warning(e);
    return;
  }
  saving.value = true;
  try {
    await saveModel("facts", { ...form });
    ElMessage.success("事实表草稿已保存");
    visible.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function publish(r) {
  await ElMessageBox.confirm(`确认发布“${r.factName}”？`, "发布确认", {
    type: "warning",
  });
  await publishModel("facts", r.id);
  await load();
}
async function remove(r) {
  await ElMessageBox.confirm(`确认删除“${r.factName}”？`, "删除确认", {
    type: "warning",
  });
  await deleteModel("facts", r.id);
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
  min-height: 500px;
}
.pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding: 11px 13px;
  border: 1px solid var(--ry-border-light);
  border-radius: 6px;
  background: var(--ry-neutral-50);
  color: var(--ry-muted-foreground);
  font-size: 12px;
}
.section-title {
  margin: 10px 0 18px;
  padding-left: 8px;
  border-left: 3px solid var(--ry-primary);
  font-weight: 600;
}
.el-input-number {
  width: 100%;
}
</style>
