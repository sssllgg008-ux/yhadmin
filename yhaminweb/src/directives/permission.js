/**
 * v-permission 指令：根据权限码控制按钮/元素显示
 * 用法：<el-button v-permission="'system:user:add'">新增</el-button>
 */
import { useUserStore } from "@/store/modules/user";

export default {
  install(app) {
    app.directive("permission", {
      mounted(el, binding) {
        check(el, binding);
      },
      updated(el, binding) {
        check(el, binding);
      },
    });
  },
};

function check(el, binding) {
  const { value } = binding;
  if (!value) return;
  const userStore = useUserStore();
  if (!userStore.hasPermission(value)) {
    el.parentNode && el.parentNode.removeChild(el);
  }
}
