import { request } from "@/utils/request";

/** 通用列表查询 */
export function listPage(url, params) {
  return request({ url: `${url}/list`, method: "get", params });
}

/** 通用详情查询 */
export function getById(url, id) {
  return request({ url: `${url}/${id}`, method: "get" });
}

/** 通用新增 */
export function add(url, data) {
  return request({ url, method: "post", data });
}

/** 通用修改 */
export function update(url, data) {
  return request({ url, method: "put", data });
}

/** 通用删除（支持批量，逗号分隔） */
export function remove(url, ids) {
  const idStr = Array.isArray(ids) ? ids.join(",") : ids;
  return request({ url: `${url}/${idStr}`, method: "delete" });
}

/** 通用状态切换 */
export function changeStatus(url, data) {
  return request({ url: `${url}/changeStatus`, method: "put", data });
}
