<template>
  <div class="ry-page">
    <!-- 1. 搜索筛选条 -->
    <div class="ry-card ry-search-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="角色名称">
          <el-input
            v-model.trim="query.roleName"
            placeholder="请输入角色名称"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="权限字符">
          <el-input
            v-model.trim="query.roleKey"
            placeholder="请输入权限字符"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="角色状态"
            clearable
            style="width: 140px"
          >
            <el-option label="正常" value="0" />
            <el-option label="停用" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建时间">
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
          <el-button type="primary" :icon="Plus" @click="handleAdd"
            >新增</el-button
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
        <el-table-column label="角色编号" prop="id" width="90" align="center" />
        <el-table-column
          label="角色名称"
          prop="roleName"
          min-width="140"
          show-overflow-tooltip
        />
        <el-table-column label="权限字符" prop="roleKey" min-width="140">
          <template #default="{ row }">
            <span class="ry-mono">{{ row.roleKey }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="显示顺序"
          prop="sort"
          width="90"
          align="center"
        />
        <el-table-column label="状态" prop="status" width="90" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.status === '0' ? 'success' : 'danger'"
              effect="light"
            >
              {{ row.status === "0" ? "正常" : "停用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="170" />
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }">
            <div class="role-actions">
              <el-button type="primary" link @click="handleEdit(row)"
                >修改</el-button
              >
              <el-divider direction="vertical" />
              <el-button type="danger" link @click="handleDelete(row)"
                >删除</el-button
              >
              <el-divider direction="vertical" />
              <el-button type="primary" link @click="handleDataScope(row)"
                >数据权限</el-button
              >
              <el-divider direction="vertical" />
              <el-button type="primary" link @click="handleAuthUser(row)"
                >分配用户</el-button
              >
            </div>
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
      :title="dialog.isEdit ? '修改角色' : '新增角色'"
      width="640px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="角色名称" prop="roleName">
              <el-input
                v-model.trim="form.roleName"
                placeholder="请输入角色名称"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="权限字符" prop="roleKey">
              <el-input
                v-model.trim="form.roleKey"
                placeholder="如 system:user:list"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="显示顺序" prop="sort">
              <el-input-number
                v-model="form.sort"
                :min="0"
                controls-position="right"
                style="width: 100%"
              />
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
        <el-form-item label="备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注"
          />
        </el-form-item>
        <el-form-item label="菜单权限">
          <div class="data-scope-toolbar">
            <label class="data-scope-checkbox">
              <input
                type="checkbox"
                v-model="dialog.isExpandAll"
                @change="toggleMenuExpandAll"
              />
              展开/折叠
            </label>
            <label class="data-scope-checkbox">
              <input
                type="checkbox"
                v-model="dialog.isSelectAll"
                @change="toggleMenuSelectAll"
              />
              全选/全不选
            </label>
            <label class="data-scope-checkbox">
              <input type="checkbox" v-model="dialog.isParentChildLink" />
              父子联动
            </label>
          </div>
          <div class="menu-tree-wrap">
            <el-tree
              ref="menuTreeRef"
              :data="menuTreeData"
              :props="menuTreeProps"
              show-checkbox
              node-key="id"
              :default-expand-all="dialog.isExpandAll"
              :default-checked-keys="dialog.menuIds"
              :check-strictly="!dialog.isParentChildLink"
              @check-change="handleMenuCheckChange"
            />
          </div>
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

    <!-- 4. 数据权限弹窗 -->
    <el-dialog
      v-model="dataScopeDialog.visible"
      title="分配数据权限"
      width="600px"
    >
      <el-form label-width="100px">
        <el-form-item label="角色名称">
          <span>{{ dataScopeDialog.row?.roleName }}</span>
        </el-form-item>
        <el-form-item label="权限字符">
          <span>{{ dataScopeDialog.row?.roleKey }}</span>
        </el-form-item>
        <el-form-item label="权限范围">
          <el-select
            v-model="dataScopeDialog.dataScope"
            style="width: 100%"
            @change="handleDataScopeChange"
          >
            <el-option label="全部数据权限" :value="1" />
            <el-option label="自定义数据权限" :value="2" />
            <el-option label="本部门数据权限" :value="3" />
            <el-option label="本部门及以下数据权限" :value="4" />
            <el-option label="仅本人数据权限" :value="5" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据权限" v-if="dataScopeDialog.dataScope === 2">
          <div class="data-scope-toolbar">
            <label class="data-scope-checkbox">
              <input
                type="checkbox"
                v-model="dataScopeDialog.isExpandAll"
                @change="toggleExpandAll"
              />
              展开/折叠
            </label>
            <label class="data-scope-checkbox">
              <input
                type="checkbox"
                v-model="dataScopeDialog.isSelectAll"
                @change="toggleSelectAll"
              />
              全选/全不选
            </label>
            <label class="data-scope-checkbox">
              <input
                type="checkbox"
                v-model="dataScopeDialog.isParentChildLink"
              />
              父子联动
            </label>
          </div>
          <el-tree
            ref="deptTreeRef"
            :data="deptTreeData"
            :props="treeProps"
            show-checkbox
            node-key="id"
            :default-expand-all="dataScopeDialog.isExpandAll"
            :checked-keys="dataScopeDialog.deptIds"
            :check-strictly="!dataScopeDialog.isParentChildLink"
            @check-change="handleDeptCheckChange"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dataScopeDialog.visible = false">取 消</el-button>
        <el-button type="primary" @click="confirmDataScope">确 定</el-button>
      </template>
    </el-dialog>

    <RoleUserAssignmentDialog
      v-model="authUserDialog.visible"
      :role="authUserDialog.row"
      @saved="loadList"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from "vue";
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
  listRole,
  addRole,
  updateRole,
  delRole,
  getDeptTree,
  getMenuTreeselect,
  getAuthMenu,
  authMenu,
} from "@/api/system";
import { exportJson } from "@/utils";
import RoleUserAssignmentDialog from "./RoleUserAssignmentDialog.vue";

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  roleName: "",
  roleKey: "",
  status: "",
});
const dateRange = ref([]);

