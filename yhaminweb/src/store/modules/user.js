import { defineStore } from "pinia";
import { login as apiLogin, getInfo, logout as apiLogout } from "@/api/login";
import { getToken, setToken, removeToken } from "@/utils/auth";

const TENANT_KEY = "tenantId";

/**
 * 用户状态：token、租户ID、用户信息、权限码
 */
export const useUserStore = defineStore("user", {
  state: () => ({
    token: getToken() || "",
    tenantId: Number(localStorage.getItem(TENANT_KEY)) || 1,
    userInfo: {},
    roles: [],
    permissions: [],
    menus: [],
  }),
  getters: {
    isLogin: (state) => !!state.token,
    username: (state) => state.userInfo.username || "",
    displayName: (state) =>
      state.userInfo.nickname || state.userInfo.username || "",
    avatarText: (state) => {
      const n = state.userInfo.nickname || state.userInfo.username || "若";
      return n.slice(0, 1);
    },
  },
  actions: {
    async login(payload) {
      const res = await apiLogin(payload);
      // 后端 R<> 包装：{ code, msg, data: { token } }
      const token = (res.data && res.data.token) || res.token;
      this.token = token;
      setToken(token);
      // 保存租户ID（登录时携带）
      if (
        payload.tenantId !== undefined &&
        payload.tenantId !== null &&
        payload.tenantId !== ""
      ) {
        this.tenantId = payload.tenantId;
        localStorage.setItem(TENANT_KEY, payload.tenantId);
      }
      return res;
    },
    async fetchInfo() {
      const res = await getInfo();
      // 兼容 R<> 包装：data 在 res.data 内；旧扁平格式直接读 res
      const info = res.data || res;
      this.userInfo = info.user || {};
      this.roles = info.roles || [];
      this.permissions = info.permissions || [];
      this.menus = info.menus || [];
      // 保存租户ID（后端返回时优先）
      const tenantId = info.tenantId ?? info.user?.tenantId;
      if (tenantId !== undefined && tenantId !== null && tenantId !== "") {
        this.tenantId = tenantId;
        localStorage.setItem(TENANT_KEY, tenantId);
      }
      return info;
    },
    async logout() {
      try {
        await apiLogout();
      } catch (e) {
        // 忽略登出失败
      }
      this.resetState();
    },
    resetState() {
      this.token = "";
      this.tenantId = 1;
      this.userInfo = {};
      this.roles = [];
      this.permissions = [];
      this.menus = [];
      removeToken();
      localStorage.removeItem(TENANT_KEY);
    },
    hasPermission(code) {
      if (!code) return true;
      if (this.roles.includes("admin") || this.roles.includes("*")) return true;
      if (this.permissions.includes("*")) return true;
      return this.permissions.includes(code);
    },
  },
});
