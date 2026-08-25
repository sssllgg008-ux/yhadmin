<template>
  <div class="ry-page">
    <!-- 1. 搜索筛选条 -->
    <div class="ry-card ry-search-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="租户名称">
          <el-input
            v-model.trim="query.tenantName"
            placeholder="请输入租户名称"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="租户编码">
          <el-input
            v-model.trim="query.tenantCode"
            placeholder="请输入租户编码"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="租户状态"
            clearable
            style="width: 140px"
          >
            <el-option label="正常" value="0" />
            <el-option label="停用" value="1" />
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
          <el-button
            v-permission="'system:tenant:add'"
            type="primary"
            :icon="Plus"
            @click="handleAdd"
            >新增</el-button
          >
          <el-button
            v-permission="'system:tenant:edit'"
            type="success"
            :icon="Edit"
            :disabled="selection.length !== 1"
            @click="handleEditSingle"
            >修改</el-button
          >
          <el-button
            v-permission="'system:tenant:remove'"
            type="danger"
            :icon="Delete"
            :disabled="!selection.length"
            @click="handleBatchDelete"
            >批量删除</el-button
          >
          <el-button type="info" @click="openOperations">SaaS 运营</el-button>
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
        <el-table-column
          label="租户名称"
          prop="tenantName"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column label="租户编码" prop="tenantCode" min-width="140">
          <template #default="{ row }">
            <span class="ry-mono">{{ row.tenantCode }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="联系人"
          prop="contact"
          min-width="120"
          show-overflow-tooltip
        />
        <el-table-column label="联系电话" prop="phone" width="130" />
        <el-table-column
          label="邮箱"
          prop="email"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column label="到期时间" prop="expireTime" width="170" />
        <el-table-column label="当前套餐" min-width="150"><template #default="{ row }"><span>{{ row.planName || '-' }}</span><el-tag v-if="row.planVersion" size="small" style="margin-left:6px">v{{ row.planVersion }}</el-tag></template></el-table-column>
        <el-table-column label="订阅状态" prop="subscriptionStatus" width="100" />
        <el-table-column label="订阅到期" prop="subscriptionEndTime" width="170" />
        <el-table-column label="状态" prop="status" width="90" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === '0'"
              :disabled="row.isDefault"
              @change="(val) => handleStatusChange(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="170" />
        <el-table-column label="生命周期" width="130" align="center">
          <template #default="{ row }"><el-tag :type="lifecycleType(row.lifecycleStatus)">{{ row.lifecycleStatus || (row.status === '0' ? 'ACTIVE' : 'DISABLED') }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }">
            <el-button
              type="info"
              link
              @click="openTenantSaas(row)"
              >SaaS 详情</el-button
            >
            <el-divider direction="vertical" />
            <el-button
              v-permission="'system:tenant:edit'"
              type="primary"
              link
              @click="handleEdit(row)"
            >编辑</el-button
            >
            <el-divider direction="vertical" />
            <el-button
              v-permission="'system:tenant:edit'"
              type="warning"
              link
              :disabled="row.isDefault || row.status !== '0'"
              @click="handleResetAdminPassword(row)"
              >重置管理员密码</el-button
            >
            <el-divider direction="vertical" />
            <el-button
              v-permission="'system:tenant:remove'"
              type="danger"
              link
              :disabled="row.isDefault"
              @click="handleDelete(row)"
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
      :title="dialog.isEdit ? '修改租户' : '新增租户'"
      width="640px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="租户名称" prop="tenantName">
              <el-input
                v-model.trim="form.tenantName"
                placeholder="请输入租户名称"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="租户编码" prop="tenantCode">
              <el-input
                v-model.trim="form.tenantCode"
                placeholder="请输入租户编码"
                :disabled="dialog.isEdit"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="联系人">
              <el-input
                v-model.trim="form.contact"
                placeholder="请输入联系人"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话">
              <el-input
                v-model.trim="form.phone"
                placeholder="请输入联系电话"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="邮箱">
              <el-input v-model.trim="form.email" placeholder="请输入邮箱" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="到期时间">
              <el-date-picker
                v-model="form.expireTime"
                type="datetime"
                value-format="YYYY-MM-DDTHH:mm:ss"
                placeholder="请选择到期时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio>
            <el-radio value="1">停用</el-radio>
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

    <el-drawer v-model="saas.visible" :title="`${saas.tenant?.tenantName || ''} · SaaS 详情`" size="720px">
      <el-tabs v-model="saas.tab" @tab-change="loadSaasDetail">
        <el-tab-pane label="生命周期" name="lifecycle">
          <el-descriptions v-if="saas.lifecycle" :column="2" border>
            <el-descriptions-item label="状态">{{ saas.lifecycle.lifecycleStatus }}</el-descriptions-item>
            <el-descriptions-item label="阶段">{{ saas.lifecycle.stage }}</el-descriptions-item>
            <el-descriptions-item label="重试次数">{{ saas.lifecycle.retryCount || 0 }}</el-descriptions-item>
            <el-descriptions-item label="错误">{{ saas.lifecycle.errorMessage || '-' }}</el-descriptions-item>
          </el-descriptions>
          <div class="detail-actions">
            <el-button v-if="['DELETE_FAILED'].includes(saas.lifecycle?.lifecycleStatus)" type="warning" @click="retryDeleteTask">重试删除</el-button>
            <el-button v-if="saas.lifecycle?.stage === 'QUEUED'" @click="cancelDeleteTask">取消删除</el-button>
          </div>
          <el-table :data="saas.lifecycle?.tasks || []" border>
            <el-table-column prop="id" label="任务" width="80" /><el-table-column prop="operation" label="操作" /><el-table-column prop="status" label="状态" /><el-table-column prop="stage" label="阶段" /><el-table-column prop="progress" label="进度" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="套餐与用量" name="usage">
          <el-descriptions v-if="saas.usage?.subscription" :column="3" border class="subscription-summary">
            <el-descriptions-item label="当前套餐">{{ saas.usage.subscription.planName }} v{{ saas.usage.subscription.version }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ saas.usage.subscription.status }}</el-descriptions-item>
            <el-descriptions-item label="价格">{{ saas.usage.subscription.currency }} {{ Number(saas.usage.subscription.displayPrice || 0).toFixed(2) }}</el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ saas.usage.subscription.startTime }}</el-descriptions-item>
            <el-descriptions-item label="结束时间">{{ saas.usage.subscription.endTime || '长期有效' }}</el-descriptions-item>
            <el-descriptions-item label="待生效套餐">{{ saas.usage.subscription.pendingPlanName ? `${saas.usage.subscription.pendingPlanName} v${saas.usage.subscription.pendingPlanVersion}` : '-' }}</el-descriptions-item>
          </el-descriptions>
          <el-card shadow="never" class="subscription-change">
            <el-form inline>
              <el-form-item label="目标套餐"><el-select v-model="saas.planId" style="width:240px"><el-option v-for="p in subscribablePlans" :key="p.id" :label="`${p.planName} v${p.version}`" :value="p.id" /></el-select></el-form-item>
              <el-form-item label="生效方式"><el-radio-group v-model="saas.effectiveMode"><el-radio value="IMMEDIATE">立即生效</el-radio><el-radio value="AT_EXPIRY" :disabled="!saas.usage?.subscription?.endTime">到期生效</el-radio></el-radio-group></el-form-item>
              <el-button type="primary" @click="changePlan">预览并变更</el-button>
              <el-button v-if="saas.usage?.subscription?.pendingPlanId" type="warning" plain @click="cancelPendingPlan">取消待生效变更</el-button>
            </el-form>
          </el-card>
          <el-table :data="saas.quotaRows" border>
            <el-table-column prop="category" label="分类" width="90" />
            <el-table-column label="配额项" min-width="165"><template #default="{row}"><div>{{ row.quotaName }}</div><small class="quota-key">{{ row.quotaKey }}</small></template></el-table-column>
            <el-table-column label="套餐额度" width="110"><template #default="{row}">{{ row.unlimited ? '无限制' : `${row.planLimit} ${row.unit || ''}` }}</template></el-table-column>
            <el-table-column label="租户覆盖" width="180"><template #default="{row}"><el-checkbox v-model="row.override">覆盖</el-checkbox><el-input-number v-if="row.override" v-model="row.overrideLimit" :min="-1" :controls="false" style="width:90px;margin-left:6px" /></template></el-table-column>
            <el-table-column prop="used" label="当前用量" width="90" />
            <el-table-column label="使用率" width="150"><template #default="{row}"><span v-if="row.effectiveLimit < 0">无限制</span><el-progress v-else :percentage="row.usagePercent || 0" :status="row.usagePercent >= 90 ? 'exception' : row.usagePercent >= 75 ? 'warning' : 'success'" /></template></el-table-column>
          </el-table>
          <div class="detail-actions"><el-button type="primary" @click="saveQuotaOverrides">保存租户覆盖值</el-button></div>
          <h4>功能权益</h4><el-table :data="saas.usage?.subscription?.features || []" border><el-table-column prop="featureName" label="权益名称" /><el-table-column prop="featureKey" label="权益编码" /><el-table-column label="状态"><template #default="{row}"><el-tag :type="row.enabled === 'Y' ? 'success' : 'info'">{{ row.enabled === 'Y' ? '启用' : '关闭' }}</el-tag></template></el-table-column><el-table-column prop="description" label="说明" /></el-table>
          <h4>变更历史</h4><el-table :data="saas.usage?.subscription?.history || []" border><el-table-column prop="createTime" label="时间" width="170" /><el-table-column label="变更"><template #default="{row}">{{ row.oldPlanName || '-' }} → {{ row.newPlanName }}</template></el-table-column><el-table-column prop="effectiveMode" label="方式" /><el-table-column prop="executionStatus" label="状态" /><el-table-column prop="changedBy" label="操作人" /></el-table>
        </el-tab-pane>
        <el-tab-pane label="限流策略" name="rate">
          <el-alert title="租户策略优先于套餐默认额度；相同请求按最长路由表达式匹配。" type="info" :closable="false" />
          <el-form :model="saas.rateEditor" inline style="margin-top:12px">
            <el-form-item label="路由"><el-input v-model="saas.rateEditor.routePattern" placeholder="/system/*" style="width:220px" /></el-form-item>
            <el-form-item label="分钟额度"><el-input-number v-model="saas.rateEditor.minuteLimit" :min="0" /></el-form-item>
            <el-form-item label="每日额度"><el-input-number v-model="saas.rateEditor.dayLimit" :min="0" /></el-form-item>
            <el-form-item label="启用"><el-switch v-model="saas.rateEditor.enabled" /></el-form-item>
            <el-button type="primary" @click="saveRate">保存策略</el-button>
          </el-form>
          <el-table :data="saas.ratePolicies" border><el-table-column prop="routePattern" label="路由表达式" /><el-table-column prop="minuteLimit" label="分钟额度" /><el-table-column prop="dayLimit" label="每日额度" /><el-table-column label="状态"><template #default="{row}">{{ row.status === '0' ? '启用' : '停用' }}</template></el-table-column><el-table-column label="操作" width="90"><template #default="{row}"><el-button link type="danger" @click="removeRate(row)">删除</el-button></template></el-table-column></el-table>
          <h4>近期限流事件</h4><el-table :data="saas.rateEvents" border><el-table-column prop="createTime" label="时间" width="170" /><el-table-column prop="requestPath" label="接口" /><el-table-column prop="quotaKey" label="配额" /><el-table-column prop="limitValue" label="限制" /><el-table-column prop="currentValue" label="当前值" /></el-table>
        </el-tab-pane>
        <el-tab-pane label="备份" name="backup">
          <el-button type="primary" :loading="saas.backupLoading" @click="createBackup">创建备份</el-button>
          <el-table :data="saas.backups" border style="margin-top:12px"><el-table-column prop="id" label="编号" width="70" /><el-table-column prop="status" label="状态" width="110" /><el-table-column prop="fileName" label="文件" /><el-table-column label="大小" width="100"><template #default="{row}">{{ formatBytes(row.fileSize) }}</template></el-table-column><el-table-column prop="checksum" label="SHA-256" show-overflow-tooltip /><el-table-column prop="errorMessage" label="失败原因" show-overflow-tooltip /><el-table-column prop="createTime" label="创建时间" width="170" /><el-table-column label="操作" width="210"><template #default="{row}"><el-button link type="primary" :disabled="row.status !== 'SUCCEEDED'" @click="downloadBackup(row)">下载</el-button><el-button link type="warning" :disabled="saas.tenant?.status !== '1' || row.status !== 'SUCCEEDED'" @click="restoreBackup(row)">恢复</el-button><el-button link type="danger" @click="removeBackup(row)">删除</el-button></template></el-table-column></el-table>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <el-dialog v-model="operations.visible" title="SaaS 运营总览" width="820px">
      <el-descriptions :column="3" border><el-descriptions-item label="租户总数">{{ operations.data?.tenants?.total || 0 }}</el-descriptions-item><el-descriptions-item label="正常">{{ operations.data?.tenants?.active || 0 }}</el-descriptions-item><el-descriptions-item label="停用">{{ operations.data?.tenants?.disabled || 0 }}</el-descriptions-item></el-descriptions>
      <h4>套餐分布</h4><el-table :data="operations.data?.plans || []" border><el-table-column prop="planName" label="套餐" /><el-table-column prop="tenantCount" label="租户数" /></el-table>
      <h4>异常任务</h4><el-table :data="operations.data?.failedTasks || []" border><el-table-column prop="tenantId" label="租户" /><el-table-column prop="status" label="状态" /><el-table-column prop="errorMessage" label="错误" /></el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Search, Refresh, Plus, Edit, Delete } from "@element-plus/icons-vue";