const list = ref([]);
const total = ref(0);
const loading = ref(false);
const selection = ref([]);

const deptTreeData = ref([]);
const menuTreeData = ref([]);
const treeProps = { label: "label", children: "children" };
const menuTreeProps = { label: "menuName", children: "children" };
const deptTreeRef = ref(null);
const menuTreeRef = ref(null);

async function loadList() {
  loading.value = true;
  try {
    const params = { ...query };
    if (dateRange.value?.length === 2) {
      params.beginTime = dateRange.value[0];
      params.endTime = dateRange.value[1];
    }
    const res = await listRole(params);
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
  query.roleName = "";
  query.roleKey = "";
  query.status = "";
  dateRange.value = [];
  query.pageNum = 1;
  loadList();
}

function handleSelectionChange(rows) {
  selection.value = rows;
}

// ===== 新增/编辑 =====
const dialog = reactive({
  visible: false,
  isEdit: false,
  submitting: false,
  isExpandAll: true,
  isSelectAll: false,
  isParentChildLink: true,
  menuIds: [],
});
const formRef = ref(null);
const defaultForm = () => ({
  id: undefined,
  roleName: "",
  roleKey: "",
  sort: 0,
  status: "0",
  remark: "",
});
const form = reactive(defaultForm());
const rules = {
  roleName: [{ required: true, message: "请输入角色名称", trigger: "blur" }],
  roleKey: [{ required: true, message: "请输入权限字符", trigger: "blur" }],
  sort: [{ required: true, message: "请输入显示顺序", trigger: "blur" }],
};

function resetForm() {
  Object.assign(form, defaultForm());
  formRef.value?.clearValidate();
  dialog.isExpandAll = true;
  dialog.isSelectAll = false;
  dialog.isParentChildLink = true;
  dialog.menuIds = [];
}

async function handleAdd() {
  resetForm();
  dialog.isEdit = false;
  if (!menuTreeData.value.length) {
    const res = await getMenuTreeselect();
    menuTreeData.value = res.data || [];
  }
  dialog.visible = true;
  nextTick(() => {
    menuTreeRef.value?.setCheckedKeys([]);
  });
}

async function handleEdit(row) {
  Object.assign(form, row);
  dialog.isEdit = true;
  if (!menuTreeData.value.length) {
    const res = await getMenuTreeselect();
    menuTreeData.value = res.data || [];
  }
  const authRes = await getAuthMenu(row.id);
  dialog.menuIds = authRes.data || [];
  dialog.isExpandAll = true;
  dialog.isSelectAll = false;
  dialog.isParentChildLink = true;
  dialog.visible = true;
  nextTick(() => {
    menuTreeRef.value?.setCheckedKeys(dialog.menuIds);
  });
}

function handleEditSingle() {
  if (selection.value.length === 1) handleEdit(selection.value[0]);
}

async function handleSubmit() {
  await formRef.value?.validate();
  dialog.submitting = true;
  try {
    if (dialog.isEdit) {
      await updateRole({ ...form });
      ElMessage.success("修改成功");
    } else {
      const res = await addRole({ ...form });
      form.id = res.data?.id || res.data;
      ElMessage.success("新增成功");
    }
    await authMenu({ roleId: form.id, menuIds: dialog.menuIds });
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
  await ElMessageBox.confirm(`确认删除角色「${row.roleName}」？`, "提示", {
    type: "warning",
  });
  try {
    await delRole(row.id);
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
    await delRole(selection.value.map((r) => r.id));
    ElMessage.success("删除成功");
    loadList();
  } catch (err) {
    ElMessage.error("删除失败");
  }
}

// ===== 数据权限 =====
const dataScopeDialog = reactive({
  visible: false,
  row: null,
  dataScope: 1,
  deptIds: [],
  isExpandAll: true,
  isSelectAll: false,
  isParentChildLink: true,
});

async function handleDataScope(row) {
  dataScopeDialog.row = row;
  dataScopeDialog.dataScope = row.dataScope || 1;
  dataScopeDialog.deptIds = row.deptIds || [];
  dataScopeDialog.isExpandAll = true;
  dataScopeDialog.isSelectAll = false;
  dataScopeDialog.isParentChildLink = true;
  if (!deptTreeData.value.length) {
    const res = await getDeptTree();
    deptTreeData.value = res.data || [];
  }
  dataScopeDialog.visible = true;
}

function handleDataScopeChange(val) {
  if (val !== 2) {
    dataScopeDialog.deptIds = [];
  }
}

function toggleExpandAll() {
  if (deptTreeRef.value) {
    if (dataScopeDialog.isExpandAll) {
      deptTreeRef.value.expandAll();
    } else {
      deptTreeRef.value.collapseAll();
    }
  }
}

function toggleSelectAll() {
  if (deptTreeRef.value) {
    if (dataScopeDialog.isSelectAll) {
      deptTreeRef.value.setCheckedKeys(getAllDeptIds(deptTreeData.value));
    } else {
      deptTreeRef.value.setCheckedKeys([]);
    }
  }
}

function getAllDeptIds(tree) {
  const ids = [];
  function traverse(nodes) {
    nodes.forEach((node) => {
      ids.push(node.id);
      if (node.children) traverse(node.children);
    });
  }
  traverse(tree);
  return ids;
}

function handleDeptCheckChange() {
  if (deptTreeRef.value) {
    dataScopeDialog.deptIds = deptTreeRef.value.getCheckedKeys();
  }
}

async function confirmDataScope() {
  if (!dataScopeDialog.row) return;
  try {
    await updateRole({
      ...dataScopeDialog.row,
      dataScope: dataScopeDialog.dataScope,
      deptIds: dataScopeDialog.deptIds,
    });
    dataScopeDialog.row.dataScope = dataScopeDialog.dataScope;
    dataScopeDialog.row.deptIds = dataScopeDialog.deptIds;
    ElMessage.success("数据权限已保存");
    dataScopeDialog.visible = false;
    loadList();
  } catch (err) {
    ElMessage.error("保存失败");
  }
}

function toggleMenuExpandAll() {
  if (menuTreeRef.value) {
    const tree = menuTreeRef.value;
    const expand = dialog.isExpandAll;
    function walk(nodes) {
      nodes.forEach((node) => {
        const treeNode = tree.store?.nodesMap?.[node.id];
        if (treeNode) treeNode.expanded = expand;
        if (node.children?.length) walk(node.children);
      });
    }
    walk(menuTreeData.value);
  }
}

function toggleMenuSelectAll() {
  if (menuTreeRef.value) {
    if (dialog.isSelectAll) {
      menuTreeRef.value.setCheckedKeys(getAllMenuIds(menuTreeData.value));
    } else {
      menuTreeRef.value.setCheckedKeys([]);
    }
    handleMenuCheckChange();
  }
}

function getAllMenuIds(tree) {
  const ids = [];
  function traverse(nodes) {
    nodes.forEach((node) => {
      ids.push(node.id);
      if (node.children) traverse(node.children);
    });
  }
  traverse(tree);
  return ids;
}

function handleMenuCheckChange() {
  if (menuTreeRef.value) {
    dialog.menuIds = menuTreeRef.value.getCheckedKeys();
  }
}

// ===== 分配用户 =====
const authUserDialog = reactive({ visible: false, row: null });

function handleAuthUser(row) {
  authUserDialog.row = row;
  authUserDialog.visible = true;
}

// ===== 导出 =====
function handleExport() {
  exportJson(list.value, `role-${Date.now()}.json`);
  ElMessage.success("导出成功");
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

.role-actions {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  white-space: nowrap;
}

.role-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.data-scope-toolbar {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.data-scope-checkbox {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--ry-neutral-600);
  cursor: pointer;
  user-select: none;

  input[type="checkbox"] {
    width: 14px;
    height: 14px;
    cursor: pointer;
  }
}

.menu-tree-wrap {
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 4px;
  padding: 8px 12px;
  max-height: 320px;
  overflow-y: auto;
  background: var(--el-fill-color-blank, #fff);
  width: 100%;
  box-sizing: border-box;
}

.menu-tree-wrap :deep(.el-tree) {
  width: 100%;
  background: transparent;
}

.menu-tree-wrap :deep(.el-tree-node) {
  width: 100%;
}

.menu-tree-wrap :deep(.el-tree-node__content) {
  width: 100%;
}
</style>
