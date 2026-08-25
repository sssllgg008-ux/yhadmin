<template>
  <div
    class="main-sidebar"
    :class="{ 'is-collapsed': isCollapsed, 'is-mobile': isMobile }"
  >
    <SidebarLogo :collapsed="isCollapsed" />
    <nav class="sidebar-nav">
      <SidebarTreeItem
        :items="treeItems"
        :depth="0"
        :open-groups="openGroups"
        :current-path="route.path"
        :collapsed="isCollapsed"
        @toggle="toggleGroup"
        @navigate="handleNavigate"
      />
      <div v-if="!treeItems.length" class="sidebar-empty">
        <el-icon><Menu /></el-icon>
      </div>
    </nav>
  </div>
</template>

<script setup>
import { computed, watch, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Menu } from "@element-plus/icons-vue";
import { useAppStore } from "@/store/modules/app";
import { usePermissionStore } from "@/store/modules/permission";
import SidebarLogo from "./SidebarLogo.vue";
import SidebarTreeItem from "./SidebarTreeItem.vue";

const props = defineProps({
  activeGroupKey: { type: String, default: "" },
});
const emit = defineEmits(["update:activeGroupKey"]);

const appStore = useAppStore();
const permissionStore = usePermissionStore();
const route = useRoute();
const router = useRouter();

// 展开的目录分组
const openGroups = ref(new Set());

function setActiveGroup(key) {
  emit("update:activeGroupKey", key);
}

/**
 * 当前选中模块节点
 */
const activeGroup = computed(() => {
  if (!props.activeGroupKey) return null;
  return (
    permissionStore.menus.find((m) => m.key === props.activeGroupKey) || null
  );
});

/**
 * 侧边栏菜单树：
 * - 模块分组（有 children）→ 显示其子菜单树
 * - 独立叶菜单（如"系统首页"）→ 显示其本身
 */
const treeItems = computed(() => {
  const g = activeGroup.value;
  if (!g) return [];
  if (g.children && g.children.length) return g.children;
  return [g];
});

const isMobile = computed(() => appStore.device === "mobile");
const isCollapsed = computed(
  () => appStore.sidebarCollapsed && appStore.device === "desktop",
);

/**
 * 根据路由推导所属模块
 */
function resolveGroupFromRoute(path) {
  const modules = permissionStore.menus;
  // 工作台是跨模块首页，不单独占用侧边栏分组；默认展示排序第一的模块菜单
  if (path === "/dashboard") {
    const firstModule = modules.find((m) => m.children && m.children.length);
    return firstModule?.key || modules[0]?.key || "";
  }
  for (const mod of modules) {
    // 独立叶菜单：模块自身 path 直接匹配（如 系统首页 path=/dashboard）
    if (mod.path && (path === mod.path || path.startsWith(mod.path + "/")))
      return mod.key;
    // 模块分组：检查 children 匹配
    const hasChild = (mod.children || []).some((c) => {
      if (c.path && (path === c.path || path.startsWith(c.path + "/")))
        return true;
      if (c.children)
        return c.children.some(
          (gc) =>
            gc.path && (path === gc.path || path.startsWith(gc.path + "/")),
        );
      return false;
    });
    if (hasChild) return mod.key;
  }
  return "";
}

// 菜单数据加载后，初始化 groupKey
watch(
  () => permissionStore.menus,
  () => {
    if (permissionStore.menus.length === 0) return;
    const keyExists =
      props.activeGroupKey &&
      permissionStore.menus.some((g) => g.key === props.activeGroupKey);
    if (!keyExists) {
      const fromRoute = resolveGroupFromRoute(route.path);
      setActiveGroup(fromRoute || permissionStore.menus[0]?.key || "");
    }
  },
  { immediate: true },
);

// 路由变化时，同步 groupKey 并自动展开包含当前路由的分组
watch(
  () => route.path,
  (path) => {
    const groupKey = resolveGroupFromRoute(path);
    if (groupKey && groupKey !== props.activeGroupKey) setActiveGroup(groupKey);
    autoOpenActiveGroups();
  },
  { immediate: true },
);

// 模块切换时，重置展开状态并展开当前路由所在分组
watch(
  () => props.activeGroupKey,
  () => {
    openGroups.value = new Set();
    autoOpenActiveGroups();
  },
);

function autoOpenActiveGroups() {
  const next = new Set(openGroups.value);
  for (const item of treeItems.value) {
    if (item.children && item.children.length) {
      const hasActive = item.children.some((c) => {
        if (
          c.path &&
          (route.path === c.path || route.path.startsWith(c.path + "/"))
        )
          return true;
        if (c.children)
          return c.children.some(
            (gc) =>
              gc.path &&
              (route.path === gc.path || route.path.startsWith(gc.path + "/")),
          );
        return false;
      });
      if (hasActive) next.add(item.key);
    }
  }
  openGroups.value = next;
}

function toggleGroup(key) {
  // Snowy 经典菜单采用同级手风琴展开
  if (openGroups.value.has(key)) openGroups.value = new Set();
  else openGroups.value = new Set([key]);
}

function handleNavigate(path) {
  if (path) router.push(path);
  if (appStore.device === "mobile") {
    appStore.closeMobileSidebar();
  }
}
</script>

<style lang="scss" scoped>
.main-sidebar {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  background: var(--ry-sidebar-bg);
  overflow: hidden;
}

.sidebar-nav {
  flex: 1;
  padding: 8px 0;
  overflow-y: auto;
  overflow-x: hidden;

  &::-webkit-scrollbar {
    width: 0;
  }
}

.sidebar-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 0;
  color: var(--ry-sidebar-text);
  font-size: 22px;
  opacity: 0.4;
}
</style>
