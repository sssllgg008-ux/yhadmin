<template>
  <div class="iframe-container">
    <iframe
      v-if="src"
      :src="src"
      frameborder="0"
      allow="fullscreen"
      class="iframe-view"
      @load="onLoad"
    />
    <div v-else class="iframe-empty">
      <el-empty description="未配置外部链接地址" />
    </div>
  </div>
</template>

<script setup>
import { computed } from "vue";
import { useRoute } from "vue-router";

const route = useRoute();
const src = computed(() => route.meta?.externalUrl || "");

function onLoad() {
  // iframe 加载完成
}
</script>

<style lang="scss" scoped>
.iframe-container {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.iframe-view {
  flex: 1;
  width: 100%;
  border: none;
  border-radius: var(--ry-radius-base);
  background: #fff;
}
</style>
