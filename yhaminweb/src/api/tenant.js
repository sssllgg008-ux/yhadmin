import request from "@/utils/request";

const dataOf = (response) => response?.data ?? response;
const call = async (config) => dataOf(await request(config));

export function listTenants(query) {
  return request({ url: "/system/tenant/list", method: "get", params: query });
}

export function getTenant(id) {
  return request({ url: "/system/tenant/" + id, method: "get" });
}

export function createTenant(data) {
  return request({ url: "/system/tenant", method: "post", data });
}

export function updateTenant(data) {
  return request({ url: "/system/tenant", method: "put", data });
}

export function changeTenantStatus(id, status) {
  return request({
    url: "/system/tenant/changeStatus",
    method: "put",
    data: { id, status },
  });
}

export function resetTenantAdminPassword(id) {
  return request({
    url: "/system/tenant/resetAdminPwd",
    method: "put",
    data: { id },
  });
}

export function deleteTenant(id) {
  return request({ url: "/system/tenant/" + id, method: "delete" });
}

export const getTenantLifecycle = (id) => call({ url: `/system/tenant/${id}/lifecycle`, method: "get" });
export const retryTenantDelete = (id) => call({ url: `/system/tenant/${id}/deleteRetry`, method: "post" });
export const cancelTenantDelete = (id) => call({ url: `/system/tenant/${id}/deleteCancel`, method: "post" });
export const listDeletedTenants = () => call({ url: "/system/tenant/deleted", method: "get" });
export const listPlans = () => call({ url: "/system/saas/plans", method: "get" });
export const savePlan = (data) => call({ url: "/system/saas/plan", method: data.id ? "put" : "post", data });
export const deletePlan = (id) => call({ url: `/system/saas/plan/${id}`, method: "delete" });
export const getTenantSubscription = (id) => call({ url: `/system/saas/tenant/${id}/subscription`, method: "get" });
export const changeTenantSubscription = (id, data) => call({ url: `/system/saas/tenant/${id}/subscription`, method: "put", data });
export const getTenantUsage = (id) => call({ url: `/system/saas/tenant/${id}/usage`, method: "get" });
export const getCurrentTenantUsage = () => call({ url: "/system/tenant/currentUsage", method: "get" });
export const calibrateTenantUsage = (id) => call({ url: `/system/saas/tenant/${id}/usage/calibrate`, method: "post" });
export const saveTenantQuotaOverrides = (id, quotas) => call({ url: `/system/saas/tenant/${id}/quotaOverrides`, method: "put", data: { quotas } });
export const getSaasOverview = () => call({ url: "/system/saas/overview", method: "get" });
export const listRatePolicies = (id) => call({ url: `/system/saas/tenant/${id}/ratePolicies`, method: "get" });
export const saveRatePolicy = (id, data) => call({ url: `/system/saas/tenant/${id}/ratePolicy`, method: "put", data });
export const deleteRatePolicy = (id) => call({ url: `/system/saas/ratePolicy/${id}`, method: "delete" });
export const listRateEvents = (id, params) => call({ url: `/system/saas/tenant/${id}/rateEvents`, method: "get", params });
export const getRateStatistics = (id, params) => call({ url: `/system/saas/tenant/${id}/rateStatistics`, method: "get", params });
export const listTenantBackups = (id) => call({ url: `/system/saas/tenant/${id}/backups`, method: "get" });
export const createTenantBackup = (id) => call({ url: `/system/saas/tenant/${id}/backup`, method: "post" });
export const restoreTenantBackup = (id, backupId) => call({ url: `/system/saas/tenant/${id}/backup/${backupId}/restore`, method: "post" });
export const deleteTenantBackup = (id, backupId) => call({ url: `/system/saas/tenant/${id}/backup/${backupId}`, method: "delete" });
export const downloadTenantBackup = (id, backupId) => request({ url: `/system/saas/tenant/${id}/backup/${backupId}/download`, method: "get", responseType: "blob" });
export const getRestoreTask = (id, taskId) => call({ url: `/system/saas/tenant/${id}/restoreTask/${taskId}`, method: "get" });
export const retryRestoreTask = (id, taskId) => call({ url: `/system/saas/tenant/${id}/restoreTask/${taskId}/retry`, method: "post" });
