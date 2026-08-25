<template>
  <el-dialog
    :model-value="modelValue"
    class="role-user-dialog"
    width="min(1100px, 95vw)"
    :close-on-click-modal="false"
    :before-close="beforeClose"
    @opened="initialize"
    @closed="resetState"
  >
    <template #header>
      <div class="dialog-heading">
        <div>
          <div class="dialog-title">分配用户</div>
          <div class="dialog-subtitle">
            <strong>{{ role?.roleName || "-" }}</strong>
            <span class="role-key">{{ role?.roleKey || "-" }}</span>
          </div>
        </div>
        <div class="change-summary">
          <span>已分配 {{ effectiveAssignedTotal }}</span>
          <el-tag v-if="addedIds.size" type="success" effect="light"
            >新增 {{ addedIds.size }}</el-tag
          >
          <el-tag v-if="removedIds.size" type="danger" effect="light"
            >移除 {{ removedIds.size }}</el-tag
          >
        </div>
      </div>
    </template>

    <div class="assignment-workspace">
      <section class="user-panel">
        <div class="panel-heading">
          <div>
            <strong>待分配用户</strong>
            <span>共 {{ candidateTotal }} 人</span>
          </div>
          <el-button
            circle
            :icon="Refresh"
            title="刷新"
            @click="loadCandidates"
          />
        </div>
        <UserFilters
          v-model="candidateQuery"
          :dept-tree="deptTree"
          @search="searchCandidates"
          @reset="resetCandidates"
        />
        <el-table
          ref="candidateTableRef"
          v-loading="candidateLoading"
          :data="candidateRows"
          row-key="id"
          height="330"
          @selection-change="candidateSelection = $event"
        >
          <el-table-column
            type="selection"
            width="42"
            :selectable="(row) => row.status === '0'"
          />
          <el-table-column label="用户" min-width="135">
            <template #default="{ row }">
              <div class="user-cell">
                <span>{{ row.nickname || row.username }}</span>
                <small>{{ row.username }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            label="部门"
            prop="deptName"
            min-width="100"
            show-overflow-tooltip
          />
          <el-table-column label="手机" prop="phone" width="112">
            <template #default="{ row }">
              <el-tooltip :content="row.email || '未设置邮箱'" placement="top">
                <span>{{ row.phone || "-" }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="65" align="center">
            <template #default="{ row }">
              <el-tag
                :type="row.status === '0' ? 'success' : 'info'"
                effect="light"
                size="small"
              >
                {{ row.status === "0" ? "正常" : "停用" }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <CompactPager
          v-model:page-num="candidateQuery.pageNum"
          v-model:page-size="candidateQuery.pageSize"
          :total="candidateTotal"
          @change="loadCandidates"
        />
      </section>

      <div class="transfer-actions">
        <el-button
          type="primary"
          :disabled="!candidateSelection.length"
          @click="addSelected"
        >
          添加 {{ candidateSelection.length || "" }}
          <el-icon><ArrowRight /></el-icon>
        </el-button>
        <el-button
          :disabled="!assignedSelection.length"
          @click="removeSelected"
        >
          <el-icon><ArrowLeft /></el-icon> 移除
          {{ assignedSelection.length || "" }}
        </el-button>
      </div>

      <section class="user-panel">
        <div class="panel-heading">
          <div>
            <strong>已分配用户</strong>
            <span>共 {{ assignedTotal }} 人</span>
          </div>
          <el-button
            circle
            :icon="Refresh"
            title="刷新"
            @click="loadAssigned"
          />
        </div>
        <UserFilters
          v-model="assignedQuery"
          :dept-tree="deptTree"
          @search="searchAssigned"
          @reset="resetAssigned"
        />
        <el-table
          ref="assignedTableRef"
          v-loading="assignedLoading"
          :data="assignedRows"
          row-key="id"
          height="330"
          @selection-change="assignedSelection = $event"
        >
          <el-table-column type="selection" width="42" />
          <el-table-column label="用户" min-width="135">
            <template #default="{ row }">
              <div class="user-cell">
                <span>{{ row.nickname || row.username }}</span>
                <small>{{ row.username }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column
            label="部门"
            prop="deptName"
            min-width="100"
            show-overflow-tooltip
          />
          <el-table-column label="手机" prop="phone" width="112">
            <template #default="{ row }">
              <el-tooltip :content="row.email || '未设置邮箱'" placement="top">
                <span>{{ row.phone || "-" }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="65" align="center">
            <template #default="{ row }">
              <el-tag
                :type="row.status === '0' ? 'success' : 'info'"
                effect="light"
                size="small"
              >
                {{ row.status === "0" ? "正常" : "停用" }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <CompactPager
          v-model:page-num="assignedQuery.pageNum"
          v-model:page-size="assignedQuery.pageSize"
          :total="assignedTotal"
          @change="loadAssigned"
        />
      </section>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <span class="draft-tip">所有调整将在点击确定后统一保存</span>
        <div>
          <el-button @click="requestCancel">取消</el-button>
          <el-button
            type="primary"
            :loading="saving"
            :disabled="!dirty"
            @click="save"
          >
            确定<span v-if="dirty"
              >（新增 {{ addedIds.size }} / 移除 {{ removedIds.size }}）</span
            >
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, defineComponent, h, reactive, ref } from "vue";
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElTreeSelect,
} from "element-plus";
import {
  ArrowLeft,
  ArrowRight,
  Refresh,
  Search,
} from "@element-plus/icons-vue";
import { changeRoleUsers, getDeptTree, listRoleUsers } from "@/api/system";

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  role: { type: Object, default: null },
});
const emit = defineEmits(["update:modelValue", "saved"]);

const createQuery = (assigned) =>
  reactive({
    assigned,
    keyword: "",
    phone: "",
    deptId: null,
    status: "",
    pageNum: 1,
    pageSize: 10,
  });
const candidateQuery = createQuery(false);
const assignedQuery = createQuery(true);
const candidateRows = ref([]);
const assignedRows = ref([]);
const candidateTotal = ref(0);
const assignedTotal = ref(0);
const originalAssignedTotal = ref(0);
const candidateLoading = ref(false);
const assignedLoading = ref(false);
const saving = ref(false);
const candidateSelection = ref([]);
const assignedSelection = ref([]);
const candidateTableRef = ref();
const assignedTableRef = ref();
const deptTree = ref([]);
const addedIds = reactive(new Set());
const removedIds = reactive(new Set());
const draftUsers = reactive(new Map());

const dirty = computed(() => addedIds.size > 0 || removedIds.size > 0);
const effectiveAssignedTotal = computed(
  () => originalAssignedTotal.value + addedIds.size - removedIds.size,
);

function normalizedParams(query) {
  const params = { ...query };
  Object.keys(params).forEach((key) => {
    if (params[key] === "" || params[key] == null) delete params[key];
  });
  return params;
}

function matches(user, query) {
  const keyword = query.keyword?.toLowerCase();
  if (
    keyword &&
    !`${user.username || ""} ${user.nickname || ""}`
      .toLowerCase()
      .includes(keyword)
  )
    return false;
  if (query.phone && !(user.phone || "").includes(query.phone)) return false;
  if (query.status && user.status !== query.status) return false;
  if (query.deptId && user.deptId !== query.deptId) return false;
  return true;
}

function mergeDraftRows(baseRows, targetAssigned, query) {
  const hidden = targetAssigned ? removedIds : addedIds;
  const appended = targetAssigned ? addedIds : removedIds;
  const rows = baseRows.filter((row) => !hidden.has(row.id));
  const ids = new Set(rows.map((row) => row.id));
  appended.forEach((id) => {
    const user = draftUsers.get(id);
    if (user && !ids.has(id) && matches(user, query)) rows.unshift(user);
  });
  return rows.slice(0, query.pageSize);
}

function adjustedTotal(serverTotal, targetAssigned, query) {
  const added = [...(targetAssigned ? addedIds : removedIds)].filter((id) =>
    matches(draftUsers.get(id) || {}, query),
  ).length;
  const hidden = [...(targetAssigned ? removedIds : addedIds)].filter((id) =>
    matches(draftUsers.get(id) || {}, query),
  ).length;
  return Math.max(0, serverTotal + added - hidden);
}

async function loadCandidates() {
  if (!props.role?.id) return;
  candidateLoading.value = true;
  try {
    const res = await listRoleUsers(
      props.role.id,
      normalizedParams(candidateQuery),
    );
    candidateRows.value = mergeDraftRows(res.rows || [], false, candidateQuery);
    candidateTotal.value = adjustedTotal(
      Number(res.total || 0),
      false,
      candidateQuery,
    );
    candidateSelection.value = [];
  } finally {
    candidateLoading.value = false;
  }
}

async function loadAssigned(trackInitial = false) {
  if (!props.role?.id) return;
  assignedLoading.value = true;
  try {
    const res = await listRoleUsers(
      props.role.id,
      normalizedParams(assignedQuery),
    );
    if (trackInitial) originalAssignedTotal.value = Number(res.total || 0);
    assignedRows.value = mergeDraftRows(res.rows || [], true, assignedQuery);
    assignedTotal.value = adjustedTotal(
      Number(res.total || 0),
      true,
      assignedQuery,
    );
    assignedSelection.value = [];
  } finally {
    assignedLoading.value = false;
  }
}

async function initialize() {
  if (!deptTree.value.length) {
    const res = await getDeptTree();
    deptTree.value = res.data || res || [];
  }
  await Promise.all([loadCandidates(), loadAssigned(true)]);
}

function remember(rows) {
  rows.forEach((row) => draftUsers.set(row.id, row));
}
function addSelected() {
  remember(candidateSelection.value);
  candidateSelection.value.forEach((user) => {
    if (removedIds.has(user.id)) {
      removedIds.delete(user.id);
    } else {
      addedIds.add(user.id);
    }
  });
  candidateTableRef.value?.clearSelection();
  Promise.all([loadCandidates(), loadAssigned()]);
}
function removeSelected() {
  remember(assignedSelection.value);
  assignedSelection.value.forEach((user) => {
    if (addedIds.has(user.id)) {
      addedIds.delete(user.id);
    } else {
      removedIds.add(user.id);
    }
  });
  assignedTableRef.value?.clearSelection();
  Promise.all([loadCandidates(), loadAssigned()]);
}

function searchCandidates() {
  candidateQuery.pageNum = 1;
  loadCandidates();
}
function searchAssigned() {
  assignedQuery.pageNum = 1;
  loadAssigned();
}
function resetQuery(query, loader) {
  Object.assign(query, {
    keyword: "",
    phone: "",
    deptId: null,
    status: "",
    pageNum: 1,
    pageSize: 10,
  });
  loader();
}
function resetCandidates() {
  resetQuery(candidateQuery, loadCandidates);
}
function resetAssigned() {
  resetQuery(assignedQuery, loadAssigned);
}

async function save() {
  if (!dirty.value || saving.value) return;
  saving.value = true;
  try {
    await changeRoleUsers({
      roleId: props.role.id,
      addUserIds: [...addedIds],
      removeUserIds: [...removedIds],
    });
    ElMessage.success("用户分配已保存");
    addedIds.clear();
    removedIds.clear();
    draftUsers.clear();
    emit("saved");
    emit("update:modelValue", false);
  } catch (error) {
    ElMessage.error(error?.message || "用户分配保存失败");
  } finally {
    saving.value = false;
  }
}

function beforeClose(done) {
  if (!dirty.value) return done();
  ElMessageBox.confirm(
    "当前分配结果尚未保存，确定放弃本次调整吗？",
    "未保存的调整",
    { type: "warning" },
  )
    .then(done)
    .catch(() => {});
}
function requestCancel() {
  if (!dirty.value) return emit("update:modelValue", false);
  beforeClose(() => emit("update:modelValue", false));
}
function resetState() {
  addedIds.clear();
  removedIds.clear();
  draftUsers.clear();
  candidateRows.value = [];
  assignedRows.value = [];
  candidateSelection.value = [];
  assignedSelection.value = [];
  Object.assign(candidateQuery, {
    assigned: false,
    keyword: "",
    phone: "",
    deptId: null,
    status: "",
    pageNum: 1,
    pageSize: 10,
  });
  Object.assign(assignedQuery, {
    assigned: true,
    keyword: "",
    phone: "",
    deptId: null,
    status: "",
    pageNum: 1,
    pageSize: 10,
  });
  candidateTotal.value = 0;
  assignedTotal.value = 0;
  originalAssignedTotal.value = 0;
}

const UserFilters = defineComponent({
  props: {
    modelValue: { type: Object, required: true },
    deptTree: { type: Array, default: () => [] },
  },
  emits: ["search", "reset"],
  setup(p, { emit: childEmit }) {
    return () =>
      h(
        ElForm,
        {
          inline: true,
          class: "panel-filters",
          onSubmit: (e) => e.preventDefault(),
        },
        () => [
          h(ElFormItem, null, () =>
            h(ElInput, {
              modelValue: p.modelValue.keyword,
              "onUpdate:modelValue": (v) => (p.modelValue.keyword = v),
              placeholder: "用户名或昵称",
              clearable: true,
              onKeyup: (e) => e.key === "Enter" && childEmit("search"),
            }),
          ),
          h(ElFormItem, null, () =>
            h(ElInput, {
              modelValue: p.modelValue.phone,
              "onUpdate:modelValue": (v) => (p.modelValue.phone = v),
              placeholder: "手机号",
              clearable: true,
            }),
          ),
          h(ElFormItem, null, () =>
            h(ElTreeSelect, {
              modelValue: p.modelValue.deptId,
              "onUpdate:modelValue": (v) => (p.modelValue.deptId = v),
              data: p.deptTree,
              props: { label: "label", children: "children", value: "id" },
              checkStrictly: true,
              clearable: true,
              placeholder: "部门",
            }),
          ),
          h(ElFormItem, null, () =>
            h(
              ElSelect,
              {
                modelValue: p.modelValue.status,
                "onUpdate:modelValue": (v) => (p.modelValue.status = v),
                placeholder: "状态",
                clearable: true,
              },
              () => [
                h(ElOption, { label: "正常", value: "0" }),
                h(ElOption, { label: "停用", value: "1" }),
              ],
            ),
          ),
          h(ElFormItem, { class: "filter-actions" }, () => [
            h(
              ElButton,
              {
                type: "primary",
                icon: Search,
                onClick: () => childEmit("search"),
              },
              () => "查询",
            ),
            h(ElButton, { onClick: () => childEmit("reset") }, () => "重置"),
          ]),
        ],
      );
  },
});

const CompactPager = defineComponent({
  props: { pageNum: Number, pageSize: Number, total: Number },
  emits: ["update:pageNum", "update:pageSize", "change"],
  setup(p, { emit: childEmit }) {
    return () =>
      h(ElPagination, {
        class: "compact-pager",
        small: true,
        background: true,
        total: p.total,
        currentPage: p.pageNum,
        pageSize: p.pageSize,
        pageSizes: [10, 20, 50],
        layout: "total, sizes, prev, pager, next",
        "onUpdate:currentPage": (v) => childEmit("update:pageNum", v),
        "onUpdate:pageSize": (v) => childEmit("update:pageSize", v),
        onCurrentChange: () => childEmit("change"),
        onSizeChange: () => childEmit("change"),
      });
  },
});
</script>

<style scoped lang="scss">
.dialog-heading,
.dialog-footer,
.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.dialog-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--ry-neutral-900, #1f2937);
}
.dialog-subtitle {
  display: flex;
  gap: 10px;
  margin-top: 6px;
  color: var(--ry-neutral-500, #6b7280);
  font-size: 13px;
}
.role-key {
  font-family: var(--ry-font-mono);
  padding-left: 10px;
  border-left: 1px solid var(--el-border-color);
}
.change-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--ry-neutral-600, #4b5563);
  font-size: 13px;
  padding-right: 28px;
}
.assignment-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 104px minmax(0, 1fr);
  gap: 14px;
  align-items: center;
}
.user-panel {
  min-width: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
  overflow: hidden;
}
.panel-heading {
  padding: 12px 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-light);
}
.panel-heading > div {
  display: flex;
  align-items: center;
  gap: 8px;
}
.panel-heading span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.transfer-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.transfer-actions .el-button {
  margin: 0;
  width: 100%;
}
.panel-filters {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 12px 12px 4px;
}
.panel-filters :deep(.el-form-item) {
  margin: 0;
}
.panel-filters :deep(.el-input),
.panel-filters :deep(.el-select),
.panel-filters :deep(.el-tree-select) {
  width: 100%;
}
.filter-actions {
  grid-column: 1/-1;
}
.user-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}
.user-cell small {
  color: var(--el-text-color-secondary);
  font-size: 11px;
}
.compact-pager {
  justify-content: flex-end;
  padding: 10px 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.dialog-footer .draft-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
@media (max-width: 900px) {
  .assignment-workspace {
    grid-template-columns: 1fr;
  }
  .transfer-actions {
    flex-direction: row;
    justify-content: center;
  }
  .transfer-actions .el-button {
    width: auto;
  }
  .transfer-actions .el-button:first-child :deep(.el-icon) {
    transform: rotate(90deg);
  }
  .transfer-actions .el-button:last-child :deep(.el-icon) {
    transform: rotate(90deg);
  }
}
@media (max-width: 600px) {
  .dialog-heading,
  .dialog-footer {
    align-items: flex-start;
    flex-direction: column;
  }
  .change-summary {
    padding-right: 0;
    flex-wrap: wrap;
  }
  .panel-filters {
    grid-template-columns: 1fr;
  }
  .filter-actions {
    grid-column: auto;
  }
  .dialog-footer > div {
    display: flex;
    width: 100%;
  }
  .dialog-footer > div .el-button {
    flex: 1;
  }
  .compact-pager {
    overflow-x: auto;
    justify-content: flex-start;
  }
}
</style>
