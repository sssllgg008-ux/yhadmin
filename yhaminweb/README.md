# 权限管理后台前端 (yhaminweb)

基于设计稿实现的 Vue3 + Element Plus 前端框架，遵循异火（Yihuo）前端规范。

## 技术栈

- Vue 3（Composition API + `<script setup>`）
- Vite 5
- Element Plus
- Vue Router 4
- Pinia（含持久化插件）
- Axios
- SCSS

## 快速开始

```bash
# 安装依赖
npm install

# 启动开发服务器（默认端口 80，可改 vite.config.js）
npm run dev

# 打包构建
npm run build

# 预览构建结果
npm run preview
```

开发模式默认启用 Mock 数据，无需后端即可运行。

## 目录结构

```
src/
├── api/                # 接口封装（按模块拆分，含 mock）
├── assets/             # 静态资源
├── components/         # 全局通用组件
├── directives/         # 自定义指令（权限等）
├── layout/             # 主壳布局（侧栏 + 顶栏 + 标签页 + 内容）
├── router/             # 路由配置 + 守卫
├── store/              # Pinia 状态管理
├── styles/             # 全局样式（设计系统 + Element Plus 增强）
├── utils/              # 工具函数（请求/鉴权/通用）
├── views/              # 页面视图
├── App.vue
└── main.js
```

## 设计稿对齐说明

- 主题色：`#409EFF`（Element Plus Blue）
- 侧栏：暗色 `#304156`
- 卡片：白底 8px 圆角 + `box-shadow-xs`
- 表格：紧凑型，行高 48px
- 按钮：默认 32px 高，4px 圆角
