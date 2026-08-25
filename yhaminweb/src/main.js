import { createApp } from "vue";
import { createPinia } from "pinia";
import piniaPersist from "pinia-plugin-persistedstate";
import ElementPlus from "element-plus";
import zhCn from "element-plus/dist/locale/zh-cn.mjs";
import "element-plus/dist/index.css";
import * as ElementPlusIconsVue from "@element-plus/icons-vue";

import App from "./App.vue";
import router from "./router";
import permissionDirective from "./directives/permission";

import "./styles/index.scss";
import "./permission";

const app = createApp(App);

// 注册 Element Plus 图标（全局可用，使用 PascalCase 名称）
for (const [name, comp] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, comp);
}

const pinia = createPinia();
pinia.use(piniaPersist);

app.use(pinia);
app.use(router);
app.use(ElementPlus, { locale: zhCn, size: "default" });
app.use(permissionDirective);

app.config.errorHandler = (err) => {
  console.error("[App Error]", err);
};

app.mount("#app");