import {
  listTenants,
  createTenant,
  updateTenant,
  changeTenantStatus,
  resetTenantAdminPassword,
  deleteTenant,
  getTenantLifecycle,
  retryTenantDelete,
  cancelTenantDelete,
  listPlans,
  getTenantUsage,
  changeTenantSubscription,
  listTenantBackups,
  createTenantBackup,
  restoreTenantBackup,
  deleteTenantBackup,
  getSaasOverview,
  saveTenantQuotaOverrides,
  calibrateTenantUsage,
  listRatePolicies,
  saveRatePolicy,
  deleteRatePolicy,
  listRateEvents,
  downloadTenantBackup,
} from "@/api/tenant";
import { previewSubscription, cancelPendingSubscription } from "@/api/plan";

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  tenantName: "",
  tenantCode: "",
  status: "",
});

const list = ref([]);
const total = ref(0);
const loading = ref(false);
const selection = ref([]);
const plans = ref([]);
const saas = reactive({ visible: false, tab: "lifecycle", tenant: null, lifecycle: null, usage: null, planId: null, effectiveMode: "IMMEDIATE", quotaRows: [], ratePolicies: [], rateEvents: [], rateEditor: { routePattern: "", minuteLimit: null, dayLimit: null, enabled: true }, backups: [], backupLoading: false });
const operations = reactive({ visible: false, data: null });
const subscribablePlans = computed(() => plans.value.filter(p => p.lifecycleStatus === "PUBLISHED" && String(p.status) === "0"));

