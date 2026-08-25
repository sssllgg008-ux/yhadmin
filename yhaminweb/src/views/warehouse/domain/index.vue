<template>
  <div class="warehouse-page">
    <section class="warehouse-hero ry-card">
      <div class="hero-main">
        <div class="hero-icon">
          <el-icon><Grid /></el-icon>
        </div>
        <div>
          <h2>业务领域</h2>
          <p>
            按业务边界组织维度、指标、事实表和数据集，统一管理数据模型资产。
          </p>
        </div>
      </div>
      <div class="hero-stats">
        <div class="stat-item">
          <strong>{{ rows.length }}</strong
          ><span>领域总数</span>
        </div>
        <div class="stat-item">
          <strong>{{ enabledCount }}</strong
          ><span>已启用</span>
        </div>
        <div class="stat-item">
          <strong>{{ rows.length - enabledCount }}</strong
          ><span>已停用</span>
        </div>
      </div>
    </section>

    <div class="ry-card ry-search-card">
      <el-form inline @submit.prevent>
        <el-form-item label="关键词"
          ><el-input
            v-model.trim="query.keyword"
            :prefix-icon="Search"
            placeholder="领域名称 / 编码 / 负责人"
            clearable
        /></el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.enabled"
            placeholder="全部状态"
            clearable
            style="width: 150px"
          >
            <el-option label="已启用" :value="true" /><el-option
              label="已停用"
              :value="false"
            />
          </el-select>
        </el-form-item>
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
            >新增领域</el-button
          ><span class="toolbar-hint">用于承载同一业务范围内的数据模型</span>
        </div>
        <div class="ry-toolbar-right">
          <span class="result-count">共 {{ filteredRows.length }} 项</span
          ><el-tooltip content="刷新"
            ><el-button circle :icon="Refresh" @click="load"
          /></el-tooltip>
        </div>
      </div>
      <el-table v-loading="loading" :data="filteredRows" border stripe>
        <el-table-column label="领域" min-width="240">
          <template #default="{ row }"
            ><div class="model-cell">
              <div class="model-avatar">{{ initial(row.domainName) }}</div>
              <div class="model-info">
                <span class="model-name">{{ row.domainName }}</span
                ><span class="model-code">{{
                  row.domainCode?.value || "—"
                }}</span>
              </div>
            </div></template
          >
        </el-table-column>
        <el-table-column label="负责人" width="180"
          ><template #default="{ row }"
            ><span class="owner-chip"
              ><el-icon><User /></el-icon
              >{{ ownerLabel(row.ownerUserId) }}</span
            ></template
          ></el-table-column
        >
        <el-table-column label="状态" width="110" align="center"
          ><template #default="{ row }"
            ><el-tag
              :type="row.enabled ? 'success' : 'info'"
              effect="light"
              round
              ><span class="status-dot" />{{
                row.enabled ? "已启用" : "已停用"
              }}</el-tag
            ></template
          ></el-table-column
        >
        <el-table-column
          prop="description"
          label="说明"
          min-width="260"
          show-overflow-tooltip
          ><template #default="{ row }"
            ><span class="description">{{
              row.description || "暂无说明"
            }}</span></template
          ></el-table-column
        >
        <el-table-column label="操作" width="150" fixed="right" align="center"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="open(row)">编辑</el-button
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
            description="暂无业务领域，点击“新增领域”开始构建"
            :image-size="88"
        /></template>
      </el-table>
    </div>

    <el-dialog
      v-model="visible"
      :title="form.id ? '编辑业务领域' : '新增业务领域'"
      width="640px"
      :close-on-click-modal="false"
      @closed="formRef?.clearValidate()"
    >
      <div class="dialog-tip">
        <el-icon><InfoFilled /></el-icon
        ><span>领域编码保存后不可修改，建议使用简洁、稳定的英文标识。</span>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-row :gutter="18">
          <el-col :span="12"
            ><el-form-item label="领域名称" prop="name"
              ><el-input
                v-model.trim="form.name"
                placeholder="例如：客户经营" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="领域编码" prop="code"
              ><el-input
                v-model.trim="form.code"
                :disabled="!!form.id"
                placeholder="例如：customer" /></el-form-item
          ></el-col>
        </el-row>
        <el-row :gutter="18">
          <el-col :span="12">
            <el-form-item label="负责人" prop="ownerUserId">
              <el-select
                v-model="form.ownerUserId"
                :loading="userLoading"
                filterable
                clearable
                placeholder="请选择负责人"
                style="width: 100%"
              >
                <el-option
                  v-for="user in users"
                  :key="user.id"
                  :label="userLabel(user)"
                  :value="user.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"
            ><el-form-item label="启用状态"
              ><el-switch
                v-model="form.enabled"
                inline-prompt
                active-text="启"
                inactive-text="停" /></el-form-item
          ></el-col>
        </el-row>
        <el-form-item label="领域说明"
          ><el-input
            v-model="form.description"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="说明该领域的业务范围和数据边界"
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="visible = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="submit"
          >保存</el-button
        ></template
      >
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import {
  Grid,
  InfoFilled,
  Plus,
  Refresh,
  Search,
  User,
} from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { deleteDomain, listDomains, saveDomain } from "@/api/warehouse";
import { listUser } from "@/api/system";

const rows = ref([]),
  loading = ref(false),
  saving = ref(false),
  visible = ref(false),
  formRef = ref();
