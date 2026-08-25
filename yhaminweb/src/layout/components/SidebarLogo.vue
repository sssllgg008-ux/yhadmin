<template>
  <div class="sidebar-logo" :class="{ 'is-collapsed': collapsed }">
    <span class="logo-mark">
      <el-icon class="logo-icon"><Key /></el-icon>
    </span>
    <span v-show="!collapsed" class="logo-name">{{ title }}</span>
  </div>
</template>

<script setup>
import { Key } from "@element-plus/icons-vue";
defineProps({
  collapsed: { type: Boolean, default: false },
  title: { type: String, default: "权限管理后台" },
});
</script>

<style lang="scss" scoped>
.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  height: var(--ry-topbar-height);
  padding: 0 20px;
  flex-shrink: 0;
  border-bottom: 1px solid var(--ry-sidebar-divider);
  box-sizing: border-box;

  &.is-collapsed {
    justify-content: center;
    padding: 0;
    gap: 0;
    .logo-mark {
      width: 35px;
      height: 35px;
    }
  }
}
.logo-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 35px;
  height: 35px;
  border-radius: 5px;
  background: var(--ry-gradient-primary);
  box-shadow: var(--ry-shadow-primary-glow);
  flex-shrink: 0;
}
.logo-icon {
  color: var(--ry-primary-foreground);
  font-size: 18px;
}
.logo-name {
  color: var(--ry-sidebar-title);
  font-family: var(--ry-font-title);
  font-weight: 600;
  font-size: 16px;
  letter-spacing: 0.5px;
  line-height: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