function lifecycleType(status) {
  return ({ ACTIVE: "success", INITIALIZING: "warning", DISABLED: "info", DELETING: "warning", DELETE_FAILED: "danger", DELETED: "info" })[status] || "info";
}

async function openOperations() {
  operations.visible = true;
  try { [operations.data, plans.value] = await Promise.all([getSaasOverview(), listPlans()]); }
  catch (err) { operations.visible = false; ElMessage.error(err?.message || "运营数据加载失败"); }
}

async function openTenantSaas(row) {
  saas.visible = true;
  saas.tab = "lifecycle";
  saas.tenant = row;
  if (!plans.value.length) plans.value = await listPlans();
  await loadSaasDetail("lifecycle");
}

async function loadSaasDetail(tab = saas.tab) {
  if (!saas.tenant) return;
  try {
    if (tab === "lifecycle") saas.lifecycle = await getTenantLifecycle(saas.tenant.id);
    if (tab === "usage") {
      saas.usage = await getTenantUsage(saas.tenant.id);
      saas.planId = null;
      saas.quotaRows = (saas.usage?.quotaRows || []).map(row => ({ ...row, override: row.overrideLimit !== null && row.overrideLimit !== undefined }));
    }
    if (tab === "backup") saas.backups = await listTenantBackups(saas.tenant.id);
    if (tab === "rate") [saas.ratePolicies, saas.rateEvents] = await Promise.all([listRatePolicies(saas.tenant.id), listRateEvents(saas.tenant.id)]);
  } catch (err) { ElMessage.error(err?.message || "SaaS 详情加载失败"); }
}

