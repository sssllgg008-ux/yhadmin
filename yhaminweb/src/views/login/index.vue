<template>
  <div class="login-shell">
    <!-- 左侧品牌展示区 -->
    <aside class="login-brand">
      <div class="login-brand-top">
        <div class="login-brand-logo">
          <el-icon><Box /></el-icon>
        </div>
        <span class="login-brand-name">{{ appTitle }}</span>
      </div>

      <div class="login-brand-center">
        <h1 class="login-brand-title">{{ appTitle }}</h1>
        <p class="login-brand-subtitle">企业级权限管理解决方案</p>
      </div>

      <div class="login-brand-features">
        <div v-for="f in features" :key="f.label" class="login-brand-feature">
          <div class="login-brand-feature-icon">
            <el-icon><component :is="f.icon" /></el-icon>
          </div>
          <span>{{ f.label }}</span>
        </div>
      </div>
    </aside>

    <!-- 右侧登录表单区 -->
    <main class="login-form-area">
      <form class="login-form" @submit.prevent="handleLogin">
        <h2 class="login-form-title">{{ appTitle }}</h2>
        <p class="login-form-subtitle">欢迎登录，请输入您的账号信息</p>

        <!-- 租户选择 -->
        <div class="login-input-group login-tenant-select">
          <span class="login-input-icon"
            ><el-icon><OfficeBuilding /></el-icon
          ></span>
          <el-select
            v-model="form.tenantId"
            placeholder="请选择租户"
            filterable
            class="login-select"
          >
            <el-option
              v-for="t in tenantOptions"
              :key="t.id"
              :label="t.tenantName + (t.isDefault ? '（默认）' : '')"
              :value="t.id"
            />
          </el-select>
        </div>

        <!-- 用户名 -->
        <div class="login-input-group">
          <span class="login-input-icon"
            ><el-icon><User /></el-icon
          ></span>
          <input
            v-model.trim="form.username"
            type="text"
            class="login-input"
            placeholder="请输入用户名"
            autocomplete="username"
            @keyup.enter="handleLogin"
          />
        </div>

        <!-- 密码 -->
        <div class="login-input-group">
          <span class="login-input-icon"
            ><el-icon><Lock /></el-icon
          ></span>
          <input
            v-model.trim="form.password"
            :type="passwordVisible ? 'text' : 'password'"
            class="login-input padding-right-40"
            placeholder="请输入密码"
            autocomplete="current-password"
            @keyup.enter="handleLogin"
          />
          <span
            class="login-input-icon-right"
            title="显示/隐藏密码"
            @click="passwordVisible = !passwordVisible"
          >
            <el-icon><View v-if="!passwordVisible" /><Hide v-else /></el-icon>
          </span>
        </div>

        <!-- 验证码 -->
        <div v-if="captchaEnabled" class="login-captcha-row">
          <div class="login-input-group login-captcha-input">
            <span class="login-input-icon"
              ><el-icon><Key /></el-icon
            ></span>
            <input
              v-model.trim="form.code"
              type="text"
              class="login-input"
              placeholder="请输入验证码"
              maxlength="6"
              @keyup.enter="handleLogin"
            />
          </div>
          <div
            class="captcha-img"
            title="点击刷新验证码"
            @click="refreshCaptcha"
          >
            <img v-if="captchaImage" :src="captchaImage" alt="验证码" />
            <span v-else class="captcha-loading">加载中...</span>
          </div>
        </div>

        <!-- 记住账号 / 忘记密码 -->
        <div class="login-options-row">
          <label class="login-remember">
            <input
              v-model="form.remember"
              type="checkbox"
              class="login-checkbox"
            />
            <span class="login-remember-label">记住账号</span>
          </label>
          <a
            class="login-forgot"
            href="javascript:void(0);"
            @click="handleForgot"
            >忘记密码？</a
          >
        </div>

        <!-- 登录按钮 -->
        <el-button
          type="primary"
          class="login-submit"
          :loading="loading"
          native-type="submit"
        >
          登 录
        </el-button>

      </form>
    </main>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import {
  User,
  Lock,
  Key,
  View,
  Hide,
  Box,
  Aim,
  Connection,
  DataLine,
  OfficeBuilding,
} from "@element-plus/icons-vue";
import { useUserStore } from "@/store/modules/user";
import { getCaptcha, listTenantsForLogin } from "@/api/login";
import { getCookie, setCookie, removeCookie } from "@/utils/auth";

