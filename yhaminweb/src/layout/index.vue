<template>
  <div
    class="layout-shell"
    :class="{ 'is-mobile': appStore.device === 'mobile' }"
  >
    <!-- 深色主侧边栏：Logo + 当前模块菜单树 -->
    <aside
      class="layout-sidebar"
      :class="{
        'is-collapsed':
          appStore.sidebarCollapsed && appStore.device === 'desktop',
        'is-hidden-mobile':
          appStore.device === 'mobile' && !appStore.mobileSidebarOpen,
      }"
    >
      <SidebarMenu v-model:active-group-key="activeGroupKey" />
    </aside>

    <!-- 移动端遮罩 -->
    <transition name="fade">
      <div
        v-if="appStore.device === 'mobile' && appStore.mobileSidebarOpen"
        class="layout-mobile-mask"
        @click="appStore.closeMobileSidebar()"
      />
    </transition>

    <!-- 右侧主区域 -->
    <div class="layout-right">
      <!-- 顶栏：折叠按钮 + 模块切换 + 右侧操作 -->
      <Topbar
        :active-module-key="activeGroupKey"
        @module-switch="handleModuleSwitch"
      />
      <Breadcrumb v-if="appStore.showBreadcrumb" />
      <TabsView v-if="appStore.showTabs" />
      <AppMain />
    </div>

    <!-- 布局设置抽屉 -->
    <SettingDrawer />
  </div>
</template>

<script setup>
import { ref, onBeforeUnmount } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAppStore } from "@/store/modules/app";
import { usePermissionStore } from "@/store/modules/permission";
import SidebarMenu from "./components/SidebarMenu.vue";
import Topbar from "./components/Topbar.vue";
import Breadcrumb from "./components/Breadcrumb.vue";
import TabsView from "./components/TabsView.vue";
import AppMain from "./components/AppMain.vue";
import SettingDrawer from "./components/SettingDrawer.vue";

const appStore = useAppStore();
const permissionStore = usePermissionStore();
const route = useRoute();
const router = useRouter();
const activeGroupKey = ref("");

/**
 * 顶部模块切换：导航到目标模块的第一个可路由菜单
 * 路由变化后 SidebarMenu 的 watch(route.path) 会自动同步 activeGroupKey
 */
function handleModuleSwitch(key) {
  const group = permissionStore.menus.find((m) => m.key === key);
  if (!group) return;
  if (group.children && group.children.length) {
    const firstLeaf = findFirstMenuLeaf(group);
    if (firstLeaf && firstLeaf.path) {
      router.push(firstLeaf.path);
    }
  } else if (group.path) {
    router.push(group.path);
  }
}

function findFirstMenuLeaf(node) {
  if (node.path && (!node.children || node.children.length === 0)) return node;
  if (node.children) {
    for (const c of node.children) {
      const leaf = findFirstMenuLeaf(c);
      if (leaf) return leaf;
    }
  }
  return null;
}

function handleResize() {
  const w = window.innerWidth;
  if (w < 992) {
    if (appStore.device !== "mobile") appStore.setDevice("mobile");
  } else {
    if (appStore.device !== "desktop") appStore.setDevice("desktop");
  }
}
handleResize();
window.addEventListener("resize", handleResize);
onBeforeUnmount(() => window.removeEventListener("resize", handleResize));
</script>

<style lang="scss" scoped>
.layout-shell {
  display: flex;
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background: var(--ry-background);
}

/* ---------- 深色主侧边栏 ---------- */
.layout-sidebar {
  width: var(--ry-sidebar-width);
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width 0.2s ease;
  border-right: 1px solid var(--ry-sidebar-divider);
  background: var(--ry-sidebar-bg);
  box-shadow: var(--ry-sidebar-shadow);
  z-index: 12;

  &.is-collapsed {
    width: var(--ry-sidebar-width-collapsed);
  }
}

/* ---------- 移动端：侧栏变为抽屉 ---------- */
.layout-mobile-mask {
  position: fixed;
  inset: 0;
  background: var(--ry-mask-bg);
  z-index: var(--ry-z-mask);
}
.layout-shell.is-mobile .layout-sidebar {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  z-index: var(--ry-z-sidebar);
  transform: translateX(0);
  transition: transform 0.25s ease;
  box-shadow: var(--ry-sidebar-shadow);
}
.layout-shell.is-mobile .layout-sidebar.is-hidden-mobile {
  transform: translateX(-100%);
}

/* ---------- 右侧主区域 ---------- */
.layout-right {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