async function retryDeleteTask() {
  await retryTenantDelete(saas.tenant.id);
  ElMessage.success("删除任务已重新提交");
  await loadSaasDetail("lifecycle");
  loadList();
}

async function cancelDeleteTask() {
  await cancelTenantDelete(saas.tenant.id);
  ElMessage.success("已取消待执行的删除任务");
  await loadSaasDetail("lifecycle");
  loadList();
}

async function changePlan() {
  if (!saas.planId) return ElMessage.warning("请选择套餐");
  const preview = await previewSubscription(saas.tenant.id, { planId: saas.planId });
  const target = preview.target;
  const over = preview.overLimit || [];
  const warning = over.length ? `\n当前有 ${over.length} 项用量超过新套餐额度，切换后将禁止继续新增对应资源。` : "";
  await ElMessageBox.confirm(`确认切换到「${target.planName} v${target.version}」？${warning}`, "套餐变更预览", { type: over.length ? "warning" : "info" });
  await changeTenantSubscription(saas.tenant.id, { planId: saas.planId, effectiveMode: saas.effectiveMode, remark: "平台后台调整" });
  ElMessage.success(saas.effectiveMode === "AT_EXPIRY" ? "套餐变更已安排在到期时生效" : "租户套餐已更新");
  await loadSaasDetail("usage");
  loadList();
}

