<template>
  <div class="user-layout">
    <!-- 左栏：部门树 -->
    <aside class="ry-card dept-tree-card">
      <div class="dept-search">
        <el-input
          v-model.trim="deptKeyword"
          placeholder="部门名称"
          :prefix-icon="Search"
          clearable
          size="small"
        />
      </div>
      <div class="dept-tree-wrap">
        <el-tree
          ref="deptTreeRef"
          :data="deptTree"
          :props="{ label: 'label', children: 'children' }"
          :expand-on-click-node="false"
          :filter-node-method="filterDeptNode"
          default-expand-all
          highlight-current
          node-key="id"
          @node-click="handleDeptClick"
        />
      </div>
    </aside>

    <!-- 右栏：用户主区域 -->
    <div class="user-main">
      <!-- 1. 搜索筛选条 -->
      <div class="ry-card ry-search-card">
        <el-form :model="query" inline @submit.prevent>
          <el-form-item label="用户名称">
            <el-input
              v-model.trim="query.username"
              placeholder="请输入用户名称"
              clearable
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="手机号码">
            <el-input
              v-model.trim="query.phone"
              placeholder="请输入手机号码"
              clearable
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item label="状态">
            <el-select
              v-model="query.status"
              placeholder="全部"
              clearable
              style="width: 120px"
            >
              <el-option label="正常" value="0" />
              <el-option label="停用" value="1" />
            </el-select>
          </el-form-item>
          <el-form-item label="创建时间">
            <el-date-picker
              v-model="query.dateRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="-"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
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
            <el-button type="primary" :icon="Plus" @click="handleAdd"
              >新增用户</el-button
            >
            <el-button
              type="success"
              :icon="Edit"
              :disabled="selection.length !== 1"
              @click="handleEditSingle"
              >修改</el-button
            >
            <el-button
              type="danger"
              :icon="Delete"
              :disabled="!selection.length"
              @click="handleBatchDelete"
              >批量删除</el-button
            >
            <el-button :icon="Download" @click="handleExport">导出</el-button>
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
          <el-table-column label="编号" prop="id" width="70" align="center" />
          <el-table-column
            label="用户名称"
            prop="username"
            min-width="120"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <span class="ry-mono">{{ row.username }}</span>
            </template>
          </el-table-column>
          <el-table-column
            label="用户昵称"
            prop="nickname"
            min-width="120"
            show-overflow-tooltip
          />
          <el-table-column
            label="部门"
            prop="deptName"
            min-width="180"
            show-overflow-tooltip
          />
          <el-table-column label="手机号码" prop="phone" width="130" />
          <el-table-column
            label="邮箱"
            prop="email"
            min-width="180"
            show-overflow-tooltip
          />
          <el-table-column label="状态" prop="status" width="90" align="center">
            <template #default="{ row }">
              <el-switch
                :model-value="row.status === '0'"
                :disabled="row.username === 'admin'"
                @change="(val) => handleStatusChange(row, val)"
              />
            </template>
          </el-table-column>
          <el-table-column label="创建时间" prop="createTime" width="180">
            <template #default="{ row }">
              <span class="create-time">{{ row.createTime || "-" }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click="handleEdit(row)"
                >编辑</el-button
              >
              <el-button type="warning" link @click="handleResetPwd(row)"
                >重置密码</el-button
              >
              <el-button
                type="danger"
                link
                :disabled="row.username === 'admin'"
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
    </div>

    <!-- 3. 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.isEdit ? '编辑用户' : '新增用户'"
      width="640px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="用户名称" prop="username">
              <el-input
                v-model.trim="form.username"
                :disabled="dialog.isEdit"
                placeholder="请输入登录账号"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="用户昵称" prop="nickname">
              <el-input
                v-model.trim="form.nickname"
                placeholder="请输入显示昵称"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="所属部门" prop="deptId">
              <el-tree-select
                v-model="form.deptId"
                :data="deptTree"
                :props="{ label: 'label', children: 'children', value: 'id' }"
                :render-after-expand="false"
                :check-strictly="true"
                node-key="id"
                placeholder="请选择部门"
                style="width: 100%"
                default-expand-all
                clearable
                check-on-click-node
                @change="onDeptChange"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号码" prop="phone">
              <el-input
                v-model.trim="form.phone"
                placeholder="请输入手机号码"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model.trim="form.email" placeholder="请输入邮箱" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="form.status">
                <el-radio value="0">正常</el-radio>
                <el-radio value="1">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="!dialog.isEdit" label="登录密码" prop="password">
          <el-input
            v-model.trim="form.password"
            type="password"
            placeholder="请输入登录密码"
            show-password
          />
        </el-form-item>
        <el-form-item label="用户角色" prop="roleIds">
          <el-select
            v-model="form.roleIds"
            multiple
            clearable
            collapse-tags
            collapse-tags-tooltip
            placeholder="请选择角色"
            style="width: 100%"
          >
            <el-option
              v-for="role in roleOptions"
              :key="role.id"
              :label="role.roleName"
              :value="role.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="2"
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

    <!-- 4. 重置密码弹窗 -->
    <el-dialog
      v-model="pwdDialog.visible"
      title="重置密码"
      width="420px"
      @closed="pwdForm.password = ''"
    >
      <el-form label-width="100px">
        <el-form-item label="用户名称">
          <el-input :model-value="pwdForm.username" disabled />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input
            v-model.trim="pwdForm.password"
            type="password"
            placeholder="请输入新密码"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialog.visible = false">取 消</el-button>
        <el-button type="primary" @click="handleResetPwdSubmit"
          >确 定</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Search,
  Refresh,
  Plus,
  Edit,
  Delete,
  Download,
} from "@element-plus/icons-vue";
import {
  listUser,
  addUser,
  updateUser,
  delUser,
  changeUserStatus,
  resetUserPwd,
  getDeptTree,
  getRoleOptionselect,
} from "@/api/system";
import { exportJson } from "@/utils";

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  username: "",
  phone: "",
  status: "",
  deptId: undefined,
  dateRange: [],
});

