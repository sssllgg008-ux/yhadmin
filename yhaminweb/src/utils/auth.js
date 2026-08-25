import Cookies from "js-cookie";

const TOKEN_KEY = import.meta.env.VITE_APP_TOKEN_KEY || "ry_admin_token";
const EXPIRES = 7; // 天

export function getToken() {
  return Cookies.get(TOKEN_KEY);
}

export function setToken(token) {
  return Cookies.set(TOKEN_KEY, token, { expires: EXPIRES, sameSite: "lax" });
}

export function removeToken() {
  return Cookies.remove(TOKEN_KEY);
}

/**
 * 通用 Cookie 工具：只用于非敏感偏好（例如记住用户名），禁止保存密码。
 */
export function getCookie(key) {
  return Cookies.get(key);
}

export function setCookie(key, value, days = 7) {
  return Cookies.set(key, value, { expires: days, sameSite: "lax" });
}

export function removeCookie(key) {
  return Cookies.remove(key);
}
