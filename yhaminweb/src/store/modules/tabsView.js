import { defineStore } from "pinia";

/**
 * 多标签页：维护已打开的页面 tab 列表
 */
export const useTabsViewStore = defineStore("tabsView", {
  state: () => ({
    visitedViews: [],
  }),
  getters: {
    cachedNames: (state) =>
      state.visitedViews
        .filter((v) => v.cacheable && v.name)
        .map((v) => v.name),
  },
  actions: {
    addView(route) {
      if (!route.name || route.meta?.hidden) return;
      if (this.visitedViews.some((v) => v.path === route.path)) return;
      const view = {
        name: route.name,
        path: route.path,
        title: route.meta?.title || "未命名",
        affix: !!route.meta?.affix,
        cacheable: route.meta?.keepAlive !== false,
      };
      if (view.affix) {
        // affix 标签（如工作台）始终插入到第一个非 affix 标签之前，
        // 保证固定标签永远排在可关闭标签之前
        const firstNonAffixIdx = this.visitedViews.findIndex((v) => !v.affix);
        if (firstNonAffixIdx === -1) {
          this.visitedViews.push(view);
        } else {
          this.visitedViews.splice(firstNonAffixIdx, 0, view);
        }
      } else {
        this.visitedViews.push(view);
      }
    },
    removeView(path) {
      const idx = this.visitedViews.findIndex((v) => v.path === path);
      if (idx === -1) return null;
      if (this.visitedViews[idx].affix) return null;
      const next =
        this.visitedViews[idx + 1] || this.visitedViews[idx - 1] || null;
      this.visitedViews.splice(idx, 1);
      return next;
    },
    removeOthers(path) {
      this.visitedViews = this.visitedViews.filter(
        (v) => v.affix || v.path === path,
      );
    },
    removeAll() {
      const affix = this.visitedViews.filter((v) => v.affix);
      this.visitedViews = affix;
      return affix[affix.length - 1] || null;
    },
    reset() {
      this.visitedViews = [];
    },
  },
});
