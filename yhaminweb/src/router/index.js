import { createRouter, createWebHashHistory } from "vue-router";
import { menuTree } from "./menus";
import WarehouseDataSourceView from "@/views/warehouse/data-source/index.vue";

// 静态路由
export const constantRoutes = [
  {
    path: "/login",
    name: "Login",
    component: () => import("@/views/login/index.vue"),
    meta: { title: "登录", hidden: true },
  },
  {
    path: "/redirect",
    component: () => import("@/layout/index.vue"),
    meta: { hidden: true },
    children: [
      {
        path: "/redirect/:path(.*)",
        name: "Redirect",
        component: () => import("@/views/redirect/index.vue"),
        meta: { hidden: true },
      },
    ],
  },
  {
    path: "/",
    name: "RootLayout",
    component: () => import("@/layout/index.vue"),
    redirect: "/dashboard",
    children: [
      {
        path: "/dashboard",
        name: "Dashboard",
        component: () => import("@/views/dashboard/index.vue"),
        meta: { title: "工作台", icon: "HomeFilled", affix: true },
      },
      // 个人中心
      {
        path: "/profile",
        name: "Profile",
        component: () => import("@/views/profile/index.vue"),
        meta: { title: "个人中心", icon: "User" },
      },
      // 系统管理
      {
        path: "/system/user",
        name: "SystemUser",
        component: () => import("@/views/system/user/index.vue"),
        meta: { title: "用户管理", icon: "User" },
      },
      {
        path: "/system/role",
        name: "SystemRole",
        component: () => import("@/views/system/role/index.vue"),
        meta: { title: "角色管理", icon: "UserFilled" },
      },
      {
        path: "/system/menu",
        name: "SystemMenu",
        component: () => import("@/views/system/menu/index.vue"),
        meta: { title: "菜单管理", icon: "Menu" },
      },
      {
        path: "/system/module",
        name: "SystemModule",
        component: () => import("@/views/system/module/index.vue"),
        meta: { title: "模块管理", icon: "Box" },
      },
      {
        path: "/system/dept",
        name: "SystemDept",
        component: () => import("@/views/system/dept/index.vue"),
        meta: { title: "部门管理", icon: "OfficeBuilding" },
      },
      {
        path: "/system/dict",
        name: "SystemDict",
        component: () => import("@/views/system/dict/index.vue"),
        meta: { title: "字典管理", icon: "Collection" },
      },
      {
        path: "/system/config",
        name: "SystemConfig",
        component: () => import("@/views/system/config/index.vue"),
        meta: { title: "参数设置", icon: "Operation" },
      },
      {
        path: "/system/tenant",
        name: "SystemTenant",
        component: () => import("@/views/system/tenant/index.vue"),
        meta: { title: "租户管理", icon: "Coin" },
      },
      {
        path: "/system/plan",
        name: "SystemPlan",
        component: () => import("@/views/system/plan/index.vue"),
        meta: { title: "套餐管理", icon: "Tickets" },
      },
      // 通知公告（独立模块）
      {
        path: "/system/notice",
        alias: "/notice",
        name: "Notice",
        component: () => import("@/views/system/notice/index.vue"),
        meta: { title: "通知公告", icon: "Bell" },
      },
      // 系统监控
      {
        path: "/system/monitor/operlog",
        alias: "/monitor/operlog",
        name: "MonitorOperlog",
        component: () => import("@/views/monitor/operlog/index.vue"),
        meta: { title: "操作日志", icon: "Document" },
      },
      {
        path: "/system/monitor/logininfor",
        alias: "/monitor/logininfor",
        name: "MonitorLogininfor",
        component: () => import("@/views/monitor/logininfor/index.vue"),
        meta: { title: "登录日志", icon: "Key" },
      },
      {
        path: "/system/monitor/errorlog",
        alias: "/monitor/errorlog",
        name: "MonitorErrorlog",
        component: () => import("@/views/monitor/errorlog/index.vue"),
        meta: { title: "错误日志", icon: "Warning" },
      },
      // AI 模型管理
      {
        path: "/ai/model",
        name: "AiModel",
        component: () => import("@/views/ai/model/index.vue"),
        meta: { title: "模型管理", icon: "Cpu" },
      },
      {
        path: "/ai/provider",
        name: "AiProvider",
        component: () => import("@/views/ai/provider/index.vue"),
        meta: { title: "模型提供商", icon: "Connection" },
      },
      // AI 应用管理
      {
        path: "/ai/knowledge",
        name: "AiKnowledge",
        component: () => import("@/views/ai/knowledge/index.vue"),
        meta: { title: "知识库管理", icon: "Reading" },
      },
      {
        path: "/ai/knowledge/:id",
        name: "AiKnowledgeWorkbench",
        component: () => import("@/views/ai/knowledge/detail.vue"),
        meta: { title: "知识库工作台", icon: "Reading", keepAlive: false },
      },
      {
        path: "/ai/tools",
        name: "AiTools",
        component: () => import("@/views/ai/tools/index.vue"),
        meta: { title: "Tools管理", icon: "Tools" },
      },
      {
        path: "/ai/skills",
        name: "AiSkills",
        component: () => import("@/views/ai/skills/index.vue"),
        meta: { title: "Skills管理", icon: "MagicStick" },
      },
      {
        path: "/ai/mcp",
        name: "AiMcp",
        component: () => import("@/views/ai/mcp/index.vue"),
        meta: { title: "MCP管理", icon: "Connection" },
      },
      // AI 编排管理
      {
        path: "/ai/workflow",
        name: "AiWorkflow",
        component: () => import("@/views/ai/workflow/index.vue"),
        meta: { title: "Workflow工作流", icon: "SetUp" },
      },
      {
        path: "/ai/agent",
        name: "AiAgent",
        component: () => import("@/views/ai/agent/index.vue"),
        meta: { title: "Agent智能体", icon: "Service" },
      },
      {
        path: "/ai/bot",
        name: "AiBot",
        component: () => import("@/views/ai/bot/index.vue"),
        meta: { title: "Bot聊天发布", icon: "ChatLineRound" },
      },
      {
        path: "/warehouse/data-source",
        name: "WarehouseDataSource",
        component: WarehouseDataSourceView,
        meta: { title: "数据源管理", icon: "Connection" },
      },
      {
        path: "/warehouse/domain",
        name: "WarehouseDomain",
        component: () => import("@/views/warehouse/domain/index.vue"),
        meta: { title: "业务领域", icon: "Collection" },
      },
      {
        path: "/warehouse/dimension",
        name: "WarehouseDimension",
        component: () => import("@/views/warehouse/dimension/index.vue"),
        meta: { title: "维度管理", icon: "Grid" },
      },
      {
        path: "/warehouse/fact",
        name: "WarehouseFact",
        component: () => import("@/views/warehouse/fact/index.vue"),
        meta: { title: "事实表管理", icon: "DataBoard" },
      },
      {
        path: "/warehouse/metric",
        name: "WarehouseMetric",
        component: () => import("@/views/warehouse/metric/index.vue"),
        meta: { title: "指标管理", icon: "TrendCharts" },
      },
      {
        path: "/warehouse/dataset",
        name: "WarehouseDataset",
        component: () => import("@/views/warehouse/dataset/index.vue"),
        meta: { title: "数据集管理", icon: "Files" },
      },
    ],
  },
  {
    path: "/:pathMatch(.*)*",
    name: "NotFound",
    component: () => import("@/views/error/404.vue"),
    meta: { title: "404", hidden: true },
  },
];

const router = createRouter({
  history: createWebHashHistory(),
  routes: constantRoutes,
  scrollBehavior: () => ({ top: 0 }),
});

export { menuTree };
export default router;
