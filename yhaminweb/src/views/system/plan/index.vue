<template>
  <div class="ry-page plan-page">
    <div class="ry-card ry-search-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="套餐名称"><el-input v-model.trim="query.planName" clearable placeholder="请输入套餐名称" @keyup.enter="load" /></el-form-item>
        <el-form-item label="套餐编码"><el-input v-model.trim="query.planCode" clearable placeholder="请输入套餐编码" @keyup.enter="load" /></el-form-item>
        <el-form-item label="生命周期"><el-select v-model="query.lifecycleStatus" clearable style="width:140px"><el-option v-for="x in lifecycleOptions" :key="x.value" :label="x.label" :value="x.value" /></el-select></el-form-item>
        <el-form-item label="计费周期"><el-select v-model="query.billingCycle" clearable style="width:130px"><el-option v-for="x in cycleOptions" :key="x.value" :label="x.label" :value="x.value" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="load">搜索</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </div>

    <div class="ry-card ry-table-card">
      <div class="ry-toolbar">
        <div class="ry-toolbar-left"><el-button v-permission="'system:plan:add'" type="primary" :icon="Plus" @click="openEditor()">新增套餐</el-button></div>
        <el-button circle :icon="Refresh" @click="load" />
      </div>
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="planName" label="套餐名称" min-width="150" />
        <el-table-column label="编码 / 版本" min-width="150"><template #default="{ row }"><span class="mono">{{ row.planCode }}</span><el-tag size="small" class="version">v{{ row.version }}</el-tag></template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="statusType(row.lifecycleStatus)">{{ statusText(row.lifecycleStatus) }}</el-tag></template></el-table-column>
        <el-table-column label="展示价格" width="140"><template #default="{ row }">{{ money(row) }}</template></el-table-column>
        <el-table-column label="周期" width="90"><template #default="{ row }">{{ cycleText(row.billingCycle) }}</template></el-table-column>
        <el-table-column prop="tenantCount" label="订阅租户" width="100" align="center" />
        <el-table-column label="默认" width="80" align="center"><template #default="{ row }"><el-tag v-if="row.isDefault === 'Y'" type="success">默认</el-tag><span v-else>-</span></template></el-table-column>
        <el-table-column prop="publishTime" label="发布时间" width="170" />
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">详情</el-button>
            <el-button link type="primary" @click="showVersions(row)">版本比较</el-button>
            <el-button v-if="row.lifecycleStatus === 'DRAFT'" v-permission="'system:plan:edit'" link type="primary" @click="openEditor(row)">编辑</el-button>
            <el-button v-if="row.lifecycleStatus !== 'DRAFT'" v-permission="'system:plan:add'" link type="success" @click="duplicate(row)">复制新版本</el-button>
            <el-button v-if="row.lifecycleStatus === 'DRAFT'" v-permission="'system:plan:publish'" link type="success" @click="publish(row)">发布</el-button>
            <el-dropdown v-if="row.lifecycleStatus !== 'DRAFT'" @command="command => changeStatus(row, command)"><el-button link type="warning">更多</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="DISABLED" :disabled="row.isDefault === 'Y'">停用</el-dropdown-item><el-dropdown-item command="ARCHIVED" :disabled="row.isDefault === 'Y'">归档</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
            <el-button v-if="row.lifecycleStatus === 'DRAFT'" v-permission="'system:plan:remove'" link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer v-model="editor.visible" :title="editor.form.id ? '编辑套餐草稿' : '新增套餐草稿'" size="760px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="editor.form" :rules="rules" label-width="105px">
        <el-tabs v-model="editor.tab">
          <el-tab-pane label="基本信息" name="base">
            <el-row :gutter="16"><el-col :span="12"><el-form-item label="套餐名称" prop="planName"><el-input v-model.trim="editor.form.planName" /></el-form-item></el-col><el-col :span="12"><el-form-item label="套餐编码" prop="planCode"><el-input v-model.trim="editor.form.planCode" :disabled="!!editor.form.id" placeholder="如 BASIC" /></el-form-item></el-col></el-row>
            <el-row :gutter="16"><el-col :span="12"><el-form-item label="版本"><el-input-number v-model="editor.form.version" :min="1" disabled /></el-form-item></el-col><el-col :span="12"><el-form-item label="排序"><el-input-number v-model="editor.form.sortOrder" :min="0" /></el-form-item></el-col></el-row>
            <el-form-item label="套餐说明"><el-input v-model="editor.form.description" type="textarea" :rows="4" /></el-form-item>
          </el-tab-pane>
          <el-tab-pane label="商业展示" name="business">
            <el-row :gutter="16"><el-col :span="12"><el-form-item label="展示价格"><el-input-number v-model="editor.form.displayPrice" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col><el-col :span="12"><el-form-item label="币种"><el-select v-model="editor.form.currency" style="width:100%"><el-option label="人民币 CNY" value="CNY" /><el-option label="美元 USD" value="USD" /></el-select></el-form-item></el-col></el-row>
            <el-row :gutter="16"><el-col :span="12"><el-form-item label="计费周期"><el-select v-model="editor.form.billingCycle" style="width:100%"><el-option v-for="x in cycleOptions" :key="x.value" :label="x.label" :value="x.value" /></el-select></el-form-item></el-col><el-col :span="12"><el-form-item label="试用天数"><el-input-number v-model="editor.form.trialDays" :min="0" /></el-form-item></el-col></el-row>
            <el-alert type="info" :closable="false" title="价格仅用于运营展示，本系统不会生成订单或发起支付。" />
          </el-tab-pane>
          <el-tab-pane label="功能权益" name="features">
            <div class="section-actions"><el-button type="primary" plain @click="addFeature">新增权益</el-button></div>
            <el-table :data="editor.form.features" border><el-table-column label="权益名称"><template #default="{ row }"><el-input v-model.trim="row.featureName" /></template></el-table-column><el-table-column label="权益编码"><template #default="{ row }"><el-input v-model.trim="row.featureKey" placeholder="如 advanced.audit" /></template></el-table-column><el-table-column label="启用" width="80"><template #default="{ row }"><el-switch v-model="row.enabled" /></template></el-table-column><el-table-column label="说明"><template #default="{ row }"><el-input v-model="row.description" /></template></el-table-column><el-table-column width="70"><template #default="{ $index }"><el-button link type="danger" @click="editor.form.features.splice($index, 1)">删除</el-button></template></el-table-column></el-table>
          </el-tab-pane>
          <el-tab-pane label="配额限制" name="quotas">
            <el-table :data="editor.form.quotaDefinitions" border>
              <el-table-column prop="category" label="分类" width="90" />
              <el-table-column label="配额项" min-width="170"><template #default="{ row }"><div>{{ row.quotaName }}</div><small class="muted">{{ row.quotaKey }}</small></template></el-table-column>
              <el-table-column label="额度" width="175"><template #default="{ row }"><el-input-number v-model="row.quotaLimit" :disabled="row.unlimited" :min="0" :controls="false" style="width:115px" /> {{ row.unit }}</template></el-table-column>
              <el-table-column label="无限制" width="90"><template #default="{ row }"><el-switch v-model="row.unlimited" /></template></el-table-column>
              <el-table-column prop="description" label="说明" min-width="180" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </el-form>
      <template #footer><el-button @click="editor.visible=false">取消</el-button><el-button type="primary" :loading="editor.saving" @click="submit">保存草稿</el-button></template>
    </el-drawer>

    <el-dialog v-model="detail.visible" title="套餐详情" width="760px">
      <template v-if="detail.data"><el-descriptions :column="3" border><el-descriptions-item label="名称">{{ detail.data.planName }}</el-descriptions-item><el-descriptions-item label="编码">{{ detail.data.planCode }}</el-descriptions-item><el-descriptions-item label="版本">v{{ detail.data.version }}</el-descriptions-item><el-descriptions-item label="状态">{{ statusText(detail.data.lifecycleStatus) }}</el-descriptions-item><el-descriptions-item label="价格">{{ money(detail.data) }}</el-descriptions-item><el-descriptions-item label="订阅租户">{{ detail.data.tenantCount || 0 }}</el-descriptions-item></el-descriptions><h4>配额</h4><el-table :data="detail.data.quotaDefinitions" border><el-table-column prop="quotaName" label="名称" /><el-table-column prop="quotaKey" label="编码" /><el-table-column label="额度"><template #default="{ row }">{{ row.unlimited === 'Y' ? '无限制' : `${row.quotaLimit} ${row.unit || ''}` }}</template></el-table-column><el-table-column prop="overrideCount" label="覆盖租户" /></el-table><h4>功能权益</h4><el-table :data="detail.data.features" border><el-table-column prop="featureName" label="名称" /><el-table-column prop="featureKey" label="编码" /><el-table-column prop="enabled" label="启用" /><el-table-column prop="description" label="说明" /></el-table></template>
    </el-dialog>
    <el-drawer v-model="versions.visible" title="套餐版本比较" size="760px">
      <el-form inline><el-form-item label="源版本"><el-select v-model="versions.fromId" style="width:220px"><el-option v-for="v in versions.rows" :key="v.id" :label="`v${v.version} · ${statusText(v.lifecycleStatus)}`" :value="v.id" /></el-select></el-form-item><el-form-item label="目标版本"><el-select v-model="versions.toId" style="width:220px"><el-option v-for="v in versions.rows" :key="v.id" :label="`v${v.version} · ${statusText(v.lifecycleStatus)}`" :value="v.id" /></el-select></el-form-item><el-button type="primary" @click="compareVersion">比较</el-button></el-form>
      <el-alert v-if="versions.rows.length < 2" title="当前套餐只有一个版本，复制新版本后可进行比较。" type="info" :closable="false" />
      <template v-if="versions.diff"><h4>基本信息与价格差异</h4><pre class="diff-json">{{ JSON.stringify(versions.diff.basic || versions.diff, null, 2) }}</pre><h4>功能权益差异</h4><pre class="diff-json">{{ JSON.stringify(versions.diff.features || [], null, 2) }}</pre><h4>配额差异</h4><pre class="diff-json">{{ JSON.stringify(versions.diff.quotas || [], null, 2) }}</pre></template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Refresh } from "@element-plus/icons-vue";
