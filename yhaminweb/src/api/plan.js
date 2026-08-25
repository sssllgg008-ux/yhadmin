import request from "@/utils/request";

// pluginSystem uses the common { code, msg, data } response envelope. Keep the
// envelope handling in this API module so views always receive the business
// value (arrays for lists/catalogs and objects for details).
const dataOf = (response) => response?.data ?? response;
const call = async (config) => dataOf(await request(config));

export const listPlans = (params) => call({ url: "/system/saas/plans", method: "get", params });
export const getPlan = (id) => call({ url: `/system/saas/plan/${id}`, method: "get" });
export const savePlan = (data) => call({ url: "/system/saas/plan", method: data.id ? "put" : "post", data });
export const deletePlan = (id) => call({ url: `/system/saas/plan/${id}`, method: "delete" });
export const copyPlan = (id) => call({ url: `/system/saas/plan/${id}/copy`, method: "post" });
export const publishPlan = (id, isDefault = false) => call({ url: `/system/saas/plan/${id}/publish`, method: "post", data: { isDefault } });
export const changePlanLifecycle = (id, lifecycleStatus) => call({ url: `/system/saas/plan/${id}/status`, method: "put", data: { lifecycleStatus } });
export const listPlanVersions = (id) => call({ url: `/system/saas/plan/${id}/versions`, method: "get" });
export const comparePlans = (fromId, toId) => call({ url: "/system/saas/plan/compare", method: "get", params: { fromId, toId } });
export const getQuotaCatalog = () => call({ url: "/system/saas/quotaCatalog", method: "get" });

export const previewSubscription = (tenantId, data) => call({ url: `/system/saas/tenant/${tenantId}/subscription/preview`, method: "post", data });
export const cancelPendingSubscription = (tenantId) => call({ url: `/system/saas/tenant/${tenantId}/subscription/pending`, method: "delete" });
export const getSubscriptionHistory = (tenantId) => call({ url: `/system/saas/tenant/${tenantId}/subscription/history`, method: "get" });
