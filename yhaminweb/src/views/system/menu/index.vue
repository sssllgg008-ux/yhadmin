<template>
  <div class="ry-page">
    <!-- 1. 顶部：模块切换 + 搜索条件（紧凑一行） -->
    <div class="ry-card ry-search-card">
      <div class="ry-search-row">
        <div class="ry-module-switch">
          <span class="ry-module-label">模块</span>
          <el-radio-group
            v-model="activeModuleTab"
            size="small"
            @change="handleModuleTabChange"
          >
            <el-radio-button label="全部模块" value="all" />
            <el-radio-button
              v-for="(m, idx) in moduleOptions"
              :key="m.id"
              :label="m.moduleName"
              :value="String(m.id)"
            />
          </el-radio-group>
        </div>
        <div class="ry-search-actions">
          <el-input
            v-model="quickSearch"
            placeholder="菜单名称"
            clearable
            :prefix-icon="Search"
            size="small"
            style="width: 180px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
          <el-select
            v-model="query.status"
            placeholder="状态"
            clearable
            size="small"
            style="width: 110px"
          >
            <el-option label="正常" value="0" />
            <el-option label="停用" value="1" />
          </el-select>
          <el-button
            type="primary"
            :icon="Search"
            size="small"
            @click="handleSearch"
            >查询</el-button
          >
          <el-button :icon="Refresh" size="small" @click="handleReset"
            >重置</el-button
          >
        </div>
      </div>
    </div>

    <!-- 2. 树表格卡片 -->
    <div class="ry-card ry-table-card">
      <div class="ry-toolbar">
        <div class="ry-toolbar-left">
          <el-button
            v-permission="'system:menu:add'"
            type="primary"
            :icon="Plus"
            @click="handleAdd()"
            >新增菜单</el-button
          >
          <el-button
            v-permission="'system:menu:remove'"
            type="danger"
            plain
            :icon="Delete"
            :disabled="!selection.length"
            @click="handleBatchDelete"
            >批量删除</el-button
          >
          <el-divider direction="vertical" />
          <el-button :icon="Sort" @click="toggleExpandAll">{{
            isExpandAll ? "折叠全部" : "展开全部"
          }}</el-button>
          <el-dropdown trigger="click" @command="handleDensityCommand">
            <el-button :icon="Operation"
              >密度<el-icon class="el-icon--right"><ArrowDown /></el-icon
            ></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  command="large"
                  :class="{ 'is-active': tableSize === 'large' }"
                  >宽松</el-dropdown-item
                >
                <el-dropdown-item
                  command="default"
                  :class="{ 'is-active': tableSize === 'default' }"
                  >默认</el-dropdown-item
                >
                <el-dropdown-item
                  command="small"
                  :class="{ 'is-active': tableSize === 'small' }"
                  >紧凑</el-dropdown-item
                >
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-dropdown trigger="click" @command="handleColumnCommand">
            <el-button :icon="Setting"
              >列设置<el-icon class="el-icon--right"><ArrowDown /></el-icon
            ></el-button>
            <template #dropdown>
              <el-dropdown-menu class="ry-col-menu">
                <el-dropdown-item
                  v-for="col in columnConfig"
                  :key="col.prop"
                  :command="col.prop"
                >
                  <el-checkbox
                    :model-value="col.visible"
                    @update:model-value="(val) => toggleColumn(col.prop, val)"
                    >{{ col.label }}</el-checkbox
                  >
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <div class="ry-toolbar-right">
          <span class="ry-count-tip">共 {{ filteredMenuCount }} 项</span>
          <el-tooltip content="刷新">
            <el-button circle :icon="Refresh" @click="loadList" />
          </el-tooltip>
        </div>
      </div>

      <el-table
        v-if="refreshTable"
        v-loading="loading"
        :data="filteredTreeData"
        row-key="id"
        border
        :size="tableSize"
        :default-expand-all="isExpandAll"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        class="ry-tree-table"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="48" align="center" />
        <el-table-column
          label="菜单名称"
          prop="menuName"
          min-width="220"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <span class="ry-menu-name">
              <el-icon v-if="row.icon" class="ry-menu-icon"
                ><component :is="iconComp(mapIcon(row.icon))"
              /></el-icon>
              <span class="ry-menu-text">{{ row.menuName }}</span>
              <el-tag
                :type="menuTypeTag(row.menuType)"
                effect="light"
                size="small"
                class="ry-type-tag"
                >{{ menuTypeText(row.menuType) }}</el-tag
              >
            </span>
          </template>
        </el-table-column>
        <el-table-column
          v-if="columnVisible('moduleName')"
          label="所属模块"
          prop="moduleId"
          width="120"
          align="center"
        >
          <template #default="{ row }">
            <span>{{ moduleNameOf(row.moduleId) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          v-if="columnVisible('path')"
          label="路由/组件"
          min-width="220"
        >
          <template #default="{ row }">
            <div class="ry-route-cell">
              <div class="ry-route-line">
                <span class="ry-route-label">路径</span>
                <span class="ry-mono">{{ displayPath(row) }}</span>
              </div>
              <div class="ry-route-line">
                <span class="ry-route-label">组件</span>
                <span class="ry-mono">{{ displayComponent(row) }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          v-if="columnVisible('perms')"
          label="权限标识"
          prop="perms"
          min-width="160"
        >
          <template #default="{ row }">
            <span class="ry-mono">{{ row.perms || "-" }}</span>
          </template>
        </el-table-column>
        <el-table-column
          v-if="columnVisible('visible')"
          label="可见/排序"
          width="110"
          align="center"
        >
          <template #default="{ row }">
            <div class="ry-meta-cell">
              <el-tag
                :type="isVisible(row) ? 'success' : 'info'"
                effect="plain"
                size="small"
              >
                {{ isVisible(row) ? "显示" : "隐藏" }}
              </el-tag>
              <span class="ry-order">#{{ row.orderNum ?? 0 }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-tooltip content="修改" placement="top">
              <el-button
                v-permission="'system:menu:edit'"
                type="primary"
                link
                :icon="Edit"
                @click="handleEdit(row)"
              />
            </el-tooltip>
            <el-tooltip content="新增子菜单" placement="top">
              <el-button
                v-permission="'system:menu:add'"
                type="success"
                link
                :icon="Plus"
                @click="handleAddChild(row)"
              />
            </el-tooltip>
            <el-dropdown
              trigger="click"
              @command="(cmd) => handleMoreCommand(cmd, row)"
            >
              <el-button type="primary" link>
                <el-icon><MoreFilled /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    command="copy"
                    v-permission="'system:menu:add'"
                    :icon="CopyDocument"
                    >复制菜单</el-dropdown-item
                  >
                  <el-dropdown-item
                    command="toggleVisible"
                    v-permission="'system:menu:edit'"
                    :icon="Hide"
                    >切换显隐</el-dropdown-item
                  >
                  <el-dropdown-item
                    command="delete"
                    v-permission="'system:menu:remove'"
                    :icon="Delete"
                    divided
                  >
                    <span style="color: var(--el-color-danger)">删除菜单</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 3. 新增/编辑弹窗（分组布局） -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.isEdit ? '修改菜单' : '新增菜单'"
      width="720px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="96px"
        class="ry-menu-form"
      >
        <!-- 分组1：基础信息 -->
        <el-divider content-position="left">基础信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="上级菜单" prop="parentId">
              <el-select
                v-model="form.parentId"
                placeholder="选择上级菜单（主类目为顶层）"
                clearable
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="menu in menuSelectOptions"
                  :key="menu.id"
                  :label="menu.label"
                  :value="menu.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="14">
            <el-form-item label="菜单类型" prop="menuType">
              <el-radio-group
                v-model="form.menuType"
                class="ry-menu-type-radio"
              >
                <el-radio-button value="M">目录</el-radio-button>
                <el-radio-button value="C">菜单</el-radio-button>
                <el-radio-button value="I">内链</el-radio-button>
                <el-radio-button value="F">按钮</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="所属模块" prop="moduleId">
              <el-select
                v-model="form.moduleId"
                placeholder="选择所属模块"
                style="width: 100%"
              >
                <el-option
                  v-for="m in moduleOptions"
                  :key="m.id"
                  :label="m.moduleName"
                  :value="m.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="菜单名称" prop="menuName">
              <el-input
                v-model.trim="form.menuName"
                placeholder="请输入菜单名称"
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

        <!-- 分组2：路由配置（按钮类型不显示） -->
        <el-divider v-if="form.menuType !== 'F'" content-position="left"
          >路由配置</el-divider
        >
        <el-row v-if="form.menuType !== 'F'" :gutter="16">
          <el-col :span="12">
            <el-form-item label="路由地址" prop="path">
              <el-input
                v-model.trim="form.path"
                :placeholder="pathPlaceholder"
              />
            </el-form-item>
          </el-col>
          <el-col v-if="form.menuType === 'C'" :span="12">
            <el-form-item label="组件路径" prop="component">
              <el-input
                v-model.trim="form.component"
                placeholder="如 system/user/index"
              />
            </el-form-item>
          </el-col>
          <el-col v-if="form.menuType === 'I'" :span="12">
            <el-form-item label="内链地址" prop="component">
              <el-input
                v-model.trim="form.component"
                placeholder="如 https://www.baidu.com"
              />
            </el-form-item>
          </el-col>
          <el-col v-if="form.menuType !== 'M'" :span="12">
            <el-form-item label="权限标识" prop="perms">
              <el-input
                v-model.trim="form.perms"
                placeholder="如 system:user:list"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 分组3：显示设置 -->
        <el-divider content-position="left">显示设置</el-divider>
        <el-row :gutter="16">
          <el-col v-if="form.menuType !== 'F'" :span="12">
            <el-form-item label="图标" prop="icon">
              <div class="icon-picker">
                <el-input
                  v-model.trim="form.icon"
                  placeholder="图标名"
                  class="icon-input"
                >
                  <template #prepend>
                    <el-icon class="icon-preview">
                      <component :is="iconComp(mapIcon(form.icon))" />
                    </el-icon>
                  </template>
                </el-input>
                <el-popover
                  placement="bottom"
                  :width="380"
                  trigger="click"
                  popper-class="icon-popover-wrap"
                >
                  <template #reference>
                    <el-button>选择</el-button>
                  </template>
                  <div class="icon-popover">
                    <el-input
                      v-model="iconSearch"
                      placeholder="搜索图标名"
                      clearable
                      size="small"
                      :prefix-icon="Search"
                    />
                    <div class="icon-grid">
                      <div
                        v-for="name in filteredIcons"
                        :key="name"
                        class="icon-cell"
                        :class="{ 'is-active': name === form.icon }"
                        :title="name"
                        @click="selectIcon(name)"
                      >
                        <el-icon
                          ><component :is="ElementPlusIconsVue[name]"
                        /></el-icon>
                      </div>
                      <div v-if="!filteredIcons.length" class="icon-empty">
                        无匹配图标
                      </div>
                    </div>
                  </div>
                </el-popover>
              </div>
            </el-form-item>
          </el-col>
          <el-col v-if="form.menuType !== 'F'" :span="6">
            <el-form-item label="是否显示" prop="visible">
              <el-switch
                v-model="form.visible"
                active-value="0"
                inactive-value="1"
                active-text="显示"
                inactive-text="隐藏"
                inline-prompt
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="状态" prop="status">
              <el-switch
                v-model="form.status"
                active-value="0"
                inactive-value="1"
                active-text="正常"
                inactive-text="停用"
                inline-prompt
              />
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
import { ref, shallowRef, reactive, computed, nextTick, onMounted } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  Search,
  Refresh,
  Plus,
  Delete,
  Sort,
  ArrowDown,
  Edit,
  MoreFilled,
  CopyDocument,
  Hide,
  Operation,
  Setting,
} from "@element-plus/icons-vue";
import * as ElementPlusIconsVue from "@element-plus/icons-vue";
import {
  listMenu,
  addMenu,
  updateMenu,
  delMenu,
  moduleOptionSelect,
} from "@/api/system";
import { mapIcon } from "@/store/modules/permission";

const query = reactive({ menuName: "", status: "", moduleId: "" });
const quickSearch = ref("");
const activeModuleTab = ref("all");

// ===== 表格密度 & 列设置 =====
const tableSize = ref("default");
function handleDensityCommand(cmd) {
  tableSize.value = cmd;
}
const columnConfig = reactive([
  { prop: "moduleName", label: "所属模块", visible: true },
  { prop: "path", label: "路由/组件", visible: true },
  { prop: "perms", label: "权限标识", visible: true },
  { prop: "visible", label: "可见/排序", visible: true },
]);
function columnVisible(prop) {
  const col = columnConfig.find((c) => c.prop === prop);
  return col ? col.visible : true;
}
function toggleColumn(prop, val) {
  const col = columnConfig.find((c) => c.prop === prop);
  if (col) col.visible = val;
}
function handleColumnCommand() {
  // dropdown 仅用于触发显示，实际切换由 checkbox 处理
}

// ===== 所属模块 =====
const moduleOptions = ref([]);
const moduleMap = ref(new Map());
async function loadModuleOptions() {
  try {
    const res = await moduleOptionSelect();
    const rows = res.data || res.rows || res || [];
    moduleOptions.value = Array.isArray(rows) ? rows : [];
    const map = new Map();
    moduleOptions.value.forEach((m) => map.set(m.id, m.moduleName));
    moduleMap.value = map;
    // “全部模块”是固定选项；仅修复已经失效的具体模块选择。
    if (moduleOptions.value.length > 0 && activeModuleTab.value !== "all") {
      const currentId = String(activeModuleTab.value);
      const exists = moduleOptions.value.some(
        (m) => String(m.id) === currentId,
      );
      if (!exists) {
        const firstId = String(moduleOptions.value[0].id);
        activeModuleTab.value = firstId;
        query.moduleId = Number(firstId);
      }
    }
  } catch (err) {
    moduleOptions.value = [];
    moduleMap.value = new Map();
  }
}
function moduleNameOf(moduleId) {
  if (!moduleId || moduleId === 0) return "—";
  return moduleMap.value.get(moduleId) || "—";
}

function handleModuleTabChange(tabName) {
  if (tabName === "all") {
    query.moduleId = "";
    loadList();
    return;
  }
  const moduleId = Number(tabName);
  // 模块选项首次渲染时可能发出与当前值相同的 change，避免重复加载
  if (query.moduleId === moduleId) return;
  query.moduleId = moduleId;
  loadList();
}

// 列表由接口整体替换，不需要为每个菜单对象创建深层响应式代理
const flatList = shallowRef([]);
const loading = ref(false);
const selection = ref([]);
// 菜单管理默认展示完整层级，包括按钮类型节点。
const isExpandAll = ref(true);
const refreshTable = ref(true);
let listRequestId = 0;
let pendingListKey = "";
let pendingListPromise = null;

function loadList() {
  if (quickSearch.value && !query.menuName) query.menuName = quickSearch.value;
  const params = { ...query, pageNum: 1, pageSize: 10000 };
  if (!params.moduleId) delete params.moduleId;
  const requestKey = JSON.stringify(params);

  // 模块选项初始化可能触发 radio-group change；相同参数的在途请求直接复用
  if (pendingListPromise && pendingListKey === requestKey) {
    return pendingListPromise;
  }

  const requestId = ++listRequestId;
  loading.value = true;
  pendingListKey = requestKey;
  const requestPromise = listMenu(params)
    .then((res) => {
      if (requestId === listRequestId) {
        flatList.value = res.rows || res.data || [];
      }
    })
    .catch(() => {
      if (requestId === listRequestId) ElMessage.error("列表加载失败");
    })
    .finally(() => {
      if (requestId === listRequestId) loading.value = false;
      if (pendingListPromise === requestPromise) {
        pendingListPromise = null;
        pendingListKey = "";
      }
    });
  pendingListPromise = requestPromise;
  return requestPromise;
}

function handleSearch() {
  if (quickSearch.value) query.menuName = quickSearch.value;
  loadList();
}

function handleReset() {
  query.menuName = "";
  query.status = "";
  quickSearch.value = "";
  activeModuleTab.value = "all";
  query.moduleId = "";
  loadList();
}

function handleSelectionChange(rows) {
  selection.value = rows;
}

// ===== 构建树 =====
const treeData = computed(() => buildTree(flatList.value));

// 顶部 Tab 过滤后的树（按模块）
const filteredTreeData = computed(() => {
  if (activeModuleTab.value === "all") return treeData.value;
  const moduleId = Number(activeModuleTab.value);
  if (!moduleId) return treeData.value;
  return treeData.value.filter((n) => n.moduleId === moduleId);
});
const filteredMenuCount = computed(() => {
  let count = 0;
  const walk = (nodes) => nodes.forEach((node) => {
    count += 1;
    if (node.children?.length) walk(node.children);
  });
  walk(filteredTreeData.value);
  return count;
});
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

const menuSelectOptions = computed(() => {
  const options = [{ id: 0, label: "主类目" }];
  function flatten(nodes, prefix = "") {
    nodes.forEach((node) => {
      const label = prefix ? `${prefix} / ${node.menuName}` : node.menuName;
      options.push({ id: node.id, label });
      if (node.children) flatten(node.children, label);
    });
  }
  flatten(treeData.value);
  return options;
});

function toggleExpandAll() {
  refreshTable.value = false;
  isExpandAll.value = !isExpandAll.value;
  nextTick(() => {
    refreshTable.value = true;
  });
}

// ===== 类型/图标辅助 =====
function menuTypeText(t) {
  return { M: "目录", C: "菜单", I: "内链", F: "按钮" }[t] || t;
}
function menuTypeTag(t) {
  return { M: "primary", C: "success", I: "warning", F: "info" }[t] || "info";
}
function iconComp(name) {
  return ElementPlusIconsVue[name] || ElementPlusIconsVue.Menu;
}
function displayPath(row) {
  if (row.menuType === "M" || row.menuType === "I") return "-";
  return row.path || "-";
}
function displayComponent(row) {
  if (row.menuType === "M" || row.menuType === "I") return "-";
  return row.component || "-";
}
function isVisible(row) {
  return row.visible === true || row.visible === "0" || row.visible === 0;
}

const pathPlaceholder = computed(() => {
  if (form.menuType === "M") return "目录路由地址（如 system）";
  if (form.menuType === "C") return "菜单路由地址（如 user）";
  if (form.menuType === "I") return "内链路由地址（如 baidu）";
  return "";
});

// ===== 图标选择器 =====
const iconNames = Object.keys(ElementPlusIconsVue).filter(
  (k) => k !== "default" && typeof ElementPlusIconsVue[k] === "object",
);
const iconSearch = ref("");
const filteredIcons = computed(() => {
  const kw = iconSearch.value.trim().toLowerCase();
  if (!kw) return iconNames;
  return iconNames.filter((n) => n.toLowerCase().includes(kw));
});
function selectIcon(name) {
  form.icon = name;
}

// ===== 新增/编辑 =====
const dialog = reactive({ visible: false, isEdit: false, submitting: false });
const formRef = ref(null);
const defaultForm = () => ({
  id: undefined,
  parentId: 0,
  menuType: "M",
  menuName: "",
  order: 0,
  path: "",
  component: "",
  perms: "",
  icon: "",
  moduleId: query.moduleId || undefined,
  visible: "0",
  status: "0",
});
const form = reactive(defaultForm());
const rules = {
  menuName: [{ required: true, message: "请输入菜单名称", trigger: "blur" }],
  order: [{ required: true, message: "请输入显示排序", trigger: "blur" }],
  menuType: [{ required: true, message: "请选择菜单类型", trigger: "change" }],
  moduleId: [{ required: true, message: "请选择所属模块", trigger: "change" }],
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

function handleAddChild(row) {
  resetForm();
  form.parentId = row.id;
  if (row.moduleId) form.moduleId = row.moduleId;
  dialog.isEdit = false;
  dialog.visible = true;
}

function handleEdit(row) {
  resetForm();
  Object.assign(form, {
    id: row.id,
    parentId: row.parentId ?? 0,
    menuType: row.menuType,
    menuName: row.menuName,
    order: row.orderNum ?? row.order ?? 0,
    path: row.path || "",
    component: row.component || "",
    perms: row.perms || "",
    icon: row.icon || "",
    moduleId: row.moduleId || undefined,
    visible: row.visible === false || row.visible === "1" ? "1" : "0",
    status: row.status || "0",
  });
  dialog.isEdit = true;
  dialog.visible = true;
}

function handleCopy(row) {
  resetForm();
  Object.assign(form, {
    parentId: row.parentId ?? 0,
    menuType: row.menuType,
    menuName: row.menuName ? `${row.menuName}_copy` : "",
    order: row.orderNum ?? row.order ?? 0,
    path: row.path || "",
    component: row.component || "",
    perms: row.perms || "",
    icon: row.icon || "",
    moduleId: row.moduleId || query.moduleId || undefined,
    visible: row.visible === false || row.visible === "1" ? "1" : "0",
    status: row.status || "0",
  });
  dialog.isEdit = false;
  dialog.visible = true;
}

/**
 * 快速切换显隐（不需要打开弹窗）
 */
async function handleToggleVisible(row) {
  const newVisible = isVisible(row) ? "1" : "0";
  try {
    await updateMenu({
      id: row.id,
      parentId: row.parentId ?? 0,
      menuType: row.menuType,
      menuName: row.menuName,
      order: row.orderNum ?? 0,
      path: row.path || "",
      component: row.component || "",
      perms: row.perms || "",
      icon: row.icon || "",
      moduleId: row.moduleId ?? 0,
      visible: newVisible,
      status: row.status || "0",
    });
    ElMessage.success(isVisible(row) ? "已隐藏" : "已显示");
    loadList();
  } catch (err) {
    ElMessage.error("切换失败");
  }
}

function handleMoreCommand(cmd, row) {
  if (cmd === "copy") handleCopy(row);
  else if (cmd === "delete") handleDelete(row);
  else if (cmd === "toggleVisible") handleToggleVisible(row);
}

async function handleSubmit() {
  await formRef.value?.validate();
  dialog.submitting = true;
  try {
    // 与 sys_menu.visible 保持一致："0"=显示，"1"=隐藏。
    const payload = { ...form, visible: form.visible === "1" ? "1" : "0" };
    if (dialog.isEdit) {
      await updateMenu(payload);
      ElMessage.success("修改成功");
    } else {
      await addMenu(payload);
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
  await ElMessageBox.confirm(`确认删除菜单「${row.menuName}」？`, "提示", {
    type: "warning",
  });
  try {
    await delMenu(row.id);
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
    await delMenu(selection.value.map((r) => r.id));
    ElMessage.success("删除成功");
    loadList();
  } catch (err) {
    ElMessage.error("删除失败");
  }
}

onMounted(async () => {
  // 先完成列表首屏请求，避免两个查询同时争用后端连接和初始化资源
  await loadList();
  await loadModuleOptions();
});
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

/* ===== 顶部搜索区（紧凑一行） ===== */
.ry-search-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.ry-module-switch {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.ry-module-label {
  color: var(--ry-neutral-600);
  font-size: 13px;
  flex-shrink: 0;
}

.ry-search-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

/* ===== 工具栏 ===== */
.ry-count-tip {
  font-size: 13px;
  color: var(--ry-neutral-500);
  margin-right: 8px;
}

/* ===== 表格内单元格 ===== */
.ry-menu-name {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.ry-menu-icon {
  color: var(--ry-primary);
  font-size: 16px;
}

.ry-menu-text {
  font-weight: 500;
}

.ry-type-tag {
  margin-left: 4px;
  transform: scale(0.85);
  transform-origin: left center;
}

.ry-route-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.5;
}

.ry-route-line {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.ry-route-label {
  display: inline-block;
  width: 28px;
  color: var(--ry-neutral-500);
  flex-shrink: 0;
}

.ry-meta-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.ry-order {
  font-size: 12px;
  color: var(--ry-neutral-500);
  font-family: var(--ry-font-mono);
}

/* ===== 树形展开图标：右折 / 下折 ===== */
.ry-tree-table {
  :deep(.el-table__expand-icon) {
    color: var(--ry-neutral-600);
  }
}

/* ===== 弹窗表单 ===== */
.ry-menu-form {
  :deep(.el-divider__text) {
    font-size: 13px;
    font-weight: 500;
    color: var(--ry-primary);
  }
  :deep(.el-radio-button__inner) {
    padding: 8px 12px;
  }
  .ry-menu-type-radio {
    display: inline-flex;
    flex-wrap: nowrap;
    white-space: nowrap;
  }
}

/* ===== 图标选择器 ===== */
.icon-picker {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.icon-input {
  flex: 1;
}
.icon-preview {
  font-size: 16px;
  color: var(--ry-foreground);
}
</style>

<style lang="scss">
.icon-popover-wrap {
  .icon-popover {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  .icon-grid {
    display: grid;
    grid-template-columns: repeat(8, 1fr);
    gap: 4px;
    max-height: 280px;
    overflow-y: auto;
  }
  .icon-cell {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 36px;
    border-radius: var(--ry-radius-small);
    cursor: pointer;
    color: var(--ry-neutral-500);
    font-size: 18px;
    transition: all 0.15s ease;
    &:hover {
      background: var(--ry-primary-50);
      color: var(--ry-primary);
    }
    &.is-active {
      background: var(--ry-primary);
      color: #fff;
    }
  }
  .icon-empty {
    grid-column: 1 / -1;
    text-align: center;
    padding: 16px;
    color: var(--ry-neutral-500);
    font-size: 13px;
  }
}

/* 列设置下拉菜单（防止内容被点击事件吞掉） */
.ry-col-menu {
  .el-dropdown-menu__item {
    padding: 0 12px;
    &:hover {
      background: transparent;
    }
  }
}
</style>
