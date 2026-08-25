import { defineStore } from "pinia";
import router from "@/router";
import { useUserStore } from "./user";
import { menuTree as staticMenuTree } from "@/router/menus";

// Vite 要求动态导入路径在构建时可分析，因此使用视图模块表解析后端 component。
const VIEW_MODULES = import.meta.glob("/src/views/**/*.vue");

function resolveView(component) {
  if (!component) return null;
  const normalized = String(component)
    .trim()
    .replace(/^\/+/, "")
    .replace(/^src\/views\//, "")
    .replace(/^views\//, "")
    .replace(/\.vue$/, "");
  return (
    VIEW_MODULES[`/src/views/${normalized}.vue`] ||
    VIEW_MODULES[`/src/views/${normalized}/index.vue`] ||
    null
  );
}

/**
 * 数据库图标名 → Element Plus 图标名映射
 */
const ICON_MAP = {
  dashboard: "HomeFilled",
  HomeFilled: "HomeFilled",
  system: "Setting",
  monitor: "Monitor",
  message: "Bell",
  Menu: "Menu",
  user: "User",
  peoples: "UserFilled",
  "tree-table": "Grid",
  tree: "OfficeBuilding",
  dict: "Collection",
  edit: "Operation",
  form: "Document",
  logininfor: "Key",
  tenant: "Coin",
  Box: "Box",
  module: "Box",
  role: "UserFilled",
  config: "Operation",
  dept: "OfficeBuilding",
  operlog: "Document",
  extend: "Document",
};

function mapIcon(dbIcon) {
  if (!dbIcon || dbIcon === "#") return "Menu";
  return ICON_MAP[dbIcon] || dbIcon || "Menu";
}

function normalizePath(p) {
  if (!p) return "/";
  let path = "/" + String(p).replace(/^\/+/, "").replace(/\/+$/, "");
  path = path.replace(/\/+/g, "/");
  return path;
}

/**
 * 规范化外部链接 URL
 * 支持 //example.com（协议相对）和 http(s)://example.com 两种写法
 */
function normalizeExternalUrl(url) {
  if (!url) return "";
  const u = String(url).trim();
  if (/^https?:\/\//i.test(u)) return u;
  if (u.startsWith("//")) return window.location.protocol + u;
  return u;
}

/**
 * 将后端返回的菜单树转换为前端侧边栏所需格式
 * 后端格式：{ id, name, path, component, icon, menuType, perms, children }
 * 前端格式：{ key, title, icon, path?, routeName?, menuType, children }
 *
 * 支持三种节点：
 * 1. 独立叶菜单（menuType=C，无 children 或 children 为空）→ path 可点击
 * 2. 模块/目录（menuType=M，有 children）→ 可折叠分组，path 为 null
 * 3. 带子菜单的菜单（menuType=C 或 M，有 children）→ 可折叠，若自身有 path 也可点击
 */
function convertMenus(rawMenus) {
  if (!rawMenus || !rawMenus.length) return [];
  return rawMenus.map((m) => {
    const node = {
      key: "menu-" + m.id,
      title: m.name,
      icon: mapIcon(m.icon),
      path: null,
      routeName: m.component || null,
      menuType: m.menuType,
      externalUrl: null,
      alwaysShow: false,
    };

    const hasChildren = !!(m.children && m.children.length);

    if (m.menuType === "C" || m.menuType === "I") {
      node.path = normalizePath(m.path || "");
    }

    // 内链类型：component 存外部 URL，内嵌 iframe 显示
    if (m.menuType === "I" && m.component) {
      node.externalUrl = normalizeExternalUrl(m.component);
    }

    if (m.menuType === "M") {
      node.path = null;
    }

    if (hasChildren) {
      // 递归处理子节点，父路径传当前菜单 path 用于拼接子路径
      node.children = convertChildren(m.children, m.path || "");
      // 如果目录下只有一个子菜单，仍显示分组
      node.alwaysShow = true;
    } else {
      node.children = [];
    }
    return node;
  });
}

/**
 * 递归转换子节点：子菜单的 path 已经是后端拼接好的完整路径（如 system/user），
 * 无需父路径再次拼接，直接加 "/" 前缀即可
 */
function convertChildren(children) {
  if (!children || !children.length) return [];
  return children.map((m) => {
    const hasChildren = !!(m.children && m.children.length);
    const node = {
      key: "menu-" + m.id,
      title: m.name,
      icon: mapIcon(m.icon),
      path:
        m.menuType === "C" || m.menuType === "I"
          ? normalizePath(m.path || "")
          : null,
      routeName: m.component || null,
      menuType: m.menuType,
      externalUrl: null,
      alwaysShow: false,
    };
    // 内链类型：component 存外部 URL，内嵌 iframe 显示
    if (m.menuType === "I" && m.component) {
      node.externalUrl = normalizeExternalUrl(m.component);
    }
    if (hasChildren) {
      node.children = convertChildren(m.children);
      node.alwaysShow = true;
    } else {
      node.children = [];
    }
    return node;
  });
}

/**
 * 权限状态：菜单树、路由
 */
export const usePermissionStore = defineStore("permission", {
  state: () => ({
    menus: [],
    routes: [],
    loaded: false,
    isDynamic: false,
    dynamicRouteRemovers: [],
  }),
  getters: {
    /** 所有顶层菜单节点（含独立菜单项和模块分组），用于侧边栏渲染 */
    menuGroups: (state) => state.menus,
    /** 仅模块分组（有 children 的顶层节点），用于顶部模块Tab和图标栏高亮 */
    moduleGroups: (state) =>
      state.menus.filter((m) => m.children && m.children.length > 0),
  },
  actions: {
    generateMenus() {
      const userStore = useUserStore();
      const rawMenus = userStore.menus;
      if (rawMenus && rawMenus.length > 0) {
        this.menus = convertMenus(rawMenus);
        this.isDynamic = true;
      } else {
        this.menus = staticMenuTree;
        this.isDynamic = false;
      }
      this.routes = flattenRoutes(this.menus);
      // 为内链（I 类型）菜单动态注册路由，使其内嵌 iframe 显示
      registerIframeRoutes(this.menus);
      this.registerComponentRoutes(this.menus);
      this.loaded = true;
      return this.menus;
    },
    reset() {
      this.dynamicRouteRemovers.forEach((remove) => remove());
      this.dynamicRouteRemovers = [];
      this.menus = [];
      this.routes = [];
      this.loaded = false;
      this.isDynamic = false;
    },
    /** 按菜单 component 注册普通页面路由，使菜单移动目录后仍能打开原组件。 */
    registerComponentRoutes(tree) {
      const walk = (nodes) => {
        nodes.forEach((node) => {
          if (node.menuType === "C" && node.path && node.routeName) {
            const component = resolveView(node.routeName);
            const routeName = `Dynamic_${node.key}`;
            if (component && !router.hasRoute(routeName)) {
              const remove = router.addRoute("RootLayout", {
                path: node.path,
                name: routeName,
                component,
                meta: {
                  title: node.title,
                  icon: node.icon,
                  dynamicMenu: true,
                },
              });
              this.dynamicRouteRemovers.push(remove);
            }
          }
          if (node.children?.length) walk(node.children);
        });
      };
      walk(tree || []);
    },
  },
});

function flattenRoutes(tree) {
  const list = [];
  function walk(nodes) {
    nodes.forEach((n) => {
      if (n.path) list.push(n);
      if (n.children?.length) walk(n.children);
    });
  }
  walk(tree);
  return list;
}

/**
 * 已注册的内链路由名称集合，避免重复注册
 */
const registeredIframeRoutes = new Set();

/**
 * 遍历菜单树，为所有内链（I 类型）菜单动态注册路由
 * 路由指向 IframeView 组件，externalUrl 通过 meta 传递
 */
function registerIframeRoutes(tree) {
  function walk(nodes) {
    nodes.forEach((n) => {
      if (n.menuType === "I" && n.path && n.externalUrl) {
        const routeName = "Iframe_" + n.key;
        if (!registeredIframeRoutes.has(routeName)) {
          router.addRoute({
            path: n.path,
            name: routeName,
            component: () => import("@/views/iframe/index.vue"),
            meta: { title: n.title, icon: n.icon, externalUrl: n.externalUrl },
          });
          registeredIframeRoutes.add(routeName);
        }
      }
      if (n.children?.length) walk(n.children);
    });
  }
  walk(tree);
}

export { mapIcon };