async function saveQuotaOverrides() {
  const quotas = saas.quotaRows.filter(row => row.override).map(row => ({ quotaKey: row.quotaKey, quotaLimit: row.overrideLimit, override: true, unlimited: Number(row.overrideLimit) < 0 }));
  if (quotas.some(row => row.quotaLimit === null || row.quotaLimit === undefined)) return ElMessage.warning("请填写所有启用的覆盖额度");
  await saveTenantQuotaOverrides(saas.tenant.id, quotas);
  ElMessage.success("配额覆盖已保存");
  await loadSaasDetail("usage");
}

async function cancelPendingPlan() {
  await ElMessageBox.confirm("确认取消待生效的套餐变更？", "提示", { type: "warning" });
  await cancelPendingSubscription(saas.tenant.id);
  ElMessage.success("待生效变更已取消");
  await loadSaasDetail("usage");
}

async function createBackup() {
  saas.backupLoading = true;
  try { await createTenantBackup(saas.tenant.id); ElMessage.success("备份创建成功"); await loadSaasDetail("backup"); }
  finally { saas.backupLoading = false; }
}

async function saveRate() {
  const route = saas.rateEditor.routePattern.trim();
  if (!route) return ElMessage.warning("路由表达式不能为空");
  if (saas.ratePolicies.some(row => row.routePattern === route)) return ElMessage.warning("该路由策略已存在，请先删除或直接调整现有策略");
  await saveRatePolicy(saas.tenant.id, { routePattern: route, minuteLimit: saas.rateEditor.minuteLimit, dayLimit: saas.rateEditor.dayLimit, status: saas.rateEditor.enabled ? "0" : "1" });
  saas.rateEditor = { routePattern: "", minuteLimit: null, dayLimit: null, enabled: true };
  ElMessage.success("限流策略已保存"); await loadSaasDetail("rate");
}

async function removeRate(row) {
  await ElMessageBox.confirm(`确认删除路由 ${row.routePattern} 的限流策略？`, "提示", { type: "warning" });
  await deleteRatePolicy(row.id); ElMessage.success("限流策略已删除"); await loadSaasDetail("rate");
}

function formatBytes(value) { const n = Number(value || 0); if (!n) return "-"; if (n < 1024) return `${n} B`; if (n < 1048576) return `${(n / 1024).toFixed(1)} KB`; return `${(n / 1048576).toFixed(1)} MB`; }

