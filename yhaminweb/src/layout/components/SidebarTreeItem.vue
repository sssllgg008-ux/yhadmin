<template>
  <template v-for="item in items" :key="item.key">
    <!-- 外链菜单（C类型且path为http开头）：新窗口打开 -->
    <el-tooltip
      v-if="isLeaf(item) && isExternalLink(item)"
      :disabled="!collapsed"
      :content="item.title"
      placement="right"
    >
      <a
        :href="getExternalUrl(item)"
        target="_blank"
        rel="noopener noreferrer"
        class="tree-item tree-item-external"
        :class="[depthClass, { 'is-collapsed': collapsed }]"
        :style="{ paddingLeft: indentPx + 'px' }"
      >
        <span class="tree-icon">
          <el-icon><component :is="resolveIcon(item.icon)" /></el-icon>
        </span>
        <span class="tree-label">{{ item.title }}</span>
        <el-icon class="tree-external-icon"><Link /></el-icon>
      </a>
    </el-tooltip>
    <!-- 叶子菜单（C/I类型内部路由 + 内链内嵌） -->
    <el-tooltip
      v-else-if="isLeaf(item)"
      :disabled="!collapsed"
      :content="item.title"
      placement="right"
    >
      <router-link
        :to="item.path"
        class="tree-item"
        :class="[
          depthClass,
          { 'is-active': isActive(item.path), 'is-collapsed': collapsed },
        ]"
        :style="{ paddingLeft: indentPx + 'px' }"
      >
        <span class="tree-icon">
          <el-icon><component :is="resolveIcon(item.icon)" /></el-icon>
        </span>
        <span class="tree-label">{{ item.title }}</span>
        <el-icon v-if="item.menuType === 'I'" class="tree-external-icon"
          ><Link
        /></el-icon>
      </router-link>
    </el-tooltip>
    <!-- 分组/目录（有children的M类型） -->
    <el-tooltip
      v-else
      :disabled="!collapsed"
      :content="item.title"
      placement="right"
    >
      <div
        class="tree-group"
        :class="[
          depthClass,
          { 'is-open': isOpen(item.key), 'is-collapsed': collapsed },
        ]"
      >
        <div
          class="tree-parent"
          :style="{ paddingLeft: indentPx + 'px' }"
          @click="onParentClick(item)"
        >
          <span class="tree-icon">
            <el-icon><component :is="resolveIcon(item.icon)" /></el-icon>
          </span>
          <span class="tree-label">{{ item.title }}</span>
          <span class="tree-arrow">
            <el-icon><ArrowDown /></el-icon>
          </span>
        </div>
        <div v-show="isOpen(item.key) && !collapsed" class="tree-children">
          <SidebarTreeItem
            :items="item.children"
            :depth="depth + 1"
            :open-groups="openGroups"
            :current-path="currentPath"
            :collapsed="collapsed"
            @toggle="handleToggle"
            @navigate="handleNavigate"
          />
        </div>
      </div>
    </el-tooltip>
  </template>
</template>

<script setup>
import { computed } from "vue";
import * as ElIcons from "@element-plus/icons-vue";
import { ArrowDown, Link } from "@element-plus/icons-vue";

const props = defineProps({
  items: { type: Array, default: () => [] },
  depth: { type: Number, default: 0 },
  openGroups: { type: Set, default: () => new Set() },
  currentPath: { type: String, default: "" },
  collapsed: { type: Boolean, default: false },
});
const emit = defineEmits(["toggle", "navigate"]);

const depthClass = computed(() => `depth-${props.depth}`);
const indentPx = computed(() => {
  if (props.collapsed) return 0;
  if (props.depth === 0) return 16;
  return 16 + props.depth * 16;
});

function isLeaf(item) {
  // M 类型目录永远不视为叶子（即使 children 为空）
  if (item.menuType === "M") return false;
  return !item.children || item.children.length === 0;
}
/**
 * 判断是否为外部链接（C 类型且 path 为 http(s) 开头）
 */
