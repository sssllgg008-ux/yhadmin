<template>
  <div class="tabs-view">
    <!-- 左移箭头 -->
    <button
      v-show="showLeftArrow"
      class="scroll-arrow scroll-arrow-left"
      type="button"
      aria-label="向左滚动"
      @click="scrollLeft"
    >
      <el-icon><ArrowLeft /></el-icon>
    </button>
    <div class="tabs-list" ref="listRef" @scroll="updateArrows">
      <div
        v-for="tab in tabsStore.visitedViews"
        :key="tab.path"
        class="tab-item"
        :class="{ 'is-active': isActive(tab) }"
        :ref="(el) => setTabRef(el, tab.path)"
        @click="goTo(tab)"
        @contextmenu.prevent="openMenu($event, tab)"
      >
        <span class="tab-label">{{ tab.title }}</span>
        <el-icon
          v-if="!tab.affix"
          class="tab-close"
          @click.stop="closeTab(tab)"
        >
          <Close />
        </el-icon>
      </div>
    </div>
    <!-- 右移箭头 -->
    <button
      v-show="showRightArrow"
      class="scroll-arrow scroll-arrow-right"
      type="button"
      aria-label="向右滚动"
      @click="scrollRight"
    >
      <el-icon><ArrowRight /></el-icon>
    </button>
    <div class="tabs-actions">
      <el-dropdown trigger="click" @command="handleCmd">
        <button class="icon-btn" type="button" aria-label="标签操作">
          <el-icon><ArrowDown /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="refresh"
              ><el-icon><Refresh /></el-icon> 刷新当前</el-dropdown-item
            >
            <el-dropdown-item command="closeOthers"
              ><el-icon><CircleClose /></el-icon> 关闭其他</el-dropdown-item
            >
            <el-dropdown-item command="closeAll"
              ><el-icon><Delete /></el-icon> 关闭全部</el-dropdown-item
            >
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 右键菜单 -->
    <ul v-if="ctxVisible" class="ctx-menu" :style="ctxStyle">
      <li @click="ctxRefresh">刷新</li>
      <li v-if="!ctxTab?.affix" @click="ctxClose">关闭</li>
      <li @click="ctxCloseOthers">关闭其他</li>
      <li @click="ctxCloseAll">关闭全部</li>
    </ul>
  </div>
</template>

<script setup>
import { useRouter, useRoute } from "vue-router";
import { useTabsViewStore } from "@/store/modules/tabsView";
import { ArrowLeft, ArrowRight } from "@element-plus/icons-vue";

const router = useRouter();
const route = useRoute();
const tabsStore = useTabsViewStore();

const listRef = ref(null);
const showLeftArrow = ref(false);
const showRightArrow = ref(false);
const tabRefs = new Map();

function setTabRef(el, path) {
  if (el) tabRefs.set(path, el);
  else tabRefs.delete(path);
}

// 初始化首页 tab
onMounted(() => {
  if (!tabsStore.visitedViews.find((v) => v.path === "/dashboard")) {
    tabsStore.addView({
      name: "Dashboard",
      path: "/dashboard",
      meta: { title: "工作台", affix: true },
    });
  }
  tabsStore.addView(route);
  nextTick(updateArrows);
});

function isActive(tab) {
  return tab.path === route.path;
}

function goTo(tab) {
  if (tab.path !== route.path) router.push(tab.path);
}

function closeTab(tab) {
  const next = tabsStore.removeView(tab.path);
  if (isActive(tab)) {
    if (next) router.push(next.path);
    else router.push("/dashboard");
  }
}

function handleCmd(cmd) {
  if (cmd === "refresh") refreshCurrent();
  else if (cmd === "closeOthers") {
    tabsStore.removeOthers(route.path);
    if (!tabsStore.visitedViews.find((v) => v.path === route.path))
      router.push("/dashboard");
  } else if (cmd === "closeAll") {
    const last = tabsStore.removeAll();
    router.push(last?.path || "/dashboard");
  }
}

function refreshCurrent() {
  // 用 redirect 路由刷新
  const { fullPath } = route;
  router.replace({ path: `/redirect${fullPath}` });
}

/* ---------- 滚动箭头 ---------- */
function updateArrows() {
  const el = listRef.value;
  if (!el) return;
  showLeftArrow.value = el.scrollLeft > 4;
  showRightArrow.value = el.scrollLeft + el.clientWidth < el.scrollWidth - 4;
}

function scrollLeft() {
  const el = listRef.value;
  if (!el) return;
  el.scrollBy({ left: -el.clientWidth * 0.6, behavior: "smooth" });
}

function scrollRight() {
  const el = listRef.value;
  if (!el) return;
  el.scrollBy({ left: el.clientWidth * 0.6, behavior: "smooth" });
}

