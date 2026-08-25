import { defineStore } from "pinia";

/**
 * 应用全局状态：侧栏折叠、设备类型、Element Plus 尺寸、布局设置
 */
export const useAppStore = defineStore("app", {
  state: () => ({
    sidebarCollapsed: false,
    /** 'desktop' | 'mobile' */
    device: "desktop",
    /** Element Plus 组件尺寸：large / default / small */
    size: "default",
    /** 移动端侧栏抽屉是否打开 */
    mobileSidebarOpen: false,
    /** 布局设置抽屉是否打开 */
    settingDrawerOpen: false,
    /** 主题色（十六进制），会动态覆盖 --ry-primary / --el-color-primary */
    themeColor: "#1677FF",
    /** 侧栏配色：'dark' 深色 | 'light' 浅色 */
    sidebarTheme: "dark",
    /** 视觉主题迁移版本 */
    uiThemeVersion: 0,
    /** 是否显示顶部标签栏 */
    showTabs: true,
    /** 是否显示面包屑 */
    showBreadcrumb: true,
    /** 是否显示模块切换栏 */
    showModuleBar: true,
    /** 是否固定头部（不随内容滚动） */
    fixedHeader: true,
  }),
  actions: {
    toggleSidebar() {
      if (this.device === "mobile") {
        this.mobileSidebarOpen = !this.mobileSidebarOpen;
      } else {
        this.sidebarCollapsed = !this.sidebarCollapsed;
      }
    },
    setDevice(device) {
      this.device = device;
      if (device === "mobile") {
        this.mobileSidebarOpen = false;
      }
    },
    setSize(size) {
      this.size = size;
    },
    closeMobileSidebar() {
      this.mobileSidebarOpen = false;
    },
    openSettingDrawer() {
      this.settingDrawerOpen = true;
    },
    closeSettingDrawer() {
      this.settingDrawerOpen = false;
    },
    setThemeColor(color) {
      this.themeColor = color;
      applyThemeVars(color);
    },
    setSidebarTheme(theme) {
      this.sidebarTheme = theme;
      applySidebarTheme(theme);
    },
  },
  persist: {
    paths: [
      "sidebarCollapsed",
      "size",
      "themeColor",
      "sidebarTheme",
      "uiThemeVersion",
      "showTabs",
      "showBreadcrumb",
      "showModuleBar",
      "fixedHeader",
    ],
  },
});

/**
 * 预设主题色板
 */
export const THEME_PRESETS = [
  { name: "拂晓蓝", color: "#1677FF" },
  { name: "专业蓝", color: "#3B6EF5" },
  { name: "靛青蓝", color: "#6172F3" },
  { name: "经典蓝", color: "#409EFF" },
  { name: "极客绿", color: "#00B42A" },
  { name: "晚霞橙", color: "#FF7D00" },
  { name: "胭脂红", color: "#F53F3F" },
  { name: "暗夜紫", color: "#722ED1" },
  { name: "青色", color: "#0FBFC6" },
];

/** 默认主题色，与 theme.scss 同源 */
export const DEFAULT_THEME_COLOR = "#1677FF";

/**
 * 将十六进制颜色转为 RGB 分量
 */
function hexToRgb(hex) {
  const h = hex.replace("#", "");
  const full =
    h.length === 3
      ? h
          .split("")
          .map((c) => c + c)
          .join("")
      : h;
  const bigint = parseInt(full, 16);
  return { r: (bigint >> 16) & 255, g: (bigint >> 8) & 255, b: bigint & 255 };
}

/**
 * 颜色混合：将原色与白色按比例混合
 * @param hex 原色十六进制
 * @param whiteRatio 白色占比 (0=原色, 1=纯白)
 */
function mixWithWhite(hex, whiteRatio) {
  const { r, g, b } = hexToRgb(hex);
  const mix = (c) => Math.round(c * (1 - whiteRatio) + 255 * whiteRatio);
  return `rgb(${mix(r)}, ${mix(g)}, ${mix(b)})`;
}