function isExternalLink(item) {
  if (item.menuType === "I") return false;
  const url = item.path || item.component || "";
  return /^https?:\/\//i.test(url);
}
function getExternalUrl(item) {
  if (item.component && /^https?:\/\//i.test(item.component))
    return item.component;
  return item.path || "#";
}
function isActive(path) {
  if (!path) return false;
  return props.currentPath === path || props.currentPath.startsWith(path + "/");
}
function isOpen(key) {
  return props.openGroups.has(key);
}
function toggleGroup(key) {
  emit("toggle", key);
}
function handleToggle(key) {
  emit("toggle", key);
}
function handleNavigate(path) {
  emit("navigate", path);
}
function resolveIcon(name) {
  if (!name) return "Menu";
  return ElIcons[name] || ElIcons.Menu;
}

/**
 * 折叠态点击目录：无法展开子菜单，直接导航到第一个叶子
 */
function onParentClick(item) {
  if (props.collapsed) {
    const leaf = findFirstLeaf(item);
    if (leaf && leaf.path && !isExternalLink(leaf)) emit("navigate", leaf.path);
    return;
  }
  toggleGroup(item.key);
}

function findFirstLeaf(node) {
  if (node.path && (!node.children || node.children.length === 0)) return node;
  if (node.children) {
    for (const c of node.children) {
      const leaf = findFirstLeaf(c);
      if (leaf) return leaf;
    }
  }
  return null;
}
</script>

<style lang="scss" scoped>
.tree-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  margin: 2px 8px;
  border-radius: var(--ry-radius-medium);
  color: var(--ry-sidebar-text);
  text-decoration: none;
  font-size: 13px;
  cursor: pointer;
  transition:
    background 0.18s ease,
    color 0.18s ease;
  box-sizing: border-box;
  min-height: 40px;
  position: relative;

  &:hover {
    background: var(--ry-sidebar-bg-hover);
    color: var(--ry-sidebar-title);
    .tree-icon {
      color: var(--ry-sidebar-title);
    }
  }

  &.is-active {
    background: var(--ry-sidebar-bg-active);
    color: var(--ry-sidebar-text-active);
    font-weight: 600;
    box-shadow: none;
    .tree-icon {
      color: var(--ry-sidebar-text-active);
    }
    &::before {
      content: "";
      position: absolute;
      display: none;
    }
  }
}

.tree-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  color: var(--ry-sidebar-text);
  flex-shrink: 0;
  transition: color 0.18s ease;
  :deep(svg) {
    width: 16px;
    height: 16px;
  }
}

.tree-label {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tree-external-icon {
  font-size: 11px;
  opacity: 0.6;
  flex-shrink: 0;
  margin-left: 2px;
  :deep(svg) {
    width: 11px;
    height: 11px;
  }
}

.tree-group {
  .tree-parent {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 9px 12px;
    margin: 2px 8px;
    border-radius: var(--ry-radius-medium);
    color: var(--ry-sidebar-text);
    font-size: 13px;
    cursor: pointer;
    transition:
      background 0.18s ease,
      color 0.18s ease;
    box-sizing: border-box;
    min-height: 40px;

    &:hover {
      background: var(--ry-sidebar-bg-hover);
      color: var(--ry-sidebar-title);
      .tree-icon {
        color: var(--ry-sidebar-title);
      }
    }
  }

  &.is-open > .tree-parent {
    color: var(--ry-sidebar-title);
    .tree-icon {
      color: var(--ry-sidebar-title);
    }
    .tree-arrow {
      transform: rotate(180deg);
    }
  }
}

.tree-arrow {
  font-size: 11px;
  opacity: 0.7;
  transition: transform 0.2s ease;
  :deep(svg) {
    width: 11px;
    height: 11px;
  }
}

.tree-children {
  padding: 2px 0;
  position: relative;
  background: var(--ry-sidebar-sub-bg);
  &::before {
    display: none;
  }
}

/* 二级及更深层级：文字稍小 */
.depth-1,
.depth-2 {
  font-size: 12.5px;
}

/* 折叠态：只显示图标 */
.is-collapsed {
  &.tree-item,
  .tree-parent {
    justify-content: center;
    padding-left: 0 !important;
    padding-right: 0;
    margin: 2px 6px;
    min-height: 40px;
  }
  .tree-label,
  .tree-arrow,
  .tree-external-icon,
  .tree-dot {
    display: none;
  }
}
</style>
