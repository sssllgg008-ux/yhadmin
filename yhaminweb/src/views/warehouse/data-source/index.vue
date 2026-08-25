<template>
  <div class="warehouse-page">
    <section class="warehouse-hero ry-card">
      <div class="hero-main">
        <div class="hero-icon">
          <el-icon><Connection /></el-icon>
        </div>
        <div>
          <h2>数据源管理</h2>
          <p>集中维护数据仓库的数据库连接，并浏览库、表及字段元数据。</p>
        </div>
      </div>
      <div class="hero-stats">
        <div>
          <strong>{{ rows.length }}</strong
          ><span>数据源</span>
        </div>
        <div>
          <strong>{{ enabledCount }}</strong
          ><span>已启用</span>
        </div>
        <div>
          <strong>{{ typeCount }}</strong
          ><span>数据库类型</span>
        </div>
      </div>
    </section>
    <div class="ry-card ry-search-card">
      <el-form inline @submit.prevent>
        <el-form-item label="关键词"
          ><el-input
            v-model.trim="keyword"
            :prefix-icon="Search"
            placeholder="名称 / 编码 / 主机"
            clearable
            style="width: 240px"
        /></el-form-item>
        <el-form-item label="类型"
          ><el-select
            v-model="filterType"
            clearable
            placeholder="全部类型"
            style="width: 160px"
            ><el-option
              v-for="item in types"
              :key="item.value"
              :label="item.label"
              :value="item.value" /></el-select
        ></el-form-item>
        <el-form-item
          ><el-button type="primary" :icon="Search">搜索</el-button
          ><el-button :icon="Refresh" @click="reset"
            >重置</el-button
          ></el-form-item
        >
      </el-form>
    </div>
    <div class="ry-card ry-table-card">
      <div class="ry-toolbar">
        <div class="ry-toolbar-left">
          <el-button type="primary" :icon="Plus" @click="open()"
            >新增数据源</el-button
          ><span class="toolbar-hint">密码采用 AES-GCM 加密存储且不会回显</span>
        </div>
        <div class="ry-toolbar-right">
          <span class="result-count">共 {{ filteredRows.length }} 项</span
          ><el-button circle :icon="Refresh" @click="load" />
        </div>
      </div>
      <el-table v-loading="loading" :data="filteredRows" border stripe>
        <el-table-column label="数据源" min-width="230"
          ><template #default="{ row }"
            ><div class="source-cell">
              <div :class="['db-icon', `is-${row.type?.toLowerCase()}`]">
                {{ dbShort(row.type) }}
              </div>
              <div>
                <div class="source-name">{{ row.name }}</div>
                <div class="source-code">{{ row.code }}</div>
              </div>
            </div></template
          ></el-table-column
        >
        <el-table-column label="类型" width="130" align="center"
          ><template #default="{ row }"
            ><el-tag effect="plain">{{ typeLabel(row.type) }}</el-tag></template
          ></el-table-column
        >
        <el-table-column label="连接地址" min-width="230"
          ><template #default="{ row }"
            ><span class="address"
              >{{ row.host }}:{{ row.port }}/{{ row.databaseName }}</span
            ></template
          ></el-table-column
        >
        <el-table-column prop="username" label="用户名" width="130" />
        <el-table-column label="状态" width="100" align="center"
          ><template #default="{ row }"
            ><el-switch
              v-model="row.enabled"
              @change="(value) => toggle(row, value)" /></template
        ></el-table-column>
        <el-table-column label="操作" width="245" fixed="right" align="center"
          ><template #default="{ row }"
            ><el-button
              link
              type="success"
              :loading="testingId === row.id"
              @click="test(row)"
              >测试</el-button
            ><el-divider direction="vertical" /><el-button
              link
              type="primary"
              :disabled="!row.enabled"
              @click="browse(row)"
              >元数据</el-button
            ><el-divider direction="vertical" /><el-button
              link
              type="primary"
              @click="open(row)"
              >编辑</el-button
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
            description="暂无数据源，点击“新增数据源”创建连接"
            :image-size="88"
        /></template>
      </el-table>
    </div>

    <el-dialog
      v-model="visible"
      :title="form.id ? '编辑数据源' : '新增数据源'"
      width="720px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="18"
          ><el-col :span="12"
            ><el-form-item label="数据源名称" prop="name"
              ><el-input v-model.trim="form.name" /></el-form-item></el-col
          ><el-col :span="12"
            ><el-form-item prop="code"
              ><template #label
                ><span class="field-label"
                  >数据源编码<el-tooltip
                    content="数据源的唯一标识，保存后不可修改；请以小写字母开头，仅使用小写字母、数字和下划线，例如：sales_mysql"
                    placement="top"
                    ><el-icon class="field-help"
                      ><QuestionFilled /></el-icon></el-tooltip></span></template
              ><el-input
                v-model.trim="form.code"
                :disabled="!!form.id"
                placeholder="例如：sales_mysql" /></el-form-item></el-col
        ></el-row>
        <el-row :gutter="18"
          ><el-col :span="12"
            ><el-form-item label="数据库类型" prop="type"
              ><el-select
                v-model="form.type"
                style="width: 100%"
                @change="typeChanged"
                ><el-option
                  v-for="item in types"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value" /></el-select></el-form-item></el-col
          ><el-col :span="12"
            ><el-form-item label="端口" prop="port"
              ><el-input-number
                v-model="form.port"
                :min="1"
                :max="65535"
                controls-position="right"
                style="width: 100%" /></el-form-item></el-col
        ></el-row>
        <el-row :gutter="18"
          ><el-col :span="16"
            ><el-form-item label="主机地址" prop="host"
              ><el-input
                v-model.trim="form.host"
                placeholder="127.0.0.1" /></el-form-item></el-col
          ><el-col :span="8"
            ><el-form-item label="启用"
              ><el-switch v-model="form.enabled" /></el-form-item></el-col
        ></el-row>
        <el-row :gutter="18"
          ><el-col :span="12"
            ><el-form-item label="数据库" prop="databaseName"
              ><el-input
                v-model.trim="form.databaseName" /></el-form-item></el-col
          ><el-col :span="12"
            ><el-form-item label="Schema"
              ><el-input
                v-model.trim="form.schemaName"
                placeholder="可选" /></el-form-item></el-col
        ></el-row>
        <el-row :gutter="18"
          ><el-col :span="12"
            ><el-form-item label="用户名" prop="username"
              ><el-input v-model.trim="form.username" /></el-form-item></el-col
          ><el-col :span="12"
            ><el-form-item label="密码" prop="password"
              ><el-input
                v-model="form.password"
                type="password"
                show-password
                :placeholder="
                  form.id ? '不修改请留空' : '请输入连接密码'
                " /></el-form-item></el-col
        ></el-row>
        <el-form-item label="JDBC 参数"
          ><el-input
            v-model.trim="form.jdbcParameters"
            placeholder="例如：useUnicode=true&characterEncoding=utf8"
        /></el-form-item>
        <el-form-item label="说明"
          ><el-input v-model="form.description" type="textarea" :rows="3"
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="visible = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="submit"
          >保存</el-button
        ></template
      >
    </el-dialog>

    <el-drawer
      v-model="metadataVisible"
      :title="`${current?.name || ''} · 元数据`"
      size="64%"
    >
      <div class="metadata-tools">
        <el-select
          v-model="catalog"
          placeholder="选择数据库"
          clearable
          style="width: 220px"
          @change="loadTables"
          ><el-option
            v-for="item in catalogs"
            :key="item"
            :label="item"
            :value="item" /></el-select
        ><el-input
          v-model="tableKeyword"
          :prefix-icon="Search"
          placeholder="筛选表名"
          clearable
          style="width: 220px"
        />
      </div>
      <div class="metadata-layout">
        <div class="table-list">
          <div
            v-for="item in filteredTables"
            :key="`${item.schema}-${item.tableName}`"
            :class="[
              'table-item',
              selectedTable === item.tableName && 'is-active',
            ]"
            @click="selectTable(item)"
          >
            <el-icon><Grid /></el-icon><span>{{ item.tableName }}</span
            ><small>{{ item.tableType }}</small>
          </div>
          <el-empty
            v-if="!filteredTables.length"
            description="暂无物理表"
            :image-size="70"
          />
        </div>
        <el-table v-loading="metadataLoading" :data="columns" border stripe
          ><el-table-column
            prop="columnName"
            label="字段名"
            min-width="150" /><el-table-column
            prop="typeName"
            label="数据库类型"
            width="130" /><el-table-column
            prop="dataType"
            label="模型类型"
            width="110" /><el-table-column label="主键/可空" width="120"
            ><template #default="{ row }"
              ><el-tag v-if="row.primaryKey" size="small">主键</el-tag
              ><el-tag v-else-if="row.nullable" size="small" type="info"
                >可空</el-tag
              ></template
            ></el-table-column
          ><el-table-column prop="remarks" label="备注" min-width="160"
        /></el-table>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import {
  Connection,
  Grid,
  Plus,
  QuestionFilled,
  Refresh,
  Search,
} from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  changeDataSourceStatus,
  deleteDataSource,
  listDataSourceColumns,
  listDataSourceDatabases,
  listDataSources,
  listDataSourceTables,
  saveDataSource,
  testDataSource,
} from "@/api/warehouse";
const types = [
  { label: "MySQL", value: "MYSQL", port: 3306 },
  { label: "PostgreSQL", value: "POSTGRESQL", port: 5432 },
  { label: "Oracle", value: "ORACLE", port: 1521 },
  { label: "SQL Server", value: "SQL_SERVER", port: 1433 },
];
const rows = ref([]),
  loading = ref(false),
  saving = ref(false),
  visible = ref(false),
  formRef = ref(),
  keyword = ref(""),
  filterType = ref(""),
  testingId = ref();