import { listPlans, getPlan, savePlan, deletePlan, copyPlan, publishPlan, changePlanLifecycle, getQuotaCatalog, listPlanVersions, comparePlans } from "@/api/plan";

const lifecycleOptions = [{ label: "草稿", value: "DRAFT" }, { label: "已发布", value: "PUBLISHED" }, { label: "已停用", value: "DISABLED" }, { label: "已归档", value: "ARCHIVED" }];
const cycleOptions = [{ label: "月付", value: "MONTH" }, { label: "季付", value: "QUARTER" }, { label: "年付", value: "YEAR" }, { label: "一次性", value: "ONCE" }];
const query = reactive({ planName: "", planCode: "", lifecycleStatus: "", billingCycle: "" });
const rows = ref([]); const loading = ref(false); const catalog = ref([]); const formRef = ref();
const editor = reactive({ visible: false, saving: false, tab: "base", form: {} });
const detail = reactive({ visible: false, data: null });
const versions = reactive({ visible: false, rows: [], fromId: null, toId: null, diff: null });
const rules = { planName: [{ required: true, message: "请输入套餐名称", trigger: "blur" }], planCode: [{ required: true, pattern: /^[A-Za-z][A-Za-z0-9_-]{1,63}$/, message: "编码格式不正确", trigger: "blur" }] };

