<template>
  <div class="ry-page">
    <!-- 1. 搜索筛选条 -->
    <div class="ry-card ry-search-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="部门名称">
          <el-input
            v-model.trim="query.deptName"
            placeholder="请输入部门名称"
            clearable
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="query.status"
            placeholder="部门状态"
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

    <!-- 2. 树表格卡片 -->
    <div class="ry-card ry-table-card">
      <div class="ry-toolbar">
        <div class="ry-toolbar-left">
          <el-button type="primary" :icon="Plus" @click="handleAdd()"
            >新增</el-button
          >
          <el-button
            type="danger"
            :icon="Delete"
            :disabled="!selection.length"
            @click="handleBatchDelete"
            >批量删除</el-button
          >
          <el-button :icon="Sort" @click="toggleExpandAll">展开/折叠</el-button>
        </div>
        <div class="ry-toolbar-right">
          <el-tooltip content="刷新">
            <el-button circle :icon="Refresh" @click="loadList" />
          </el-tooltip>
        </div>
      </div>

      <el-table
        v-if="refreshTable"
        v-loading="loading"
        :data="treeData"
        row-key="id"
        border
        :default-expand-all="isExpandAll"
        :tree-props="{ children: 'children' }"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="48" align="center" />
        <el-table-column label="部门名称" prop="label" min-width="260" />
        <el-table-column label="排序" prop="order" width="100" align="center" />
        <el-table-column label="状态" prop="status" width="120" align="center">
          <template #default="{ row }">
            <el-tag
              :type="row.status === '0' ? 'success' : 'danger'"
              effect="light"
            >
              {{ row.status === "0" ? "正常" : "停用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleAdd(row)"
              >新增</el-button
            >
            <el-divider direction="vertical" />
            <el-button type="primary" link @click="handleEdit(row)"
              >修改</el-button
            >
            <el-divider direction="vertical" />
            <el-button type="danger" link @click="handleDelete(row)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 3. 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.isEdit ? '修改部门' : '新增部门'"
      width="640px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="上级部门" prop="parentId">
              <el-select
                v-model="form.parentId"
                placeholder="选择上级部门"
                clearable
                style="width: 100%"
              >
                <el-option
                  v-for="dept in deptSelectOptions"
                  :key="dept.id"
                  :label="dept.label"
                  :value="dept.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="部门名称" prop="label">
              <el-input
                v-model.trim="form.label"
                placeholder="请输入部门名称"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="显示排序" prop="order">
              <el-input-number
                v-model="form.order"
                :min="0"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="负责人" prop="leader">
              <el-input v-model.trim="form.leader" placeholder="请输入负责人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="phone">
              <el-input
                v-model.trim="form.phone"
                placeholder="请输入联系电话"
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
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio value="0">正常</el-radio>
                <el-radio value="1">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
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
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Search, Refresh, Plus, Delete, Sort } from "@element-plus/icons-vue";
import { listDept, addDept, updateDept, delDept } from "@/api/system";

const query = reactive({ deptName: "", status: "" });

const flatList = ref([]);
const loading = ref(false);
const selection = ref([]);
const isExpandAll = ref(true);
const refreshTable = ref(true);

async function loadList() {
  loading.value = true;
  try {
    const res = await listDept({ ...query });
    flatList.value = res.rows || res.data || [];
  } catch (err) {
    ElMessage.error("列表加载失败");
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  loadList();
}

function handleReset() {
  query.deptName = "";
  query.status = "";
  loadList();
}

function handleSelectionChange(rows) {
  selection.value = rows;
}

// ===== 构建树 =====
const treeData = computed(() => buildTree(flatList.value));

function buildTree(list) {
  const map = new Map();
  const roots = [];
  list.forEach((m) => map.set(m.id, { ...m, children: [] }));
  map.forEach((m) => {
    if (m.parentId && map.has(m.parentId)) {
      map.get(m.parentId).children.push(m);
    } else {
      roots.push(m);
    }
  });
  const sortRec = (arr) => {
    arr.sort((a, b) => (a.order || 0) - (b.order || 0));
    arr.forEach((n) => n.children?.length && sortRec(n.children));
  };
  sortRec(roots);
  return roots;
}

const deptTreeOptions = computed(() => {
  const top = { id: 0, label: "顶级部门", children: buildTree(flatList.value) };
  return [top];
});

const deptSelectOptions = computed(() => {
  const options = [{ id: 0, label: "顶级部门" }];
  function flatten(nodes, prefix = "") {
    nodes.forEach((node) => {
      const label = prefix ? `${prefix} / ${node.label}` : node.label;
      options.push({ id: node.id, label });
      if (node.children) flatten(node.children, label);
    });
  }
  flatten(buildTree(flatList.value));
  return options;
});

function toggleExpandAll() {
  refreshTable.value = false;
  isExpandAll.value = !isExpandAll.value;
  nextTick(() => {
    refreshTable.value = true;
  });
}

// ===== 新增/编辑 =====
const dialog = reactive({ visible: false, isEdit: false, submitting: false });
const formRef = ref(null);
const defaultForm = () => ({
  id: undefined,
  parentId: 0,
  label: "",
  order: 0,
  leader: "",
  phone: "",
  email: "",
  status: "0",
});
const form = reactive(defaultForm());
const rules = {
  label: [{ required: true, message: "请输入部门名称", trigger: "blur" }],
  order: [{ required: true, message: "请输入显示排序", trigger: "blur" }],
};

function resetForm() {
  Object.assign(form, defaultForm());
  formRef.value?.clearValidate();
}

function handleAdd(row) {
  resetForm();
  if (row?.id) form.parentId = row.id;
  dialog.isEdit = false;
  dialog.visible = true;
}

function handleEdit(row) {
  Object.assign(form, row);
  dialog.isEdit = true;
  dialog.visible = true;
}

async function handleSubmit() {
  await formRef.value?.validate();
  dialog.submitting = true;
  try {
    if (dialog.isEdit) {
      await updateDept({ ...form });
      ElMessage.success("修改成功");
    } else {
      await addDept({ ...form });
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

async function handleDelete(row) {
  await ElMessageBox.confirm(`确认删除部门「${row.label}」？`, "提示", {
    type: "warning",
  });
  try {
    await delDept(row.id);
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
    await delDept(selection.value.map((r) => r.id));
    ElMessage.success("删除成功");
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
</style>
