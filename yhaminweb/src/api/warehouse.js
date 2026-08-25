import { request } from "@/utils/request";

const base = "/warehouse";
const dataOf = (response) => response?.data ?? response;

export const listDomains = () =>
  request({ url: `${base}/domains`, method: "get" }).then(dataOf);
export const saveDomain = (data) =>
  request({
    url: `${base}/domains`,
    method: data.id ? "put" : "post",
    data,
  }).then(dataOf);
export const deleteDomain = (id) =>
  request({ url: `${base}/domains/${id}`, method: "delete" });

export const listDataSources = () =>
  request({ url: `${base}/data-sources`, method: "get" }).then(dataOf);
export const saveDataSource = (data) =>
  request({
    url: `${base}/data-sources`,
    method: data.id ? "put" : "post",
    data,
  }).then(dataOf);
export const deleteDataSource = (id) =>
  request({ url: `${base}/data-sources/${id}`, method: "delete" });
export const changeDataSourceStatus = (id, enabled) =>
  request({
    url: `${base}/data-sources/${id}/status`,
    method: "put",
    params: { enabled },
  }).then(dataOf);
export const testDataSource = (id) =>
  request({ url: `${base}/data-sources/${id}/test`, method: "post" }).then(
    dataOf,
  );
export const listDataSourceDatabases = (id) =>
  request({ url: `${base}/data-sources/${id}/databases`, method: "get" }).then(
    dataOf,
  );
export const listDataSourceTables = (id, params) =>
  request({
    url: `${base}/data-sources/${id}/tables`,
    method: "get",
    params,
  }).then(dataOf);
export const listDataSourceColumns = (id, params) =>
  request({
    url: `${base}/data-sources/${id}/columns`,
    method: "get",
    params,
  }).then(dataOf);

export const listModels = (resource, domainId) =>
  request({
    url: `${base}/${resource}`,
    method: "get",
    params: { domainId },
  }).then(dataOf);
export const saveModel = (resource, data) =>
  request({
    url: `${base}/${resource}`,
    method: data.id ? "put" : "post",
    data,
  }).then(dataOf);
export const deleteModel = (resource, id) =>
  request({ url: `${base}/${resource}/${id}`, method: "delete" });
export const publishModel = (resource, id) =>
  request({ url: `${base}/${resource}/${id}/publish`, method: "post" }).then(
    dataOf,
  );
export const listVersions = (modelType, modelId) =>
  request({
    url: `${base}/versions`,
    method: "get",
    params: { modelType, modelId },
  }).then(dataOf);