async function downloadBackup(row) {
  const response = await downloadTenantBackup(saas.tenant.id, row.id);
  const blob = response instanceof Blob ? response : response.data;
  const disposition = response?.headers?.["content-disposition"] || "";
  const match = disposition.match(/filename\*?=(?:UTF-8'')?[\"]?([^\";]+)/i);
  const fileName = match ? decodeURIComponent(match[1].replace(/[\"]/g, "")) : (row.fileName || `tenant-${saas.tenant.id}-${row.id}.zip`);
  const url = URL.createObjectURL(blob); const link = document.createElement("a"); link.href = url; link.download = fileName; document.body.appendChild(link); link.click(); link.remove(); URL.revokeObjectURL(url);
}

async function restoreBackup(row) {
  await ElMessageBox.confirm("恢复会覆盖该停用租户的现有数据，确认继续？", "恢复备份", { type: "warning" });
  await restoreTenantBackup(saas.tenant.id, row.id);
  ElMessage.success("备份恢复完成");
  await loadSaasDetail("backup");
}

async function removeBackup(row) {
  await ElMessageBox.confirm("确认删除该备份文件？", "删除备份", { type: "warning" });
  await deleteTenantBackup(saas.tenant.id, row.id);
  ElMessage.success("备份已删除");
  await loadSaasDetail("backup");
}

async function loadList() {
  loading.value = true;
  try {
    const res = await listTenants(query);
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
  query.tenantName = "";
  query.tenantCode = "";
  query.status = "";
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
  tenantName: "",
  tenantCode: "",
  contact: "",
  phone: "",
  email: "",
  expireTime: null,
  status: "0",
  remark: "",
});
const form = reactive(defaultForm());
const rules = {
  tenantName: [{ required: true, message: "请输入租户名称", trigger: "blur" }],
  tenantCode: [{ required: true, message: "请输入租户编码", trigger: "blur" }],
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
function normalizeDateTime(value) {
  if (value === null || value === undefined || value === "") {
    return null;
  }

  const text = String(value).trim();
  let date;

  if (/^-?\d{10}$/.test(text)) {
    date = new Date(Number(text) * 1000);
  } else if (/^-?\d{13}$/.test(text)) {
    date = new Date(Number(text));
  } else {
    date = new Date(text.replace(" ", "T"));
  }

  if (Number.isNaN(date.getTime())) {
    return text.replace(" ", "T");
  }

  const pad = (number) => String(number).padStart(2, "0");

  return (
      `${date.getFullYear()}-` +
      `${pad(date.getMonth() + 1)}-` +
      `${pad(date.getDate())}T` +
      `${pad(date.getHours())}:` +
      `${pad(date.getMinutes())}:` +
      `${pad(date.getSeconds())}`
  );
}
function handleEdit(row) {
  Object.assign(form, defaultForm(), {
    id: row.id,
    tenantName: row.tenantName,
    tenantCode: row.tenantCode,
    contact: row.contact,
    phone: row.phone,
    email: row.email,
    // LocalDateTime 请求统一使用 ISO-8601，兼容后端 Jackson 反序列化
    expireTime: normalizeDateTime(row.expireTime),
    status: row.status,
    remark: row.remark,
  });
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
      await updateTenant({ ...form });
      ElMessage.success("修改成功");
    } else {
      const result = await createTenant({ ...form });
      const credential = result?.data;
      if (credential?.temporaryPassword) {
        await ElMessageBox.alert(
          `管理员账号：admin\n一次性密码：${credential.temporaryPassword}\n\n请立即安全保存；关闭后系统不会再次显示该密码。`,
          "租户创建成功",
          {
            confirmButtonText: "我已保存",
            type: "success",
            closeOnClickModal: false,
            closeOnPressEscape: false,
          },
        );
      } else {
        ElMessage.success("新增成功");
      }
    }
    dialog.visible = false;
    loadList();
  } catch (err) {
    ElMessage.error(err?.message || "保存失败");
  } finally {
    dialog.submitting = false;
  }
}

// ===== 状态切换 =====
async function handleStatusChange(row, val) {
  const newStatus = val ? "0" : "1";
  try {
    await changeTenantStatus(row.id, newStatus);
    row.status = newStatus;
    ElMessage.success("状态修改成功");
  } catch (err) {
    ElMessage.error("状态修改失败");
  }
}

async function handleResetAdminPassword(row) {
  await ElMessageBox.confirm(
    `确认重置租户「${row.tenantName}」的 admin 密码？该租户现有登录会话将立即失效。`,
    "重置管理员密码",
    { type: "warning" },
  );
  const result = await resetTenantAdminPassword(row.id);
  const credential = result?.data;
  await ElMessageBox.alert(
    `管理员账号：admin\n一次性密码：${credential?.temporaryPassword || ""}\n\n请立即安全保存；关闭后系统不会再次显示该密码。`,
    "密码重置成功",
    {
      confirmButtonText: "我已保存",
      type: "success",
      closeOnClickModal: false,
      closeOnPressEscape: false,
    },
  );
}

// ===== 删除 =====
async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除租户「${row.tenantName}」？`, "提示", {
    type: "warning",
  });
  try {
    await deleteTenant(row.id);
    ElMessage.success("删除任务已提交，可在 SaaS 详情中查看进度");
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
    for (const row of selection.value) {
      await deleteTenant(row.id);
    }
    ElMessage.success("删除任务已提交，可在 SaaS 详情中查看进度");
    loadList();
  } catch (err) {
    ElMessage.error("删除失败");
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