// 路由变化时，将激活的 tab 滚动到可见区域
watch(
  () => route.path,
  () => {
    nextTick(() => {
      const el = listRef.value;
      const tabEl = tabRefs.get(route.path);
      if (!el || !tabEl) return;
      const elRect = el.getBoundingClientRect();
      const tabRect = tabEl.getBoundingClientRect();
      if (tabRect.left < elRect.left) {
        el.scrollBy({
          left: tabRect.left - elRect.left - 8,
          behavior: "smooth",
        });
      } else if (tabRect.right > elRect.right) {
        el.scrollBy({
          left: tabRect.right - elRect.right + 8,
          behavior: "smooth",
        });
      }
      updateArrows();
    });
  },
);

// 监听 tab 数量变化，更新箭头状态
watch(
  () => tabsStore.visitedViews.length,
  () => {
    nextTick(updateArrows);
  },
);

/* ---------- 右键菜单 ---------- */
const ctxVisible = ref(false);
const ctxStyle = ref({ top: "0px", left: "0px" });
const ctxTab = ref(null);

function openMenu(e, tab) {
  ctxTab.value = tab;
  ctxStyle.value = { top: `${e.clientY}px`, left: `${e.clientX}px` };
  ctxVisible.value = true;
}
function closeCtx() {
  ctxVisible.value = false;
}
function ctxRefresh() {
  closeCtx();
  if (ctxTab.value) goTo(ctxTab.value);
  refreshCurrent();
}
function ctxClose() {
  closeCtx();
  if (ctxTab.value) closeTab(ctxTab.value);
}
function ctxCloseOthers() {
  closeCtx();
  tabsStore.removeOthers(route.path);
}
function ctxCloseAll() {
  closeCtx();
  const last = tabsStore.removeAll();
  router.push(last?.path || "/dashboard");
}

onMounted(() => document.addEventListener("click", closeCtx));
onBeforeUnmount(() => document.removeEventListener("click", closeCtx));
</script>

<style lang="scss" scoped>
.tabs-view {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0;
  height: var(--ry-tabs-height);
  flex-shrink: 0;
  background: var(--ry-card);
  border-bottom: 1px solid var(--ry-border-light);
  padding: 0;
  box-shadow: 0 1px 2px rgba(0, 21, 41, 0.04);
}

/* 滚动箭头 */
.scroll-arrow {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border: none;
  background: var(--ry-card);
  color: var(--ry-neutral-600);
  cursor: pointer;
  border-radius: 0;
  border-right: 1px solid var(--ry-border-light);
  font-size: 14px;
  transition: all 0.15s ease;
  &:hover {
    background: var(--ry-primary-50);
    color: var(--ry-primary);
  }
}

.tabs-list {
  display: flex;
  align-items: center;
  gap: 0;
  flex: 1;
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
  scroll-behavior: smooth;
  &::-webkit-scrollbar {
    height: 0;
  }
}
.tab-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  padding: 0 16px;
  border-radius: 0;
  background: var(--ry-card);
  border: none;
  border-right: 1px solid var(--ry-border-light);
  color: var(--ry-muted-foreground);
  font-size: 13px;
  white-space: nowrap;
  cursor: pointer;
  flex-shrink: 0;
  transition: all 0.2s ease;
  &:hover {
    color: var(--ry-primary);
    background: var(--ry-neutral-50);
  }
  &.is-active {
    background: var(--ry-primary-50);
    color: var(--ry-primary);
    font-weight: 600;
    box-shadow: inset 0 -2px 0 var(--ry-primary);
  }
}
.tab-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border-radius: var(--ry-radius-full);
  opacity: 0.55;
  font-size: 12px;
  transition:
    opacity 0.2s ease,
    background 0.2s ease;
  &:hover {
    opacity: 1;
    background: var(--ry-tab-close-hover);
  }
}
.tabs-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}
.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: none;
  background: transparent;
  color: var(--ry-neutral-500);
  cursor: pointer;
  border-radius: 0;
  border-left: 1px solid var(--ry-border-light);
  font-size: 14px;
  &:hover {
    color: var(--ry-foreground);
  }
}

.ctx-menu {
  position: fixed;
  z-index: var(--ry-z-modal);
  background: var(--ry-card);
  border: 1px solid var(--ry-border);
  border-radius: var(--ry-radius-medium);
  box-shadow: var(--ry-shadow-md);
  padding: 4px;
  list-style: none;
  margin: 0;
  min-width: 120px;
  li {
    padding: 6px 12px;
    font-size: 13px;
    color: var(--ry-foreground);
    cursor: pointer;
    border-radius: var(--ry-radius-small);
    &:hover {
      background: var(--ry-primary-50);
      color: var(--ry-primary);
    }
  }
}
</style>
