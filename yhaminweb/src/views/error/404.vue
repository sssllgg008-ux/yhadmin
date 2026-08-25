<template>
  <div class="err-page">
    <div class="err-code">404</div>
    <div class="err-text">抱歉，您访问的页面不存在</div>
    <div class="err-actions">
      <el-button type="primary" @click="goHome">返回首页</el-button>
      <el-button @click="goBack">返回上一页</el-button>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from "vue-router";

const router = useRouter();

function goHome() {
  router.push("/");
}

function goBack() {
  router.go(-1);
}
</script>

<style lang="scss" scoped>
.err-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 60vh;
  text-align: center;
  padding: 40px 20px;
}

.err-code {
  font-family: var(--ry-font-title);
  font-size: 120px;
  font-weight: 600;
  line-height: 1;
  color: var(--ry-primary);
  text-shadow: 0 4px 16px rgba(64, 158, 255, 0.18);
}

.err-text {
  margin-top: 12px;
  font-size: 16px;
  color: var(--ry-neutral-600);
}

.err-actions {
  margin-top: 28px;
  display: flex;
  gap: 12px;
}
</style>