/**
 * 颜色混合：将原色与黑色按比例混合
 * @param hex 原色十六进制
 * @param blackRatio 黑色占比 (0=原色, 1=纯黑)
 */
function mixWithBlack(hex, blackRatio) {
  const { r, g, b } = hexToRgb(hex);
  const mix = (c) => Math.round(c * (1 - blackRatio));
  return `rgb(${mix(r)}, ${mix(g)}, ${mix(b)})`;
}

/**
 * 计算颜色的相对亮度（WCAG 标准），用于判断主题色深浅
 */
function relativeLuminance(hex) {
  const { r, g, b } = hexToRgb(hex);
  const toLinear = (c) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * toLinear(r) + 0.7152 * toLinear(g) + 0.0722 * toLinear(b);
}

/**
 * 根据主题色动态生成色阶并写入 CSS 变量
 *
 * 色阶体系（50~900，数字越大颜色越深）：
 *   50:  95% 白色混合（最浅，用于激活态背景）
 *   100: 90% 白色混合
 *   200: 80% 白色混合（用于浅色背景）
 *   300: 70% 白色混合
 *   400: 30% 白色混合（用于 hover）
 *   500: 原色（基色）
 *   600: 15% 黑色混合
 *   700: 28% 黑色混合（用于激活态文字）
 *   800: 40% 黑色混合
 *   900: 55% 黑色混合（最深）
 *
 * Element Plus light-N 映射（N 越大越浅）：
 *   light-3 → 30% 白色混合
 *   light-5 → 50% 白色混合
 *   light-7 → 70% 白色混合
 *   light-8 → 80% 白色混合
 *   light-9 → 90% 白色混合
 *   dark-2  → 20% 黑色混合
 */
function applyThemeVars(hex) {
  const root = document.documentElement;

  // 基色
  root.style.setProperty("--ry-primary", hex);
  root.style.setProperty("--ry-primary-500", hex);
  root.style.setProperty("--el-color-primary", hex);

  // RGB 分量（供 rgba() 使用，避免到处硬编码）
  const { r, g, b } = hexToRgb(hex);
  root.style.setProperty("--ry-primary-rgb", `${r}, ${g}, ${b}`);

  // 前景色：根据亮度自动选择深色或白色
  const lum = relativeLuminance(hex);
  const foreground = lum > 0.6 ? "#1d2129" : "#FFFFFF";
  root.style.setProperty("--ry-primary-foreground", foreground);
  // 仅覆盖 primary 变体按钮的文字色（hover/active 状态），避免污染普通按钮
  root.style.setProperty("--el-button-hover-text-color", foreground);
  root.style.setProperty("--el-button-active-text-color", foreground);
  root.style.setProperty("--el-button-active-border-color", hex);
  root.style.setProperty("--el-radio-button-checked-text-color", foreground);
  root.style.setProperty("--el-radio-button-checked-bg-color", hex);
  root.style.setProperty("--el-radio-button-checked-border-color", hex);

  // 主色渐变 & 光晕（用于 Logo 等品牌元素）
  root.style.setProperty(
    "--ry-gradient-primary",
    `linear-gradient(135deg, ${hex} 0%, ${mixWithBlack(hex, 0.15)} 100%)`,
  );
  root.style.setProperty(
    "--ry-shadow-primary-glow",
    `0 4px 12px rgba(${r}, ${g}, ${b}, 0.18)`,
  );
  // 焦点环（输入框 focus 状态）
  root.style.setProperty(
    "--ry-focus-ring",
    `0 0 0 1px ${hex} inset, 0 0 0 2px rgba(${r}, ${g}, ${b}, 0.12)`,
  );
  root.style.setProperty(
    "--ry-ring-shadow",
    `0 0 0 2px rgba(${r}, ${g}, ${b}, 0.12)`,
  );

  // 浅色阶梯（50~400）：白色混合比例递减
  root.style.setProperty("--ry-primary-50", mixWithWhite(hex, 0.95));
  root.style.setProperty("--ry-primary-100", mixWithWhite(hex, 0.9));
  root.style.setProperty("--ry-primary-200", mixWithWhite(hex, 0.8));
  root.style.setProperty("--ry-primary-300", mixWithWhite(hex, 0.7));
  root.style.setProperty("--ry-primary-400", mixWithWhite(hex, 0.3));

  // 深色阶梯（600~900）：黑色混合比例递增
  root.style.setProperty("--ry-primary-600", mixWithBlack(hex, 0.15));
  root.style.setProperty("--ry-primary-700", mixWithBlack(hex, 0.28));
  root.style.setProperty("--ry-primary-800", mixWithBlack(hex, 0.4));
  root.style.setProperty("--ry-primary-900", mixWithBlack(hex, 0.55));

  // Element Plus light-N 系列（N 越大越浅）
  root.style.setProperty("--el-color-primary-light-1", mixWithWhite(hex, 0.1));
  root.style.setProperty("--el-color-primary-light-2", mixWithWhite(hex, 0.2));
  root.style.setProperty("--el-color-primary-light-3", mixWithWhite(hex, 0.3));
  root.style.setProperty("--el-color-primary-light-4", mixWithWhite(hex, 0.4));
  root.style.setProperty("--el-color-primary-light-5", mixWithWhite(hex, 0.5));
  root.style.setProperty("--el-color-primary-light-6", mixWithWhite(hex, 0.6));
  root.style.setProperty("--el-color-primary-light-7", mixWithWhite(hex, 0.7));
  root.style.setProperty("--el-color-primary-light-8", mixWithWhite(hex, 0.8));
  root.style.setProperty("--el-color-primary-light-9", mixWithWhite(hex, 0.9));
  root.style.setProperty("--el-color-primary-dark-2", mixWithBlack(hex, 0.2));
}