const appTitle = import.meta.env.VITE_APP_TITLE || "异火 AI 管理系统";
const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const features = [
  { label: "统一权限管控", icon: Aim },
  { label: "角色与菜单管理", icon: Connection },
  { label: "全链路审计追踪", icon: DataLine },
];

const form = reactive({
  tenantId: 1,
  username: "admin",
  password: "admin123",
  code: "",
  uuid: "",
  remember: true,
});

const loading = ref(false);
const passwordVisible = ref(false);
const tenantOptions = ref([{ id: 1, tenantName: "默认租户", isDefault: true }]);

// 加载启用状态的租户列表，默认选中默认租户
async function loadTenants() {
  try {
    const res = await listTenantsForLogin();
    const payload = res.data || res;
    const list = payload?.id ? [payload] : (Array.isArray(payload) ? payload : []);
    tenantOptions.value = list;
    // 默认租户排首位；未选过则自动选中默认租户
    const defaultTenant = list.find((t) => t.isDefault) || list[0];
    if (
      defaultTenant &&
      (form.tenantId == null || !list.some((t) => t.id === form.tenantId))
    ) {
      form.tenantId = defaultTenant.id;
    }
  } catch (err) {
    // 加载失败保持默认租户 id=1
    form.tenantId = 1;
  }
}

const captchaEnabled = ref(true);
const captchaImage = ref("");

async function refreshCaptcha() {
  form.code = "";
  form.uuid = "";
  captchaImage.value = "";
  try {
    const res = await getCaptcha(form.tenantId || 1);
    const data = res?.data || res || {};
    captchaEnabled.value = data.captchaEnabled === true;
    form.uuid = data.uuid || "";
    captchaImage.value = data.img
      ? `data:image/png;base64,${data.img}`
      : "";
  } catch (err) {
    // Fail closed: when configuration/captcha cannot be loaded, do not hide the
    // field and accidentally suggest that captcha validation has been bypassed.
    captchaEnabled.value = true;
    ElMessage.error("验证码加载失败，请点击图片区域重试");
    console.error("[Login] captcha load failed:", err);
  }
}

// 仅记住账号。历史版本曾写入明文密码 Cookie，加载登录页时立即清除。
function loadRemembered() {
  removeCookie("ry-password");
  const username = getCookie("ry-username");
  const remember = getCookie("ry-remember");
  if (remember === "1" && username) {
    form.username = username;
    form.remember = true;
  } else if (username) {
    form.username = username;
    form.remember = false;
  }
}

function saveRemembered() {
  if (form.remember) {
    setCookie("ry-username", form.username, 30);
    setCookie("ry-remember", "1", 30);
  } else {
    removeCookie("ry-username");
    removeCookie("ry-remember");
  }
}

watch(
  () => form.remember,
  (val) => {
    if (!val) {
      removeCookie("ry-username");
      removeCookie("ry-remember");
    }
  },
);

async function handleLogin() {
  if (!form.username) {
    ElMessage.warning("请输入用户名");
    return;
  }
  if (!form.password) {
    ElMessage.warning("请输入密码");
    return;
  }
  if (captchaEnabled.value && !form.code) {
    ElMessage.warning("请输入验证码");
    return;
  }

  loading.value = true;
  try {
    const loginResult = await userStore.login({
      tenantId: form.tenantId || 1,
      username: form.username,
      password: form.password,
      code: form.code,
      uuid: form.uuid,
    });
    saveRemembered();
    const loginUser = loginResult?.user || loginResult?.data?.user || {};
    if (loginUser.passwordChangeRequired) {
      ElMessage.warning("当前使用的是临时密码，请立即修改密码");
      router.push("/profile");
      return;
    }
    ElMessage.success("登录成功");
    const redirect = route.query.redirect
      ? decodeURIComponent(route.query.redirect)
      : "/";
    router.push(redirect);
  } catch (err) {
    console.error("[Login] failed:", err);
    ElMessage.error(err?.message || "登录失败，请稍后重试");
    if (captchaEnabled.value) refreshCaptcha();
  } finally {
    loading.value = false;
  }
}

function handleForgot() {
  ElMessage.info("请联系系统管理员重置密码");
}

onMounted(() => {
  loadRemembered();
  loadTenants();
  refreshCaptcha();
});

watch(
  () => form.tenantId,
  () => refreshCaptcha(),
);
</script>

