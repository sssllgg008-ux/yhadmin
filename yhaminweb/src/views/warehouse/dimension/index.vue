<template>
  <div class="warehouse-page">
    <section class="warehouse-hero ry-card">
      <div class="hero-main">
        <div class="hero-icon">
          <el-icon><Files /></el-icon>
        </div>
        <div>
          <h2>维度管理</h2>
          <p>以结构化方式维护维度、属性、业务键、显示属性及分析层级。</p>
        </div>
      </div>
      <div class="hero-stats">
        <div>
          <strong>{{ rows.length }}</strong
          ><span>维度总数</span>
        </div>
        <div>
          <strong>{{ publishedCount }}</strong
          ><span>已发布</span>
        </div>
        <div>
          <strong>{{ attributeCount }}</strong
          ><span>属性总数</span>
        </div>
      </div>
    </section>

    <div class="ry-card ry-search-card">
      <el-form inline @submit.prevent>
        <el-form-item label="业务领域">
          <el-select
            v-model="domainId"
            placeholder="请选择业务领域"
            style="width: 200px"
            @change="load"
          >
            <el-option
              v-for="item in domains"
              :key="item.id"
              :label="item.domainName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词"
          ><el-input
            v-model.trim="keyword"
            :prefix-icon="Search"
            placeholder="名称 / 编码"
            clearable
            style="width: 210px"
        /></el-form-item>
        <el-form-item label="状态"
          ><el-select
            v-model="status"
            clearable
            placeholder="全部状态"
            style="width: 130px"
            ><el-option label="草稿" value="DRAFT" /><el-option
              label="已发布"
              value="PUBLISHED" /><el-option
              label="已停用"
              value="DISABLED" /></el-select
        ></el-form-item>
        <el-form-item
          ><el-button :icon="Refresh" @click="reset"
            >重置</el-button
          ></el-form-item
        >
      </el-form>
    </div>

    <div class="ry-card ry-table-card">
      <div class="ry-toolbar">
        <div class="ry-toolbar-left">
          <el-button
            type="primary"
            :icon="Plus"
            :disabled="!domainId"
            @click="add"
            >新增维度</el-button
          ><span class="toolbar-hint"
            >维度结构使用独立属性、层级和级别维护，不使用 JSON 编辑。</span
          >
        </div>
        <div class="ry-toolbar-right">
          <span class="result-count">共 {{ filteredRows.length }} 项</span
          ><el-tooltip content="刷新"
            ><el-button circle :icon="Refresh" @click="load"
          /></el-tooltip>
        </div>
      </div>
      <el-table v-loading="loading" :data="filteredRows" border stripe>
        <el-table-column label="维度" min-width="240"
          ><template #default="{ row }"
            ><div class="dimension-cell">
              <div class="dimension-icon">
                <el-icon><Files /></el-icon>
              </div>
              <div>
                <div class="dimension-name">{{ row.dimensionName }}</div>
                <div class="dimension-code">
                  {{ modelCode(row.dimensionCode) }}
                </div>
              </div>
            </div></template
          ></el-table-column
        >
        <el-table-column label="类型" width="110" align="center"
          ><template #default="{ row }"
            ><el-tag effect="plain">{{
              typeText(row.dimensionType)
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column label="物理来源" min-width="190" show-overflow-tooltip
          ><template #default="{ row }"
            ><span class="muted">{{
              row.sourceTable || "手工定义"
            }}</span></template
          ></el-table-column
        >
        <el-table-column label="属性 / 层级" width="130" align="center"
          ><template #default="{ row }"
            ><b>{{ row.attributes?.length || 0 }}</b
            ><span class="muted">
              / {{ row.hierarchies?.length || 0 }}</span
            ></template
          ></el-table-column
        >
        <el-table-column label="业务键" width="130"
          ><template #default="{ row }">{{
            modelCode(row.keyAttributeCode) || "—"
          }}</template></el-table-column
        >
        <el-table-column label="状态" width="100" align="center"
          ><template #default="{ row }"
            ><el-tag :type="statusType(row.status)" round>{{
              statusText(row.status)
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column label="版本" width="75" align="center"
          ><template #default="{ row }"
            >v{{ row.version || 1 }}</template
          ></el-table-column
        >
        <el-table-column label="操作" width="210" fixed="right" align="center"
          ><template #default="{ row }"
            ><el-button
              link
              type="primary"
              :disabled="row.status !== 'DRAFT'"
              @click="edit(row)"
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
        >
        <template #empty
          ><el-empty
            :description="
              domainId ? '暂无维度，请点击新增维度' : '请先选择业务领域'
            "
            :image-size="88"
        /></template>
      </el-table>
    </div>

    <el-dialog
      v-model="visible"
      :title="form.id ? '编辑维度' : '新增维度'"
      width="1100px"
      top="3vh"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-tabs v-model="activeTab" class="dimension-tabs">
          <el-tab-pane label="基础信息" name="basic">
            <div class="section-title">基本定义</div>
            <el-row :gutter="18">
              <el-col :span="12"
                ><el-form-item label="维度名称" prop="name"
                  ><el-input
                    v-model.trim="form.name"
                    placeholder="例如：客户维度" /></el-form-item
              ></el-col>
              <el-col :span="12"
                ><el-form-item prop="code"
                  ><template #label
                    ><span class="field-label"
                      >维度编码<el-tooltip
                        content="领域内唯一，保存后不可修改；以小写字母开头，仅使用小写字母、数字和下划线"
                        ><el-icon class="field-help"
                          ><QuestionFilled /></el-icon></el-tooltip></span></template
                  ><el-input
                    v-model.trim="form.code"
                    :disabled="!!form.id"
                    placeholder="例如：customer" /></el-form-item
              ></el-col>
            </el-row>
            <el-row :gutter="18">
              <el-col :span="8"
                ><el-form-item label="维度类型" prop="type"
                  ><el-select v-model="form.type" style="width: 100%"
                    ><el-option label="标准维度" value="STANDARD" /><el-option
                      label="时间维度"
                      value="TIME" /><el-option
                      label="退化维度"
                      value="DEGENERATE" /></el-select></el-form-item
              ></el-col>
              <el-col :span="8"
                ><el-form-item label="历史策略"
                  ><el-select v-model="form.scdType" style="width: 100%"
                    ><el-option label="不保留历史" value="NONE" /><el-option
                      label="SCD1 覆盖更新"
                      value="SCD1" /><el-option
                      label="SCD2 保留历史"
                      value="SCD2" /></el-select></el-form-item
              ></el-col>
              <el-col :span="8"
                ><el-form-item label="所属领域"
                  ><el-input
                    :model-value="currentDomain?.domainName"
                    disabled /></el-form-item
              ></el-col>
            </el-row>
            <el-form-item label="说明"
              ><el-input
                v-model="form.description"
                type="textarea"
                :rows="3"
                placeholder="描述维度的业务含义和使用范围"
            /></el-form-item>
            <div class="section-title">
              物理来源 <small>可选；选择物理表后可在“属性定义”中导入字段</small>
            </div>
            <el-row :gutter="18">
              <el-col :span="12"
                ><el-form-item label="数据源"
                  ><el-select
                    v-model="form.dataSourceId"
                    clearable
                    filterable
                    placeholder="手工定义或选择数据源"
                    style="width: 100%"
                    @change="sourceChanged"
                    ><el-option
                      v-for="item in dataSources"
                      :key="item.id"
                      :label="`${item.name} · ${item.databaseName}`"
                      :value="item.id" /></el-select></el-form-item
              ></el-col>
              <el-col :span="12"
                ><el-form-item label="物理表"
                  ><el-select
                    v-model="form.sourceTable"
                    clearable
                    filterable
                    :loading="tableLoading"
                    :disabled="!form.dataSourceId"
                    placeholder="请选择物理表"
                    style="width: 100%"
                    ><el-option
                      v-for="item in sourceTables"
                      :key="`${item.schema}-${item.tableName}`"
                      :label="
                        item.schema
                          ? `${item.schema}.${item.tableName}`
                          : item.tableName
                      "
                      :value="item.tableName" /></el-select></el-form-item
              ></el-col>
            </el-row>
          </el-tab-pane>

          <el-tab-pane name="attributes">
            <template #label
              >属性定义 <el-badge :value="form.attributes.length" :max="99"
            /></template>
            <div class="pane-toolbar">
              <div>
                <b>维度属性</b>
                <p>
                  每个属性独立保存；业务键用于唯一识别维度成员，显示属性用于界面展示。
                </p>
              </div>
              <div>
                <el-button
                  :icon="Download"
                  :disabled="!form.dataSourceId || !form.sourceTable"
                  :loading="columnLoading"
                  @click="importColumns"
                  >从物理表导入</el-button
                ><el-button
                  type="primary"
                  plain
                  :icon="Plus"
                  @click="addAttribute"
                  >新增属性</el-button
                >
              </div>
            </div>
            <el-table :data="form.attributes" border max-height="430">
              <el-table-column label="属性编码" min-width="155"
                ><template #default="{ row }"
                  ><el-input
                    v-model.trim="row.code"
                    placeholder="customer_id" /></template
              ></el-table-column>
              <el-table-column label="属性名称" min-width="150"
                ><template #default="{ row }"
                  ><el-input
                    v-model.trim="row.name"
                    placeholder="客户编号" /></template
              ></el-table-column>
              <el-table-column label="逻辑类型" width="145"
                ><template #default="{ row }"
                  ><el-select v-model="row.dataType"
                    ><el-option
                      v-for="item in dataTypes"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value" /></el-select></template
              ></el-table-column>
              <el-table-column label="来源字段" min-width="150"
                ><template #default="{ row }"
                  ><el-input
                    v-model.trim="row.sourceColumn"
                    placeholder="物理字段名" /></template
              ></el-table-column>
              <el-table-column label="可见" width="75" align="center"
                ><template #default="{ row }"
                  ><el-switch v-model="row.visible" /></template
              ></el-table-column>
              <el-table-column label="业务键" width="85" align="center"
                ><template #default="{ row }"
                  ><el-radio
                    v-model="form.keyAttributeCode"
                    :value="row.code"
                    :disabled="!row.code"
                    ><span /></el-radio></template
              ></el-table-column>
              <el-table-column label="显示属性" width="90" align="center"
                ><template #default="{ row }"
                  ><el-radio
                    v-model="form.displayAttributeCode"
                    :value="row.code"
                    :disabled="!row.code"
                    ><span /></el-radio></template
              ></el-table-column>
              <el-table-column label="操作" width="70" align="center"
                ><template #default="{ $index }"
                  ><el-button
                    link
                    type="danger"
                    @click="removeAttribute($index)"
                    >删除</el-button
                  ></template
                ></el-table-column
              >
              <template #empty
                ><el-empty
                  description="请新增属性或从物理表导入"
                  :image-size="70"
              /></template>
            </el-table>
          </el-tab-pane>

          <el-tab-pane name="hierarchies">
            <template #label
              >层级定义 <el-badge :value="form.hierarchies.length" :max="99"
            /></template>
            <div class="pane-toolbar">
              <div>
                <b>分析层级</b>
                <p>
                  例如“国家 → 省份 → 城市”，每一级绑定一个已定义的维度属性。
                </p>
              </div>
              <el-button type="primary" plain :icon="Plus" @click="addHierarchy"
                >新增层级</el-button
              >
            </div>
            <el-collapse v-if="form.hierarchies.length" accordion>
              <el-collapse-item
                v-for="(hierarchy, hIndex) in form.hierarchies"
                :key="hierarchy.localKey"
                :name="hierarchy.localKey"
              >
                <template #title
                  ><div class="hierarchy-title">
                    <el-icon><Operation /></el-icon
                    ><b>{{ hierarchy.name || "未命名层级" }}</b
                    ><span>{{ hierarchy.code || "待填写编码" }}</span>
                  </div></template
                >
                <div class="hierarchy-editor">
                  <el-row :gutter="16"
                    ><el-col :span="10"
                      ><el-form-item label="层级名称"
                        ><el-input
                          v-model.trim="hierarchy.name"
                          placeholder="行政区划" /></el-form-item></el-col
                    ><el-col :span="10"
                      ><el-form-item label="层级编码"
                        ><el-input
                          v-model.trim="hierarchy.code"
                          placeholder="region" /></el-form-item></el-col
                    ><el-col :span="4"
                      ><el-button
                        type="danger"
                        plain
                        @click="removeHierarchy(hIndex)"
                        >删除层级</el-button
                      ></el-col
                    ></el-row
                  >
                  <div class="level-head">
                    <b>级别明细</b
                    ><el-button
                      link
                      type="primary"
                      :icon="Plus"
                      @click="addLevel(hierarchy)"
                      >添加级别</el-button
                    >
                  </div>
                  <el-table :data="hierarchy.levels" border size="small">
                    <el-table-column
                      type="index"
                      label="#"
                      width="45"
                      align="center"
                    />
                    <el-table-column label="级别编码"
                      ><template #default="{ row }"
                        ><el-input
                          v-model.trim="row.code"
                          placeholder="province" /></template
                    ></el-table-column>
                    <el-table-column label="级别名称"
                      ><template #default="{ row }"
                        ><el-input
                          v-model.trim="row.name"
                          placeholder="省份" /></template
                    ></el-table-column>
                    <el-table-column label="绑定属性"
                      ><template #default="{ row }"
                        ><el-select
                          v-model="row.attributeCode"
                          filterable
                          placeholder="选择属性"
                          ><el-option
                            v-for="attr in validAttributes"
                            :key="attr.code"
                            :label="`${attr.name} (${attr.code})`"
                            :value="attr.code" /></el-select></template
                    ></el-table-column>
                    <el-table-column label="顺序" width="90"
                      ><template #default="{ row, $index }"
                        ><el-input-number
                          v-model="row.orderNum"
                          :min="1"
                          :max="99"
                          controls-position="right"
                          @change="
                            (value) => (row.orderNum = value || $index + 1)
                          " /></template
                    ></el-table-column>
                    <el-table-column label="操作" width="65" align="center"
                      ><template #default="{ $index }"
                        ><el-button
                          link
                          type="danger"
                          @click="hierarchy.levels.splice($index, 1)"
                          >删除</el-button
                        ></template
                      ></el-table-column
                    >
                  </el-table>
                </div>
              </el-collapse-item>
            </el-collapse>
            <el-empty
              v-else
              description="暂无分析层级，可按业务需要新增"
              :image-size="80"
            />
          </el-tab-pane>
        </el-tabs>
      </el-form>
      <template #footer
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
import {
  Download,
  Files,
  Operation,
  Plus,
  QuestionFilled,
  Refresh,
  Search,
} from "@element-plus/icons-vue";
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
  { label: "字符串", value: "STRING" },
  { label: "整数", value: "INTEGER" },
  { label: "长整数", value: "LONG" },
  { label: "小数", value: "DECIMAL" },
  { label: "布尔值", value: "BOOLEAN" },
  { label: "日期", value: "DATE" },
  { label: "日期时间", value: "DATETIME" },
];
const domains = ref([]),
  domainId = ref(),
  rows = ref([]),
  loading = ref(false),
  keyword = ref(""),
  status = ref("");
const dataSources = ref([]),
  sourceTables = ref([]),
  tableLoading = ref(false),
  columnLoading = ref(false);
const visible = ref(false),
  saving = ref(false),
  formRef = ref(),
  activeTab = ref("basic");
const emptyForm = () => ({
  id: undefined,
  domainId: domainId.value,
  dataSourceId: null,
  code: "",
  name: "",
  type: "STANDARD",
  scdType: "NONE",
  sourceTable: "",
  keyAttributeCode: "",
  displayAttributeCode: "",
  description: "",
  attributes: [],
  hierarchies: [],
});
const form = reactive(emptyForm());
const rules = {
  name: [{ required: true, message: "请输入维度名称", trigger: "blur" }],
  code: [
    { required: true, message: "请输入维度编码", trigger: "blur" },
    {
      pattern: /^[a-z][a-z0-9_]*$/,
      message: "请以小写字母开头，仅使用小写字母、数字和下划线",
      trigger: "blur",
    },
  ],
  type: [{ required: true, message: "请选择维度类型" }],
};
const currentDomain = computed(() =>
  domains.value.find((item) => item.id === domainId.value),
);
const publishedCount = computed(
  () => rows.value.filter((item) => item.status === "PUBLISHED").length,
);
const attributeCount = computed(() =>
  rows.value.reduce((sum, item) => sum + (item.attributes?.length || 0), 0),
);
const filteredRows = computed(() =>
  rows.value.filter((item) => {
    const text =
      `${item.dimensionName || ""} ${modelCode(item.dimensionCode) || ""}`.toLowerCase();
    return (
      (!keyword.value || text.includes(keyword.value.toLowerCase())) &&
      (!status.value || item.status === status.value)
    );
  }),
);
const validAttributes = computed(() =>
  form.attributes.filter((item) => item.code && item.name),
);
function modelCode(value) {
  return typeof value === "object" ? value?.value : value || "";
}
function typeText(value) {
  return (
    { STANDARD: "标准维度", TIME: "时间维度", DEGENERATE: "退化维度" }[value] ||
    value
  );
}
function statusText(value) {
  return (
    { DRAFT: "草稿", PUBLISHED: "已发布", DISABLED: "已停用" }[value] || value
  );
}
function statusType(value) {
  return value === "PUBLISHED"
    ? "success"
    : value === "DISABLED"
      ? "info"
      : "warning";
}
function reset() {
  keyword.value = "";
  status.value = "";
}
async function init() {
  const [domainRows, sourceRows] = await Promise.all([
    listDomains(),
    listDataSources(),
  ]);
  domains.value = domainRows || [];
  dataSources.value = (sourceRows || []).filter((item) => item.enabled);
  if (domains.value.length) {
    domainId.value = domains.value[0].id;
    await load();
  }
}
async function load() {
  if (!domainId.value) {
    rows.value = [];
    return;
  }
  loading.value = true;
  try {
    rows.value = (await listModels("dimensions", domainId.value)) || [];
  } finally {
    loading.value = false;
  }
}
function normalize(row = {}) {
  return {
    id: row.id,
    domainId: domainId.value,
    dataSourceId: row.dataSourceId || null,
    code: modelCode(row.dimensionCode) || row.code || "",
    name: row.dimensionName || row.name || "",
    type: row.dimensionType || row.type || "STANDARD",
    scdType: row.scdType || "NONE",
    sourceTable: row.sourceTable || "",
    keyAttributeCode: modelCode(row.keyAttributeCode),
    displayAttributeCode: modelCode(row.displayAttributeCode),
    description: row.description || "",
    attributes: (row.attributes || []).map((item) => ({
      id: item.id,
      code: modelCode(item.attributeCode) || item.code || "",
      name: item.attributeName || item.name || "",
      dataType: item.dataType || "STRING",
      sourceColumn: item.sourceColumn || "",
      visible: item.visible !== false,
    })),
    hierarchies: (row.hierarchies || []).map((item) => ({
      id: item.id,
      localKey: item.id || crypto.randomUUID(),
      code: modelCode(item.hierarchyCode) || item.code || "",
      name: item.hierarchyName || item.name || "",
      levels: (item.levels || []).map((level, index) => ({
        id: level.id,
        code: modelCode(level.levelCode) || level.code || "",
        name: level.levelName || level.name || "",
        attributeCode: modelCode(level.attributeCode),
        orderNum: level.orderNum || index + 1,
      })),
    })),
  };
}
function open(data) {
  Object.assign(form, emptyForm(), normalize(data));
  activeTab.value = "basic";
  sourceTables.value = [];
  visible.value = true;
  if (form.dataSourceId) sourceChanged(form.dataSourceId, false);
}
function add() {
  open({});
}
function edit(row) {
  open(row);
}
async function sourceChanged(id, clear = true) {
  if (clear) form.sourceTable = "";
  sourceTables.value = [];
  if (!id) return;
  const source = dataSources.value.find((item) => item.id === id);
  tableLoading.value = true;
  try {
    sourceTables.value =
      (await listDataSourceTables(id, {
        catalog: source?.databaseName,
        schema: source?.schemaName,
      })) || [];
  } finally {
    tableLoading.value = false;
  }
}
function addAttribute() {
  form.attributes.push({
    id: undefined,
    code: "",
    name: "",
    dataType: "STRING",
    sourceColumn: "",
    visible: true,
  });
}
function removeAttribute(index) {
  const code = form.attributes[index].code;
  form.attributes.splice(index, 1);
  if (form.keyAttributeCode === code) form.keyAttributeCode = "";
  if (form.displayAttributeCode === code) form.displayAttributeCode = "";
  form.hierarchies.forEach((item) =>
    item.levels.forEach((level) => {
      if (level.attributeCode === code) level.attributeCode = "";
    }),
  );
}
async function importColumns() {
  const source = dataSources.value.find(
    (item) => item.id === form.dataSourceId,
  );
  columnLoading.value = true;
  try {
    const columns =
      (await listDataSourceColumns(form.dataSourceId, {
        catalog: source?.databaseName,
        schema: source?.schemaName,
        table: form.sourceTable,
      })) || [];
    const existing = new Set(
      form.attributes.map((item) => item.sourceColumn || item.code),
    );
    columns.forEach((column) => {
      if (!existing.has(column.columnName))
        form.attributes.push({
          id: undefined,
          code: String(column.columnName).toLowerCase(),
          name: column.remarks || column.columnName,
          dataType: column.dataType || "STRING",
          sourceColumn: column.columnName,
          visible: true,
        });
    });
    ElMessage.success(`已读取 ${columns.length} 个物理字段`);
  } finally {
    columnLoading.value = false;
  }
}
function addHierarchy() {
  form.hierarchies.push({
    id: undefined,
    localKey: crypto.randomUUID(),
    code: "",
    name: "",
    levels: [],
  });
}
function removeHierarchy(index) {
  form.hierarchies.splice(index, 1);
}
function addLevel(hierarchy) {
  hierarchy.levels.push({
    id: undefined,
    code: "",
    name: "",
    attributeCode: "",
    orderNum: hierarchy.levels.length + 1,
  });
}
function validateStructure() {
  if (!form.attributes.length) return "请至少定义一个维度属性";
  if (
    form.attributes.some((item) => !item.code || !item.name || !item.dataType)
  )
    return "请完整填写属性编码、名称和类型";
  const codes = form.attributes.map((item) => item.code);
  if (new Set(codes).size !== codes.length) return "属性编码不能重复";
  if (!form.keyAttributeCode) return "请选择业务键属性";
  if (!form.displayAttributeCode) return "请选择显示属性";
  for (const hierarchy of form.hierarchies) {
    if (!hierarchy.code || !hierarchy.name) return "请完整填写层级编码和名称";
    if (!hierarchy.levels.length)
      return `层级“${hierarchy.name}”至少需要一个级别`;
    if (
      hierarchy.levels.some(
        (item) => !item.code || !item.name || !item.attributeCode,
      )
    )
      return `请完整填写层级“${hierarchy.name}”的级别定义`;
  }
  return "";
}
async function submit() {
  try {
    await formRef.value.validate();
  } catch {
    activeTab.value = "basic";
    return;
  }
  const error = validateStructure();
  if (error) {
    ElMessage.warning(error);
    activeTab.value = form.attributes.length ? "hierarchies" : "attributes";
    return;
  }
  saving.value = true;
  try {
    const payload = {
      ...form,
      hierarchies: form.hierarchies.map(({ localKey, ...item }) => item),
    };
    await saveModel("dimensions", payload);
    ElMessage.success("维度草稿已保存");
    visible.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function publish(row) {
  await ElMessageBox.confirm(
    `确认发布维度“${row.dimensionName}”？发布后当前版本不可编辑。`,
    "发布确认",
    { type: "warning" },
  );
  await publishModel("dimensions", row.id);
  ElMessage.success("维度已发布");
  await load();
}
async function remove(row) {
  await ElMessageBox.confirm(
    `确认删除维度“${row.dimensionName}”？`,
    "删除确认",
    { type: "warning" },
  );
  await deleteModel("dimensions", row.id);
  ElMessage.success("维度已删除");
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
  align-items: center;
  justify-content: space-between;
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
  box-shadow: var(--ry-shadow-primary-glow);
  font-size: 23px;
}
h2 {
  margin: 0 0 6px;
  font-size: 20px;
}
p {
  margin: 0;
  color: var(--ry-muted-foreground);
  font-size: 13px;
}
.hero-stats {
  display: flex;
}
.hero-stats > div {
  min-width: 92px;
  padding: 0 24px;
  text-align: center;
  border-left: 1px solid var(--ry-border-light);
}
.hero-stats strong {
  display: block;
  font: 600 22px var(--ry-font-numeric);
}
.hero-stats span,
.toolbar-hint,
.result-count,
.muted {
  color: var(--ry-muted-foreground);
  font-size: 12px;
}
.ry-search-card {
  margin-bottom: 0;
}
.ry-search-card :deep(.el-form-item) {
  margin-bottom: 0;
}
.dimension-cell {
  display: flex;
  align-items: center;
  gap: 11px;
}
.dimension-icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: var(--ry-primary-50);
  color: var(--ry-primary);
}
.dimension-name {
  font-weight: 500;
}
.dimension-code {
  margin-top: 3px;
  color: var(--ry-muted-foreground);
  font: 12px var(--ry-font-mono);
}
.section-title {
  margin: 5px 0 18px;
  padding-left: 9px;
  border-left: 3px solid var(--ry-primary);
  font-weight: 600;
}
.section-title small {
  margin-left: 8px;
  color: var(--ry-muted-foreground);
  font-weight: 400;
}
.field-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.field-help {
  color: var(--ry-muted-foreground);
  cursor: help;
}
.dimension-tabs {
  min-height: 510px;
}
.dimension-tabs :deep(.el-tabs__content) {
  padding: 14px 4px;
}
.dimension-tabs :deep(.el-badge__content) {
  transform: scale(0.8) translate(65%, -35%);
}
.pane-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  padding: 12px 14px;
  border: 1px solid var(--ry-border-light);
  border-radius: 7px;
  background: var(--ry-neutral-50);
}
.pane-toolbar p {
  margin-top: 4px;
}
.hierarchy-title {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.hierarchy-title span {
  color: var(--ry-muted-foreground);
  font: 12px var(--ry-font-mono);
}
.hierarchy-editor {
  padding: 8px 16px 18px;
}
.level-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 4px 0 10px;
}
.hierarchy-editor :deep(.el-form-item) {
  margin-bottom: 8px;
}
.hierarchy-editor :deep(.el-input-number) {
  width: 100%;
}
@media (max-width: 900px) {
  .hero-stats {
    display: none;
  }
}
</style>