const list = ref([]);
const total = ref(0);
const loading = ref(false);
const selection = ref([]);

// ===== 部门树 =====
const deptTree = ref([]);
const deptKeyword = ref("");
const deptTreeRef = ref(null);
const roleOptions = ref([]);

watch(deptKeyword, (val) => deptTreeRef.value?.filter(val));

function filterDeptNode(value, data) {
  if (!value) return true;
  return (data.label || "").includes(value);
}

async function loadDeptTree() {
  try {
    const res = await getDeptTree();
    deptTree.value = res.data || res || [];
  } catch (e) {
    deptTree.value = [];
  }
}

async function loadRoleOptions() {
  try {
    const res = await getRoleOptionselect();
    roleOptions.value = res.data || res || [];
  } catch (e) {
    roleOptions.value = [];
    ElMessage.error("角色选项加载失败");
  }
}

function handleDeptClick(node) {
  query.deptId = node.id;
  query.pageNum = 1;
  loadList();
}

// ===== 列表 =====
async function loadList() {
  loading.value = true;
  try {
    const params = { ...query };
    if (query.dateRange?.length === 2) {
      params.beginTime = query.dateRange[0];
      params.endTime = query.dateRange[1];
    }
    delete params.dateRange;
    const res = await listUser(params);
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
  query.username = "";
  query.phone = "";
  query.status = "";
  query.deptId = undefined;
  query.dateRange = [];
  query.pageNum = 1;
  deptTreeRef.value?.setCurrentKey(null);
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
  username: "",
  nickname: "",
  deptId: undefined,
  deptName: "",
  phone: "",
  email: "",
  status: "0",
  password: "",
  roleIds: [],
  remark: "",
});
const form = reactive(defaultForm());
const rules = {
  username: [{ required: true, message: "请输入用户名称", trigger: "blur" }],
  nickname: [{ required: true, message: "请输入用户昵称", trigger: "blur" }],
  deptId: [{ required: true, message: "请选择所属部门", trigger: "change" }],
  password: [{ required: true, message: "请输入登录密码", trigger: "blur" }],
  email: [{ type: "email", message: "邮箱格式不正确", trigger: "blur" }],
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

function handleEdit(row) {
  Object.assign(form, row);
  dialog.isEdit = true;
  dialog.visible = true;
}

function handleEditSingle() {
  if (selection.value.length === 1) handleEdit(selection.value[0]);
}

function onDeptChange(val) {
  const node = findDeptNode(deptTree.value, val);
  form.deptName = node?.deptPath || node?.label || "";
}

function findDeptNode(tree, id) {
  for (const n of tree) {
    if (n.id === id) return n;
    if (n.children?.length) {
      const r = findDeptNode(n.children, id);
      if (r) return r;
    }
  }
  return null;
}

async function handleSubmit() {
  await formRef.value?.validate();
  dialog.submitting = true;
  try {
    if (dialog.isEdit) {
      await updateUser({ ...form });
      ElMessage.success("修改成功");
    } else {
      await addUser({ ...form });
      ElMessage.success("新增成功");
    }
    dialog.visible = false;
    loadList();
  } catch (err) {
    ElMessage.error(err?.message || "保存失败");
  } finally {
    dialog.submitting = false;
  }
}

// ===== 删除 =====
async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除用户「${row.username}」？`, "提示", {
    type: "warning",
  });
  try {
    await delUser(row.id);
    ElMessage.success("删除成功");
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
    await delUser(selection.value.map((r) => r.id));
    ElMessage.success("删除成功");
    loadList();
  } catch (err) {
    ElMessage.error("删除失败");
  }
}

// ===== 状态切换 =====
async function handleStatusChange(row, val) {
  const newStatus = val ? "0" : "1";
  try {
    await changeUserStatus(row.id, newStatus);
    row.status = newStatus;
    ElMessage.success("状态已更新");
  } catch (err) {
    ElMessage.error("状态更新失败");
  }
}

// ===== 重置密码 =====
const pwdDialog = reactive({ visible: false });
const pwdForm = reactive({ id: undefined, username: "", password: "" });

function handleResetPwd(row) {
  pwdForm.id = row.id;
  pwdForm.username = row.username;
  pwdForm.password = "";
  pwdDialog.visible = true;
}

async function handleResetPwdSubmit() {
  if (!pwdForm.password) {
    ElMessage.warning("请输入新密码");
    return;
  }
  if (pwdForm.password.length < 6) {
    ElMessage.warning("密码长度不能少于 6 位");
    return;
  }
  try {
    await resetUserPwd(pwdForm.id, pwdForm.password);
    ElMessage.success("密码重置成功");
    pwdDialog.visible = false;
  } catch (err) {
    ElMessage.error("密码重置失败");
  }
}

// ===== 导出 =====
function handleExport() {
  exportJson(list.value, `users-${Date.now()}.json`);
  ElMessage.success("导出成功");
}

onMounted(() => {
  loadDeptTree();
  loadRoleOptions();
  loadList();
});
</script>

<style lang="scss" scoped>
.user-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.dept-tree-card {
  width: 240px;
  flex-shrink: 0;
  padding: 12px;

  .dept-search {
    margin-bottom: 8px;
  }

  .dept-tree-wrap {
    max-height: calc(100vh - 240px);
    overflow-y: auto;
  }
}

.user-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.ry-mono {
  font-family: var(--ry-font-mono);
  font-size: 13px;
}

.create-time {
  white-space: nowrap;
}

/* 响应式：窄屏隐藏部门树 */
@media (max-width: 992px) {
  .user-layout {
    flex-direction: column;
  }

  .dept-tree-card {
    width: 100%;
  }
}
</style>
