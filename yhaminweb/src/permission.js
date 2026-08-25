/**
 * 全局路由守卫
 */
import router from "./router";
import NProgress from "nprogress";
import { getToken } from "@/utils/auth";
import { useUserStore } from "@/store/modules/user";
import { usePermissionStore } from "@/store/modules/permission";
import { useTabsViewStore } from "@/store/modules/tabsView";

NProgress.configure({ showSpinner: false, trickleSpeed: 200 });

const whiteList = ["/login", "/404"];

router.beforeEach(async (to, from, next) => {
  NProgress.start();
  document.title = to.meta?.title
    ? `${to.meta.title} · ${import.meta.env.VITE_APP_TITLE || "权限管理后台"}`
    : import.meta.env.VITE_APP_TITLE || "权限管理后台";

  if (to.path === "/login") {
    if (getToken()) {
      next("/");
    } else {
      next();
    }
    NProgress.done();
    return;
  }

  if (whiteList.includes(to.path)) {
    next();
    NProgress.done();
    return;
  }

  if (!getToken()) {
    next(`/login?redirect=${encodeURIComponent(to.fullPath)}`);
    NProgress.done();
    return;
  }

  const userStore = useUserStore();
  const permissionStore = usePermissionStore();

  try {
    if (!userStore.userInfo.username) {
      await userStore.fetchInfo();
      permissionStore.generateMenus();
      // 当前导航开始时动态路由尚未注册，替换一次导航以重新匹配新菜单路径。
      next({ ...to, replace: true });
      return;
    }
    if (!permissionStore.loaded) {
      permissionStore.generateMenus();
      next({ ...to, replace: true });
      return;
    }
    next();
  } catch (err) {
    console.error("[Router Guard] fetch user info failed:", err);
    userStore.resetState();
    next(`/login?redirect=${encodeURIComponent(to.fullPath)}`);
    NProgress.done();
  }
});

router.afterEach((to) => {
  if (to.meta?.title && !to.meta?.hidden) {
    const tabsStore = useTabsViewStore();
    tabsStore.addView(to);
  }
  NProgress.done();
});
