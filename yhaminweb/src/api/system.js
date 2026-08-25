import { request } from "@/utils/request";
import { listPage, getById, add, update, remove, changeStatus } from "./crud";

const BASE = "/system";

/* ===== 用户 ===== */
export const listUser = (params) => listPage(`${BASE}/user`, params);
export const getUser = (id) => getById(`${BASE}/user`, id);
export const addUser = (data) => add(`${BASE}/user`, data);
export const updateUser = (data) => update(`${BASE}/user`, data);
export const delUser = (ids) => remove(`${BASE}/user`, ids);
export const changeUserStatus = (id, status) =>
  changeStatus(`${BASE}/user`, { id, status });
export function resetUserPwd(id, password) {
  return request({
    url: `${BASE}/user/resetPwd`,
    method: "put",
    data: { id, password },
  });
}
export function getAuthRole(userId) {
  return request({ url: `${BASE}/user/authRole/${userId}`, method: "get" });
}
export function assignRole(data) {
  return request({ url: `${BASE}/user/authRole`, method: "put", data });
}

/* ===== 部门 ===== */
export function listDept(params) {
  return request({ url: `${BASE}/dept/list`, method: "get", params });
}
export function getDeptTree() {
  return request({ url: `${BASE}/dept/tree`, method: "get" });
}
export const getDept = (id) => getById(`${BASE}/dept`, id);
export const addDept = (data) => add(`${BASE}/dept`, data);
export const updateDept = (data) => update(`${BASE}/dept`, data);
export const delDept = (ids) => remove(`${BASE}/dept`, ids);

/* ===== 角色 ===== */
export const listRole = (params) => listPage(`${BASE}/role`, params);
export const getRole = (id) => getById(`${BASE}/role`, id);
export const addRole = (data) => add(`${BASE}/role`, data);
export const updateRole = (data) => update(`${BASE}/role`, data);
export const delRole = (ids) => remove(`${BASE}/role`, ids);
export const changeRoleStatus = (id, status) =>
  changeStatus(`${BASE}/role`, { id, status });
export function getRoleOptionselect() {
  return request({ url: `${BASE}/role/optionselect`, method: "get" });
}
export function getAuthUser(roleId) {
  return request({ url: `${BASE}/role/authUser/${roleId}`, method: "get" });
}
export function authUser(data) {
  return request({ url: `${BASE}/role/authUser`, method: "put", data });
}
export function listRoleUsers(roleId, params) {
  return request({
    url: `${BASE}/role/${roleId}/users`,
    method: "get",
    params,
  });
}
export function changeRoleUsers(data) {
  return request({ url: `${BASE}/role/authUser/change`, method: "put", data });
}
export function revokeAuthUser(roleId, userId) {
  return request({
    url: `${BASE}/role/authUser/${roleId}/${userId}`,
    method: "delete",
  });
}
export function getAuthMenu(roleId) {
  return request({ url: `${BASE}/role/authMenu/${roleId}`, method: "get" });
}
export function authMenu(data) {
  return request({ url: `${BASE}/role/authMenu`, method: "put", data });
}

/* ===== 菜单 ===== */
export function listMenu(params) {
  return request({ url: `${BASE}/menu/list`, method: "get", params });
}
export const getMenu = (id) => getById(`${BASE}/menu`, id);
export const addMenu = (data) => add(`${BASE}/menu`, data);
export const updateMenu = (data) => update(`${BASE}/menu`, data);
export const delMenu = (ids) => remove(`${BASE}/menu`, ids);
export function getMenuTreeselect() {
  return request({ url: `${BASE}/menu/treeselect`, method: "get" });
}

/* ===== 模块 ===== */
export const listModule = (params) => listPage(`${BASE}/module`, params);
export const getModule = (id) => getById(`${BASE}/module`, id);
export const addModule = (data) => add(`${BASE}/module`, data);
export const updateModule = (data) => update(`${BASE}/module`, data);
export const delModule = (ids) => remove(`${BASE}/module`, ids);
export function changeModuleStatus(id, status) {
  return request({
    url: `${BASE}/module/status`,
    method: "put",
    data: { id, status },
  });
}
export function moduleOptionSelect() {
  return request({ url: `${BASE}/module/optionselect`, method: "get" });
}

/* ===== 岗位 ===== */
/* ===== 字典 ===== */
export const listDict = (params) => listPage(`${BASE}/dict`, params);
export const getDict = (id) => getById(`${BASE}/dict`, id);
export const addDict = (data) => add(`${BASE}/dict`, data);
export const updateDict = (data) => update(`${BASE}/dict`, data);
export const delDict = (ids) => remove(`${BASE}/dict`, ids);
export function getDictDataByType(dictType) {
  return request({ url: `${BASE}/dict/data/type/${dictType}`, method: "get" });
}

/* ===== 字典数据 ===== */
export function listDictData(params) {
  return request({ url: `${BASE}/dict/data/list`, method: "get", params });
}
export const getDictData = (id) => getById(`${BASE}/dictData`, id);
export const addDictData = (data) => add(`${BASE}/dictData`, data);
export const updateDictData = (data) => update(`${BASE}/dictData`, data);
export const delDictData = (ids) => remove(`${BASE}/dictData`, ids);

/* ===== 参数配置 ===== */
export const listConfig = (params) => listPage(`${BASE}/config`, params);
export const getConfig = (id) => getById(`${BASE}/config`, id);
export const addConfig = (data) => add(`${BASE}/config`, data);
export const updateConfig = (data) => update(`${BASE}/config`, data);
export const delConfig = (ids) => remove(`${BASE}/config`, ids);

/* ===== 通知公告 ===== */
export const listNotice = (params) => listPage(`${BASE}/notice`, params);
export const getNotice = (id) => getById(`${BASE}/notice`, id);
export const addNotice = (data) => add(`${BASE}/notice`, data);
export const updateNotice = (data) => update(`${BASE}/notice`, data);
export const delNotice = (ids) => remove(`${BASE}/notice`, ids);
export const getNoticeInbox = (limit = 10) =>
  request({ url: `${BASE}/notice/inbox`, method: "get", params: { limit } });
export const markNoticeRead = (id) =>
  request({ url: `${BASE}/notice/${id}/read`, method: "put" });
export const markAllNoticesRead = () =>
  request({ url: `${BASE}/notice/readAll`, method: "put" });

/* ===== 监控：操作日志 / 登录日志 ===== */
export const listOperlog = (params) => listPage("/monitor/operlog", params);
export const getOperlog = (id) => getById("/monitor/operlog", id);
export const delOperlog = (ids) => remove("/monitor/operlog", ids);
export const clearOperlog = () =>
  request({ url: "/monitor/operlog/clean", method: "delete" });

export const listLogininfor = (params) =>
  listPage("/monitor/logininfor", params);
export const getLogininfor = (id) => getById("/monitor/logininfor", id);
export const delLogininfor = (ids) => remove("/monitor/logininfor", ids);
export const clearLogininfor = () =>
  request({ url: "/monitor/logininfor/clean", method: "delete" });
export const unlockLogininfor = (userName) =>
  request({
    url: "/monitor/logininfor/unlock",
    method: "put",
    data: { userName },
  });
export const listErrorlog = (params) => listPage("/monitor/errorlog", params);
export const getErrorlog = (id) => getById("/monitor/errorlog", id);
