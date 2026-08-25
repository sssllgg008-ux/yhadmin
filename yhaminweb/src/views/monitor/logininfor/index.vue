<template>
  <div class="ry-page">
    <!-- 1. 搜索筛选条 -->
    <div class="ry-card ry-search-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="登录地址">
          <el-input
            v-model.trim="query.ipaddr"
            placeholder="请输入登录地址"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="用户名称">
          <el-input
            v-model.trim="query.username"
            placeholder="请输入用户名称"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="登录状态"
            clearable
            style="width: 120px"
          >
            <el-option label="成功" value="0" />
            <el-option label="失败" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="登录时间">
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
          <el-button
            type="primary"
            :icon="Unlock"
            :disabled="selection.length !== 1"
            @click="handleUnlock"
            >解锁</el-button
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
        <el-table-column label="用户名称" prop="username" width="110" />
        <el-table-column label="登录地址" prop="ipaddr" width="140">
          <template #default="{ row }">
            <span class="ry-mono">{{ row.ipaddr }}</span>
          </template>
        </el-table-column>
        <el-table-column label="登录地点" prop="loginLocation" width="110" />
        <el-table-column
          label="浏览器"
          prop="browser"
          width="120"
          show-overflow-tooltip
        />
        <el-table-column
          label="操作系统"
          prop="os"
          width="140"
          show-overflow-tooltip
        />
        <el-table-column
          label="登录状态"
          prop="status"
          width="96"
          align="center"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.status === '0' ? 'success' : 'danger'"
              effect="light"
            >
              {{ row.status === "0" ? "成功" : "失败" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="描述"
          prop="msg"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column label="登录时间" prop="loginTime" width="160" />
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
    <el-dialog v-model="detail.visible" title="登录日志详细" width="600px">
      <el-descriptions v-if="detail.row" :column="2" border>
        <el-descriptions-item label="日志编号">{{
          detail.row.id
        }}</el-descriptions-item>
        <el-descriptions-item label="用户名称">{{
          detail.row.username
        }}</el-descriptions-item>
        <el-descriptions-item label="登录地址">
          <span class="ry-mono">{{ detail.row.ipaddr }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="登录地点">{{
          detail.row.loginLocation
        }}</el-descriptions-item>
        <el-descriptions-item label="浏览器">{{
          detail.row.browser
        }}</el-descriptions-item>
        <el-descriptions-item label="操作系统">{{
          detail.row.os
        }}</el-descriptions-item>
        <el-descriptions-item label="登录状态">
          <el-tag
            :type="detail.row.status === '0' ? 'success' : 'danger'"
            effect="light"
            size="small"
          >
            {{ detail.row.status === "0" ? "成功" : "失败" }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="登录时间">{{
          detail.row.loginTime
        }}</el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{
          detail.row.msg
        }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detail.visible = false">关 闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Search, Refresh, Delete, Unlock } from "@element-plus/icons-vue";
import {
  listLogininfor,
  delLogininfor,
  clearLogininfor,
  unlockLogininfor,
} from "@/api/system";

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  ipaddr: "",
  username: "",
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
    const res = await listLogininfor(params);
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
  query.ipaddr = "";
  query.username = "";
  query.status = "";
  dateRange.value = [];
  query.pageNum = 1;
  loadList();
}

function handleSelectionChange(rows) {
  selection.value = rows;
}

// ===== 详情弹窗 =====
const detail = reactive({ visible: false, row: null });
function handleDetail(row) {
  detail.row = row;
  detail.visible = true;
}

// ===== 批量删除 =====
async function handleBatchDelete() {
  if (!selection.value.length) return;
  await ElMessageBox.confirm(
    `确认删除选中的 ${selection.value.length} 条日志？`,
    "提示",
    { type: "warning" },
  );
  try {
    await delLogininfor(selection.value.map((r) => r.id));
    ElMessage.success("删除成功");
    loadList();
  } catch (err) {
    ElMessage.error("删除失败");
  }
}

async function handleClean() {
  await ElMessageBox.confirm("确认清空所有登录日志？该操作不可恢复！", "警告", {
    type: "warning",
  });
  try {
    await clearLogininfor();
    ElMessage.success("清空成功");
    loadList();
  } catch (err) {
    ElMessage.error("清空失败");
  }
}

async function handleUnlock() {
  if (selection.value.length !== 1) return;
  const row = selection.value[0];
  await ElMessageBox.confirm(`确认解锁账号「${row.username}」？`, "提示", {
    type: "warning",
  });
  try {
    await unlockLogininfor(row.username);
    ElMessage.success("账号解锁成功");
  } catch (err) {
    ElMessage.error("解锁失败");
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
</style>