const form = reactive({
  id: undefined,
  code: "",
  name: "",
  type: "MYSQL",
  host: "",
  port: 3306,
  databaseName: "",
  schemaName: "",
  username: "",
  password: "",
  jdbcParameters: "",
  enabled: true,
  description: "",
});
const rules = {
  name: [{ required: true, message: "请输入数据源名称" }],
  code: [
    { required: true, message: "请输入数据源编码" },
    { pattern: /^[a-z][a-z0-9_]*$/, message: "仅支持小写字母、数字和下划线" },
  ],
  type: [{ required: true, message: "请选择类型" }],
  host: [{ required: true, message: "请输入主机" }],
  port: [{ required: true, message: "请输入端口" }],
  databaseName: [{ required: true, message: "请输入数据库" }],
  username: [{ required: true, message: "请输入用户名" }],
};
const enabledCount = computed(() => rows.value.filter((x) => x.enabled).length),
  typeCount = computed(() => new Set(rows.value.map((x) => x.type)).size);
const filteredRows = computed(() =>
  rows.value.filter(
    (x) =>
      (!filterType.value || x.type === filterType.value) &&
      (!keyword.value ||
        `${x.name} ${x.code} ${x.host}`
          .toLowerCase()
          .includes(keyword.value.toLowerCase())),
  ),
);
const metadataVisible = ref(false),
  current = ref(),
  catalogs = ref([]),
  catalog = ref(""),
  tables = ref([]),
  columns = ref([]),
  selectedTable = ref(""),
  tableKeyword = ref(""),
  metadataLoading = ref(false);