<style lang="scss" scoped>
.login-shell {
  display: flex;
  min-height: 100vh;
  background: #f5f8fd;
}

/* 左侧品牌展示区 */
.login-brand {
  flex: 0 0 52%;
  background:
    radial-gradient(
      circle at 82% 18%,
      rgba(var(--ry-primary-rgb), 0.12),
      transparent 24%
    ),
    radial-gradient(
      circle at 18% 82%,
      rgba(var(--ry-primary-rgb), 0.08),
      transparent 25%
    ),
    linear-gradient(145deg, #f8fbff 0%, #edf4ff 58%, #f5f8ff 100%);
  color: var(--ry-foreground);
  border-right: 1px solid rgba(186, 224, 255, 0.65);
  padding: clamp(40px, 5vw, 72px);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  position: relative;
  overflow: hidden;

  &::before {
    content: "";
    position: absolute;
    top: -120px;
    right: -120px;
    width: 360px;
    height: 360px;
    border-radius: 50%;
    background: rgba(var(--ry-primary-rgb), 0.045);
  }

  &::after {
    content: "";
    position: absolute;
    bottom: -100px;
    left: -80px;
    width: 280px;
    height: 280px;
    border-radius: 50%;
    background: rgba(var(--ry-primary-rgb), 0.04);
  }
}

.login-brand-top {
  display: flex;
  align-items: center;
  gap: 10px;
  position: relative;
  z-index: 1;
}

.login-brand-logo {
  width: 42px;
  height: 42px;
  border-radius: 13px;
  background: var(--ry-gradient-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 18px;
}

.login-brand-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--ry-neutral-800);
  letter-spacing: 0.5px;
}

.login-brand-center {
  position: relative;
  z-index: 1;
}

.login-brand-title {
  font-size: clamp(30px, 3vw, 42px);
  font-weight: 700;
  color: var(--ry-neutral-900);
  line-height: 1.4;
  margin: 0 0 12px 0;
}

.login-brand-subtitle {
  font-size: 16px;
  color: var(--ry-neutral-600);
  margin: 0;
  line-height: 1.6;
}

.login-brand-features {
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: relative;
  z-index: 1;
}

.login-brand-feature {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: var(--ry-neutral-700);
}

.login-brand-feature-icon {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: #fff;
  color: var(--ry-primary);
  border: 1px solid var(--ry-primary-100);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 14px;
}

/* 右侧登录表单区 */
.login-form-area {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(circle at 90% 10%, var(--ry-primary-50), transparent 30%),
    var(--ry-background);
  padding: clamp(24px, 5vw, 72px);
}

.login-form {
  width: 430px;
  max-width: 100%;
  padding: 42px 40px 36px;
  border: 1px solid rgba(226, 232, 240, 0.86);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 24px 70px rgba(30, 64, 175, 0.1);
  backdrop-filter: blur(18px);
}

.login-form-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--ry-neutral-900);
  margin: 0 0 8px 0;
  font-family: var(--ry-font-title);
}

.login-form-subtitle {
  font-size: 13px;
  color: var(--ry-neutral-500);
  margin: 0 0 28px 0;
}

/* 输入框组 */
.login-input-group {
  position: relative;
  display: flex;
  align-items: center;
  margin-bottom: 18px;
}

.login-input-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--ry-neutral-400);
  pointer-events: none;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
}

.login-input-icon-right {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--ry-neutral-400);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;

  &:hover {
    color: var(--ry-primary);
  }
}

.login-input {
  width: 100%;
  height: 48px;
  border: 1px solid #dce4ef;
  border-radius: 12px;
  font-size: 14px;
  font-family: var(--ry-font-sans);
  color: var(--ry-neutral-700);
  background: var(--ry-neutral-0);
  padding: 0 38px;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;

  &.padding-right-40 {
    padding: 0 40px 0 38px;
  }

  &::placeholder {
    color: var(--ry-neutral-400);
  }

  &:focus {
    outline: none;
    border-color: var(--ry-primary);
    box-shadow: var(--ry-ring-shadow);
  }
}

