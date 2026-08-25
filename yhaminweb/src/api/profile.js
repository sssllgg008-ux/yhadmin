import { request } from "@/utils/request";

const BASE = "/system/user/profile";

/** 获取当前登录用户资料 */
export function getProfile() {
  return request({ url: BASE, method: "get" });
}

/** 修改当前登录用户基本资料 */
export function updateProfile(data) {
  return request({ url: BASE, method: "put", data });
}

/** 修改当前登录用户密码（需校验旧密码） */
export function updateProfilePassword(data) {
  return request({ url: `${BASE}/password`, method: "put", data });
}
