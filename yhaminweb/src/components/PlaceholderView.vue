<template>
  <div class="ry-card ry-placeholder">
    <div class="placeholder-inner">
      <div class="placeholder-icon">
        <el-icon><component :is="icon || Box" /></el-icon>
      </div>
      <h2 class="placeholder-title">{{ title }}</h2>
      <p class="placeholder-desc">{{ desc }}</p>
      <div class="placeholder-actions">
        <el-button type="primary" @click="$router.push('/')"
          >返回首页</el-button
        >
        <el-button @click="$router.back()">返回上页</el-button>
      </div>
      <div v-if="hint" class="placeholder-hint">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ hint }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { Box, InfoFilled } from "@element-plus/icons-vue";

const props = defineProps({
  title: { type: String, default: "功能开发中" },
  description: { type: String, default: "" },
  icon: { type: [Object, Function], default: null },
  hint: { type: String, default: "" },
});

const desc = computed(
  () => props.description || `${props.title} 功能即将上线，敬请期待`,
);
</script>

<style lang="scss" scoped>
.ry-placeholder {
  padding: 60px 24px;
  display: flex;
  justify-content: center;
}

.placeholder-inner {
  text-align: center;
  max-width: 480px;
}

.placeholder-icon {
  width: 72px;
  height: 72px;
  margin: 0 auto 20px;
  border-radius: 16px;
  background: var(--ry-primary-50);
  color: var(--ry-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36px;
}

.placeholder-title {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 600;
  color: var(--ry-neutral-700);
}

.placeholder-desc {
  margin: 0 0 24px;
  font-size: 14px;
  color: var(--ry-neutral-500);
  line-height: 1.6;
}

.placeholder-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-bottom: 20px;
}

.placeholder-hint {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 4px;
  background: var(--ry-primary-50);
  color: var(--ry-primary);
  font-size: 12px;
}
</style>