/**
 * 浅色侧栏配色（使用语义变量引用，避免硬编码）
 */
const LIGHT_SIDEBAR = {
  "--ry-sidebar-bg": "var(--ry-neutral-0)",
  "--ry-sidebar-bg-hover": "var(--ry-neutral-100)",
  "--ry-sidebar-sub-bg": "var(--ry-neutral-50)",
  "--ry-sidebar-bg-active": "var(--ry-primary-50)",
  "--ry-sidebar-text": "var(--ry-neutral-600)",
  "--ry-sidebar-text-active": "var(--ry-primary)",
  "--ry-sidebar-title": "var(--ry-neutral-800)",
  "--ry-sidebar-divider": "var(--ry-border)",
};

/**
 * 深色侧栏配色（默认）
 */
const DARK_SIDEBAR = {
  "--ry-sidebar-bg": "#001529",
  "--ry-sidebar-bg-hover": "#0B263F",
  "--ry-sidebar-sub-bg": "rgba(0, 0, 0, 0.12)",
  "--ry-sidebar-bg-active": "var(--ry-primary)",
  "--ry-sidebar-text": "rgba(255, 255, 255, 0.68)",
  "--ry-sidebar-text-active": "#FFFFFF",
  "--ry-sidebar-title": "#FFFFFF",
  "--ry-sidebar-divider": "rgba(255, 255, 255, 0.08)",
};

/**
 * 应用侧栏配色
 */
function applySidebarTheme(theme) {
  const root = document.documentElement;
  const vars = theme === "light" ? LIGHT_SIDEBAR : DARK_SIDEBAR;
  Object.entries(vars).forEach(([k, v]) => root.style.setProperty(k, v));
}

/**
 * 从持久化恢复主题（在 store 创建后调用）
 */
export function restoreTheme(appStore) {
  // 仅执行一次第二版视觉迁移，之后尊重用户主动选择
  if (appStore.uiThemeVersion < 3) {
    if (["#409EFF", "#6172F3", "#3B6EF5"].includes(appStore.themeColor))
      appStore.themeColor = DEFAULT_THEME_COLOR;
    appStore.sidebarTheme = "dark";
    appStore.uiThemeVersion = 3;
  }
  applyThemeVars(appStore.themeColor);
  applySidebarTheme(appStore.sidebarTheme);
}
