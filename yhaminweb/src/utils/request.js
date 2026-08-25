import axios from "axios";
import { ElMessage, ElMessageBox } from "element-plus";
import { getToken, removeToken } from "./auth";
import { useUserStore } from "@/store/modules/user";
import router from "@/router";
import { mockRequest } from "@/api/mock";

const service = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API || "/dev-api",
  timeout: 20000,
});

// Anonymous endpoints may legitimately return 401 (for example invalid login
// credentials). They must not be treated as an expired authenticated session.
const anonymousPaths = new Set([
  "/login",
  "/auth/login",
  "/captchaImage",
  "/auth/captcha",
  "/auth/tenants",
  "/auth/tenant/default",
]);

function isAnonymousRequest(config) {
  const raw = config?.url || "";
  const path = raw.startsWith("http") ? new URL(raw).pathname : raw.split("?")[0];
  return anonymousPaths.has(path);
}

const LOCAL_DATE_TIME_PATTERN =
  /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?$/;
const EPOCH_SECONDS_PATTERN = /^-?\d{10}$/;
const EPOCH_MILLIS_PATTERN = /^-?\d{13}$/;

function isDateTimeField(key) {
  return (
    typeof key === "string" &&
    !/^(costTime|elapsedTime|durationTime|duration)$/i.test(key) &&
    !/(Cost|Duration)$/i.test(key) &&
    /(Time|At)$/i.test(key)
  );
}

function formatEpoch(value) {
  const text = String(value);
  const millis = EPOCH_SECONDS_PATTERN.test(text) ? Number(text) * 1000 : Number(text);
  const date = new Date(millis);
  if (Number.isNaN(date.getTime())) return value;
  const pad = (part) => String(part).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

/**
 * 将后端 LocalDateTime 的 ISO-8601 表示统一转换为界面展示格式。
 * 仅处理完整匹配的时间字符串，避免修改正文、日志和普通文本中的内容。
 */
function normalizeDateTimes(value, visited = new WeakSet(), key = null) {
  if (typeof value === "string") {
    if (!isDateTimeField(key)) return value;
    if (EPOCH_SECONDS_PATTERN.test(value) || EPOCH_MILLIS_PATTERN.test(value)) return formatEpoch(value);
    return LOCAL_DATE_TIME_PATTERN.test(value)
      ? `${value.slice(0, 10)} ${value.slice(11, 19)}`
      : value;
  }
  if (typeof value === "number" && isDateTimeField(key) &&
      (EPOCH_SECONDS_PATTERN.test(String(value)) || EPOCH_MILLIS_PATTERN.test(String(value)))) {
    return formatEpoch(value);
  }
  if (
    !value ||
    typeof value !== "object" ||
    value instanceof Blob ||
    value instanceof ArrayBuffer
  ) {
    return value;
  }
  if (visited.has(value)) return value;
  visited.add(value);
  if (Array.isArray(value)) {
    value.forEach((item, index) => {
      value[index] = normalizeDateTimes(item, visited, null);
    });
  } else {
    Object.keys(value).forEach((key) => {
      value[key] = normalizeDateTimes(value[key], visited, key);
    });
  }
  return value;
}

service.interceptors.request.use(
  (config) => {
    const token = getToken();
    if (token) {
      config.headers["Authorization"] = `Bearer ${token}`;
      // 多租户：已登录时携带租户ID到后端
      const tenantId = localStorage.getItem("tenantId");
      if (tenantId) {
        config.headers["X-Tenant-Id"] = tenantId;
      }
    }
    return config;
  },
  (error) => Promise.reject(error),
);

service.interceptors.response.use(
  (response) => {
    const res = response.data;
    // 二进制流直接返回
    if (
      response.config.responseType === "blob" ||
      response.config.responseType === "arraybuffer"
    ) {
      return response;
    }
    if (res.code === 200 || res.code === undefined) {
      return normalizeDateTimes(res);
    }
    // 401 未登录 / token 过期
    if (res.code === 401) {
      if (!isAnonymousRequest(response.config)) {
        handleUnauthorized(res.msg || "登录状态已过期，请重新登录");
      }
      return Promise.reject(new Error(res.msg || "Unauthorized"));
    }
    // 403 无权限
    if (res.code === 403) {
      ElMessage.error(res.msg || "没有访问权限");
      return Promise.reject(new Error(res.msg || "Forbidden"));
    }
    ElMessage.error(res.msg || "请求失败");
    return Promise.reject(new Error(res.msg || "Error"));
  },
  (error) => {
    const status = error.response?.status;
    if (status === 401) {
      const message = error.response?.data?.msg || "登录状态已过期，请重新登录";
      if (isAnonymousRequest(error.config)) return Promise.reject(new Error(message));
      handleUnauthorized(message);
    } else if (status === 403) {
      ElMessage.error("没有访问权限");
    } else if (status === 404) {
      ElMessage.error("请求资源不存在");
    } else if (status >= 500) {
      ElMessage.error("服务器异常，请稍后再试");
    } else if (error.code === "ECONNABORTED") {
      ElMessage.error("请求超时，请检查网络");
    } else if (!error.response) {
      ElMessage.error("网络异常，请检查连接");
    } else {
      ElMessage.error(error.response?.data?.msg || error.message || "请求失败");
    }
    return Promise.reject(error);
  },
);

function handleUnauthorized(msg) {
  ElMessageBox.confirm(msg, "提示", {
    confirmButtonText: "重新登录",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(() => {
      const userStore = useUserStore();
      userStore.resetState();
      removeToken();
      router.push(
        `/login?redirect=${encodeURIComponent(router.currentRoute.value.fullPath)}`,
      );
    })
    .catch(() => {});
}

/**
 * 统一请求函数
 * 当 VITE_APP_USE_MOCK=true 时走内置 mock，否则走真实 axios
 */
export function request(config) {
  const useMock = import.meta.env.VITE_APP_USE_MOCK === "true";
  if (useMock) {
    return mockRequest(config).then((res) => {
      if (res.code === 200) return normalizeDateTimes(res);
      if (res.code === 401) {
        handleUnauthorized(res.msg || "登录状态已过期");
        return Promise.reject(new Error(res.msg || "Unauthorized"));
      }
      ElMessage.error(res.msg || "请求失败");
      return Promise.reject(new Error(res.msg || "Error"));
    });
  }
  return service(config);
}

export default service;