const filteredTables = computed(() =>
  tables.value.filter(
    (x) =>
      !tableKeyword.value ||
      x.tableName.toLowerCase().includes(tableKeyword.value.toLowerCase()),
  ),
);
function typeLabel(v) {
  return types.find((x) => x.value === v)?.label || v;
}
function dbShort(v) {
  return (
    { MYSQL: "My", POSTGRESQL: "Pg", ORACLE: "Or", SQL_SERVER: "Sql" }[v] ||
    "DB"
  );
}
function reset() {
  keyword.value = "";
  filterType.value = "";
}
function typeChanged(v) {
  form.port = types.find((x) => x.value === v)?.port;
}
async function load() {
  loading.value = true;
  try {
    rows.value = (await listDataSources()) || [];
  } finally {
    loading.value = false;
  }
}
function open(row) {
  Object.assign(form, {
    id: row?.id,
    code: row?.code || "",
    name: row?.name || "",
    type: row?.type || "MYSQL",
    host: row?.host || "",
    port: row?.port || 3306,
    databaseName: row?.databaseName || "",
    schemaName: row?.schemaName || "",
    username: row?.username || "",
    password: "",
    jdbcParameters: row?.jdbcParameters || "",
    enabled: row?.enabled ?? true,
    description: row?.description || "",
  });
  visible.value = true;
}
async function submit() {
  await formRef.value.validate();
  saving.value = true;
  try {
    await saveDataSource({ ...form });
    ElMessage.success("数据源已保存");
    visible.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function toggle(row, value) {
  try {
    await changeDataSourceStatus(row.id, value);
    ElMessage.success(value ? "数据源已启用" : "数据源已停用");
  } catch (e) {
    row.enabled = !value;
    throw e;
  }
}
async function test(row) {
  testingId.value = row.id;
  try {
    const result = await testDataSource(row.id);
    result.success
      ? ElMessage.success(
          `${result.message}（${result.elapsedMs}ms，${result.databaseProduct || ""}）`,
        )
      : ElMessage.error(result.message);
  } finally {
    testingId.value = undefined;
  }
}
async function remove(row) {
  await ElMessageBox.confirm(`确认删除数据源“${row.name}”？`, "删除确认", {
    type: "warning",
  });
  await deleteDataSource(row.id);
  ElMessage.success("数据源已删除");
  await load();
}
async function browse(row) {
  current.value = row;
  metadataVisible.value = true;
  catalogs.value = (await listDataSourceDatabases(row.id)) || [];
  catalog.value = catalogs.value.includes(row.databaseName)
    ? row.databaseName
    : catalogs.value[0] || row.databaseName;
  await loadTables();
}
async function loadTables() {
  if (!current.value) return;
  metadataLoading.value = true;
  try {
    tables.value =
      (await listDataSourceTables(current.value.id, {
        catalog: catalog.value,
        schema: current.value.schemaName,
      })) || [];
    columns.value = [];
    selectedTable.value = "";
  } finally {
    metadataLoading.value = false;
  }
}
async function selectTable(item) {
  selectedTable.value = item.tableName;
  metadataLoading.value = true;
  try {
    columns.value =
      (await listDataSourceColumns(current.value.id, {
        catalog: item.catalog || catalog.value,
        schema: item.schema,
        table: item.tableName,
      })) || [];
  } finally {
    metadataLoading.value = false;
  }
}
onMounted(load);
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
  min-width: 88px;
  padding: 0 24px;
  text-align: center;
  border-left: 1px solid var(--ry-border-light);
}
.hero-stats strong {
  display: block;
  font: 600 22px var(--ry-font-numeric);
}
.hero-stats span {
  font-size: 12px;
  color: var(--ry-muted-foreground);
}
.ry-search-card {
  margin-bottom: 0;
}
.ry-search-card :deep(.el-form-item) {
  margin-bottom: 0;
}
.toolbar-hint,
.result-count {
  font-size: 12px;
  color: var(--ry-muted-foreground);
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
.field-help:hover {
  color: var(--ry-primary);
}
.source-cell {
  display: flex;
  align-items: center;
  gap: 11px;
}
.db-icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 9px;
  background: var(--ry-primary-50);
  color: var(--ry-primary);
  font: 600 11px var(--ry-font-mono);
}
.source-name {
  font-weight: 500;
}
.source-code,
.address {
  color: var(--ry-muted-foreground);
  font: 12px var(--ry-font-mono);
}
.metadata-tools {
  display: flex;
  gap: 12px;
  margin-bottom: 14px;
}
.metadata-layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 14px;
}
.table-list {
  height: calc(100vh - 170px);
  overflow: auto;
  border: 1px solid var(--ry-border-light);
  border-radius: 6px;
}
.table-item {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr) auto;
  align-items: center;
  gap: 6px;
  padding: 11px 12px;
  border-bottom: 1px solid var(--ry-border-light);
  cursor: pointer;
  font-size: 13px;
}
.table-item:hover,
.table-item.is-active {
  background: var(--ry-primary-50);
  color: var(--ry-primary);
}
.table-item small {
  font-size: 10px;
  color: var(--ry-muted-foreground);
}
</style>