const emptyForm = () => ({ planName: "", planCode: "", version: 1, description: "", displayPrice: 0, currency: "CNY", billingCycle: "MONTH", trialDays: 0, sortOrder: 0, features: [], quotaDefinitions: catalog.value.map(x => ({ ...x, quotaLimit: 0, unlimited: false })) });
async function load() { loading.value = true; try { rows.value = await listPlans(query); } finally { loading.value = false; } }
function reset() { Object.assign(query, { planName: "", planCode: "", lifecycleStatus: "", billingCycle: "" }); load(); }
async function openEditor(row) {
  try {
    if (!Array.isArray(catalog.value)) catalog.value = await getQuotaCatalog();
    if (!Array.isArray(catalog.value)) throw new Error("配额目录响应格式不正确");
    const data = row ? await getPlan(row.id) : emptyForm();
    editor.form = {
      ...emptyForm(),
      ...data,
      features: (data.features || []).map(x => ({ ...x, enabled: x.enabled === "Y" || x.enabled === true })),
      quotaDefinitions: catalog.value.map(x => {
        const q = (data.quotaDefinitions || []).find(v => v.quotaKey === x.quotaKey);
        return { ...x, ...q, quotaLimit: q?.quotaLimit ?? 0, unlimited: q?.unlimited === "Y" || q?.unlimited === true };
      }),
    };
    editor.tab = "base";
    editor.visible = true;
  } catch (error) {
    ElMessage.error(error?.message || "套餐编辑器打开失败");
  }
}
function addFeature() { editor.form.features.push({ featureName: "", featureKey: "", enabled: true, description: "" }); }
async function submit() { await formRef.value?.validate(); if (editor.form.features.some(x => !x.featureName || !x.featureKey)) return ElMessage.warning("请完整填写功能权益"); editor.saving = true; try { await savePlan(editor.form); editor.visible = false; ElMessage.success("套餐草稿已保存"); await load(); } finally { editor.saving = false; } }
async function showDetail(row) { detail.data = await getPlan(row.id); detail.visible = true; }
async function showVersions(row) { versions.rows = await listPlanVersions(row.id); versions.fromId = versions.rows[1]?.id || versions.rows[0]?.id || null; versions.toId = versions.rows[0]?.id || null; versions.diff = null; versions.visible = true; }
async function compareVersion() { if (!versions.fromId || !versions.toId || versions.fromId === versions.toId) return ElMessage.warning("请选择两个不同版本"); versions.diff = await comparePlans(versions.fromId, versions.toId); }
async function duplicate(row) { await copyPlan(row.id); ElMessage.success("已复制为新版本草稿"); load(); }
async function publish(row) { const makeDefault = await ElMessageBox.confirm("是否将该版本设为默认套餐？", "发布套餐", { distinguishCancelAndClose: true, confirmButtonText: "发布并设为默认", cancelButtonText: "仅发布", type: "warning" }).then(() => true).catch(action => { if (action === "cancel") return false; throw action; }); await publishPlan(row.id, makeDefault); ElMessage.success("套餐已发布"); load(); }
async function changeStatus(row, status) { await ElMessageBox.confirm(`确认将套餐${status === "DISABLED" ? "停用" : "归档"}？已有订阅不会被终止。`, "提示", { type: "warning" }); await changePlanLifecycle(row.id, status); ElMessage.success("状态已更新"); load(); }
async function remove(row) { await ElMessageBox.confirm(`确认删除草稿「${row.planName} v${row.version}」？`, "提示", { type: "warning" }); await deletePlan(row.id); ElMessage.success("草稿已删除"); load(); }
const statusText = s => ({ DRAFT: "草稿", PUBLISHED: "已发布", DISABLED: "已停用", ARCHIVED: "已归档" }[s] || s);
const statusType = s => ({ DRAFT: "info", PUBLISHED: "success", DISABLED: "warning", ARCHIVED: "info" }[s] || "info");
const cycleText = s => cycleOptions.find(x => x.value === s)?.label || s;
const money = row => `${row.currency || "CNY"} ${Number(row.displayPrice || 0).toFixed(2)} / ${cycleText(row.billingCycle)}`;
onMounted(async () => {
  try {
    catalog.value = await getQuotaCatalog();
    await load();
  } catch (error) {
    ElMessage.error(error?.message || "套餐数据加载失败");
  }
});
</script>

<style scoped>
.plan-page{padding:20px}.version{margin-left:8px}.mono{font-family:ui-monospace,Consolas,monospace}.muted{color:#909399}.section-actions{margin-bottom:12px}h4{margin:20px 0 10px}.ry-toolbar{display:flex;justify-content:space-between;margin-bottom:14px}.diff-json{padding:12px;background:#f5f7fa;border-radius:6px;white-space:pre-wrap;word-break:break-all}
</style>
