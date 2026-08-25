<template>
  <div class="ry-page">
    <!-- 1. 搜索筛选条 -->
    <div class="ry-card ry-search-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="系统模块">
          <el-input
            v-model.trim="query.title"
            placeholder="请输入系统模块"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="操作人员">
          <el-input
            v-model.trim="query.operName"
            placeholder="请输入操作人员"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select
            v-model="query.businessType"
            placeholder="操作类型"
            clearable
            style="width: 140px"
          >
            <el-option label="新增" :value="1" />
            <el-option label="修改" :value="2" />
            <el-option label="删除" :value="3" />
            <el-option label="导出" :value="4" />
            <el-option label="导入" :value="6" />
            <el-option label="其他" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="操作状态"
            clearable
            style="width: 120px"
          >
            <el-option label="成功" value="0" />
            <el-option label="失败" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作时间">
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
          <el-button
            type="danger"
            :icon="Delete"
            :disabled="!selection.length"
            @click="handleBatchDelete"
            >批量删除</el-button
          >
          <el-button type="warning" :icon="Delete" @click="handleClean"
            >清空</el-button
          >
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
        <el-table-column label="日志编号" prop="id" width="90" align="center" />
        <el-table-column
          label="系统模块"
          prop="title"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column
          label="操作类型"
          prop="businessType"
          width="96"
          align="center"
        >
          <template #default="{ row }">
            <el-tag :type="businessTypeTag(row.businessType)" effect="light">
              {{ businessTypeText(row.businessType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="请求方式"
          prop="requestMethod"
          width="96"
          align="center"
        >
          <template #default="{ row }">
            <span
              class="ry-method"
              :class="`ry-method-${(row.requestMethod || '').toLowerCase()}`"
              >{{ row.requestMethod }}</span
            >
          </template>
        </el-table-column>
        <el-table-column label="操作人员" prop="operName" width="100" />
        <el-table-column label="操作地址" prop="operIp" width="140">
          <template #default="{ row }">
            <span class="ry-mono">{{ row.operIp }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="操作状态"
          prop="status"
          width="96"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="String(row.status) === '0' ? 'success' : 'danger'"
              effect="light"
            >
              {{ String(row.status) === "0" ? "成功" : "失败" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作时间" prop="operTime" width="160" />
        <el-table-column
          label="消耗(ms)"
          prop="costTime"
          width="100"
          align="right"
        />
        <el-table-column label="操作" width="80" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleDetail(row)"
              >详细</el-button
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

    <!-- 3. 详情弹窗 -->
    <el-dialog
      v-model="detail.visible"
      title="操作日志详细"
      width="820px"
      class="ry-detail-dialog"
    >
      <div v-if="detail.row" class="ry-detail-body">
        <!-- 基本信息 -->
        <div class="ry-detail-section">
          <div class="ry-detail-section-title">基本信息</div>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="日志编号">{{
              detail.row.id
            }}</el-descriptions-item>
            <el-descriptions-item label="系统模块">{{
              detail.row.title
            }}</el-descriptions-item>
            <el-descriptions-item label="操作类型">
              <el-tag
                :type="businessTypeTag(detail.row.businessType)"
                effect="light"
                size="small"
              >
                {{ businessTypeText(detail.row.businessType) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="请求方式">
              <span
                class="ry-method"
                :class="`ry-method-${(detail.row.requestMethod || '').toLowerCase()}`"
                >{{ detail.row.requestMethod }}</span
              >
            </el-descriptions-item>
            <el-descriptions-item label="操作状态">
              <el-tag
                :type="String(detail.row.status) === '0' ? 'success' : 'danger'"
                effect="light"
                size="small"
              >
                {{ String(detail.row.status) === "0" ? "成功" : "失败" }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="消耗时间">
              <span :class="costTimeClass(detail.row.costTime)"
                >{{ detail.row.costTime }} ms</span
              >
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 操作人信息 -->
        <div class="ry-detail-section">
          <div class="ry-detail-section-title">操作人信息</div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="操作人员">{{
              detail.row.operName || "—"
            }}</el-descriptions-item>
            <el-descriptions-item label="部门">{{
              detail.row.deptName || "—"
            }}</el-descriptions-item>
            <el-descriptions-item label="主机地址">
              <span class="ry-mono">{{ detail.row.operIp || "—" }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="操作地点">{{
              detail.row.operLocation || "—"
            }}</el-descriptions-item>
            <el-descriptions-item label="操作时间" :span="2">{{
              detail.row.operTime || "—"
            }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 请求信息 -->
        <div class="ry-detail-section">
          <div class="ry-detail-section-title">请求信息</div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="请求 URL">
              <div class="ry-copyable">
                <span class="ry-mono ry-break">{{
                  detail.row.operUrl || "—"
                }}</span>
                <el-button
                  v-if="detail.row.operUrl"
                  link
                  size="small"
                  :icon="CopyDocument"
                  @click="copyText(detail.row.operUrl)"
                  >复制</el-button
                >
              </div>
            </el-descriptions-item>
            <el-descriptions-item label="调用方法">
              <div class="ry-copyable">
                <span class="ry-mono ry-break">{{
                  detail.row.method || "—"
                }}</span>
                <el-button
                  v-if="detail.row.method"
                  link
                  size="small"
                  :icon="CopyDocument"
                  @click="copyText(detail.row.method)"
                  >复制</el-button
                >
              </div>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 请求参数 -->
        <div v-if="detail.row.operParam" class="ry-detail-section">
          <div class="ry-detail-section-title">
            请求参数
            <el-button
              link
              size="small"
              :icon="CopyDocument"
              @click="copyText(detail.row.operParam)"
              >复制</el-button
            >
          </div>
          <pre class="ry-detail-pre">{{
            formatJson(detail.row.operParam)
          }}</pre>
        </div>

        <!-- 返回结果 -->
        <div v-if="detail.row.jsonResult" class="ry-detail-section">
          <div class="ry-detail-section-title">
            返回结果
            <el-button
              link
              size="small"
              :icon="CopyDocument"
              @click="copyText(detail.row.jsonResult)"
              >复制</el-button
            >
          </div>
          <pre class="ry-detail-pre">{{
            formatJson(detail.row.jsonResult)
          }}</pre>
        </div>

        <!-- 错误消息 -->
        <div v-if="detail.row.errorMsg" class="ry-detail-section">
          <div class="ry-detail-section-title ry-error-title">错误消息</div>
          <pre class="ry-detail-pre ry-error-pre">{{
            detail.row.errorMsg
          }}</pre>
        </div>
      </div>
      <template #footer>
        <el-button @click="detail.visible = false">关 闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Search, Refresh, Delete, CopyDocument } from "@element-plus/icons-vue";
import { listOperlog, delOperlog, clearOperlog } from "@/api/system";

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  title: "",
  operName: "",
  businessType: "",
  status: "",
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
    const res = await listOperlog(params);
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
  query.title = "";
  query.operName = "";
  query.businessType = "";
  query.status = "";
  dateRange.value = [];
  query.pageNum = 1;
  loadList();
}

function handleSelectionChange(rows) {
  selection.value = rows;
}

// 业务类型映射
function businessTypeText(t) {
  return (
    {
      0: "其他",
      1: "新增",
      2: "修改",
      3: "删除",
      4: "导出",
      5: "导入",
      6: "导入",
    }[t] || "其他"
  );
}
function businessTypeTag(t) {
  return (
    {
      1: "success",
      2: "primary",
      3: "danger",
      4: "warning",
      5: "info",
      6: "info",
      0: "info",
    }[t] || "info"
  );
}

// 耗时分级着色：>3000ms 红、>1000ms 橙、其余默认
function costTimeClass(ms) {
  if (ms == null) return "";
  if (ms >= 3000) return "ry-cost-danger";
  if (ms >= 1000) return "ry-cost-warning";
  return "";
}

// JSON 美化：非 JSON 原样返回
function formatJson(str) {
  if (!str) return "";
  try {
    return JSON.stringify(JSON.parse(str), null, 2);
  } catch (e) {
    return str;
  }
}

// 复制到剪贴板
async function copyText(text) {
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success("已复制");
  } catch (e) {
    ElMessage.warning("复制失败，请手动选中复制");
  }
}

// ===== 详情弹窗 =====
const detail = reactive({ visible: false, row: null });
function handleDetail(row) {
  detail.row = row;
  detail.visible = true;
}

// ===== 删除 =====
async function handleBatchDelete() {
  if (!selection.value.length) return;
  await ElMessageBox.confirm(
    `确认删除选中的 ${selection.value.length} 条日志？`,
    "提示",
    { type: "warning" },
  );
  try {
    await delOperlog(selection.value.map((r) => r.id));
    ElMessage.success("删除成功");
    loadList();
  } catch (err) {
    ElMessage.error("删除失败");
  }
}

async function handleClean() {
  await ElMessageBox.confirm("确认清空所有操作日志？该操作不可恢复！", "警告", {
    type: "warning",
  });
  try {
    await clearOperlog();
    ElMessage.success("清空成功");
    loadList();
  } catch (err) {
    ElMessage.error("清空失败");
  }
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

.ry-method {
  display: inline-block;
  padding: 0 6px;
  height: 20px;
  line-height: 20px;
  border-radius: 4px;
  font-family: var(--ry-font-mono);
  font-size: 12px;
  font-weight: 500;

  &.ry-method-get {
    background: var(--ry-primary-50);
    color: var(--ry-primary);
  }
  &.ry-method-post {
    background: var(--state-success-soft);
    color: var(--state-success);
  }
  &.ry-method-put {
    background: var(--state-warning-soft);
    color: var(--state-warning);
  }
  &.ry-method-delete {
    background: var(--state-error-soft);
    color: var(--state-error);
  }
}

.ry-detail-pre {
  margin: 0;
  padding: 10px 12px;
  background: var(--ry-neutral-100);
  border: 1px solid var(--ry-border);
  border-radius: 4px;
  font-family: var(--ry-font-mono);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 240px;
  overflow-y: auto;
}

.ry-error-pre {
  background: var(--state-error-soft);
  border-color: var(--state-error);
  color: var(--state-error);
}

.ry-error {
  color: var(--state-error);
}

/* 耗时分级着色 */
.ry-cost-warning {
  color: var(--state-warning);
  font-weight: 500;
}
.ry-cost-danger {
  color: var(--state-error);
  font-weight: 600;
}

/* 详情弹窗分组布局 */
.ry-detail-body {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.ry-detail-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ry-detail-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 600;
  color: var(--ry-foreground);

  &::before {
    content: "";
    display: inline-block;
    width: 3px;
    height: 14px;
    background: var(--ry-primary);
    border-radius: 2px;
    margin-right: 8px;
    vertical-align: middle;
  }
}

.ry-error-title::before {
  background: var(--state-error);
}

.ry-copyable {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.ry-break {
  word-break: break-all;
  flex: 1;
  min-width: 0;
}
</style>
