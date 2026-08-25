<template>
  <div class="breadcrumb-bar">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item v-for="g in breadcrumbItems" :key="g.key">{{
        g.title
      }}</el-breadcrumb-item>
    </el-breadcrumb>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { useRoute } from "vue-router";
import { usePermissionStore } from "@/store/modules/permission";

const permissionStore = usePermissionStore();
const route = useRoute();

const menuTree = computed(() => permissionStore.menus);
const currentPath = computed(() => route.path);

// 递归查找当前路由的面包屑路径
function findBreadcrumbPath(items, targetPath, trail = []) {
  for (const item of items) {
    const nextTrail = item.path
      ? [...trail]
      : [...trail, { key: item.key, title: item.title }];
    if (item.path === targetPath) {
      return nextTrail;
    }
    if (item.children && item.children.length) {
      const found = findBreadcrumbPath(item.children, targetPath, nextTrail);
      if (found) return found;
    }
  }
  return null;
}

const currentGroup = computed(() => {
  const path = findBreadcrumbPath(menuTree.value, currentPath.value) || [];
  // 模块与其唯一根目录可能同名（如"系统管理"），面包屑只展示一次。
  return path.filter(
    (item, index) => index === 0 || item.title !== path[index - 1].title,
  );
});

const currentItem = computed(() => {
  const list = [];
  function walk(items) {
    items.forEach((item) => {
      if (item.path) list.push(item);
      if (item.children && item.children.length) walk(item.children);
    });
  }
  walk(menuTree.value);
  return list.find((i) => i.path === currentPath.value) || null;
});

const breadcrumbItems = computed(() => {
  const items = currentItem.value
    ? [...currentGroup.value, currentItem.value]
    : currentGroup.value;
  return items.filter(
    (item, index) => index === 0 || item.title !== items[index - 1].title,
  );
});
</script>

<style lang="scss" scoped>
.breadcrumb-bar {
  display: flex;
  align-items: center;
  height: 34px;
  padding: 0 16px;
  flex-shrink: 0;
  background: var(--ry-card);
  border-bottom: 1px solid var(--ry-border-light);

  :deep(.el-breadcrumb) {
    font-size: 12px;
  }
}
</style>
