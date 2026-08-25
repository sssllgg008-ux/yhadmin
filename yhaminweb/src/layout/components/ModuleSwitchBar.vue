<template>
  <div v-if="moduleGroups.length" class="module-switch-bar">
    <div
      v-for="m in moduleGroups"
      :key="m.key"
      class="module-item"
      :class="{ 'is-active': m.key === activeKey }"
      @click="handleChange(m.key)"
    >
      <el-icon class="module-icon"
        ><component :is="resolveIcon(m.icon)"
      /></el-icon>
      <span class="module-title">{{ m.title }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from "vue";
import * as ElIcons from "@element-plus/icons-vue";
import { usePermissionStore } from "@/store/modules/permission";

const props = defineProps({
  activeKey: { type: String, default: "" },
});
const emit = defineEmits(["switch"]);

const permissionStore = usePermissionStore();

// 仅模块分组（有 children 的顶层节点）；独立叶菜单（如"系统首页"）不属于模块，不在此处显示
const moduleGroups = computed(() => permissionStore.moduleGroups);

function handleChange(key) {
  emit("switch", key);
}

function resolveIcon(name) {
  if (!name) return "Menu";
  return ElIcons[name] || ElIcons.Menu;
}
</script>

<style lang="scss" scoped>
.module-switch-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  overflow-x: auto;
  min-width: 0;

  &::-webkit-scrollbar {
    height: 0;
  }
}

.module-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 49px;
  padding: 0 14px;
  border-radius: 0;
  font-size: 13px;
  color: var(--ry-neutral-600);
  cursor: pointer;
  white-space: nowrap;
  transition:
    background 0.2s ease,
    color 0.2s ease;

  &:hover {
    color: var(--ry-primary);
    background: var(--ry-neutral-100);

    .module-icon {
      color: var(--ry-primary);
    }
  }

  &.is-active {
    color: var(--ry-primary);
    font-weight: 600;
    background: var(--ry-primary-50);
    box-shadow: inset 0 -2px 0 var(--ry-primary);

    .module-icon {
      color: var(--ry-primary);
    }
  }
}

.module-icon {
  font-size: 15px;
  color: var(--ry-neutral-500);
  transition: color 0.2s ease;
}

.module-title {
  line-height: 1;
}
</style>
