<template>
  <div class="module-tabs" v-if="modules.length > 0">
    <div
      v-for="mod in modules"
      :key="mod.key"
      class="module-tab"
      :class="{ 'is-active': isActive(mod) }"
      @click="handleClick(mod)"
    >
      <el-icon v-if="mod.icon" class="tab-icon"
        ><component :is="resolveIcon(mod.icon)"
      /></el-icon>
      <span class="tab-label">{{ mod.title }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import * as ElIcons from "@element-plus/icons-vue";
import { usePermissionStore } from "@/store/modules/permission";

const props = defineProps({
  activeKey: { type: String, default: "" },
});

const permissionStore = usePermissionStore();
const route = useRoute();
const router = useRouter();

const modules = computed(() => permissionStore.moduleGroups);

function isActive(mod) {
  if (props.activeKey && props.activeKey === mod.key) return true;
  if (mod.children) {
    return mod.children.some((c) => {
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
  }
  return false;
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

function handleClick(mod) {
  if (isActive(mod)) return;
  const firstLeaf = findFirstLeaf(mod);
  if (firstLeaf && firstLeaf.path) {
    router.push(firstLeaf.path);
  }
}

function resolveIcon(name) {
  if (!name) return "Menu";
  return ElIcons[name] || ElIcons.Menu;
}
</script>

<style lang="scss" scoped>
.module-tabs {
  display: flex;
  align-items: center;
  gap: 0;
  padding: 0 16px;
  height: var(--ry-module-tabs-height);
  background: var(--ry-card);
  border-bottom: 1px solid var(--ry-border);
  flex-shrink: 0;
  overflow-x: auto;
  white-space: nowrap;

  &::-webkit-scrollbar {
    height: 0;
  }
}

.module-tab {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 0 16px;
  height: var(--ry-module-tabs-height);
  font-size: 13px;
  color: var(--ry-text-regular);
  cursor: pointer;
  position: relative;
  transition: color 0.15s ease;
  box-sizing: border-box;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;

  &:hover {
    color: var(--ry-primary);
  }

  &.is-active {
    color: var(--ry-primary);
    font-weight: 500;
    border-bottom-color: var(--ry-primary);
  }
}

.tab-icon {
  font-size: 15px;
}

.tab-label {
  font-size: 13px;
}
</style>
