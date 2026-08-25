<template>
  <template v-for="item in items" :key="item.key">
    <!-- 叶子菜单（无 children）：直接作为链接 -->
    <router-link
      v-if="isLeaf(item)"
      :to="item.path"
      class="sidebar-item"
      :class="[depthClass, { 'is-active': isActive(item.path) }]"
      @click="handleItemClick"
    >
      <span class="sidebar-icon">
        <el-icon><component :is="resolveIcon(item.icon)" /></el-icon>
      </span>
      <span v-show="!isCollapsed || depth > 0" class="sidebar-label">{{
        item.title
      }}</span>
    </router-link>

    <!-- 分组/目录（有 children）：可折叠 -->
    <div
      v-else
      class="sidebar-group"
      :class="[depthClass, { 'is-open': isOpen(item.key) }]"
    >
      <div class="sidebar-parent" @click="toggleGroup(item.key)">
        <span class="sidebar-icon">
          <el-icon><component :is="resolveIcon(item.icon)" /></el-icon>
        </span>
        <span v-show="!isCollapsed || depth > 0" class="sidebar-label">{{
          item.title
        }}</span>
        <span v-show="!isCollapsed || depth > 0" class="sidebar-arrow">
          <el-icon><ArrowDown /></el-icon>
        </span>
      </div>
      <!-- 子菜单容器：仅在展开且非折叠态显示 -->
      <div
        v-show="isOpen(item.key) && (!isCollapsed || depth > 0)"
        class="sidebar-children"
      >
        <SidebarMenuItem
          :items="item.children"
          :depth="depth + 1"
          :open-groups="openGroups"
          :is-collapsed="isCollapsed"
          @toggle="handleToggle"
          @item-click="handleItemClick"
        />
      </div>
    </div>
  </template>
</template>

<script setup>
import { useRoute } from "vue-router";
import * as ElIcons from "@element-plus/icons-vue";
import { ArrowDown } from "@element-plus/icons-vue";

const props = defineProps({
  items: { type: Array, required: true },
  depth: { type: Number, default: 0 },
  openGroups: { type: Set, required: true },
  isCollapsed: { type: Boolean, default: false },
});

const emit = defineEmits(["toggle", "item-click"]);

const route = useRoute();

const depthClass = computed(() => {
  if (props.depth === 0) return "is-depth-0";
  if (props.depth === 1) return "is-depth-1";
  return "is-depth-2";
});

function isLeaf(item) {
  return !item.children || item.children.length === 0;
}

function isOpen(key) {
  return props.openGroups.has(key);
}

function toggleGroup(key) {
  // 折叠态下仅顶层不可 toggle；深层（depth>0）的子目录仍可以
  if (props.isCollapsed && props.depth === 0) return;
  emit("toggle", key);
}

function handleToggle(key) {
  emit("toggle", key);
}

function isActive(path) {
  if (!path) return false;
  return route.path === path || route.path.startsWith(path + "/");
}

function handleItemClick() {
  emit("item-click");
}

function resolveIcon(name) {
  if (!name) return "Menu";
  return ElIcons[name] || ElIcons.Menu;
}
</script>

<style lang="scss" scoped>
.sidebar-item {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--ry-sidebar-text);
  cursor: pointer;
  transition: all 0.2s ease;
  text-decoration: none;
  border-left: 3px solid transparent;
  box-sizing: border-box;

  .sidebar-icon {
    color: var(--ry-sidebar-text);
    flex-shrink: 0;
  }
  .sidebar-label {
    flex: 1;
    min-width: 0;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &:hover {
    background: var(--ry-sidebar-bg-hover);
    color: var(--ry-sidebar-title);
    .sidebar-icon {
      color: var(--ry-sidebar-title);
    }
  }

  &.is-active {
    background: var(--ry-sidebar-bg-active);
    color: var(--ry-sidebar-text-active);
    border-left-color: var(--ry-sidebar-text-active);
    font-weight: 500;
    .sidebar-icon {
      color: var(--ry-sidebar-text-active);
    }
  }

  &.is-depth-0 {
    height: 40px;
    padding: 0 20px;
    font-size: 14px;
    font-weight: 500;
    .sidebar-icon {
      color: var(--ry-sidebar-title);
    }
    &.is-active .sidebar-icon {
      color: var(--ry-sidebar-text-active);
    }
  }
  &.is-depth-1 {
    height: 38px;
    padding: 0 20px 0 50px;
    font-size: 13px;
  }
  &.is-depth-2 {
    height: 36px;
    padding: 0 20px 0 66px;
    font-size: 12.5px;
  }
}

.sidebar-group {
  .sidebar-parent {
    display: flex;
    align-items: center;
    gap: 10px;
    cursor: pointer;
    transition: background 0.2s ease;
    user-select: none;
    box-sizing: border-box;

    .sidebar-icon {
      flex-shrink: 0;
    }
    .sidebar-label {
      flex: 1;
      min-width: 0;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .sidebar-arrow {
      font-size: 12px;
      line-height: 1;
      display: inline-flex;
      align-items: center;
      transition: transform 0.2s ease;
      flex-shrink: 0;
    }

    &:hover {
      background: var(--ry-sidebar-bg-hover);
    }
  }

  &.is-depth-0 .sidebar-parent {
    height: 40px;
    padding: 0 20px;
    color: var(--ry-sidebar-title);
    font-size: 14px;
    font-weight: 500;
    .sidebar-icon {
      color: var(--ry-sidebar-title);
    }
    .sidebar-arrow {
      color: var(--ry-sidebar-text);
    }
  }
  &.is-depth-1 .sidebar-parent {
    height: 38px;
    padding: 0 20px 0 50px;
    color: var(--ry-sidebar-text);
    font-size: 13px;
    gap: 10px;
    .sidebar-icon {
      color: var(--ry-sidebar-text);
    }
    .sidebar-arrow {
      color: var(--ry-sidebar-text);
      font-size: 11px;
    }
    &:hover {
      color: var(--ry-sidebar-title);
      .sidebar-icon {
        color: var(--ry-sidebar-title);
      }
    }
  }
  &.is-depth-2 .sidebar-parent {
    height: 36px;
    padding: 0 20px 0 66px;
    color: var(--ry-sidebar-text);
    font-size: 12.5px;
    gap: 8px;
    .sidebar-arrow {
      font-size: 10px;
      color: var(--ry-sidebar-text);
    }
  }

  &:not(.is-open) .sidebar-arrow {
    transform: rotate(-90deg);
  }
}

.sidebar-children {
  display: flex;
  flex-direction: column;
  padding: 2px 0 4px;
}

.sidebar-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  :deep(svg) {
    width: 16px;
    height: 16px;
  }
}
</style>