const users = ref([]),
  userLoading = ref(false);
const query = reactive({ keyword: "", enabled: undefined });
const form = reactive({
  id: undefined,
  code: "",
  name: "",
  description: "",
  ownerUserId: undefined,
  enabled: true,
});
const rules = {
  name: [{ required: true, message: "请输入领域名称", trigger: "blur" }],
  code: [
    { required: true, message: "请输入领域编码", trigger: "blur" },
    {
      pattern: /^[a-z][a-z0-9_]*$/,
      message: "请使用小写字母、数字或下划线，并以字母开头",
      trigger: "blur",
    },
  ],
};
const enabledCount = computed(
  () => rows.value.filter((item) => item.enabled).length,
);
const filteredRows = computed(() =>
  rows.value.filter((item) => {
    const keyword = query.keyword.toLowerCase();
    const text =
      `${item.domainName || ""} ${item.domainCode?.value || ""} ${item.ownerUserId || ""}`.toLowerCase();
    return (
      (!keyword || text.includes(keyword)) &&
      (query.enabled === undefined ||
        query.enabled === "" ||
        item.enabled === query.enabled)
    );
  }),
);
function initial(name) {
  return (name || "域").slice(0, 1);
}
function userLabel(user) {
  return `${user.nickname || user.username || `用户${user.id}`}（${user.username || user.id}）`;
}
function ownerLabel(userId) {
  if (!userId) return "未设置";
  const user = users.value.find((item) => item.id === userId);
  return user ? user.nickname || user.username : `用户 ${userId}`;
}
function reset() {
  query.keyword = "";
  query.enabled = undefined;
}
async function load() {
  loading.value = true;
  try {
    rows.value = (await listDomains()) || [];
  } finally {
    loading.value = false;
  }
}
async function loadUsers() {
  userLoading.value = true;
  try {
    const response = await listUser({
      pageNum: 1,
      pageSize: 1000,
      status: "0",
    });
    users.value = response.rows || [];
  } finally {
    userLoading.value = false;
  }
}
function open(row) {
  Object.assign(form, {
    id: row?.id,
    code: row?.domainCode?.value || "",
    name: row?.domainName || "",
    description: row?.description || "",
    ownerUserId: row?.ownerUserId,
    enabled: row?.enabled ?? true,
  });
  visible.value = true;
}
async function submit() {
  await formRef.value?.validate();
  saving.value = true;
  try {
    await saveDomain({ ...form });
    ElMessage.success("业务领域已保存");
    visible.value = false;
    await load();
  } finally {
    saving.value = false;
  }
}
async function remove(row) {
  await ElMessageBox.confirm(
    `删除领域“${row.domainName}”后不可恢复，是否继续？`,
    "删除确认",
    { type: "warning", confirmButtonText: "确认删除" },
  );
  await deleteDomain(row.id);
  ElMessage.success("业务领域已删除");
  await load();
}
onMounted(() => Promise.allSettled([load(), loadUsers()]));
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
  overflow: hidden;
}
.hero-main {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}
.hero-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  flex: 0 0 48px;
  border-radius: 10px;
  background: var(--ry-gradient-primary);
  color: #fff;
  box-shadow: var(--ry-shadow-primary-glow);
  font-size: 23px;
}
h2 {
  margin: 0 0 6px;
  color: var(--ry-foreground);
  font-size: 20px;
}
p {
  margin: 0;
  color: var(--ry-muted-foreground);
  font-size: 13px;
}
.hero-stats {
  display: flex;
  align-items: center;
}
.stat-item {
  min-width: 88px;
  padding: 0 24px;
  text-align: center;
  border-left: 1px solid var(--ry-border-light);
}
.stat-item strong {
  display: block;
  color: var(--ry-foreground);
  font: 600 22px/1.2 var(--ry-font-numeric);
}
.stat-item span {
  display: block;
  margin-top: 5px;
  color: var(--ry-muted-foreground);
  font-size: 12px;
}
.ry-search-card {
  margin-bottom: 0;
}
.ry-search-card :deep(.el-form-item) {
  margin-bottom: 0;
}
.ry-search-card :deep(.el-input) {
  width: 260px;
}
.toolbar-hint,
.result-count {
  color: var(--ry-muted-foreground);
  font-size: 12px;
}
.model-cell {
  display: flex;
  align-items: center;
  gap: 11px;
}
.model-avatar {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: var(--ry-primary-50);
  color: var(--ry-primary);
  font-weight: 600;
}
.model-info {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}
.model-name {
  color: var(--ry-foreground);
  font-weight: 500;
}
.model-code {
  color: var(--ry-muted-foreground);
  font: 12px var(--ry-font-mono);
}
.owner-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--ry-neutral-600);
}
.description {
  color: var(--ry-neutral-600);
}
.status-dot {
  display: inline-block;
  width: 5px;
  height: 5px;
  margin-right: 5px;
  border-radius: 50%;
  background: currentColor;
  vertical-align: 2px;
}
.dialog-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: -4px 0 20px;
  padding: 10px 12px;
  border: 1px solid var(--ry-primary-200);
  border-radius: 6px;
  background: var(--ry-primary-50);
  color: var(--ry-primary);
  font-size: 13px;
}
@media (max-width: 900px) {
  .hero-stats {
    display: none;
  }
}
</style>