/* 租户下拉选择：与输入框视觉一致 */
.login-tenant-select {
  :deep(.login-select) {
    width: 100%;
  }

  :deep(.el-select__wrapper) {
    min-height: 48px;
    height: 48px;
    padding-left: 30px;
    border-radius: 12px;
    box-shadow: 0 0 0 1px var(--ry-neutral-300) inset;

    &:hover {
      box-shadow: 0 0 0 1px var(--ry-primary) inset;
    }

    &.is-focused {
      box-shadow: var(--ry-focus-ring);
    }
  }

  :deep(.el-select__placeholder),
  :deep(.el-select__selected-item) {
    font-size: 14px;
  }

  /* filterable select 内部输入框不重复绘制全局 focus-visible 轮廓 */
  :deep(.el-select__input:focus-visible) {
    outline: none;
  }
}

/* 验证码行 */
.login-captcha-row {
  display: flex;
  gap: 12px;
  margin-bottom: 18px;
}

.login-captcha-input {
  flex: 1;
  margin-bottom: 0 !important;
}

.captcha-img {
  width: 120px;
  height: 48px;
  cursor: pointer;
  border-radius: 12px;
  border: 1px solid var(--ry-neutral-300);
  background-color: #e8f0e3;
  background-image:
    radial-gradient(
      circle at 20% 30%,
      rgba(var(--ry-primary-rgb), 0.35) 1px,
      transparent 1.5px
    ),
    radial-gradient(
      circle at 70% 60%,
      rgba(var(--ry-primary-rgb), 0.28) 1px,
      transparent 1.5px
    ),
    radial-gradient(
      circle at 40% 80%,
      rgba(96, 165, 250, 0.25) 1px,
      transparent 1.5px
    ),
    radial-gradient(
      circle at 85% 25%,
      rgba(var(--ry-primary-rgb), 0.3) 1px,
      transparent 1.5px
    ),
    radial-gradient(
      circle at 10% 70%,
      rgba(148, 163, 184, 0.3) 1px,
      transparent 1.5px
    ),
    radial-gradient(
      circle at 55% 15%,
      rgba(var(--ry-primary-rgb), 0.22) 1px,
      transparent 1.5px
    ),
    radial-gradient(
      circle at 90% 90%,
      rgba(96, 165, 250, 0.2) 1px,
      transparent 1.5px
    ),
    linear-gradient(135deg, #e8f0e3 0%, #dce7d8 100%);
  background-size:
    8px 8px,
    10px 10px,
    7px 7px,
    9px 9px,
    11px 11px,
    6px 6px,
    12px 12px,
    100% 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--ry-font-title);
  font-weight: 600;
  font-size: 18px;
  color: var(--ry-primary-700);
  letter-spacing: 2px;
  user-select: none;
  transition: border-color 0.2s ease;

  &:hover {
    border-color: var(--ry-primary);
  }
}

.captcha-img-text {
  display: inline-block;
}

.captcha-img img {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: inherit;
  object-fit: cover;
}

.captcha-loading {
  font-size: 12px;
  font-weight: 400;
  letter-spacing: 0;
  color: var(--ry-neutral-500);
}

/* 记住账号行 */
.login-options-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 22px;
}

.login-remember {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}

.login-checkbox {
  width: 16px;
  height: 16px;
  border: 1px solid var(--ry-neutral-300);
  border-radius: 4px;
  background: var(--ry-neutral-0);
  cursor: pointer;
  position: relative;
  flex-shrink: 0;
  transition: all 0.2s ease;
  appearance: none;
  -webkit-appearance: none;
  margin: 0;

  &:checked {
    background: var(--ry-primary);
    border-color: var(--ry-primary);
  }

  &:checked::after {
    content: "";
    position: absolute;
    left: 4px;
    top: 1px;
    width: 5px;
    height: 9px;
    border: solid #fff;
    border-width: 0 2px 2px 0;
    transform: rotate(45deg);
  }
}

.login-remember-label {
  font-size: 13px;
  color: var(--ry-neutral-600);
}

.login-forgot {
  font-size: 13px;
  color: var(--ry-primary);
  text-decoration: none;
  cursor: pointer;

  &:hover {
    color: var(--ry-primary-400);
  }
}

/* 登录按钮 */
.login-submit {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 500;
  letter-spacing: 4px;
  border-radius: var(--ry-radius-medium);
  box-shadow: 0 8px 18px rgba(var(--ry-primary-rgb), 0.26);
}

/* 响应式：窄屏隐藏左侧品牌区 */
@media (max-width: 768px) {
  .login-brand {
    display: none;
  }

  .login-form-area {
    flex: 1;
    padding: 20px;
  }

  .login-form {
    width: 100%;
    padding: 26px 22px;
  }
}
</style>
