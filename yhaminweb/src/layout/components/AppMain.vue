<template>
  <section class="app-main">
    <router-view v-slot="{ Component, route }">
      <transition name="fade-transform" mode="out-in">
        <keep-alive :include="cachedNames">
          <component :is="Component" :key="route.path" />
        </keep-alive>
      </transition>
    </router-view>
  </section>
</template>

<script setup>
import { useTabsViewStore } from "@/store/modules/tabsView";
const tabsStore = useTabsViewStore();
const cachedNames = computed(() => tabsStore.cachedNames);
</script>

<style lang="scss" scoped>
.app-main {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  background: var(--ry-background);
  padding: 20px 22px 24px;
  scroll-behavior: smooth;

  &::-webkit-scrollbar {
    width: 8px;
    height: 8px;
  }
  &::-webkit-scrollbar-thumb {
    background: var(--ry-neutral-300);
    border-radius: var(--ry-radius-full);
  }
  &::-webkit-scrollbar-thumb:hover {
    background: var(--ry-neutral-400);
  }
  &::-webkit-scrollbar-track {
    background: transparent;
  }
}

/* 移动端紧凑内边距 */
@media (max-width: 768px) {
  .app-main {
    padding: 12px;
  }
}
</style>
