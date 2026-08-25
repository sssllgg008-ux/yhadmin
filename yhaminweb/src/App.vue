<template>
  <router-view />
</template>

<script setup>
import { onMounted } from "vue";
import { useAppStore, restoreTheme } from "@/store/modules/app";

const appStore = useAppStore();
// 登录页与后台框架共用同一套持久化主题
onMounted(() => restoreTheme(appStore));
</script>

<style lang="scss">
#app {
  width: 100%;
  height: 100%;
}
</style>
