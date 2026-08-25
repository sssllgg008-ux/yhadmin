import { listPage, getById, add, update, remove, changeStatus } from "./crud";
import { request } from "@/utils/request";
import { getToken } from "@/utils/auth";

export const listModels = (params) => listPage("/ai/model", params);
export const addModel = (data) => add("/ai/model", data);
export const updateModel = (data) => update("/ai/model", data);
export const delModel = (ids) => remove("/ai/model", ids);
export const changeModelStatus = (id, status) =>
  request({
    url: "/ai/model/changeStatus",
    method: "put",
    params: { id, status },
  });
export const setDefaultModel = (id) =>
  request({ url: `/ai/model/${id}/default`, method: "put" });

export const listModelProviders = () =>
  request({ url: "/ai/model/provider/list", method: "get" });
export const getModelProvider = (id) =>
  request({ url: `/ai/model/provider/${id}`, method: "get" });
export const addModelProvider = (data) =>
  request({ url: "/ai/model/provider", method: "post", data });
export const updateModelProvider = (data) =>
  request({ url: "/ai/model/provider", method: "put", data });
export const delModelProvider = (ids) => remove("/ai/model/provider", ids);
export const changeModelProviderStatus = (id, status) =>
  request({
    url: "/ai/model/provider/changeStatus",
    method: "put",
    params: { id, status },
  });

export const listKnowledge = (params) => listPage("/ai/knowledge", params);
export const getKnowledge = (id) =>
  getById("/ai/knowledge", id).then((res) => res.data);
export const addKnowledge = (data) => add("/ai/knowledge", data);
export const updateKnowledge = (data) => update("/ai/knowledge", data);
export const previewKnowledgeChunks = (knowledgeBaseId, data) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/chunk-preview`,
    method: "post",
    data,
  }).then((res) => res.data);
export const delKnowledge = (ids) => remove("/ai/knowledge", ids);
export const changeKnowledgeStatus = (id, status) =>
  request({
    url: "/ai/knowledge/changeStatus",
    method: "put",
    params: { id, status },
  });
export const listKnowledgeDocuments = (knowledgeBaseId) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/documents`,
    method: "get",
  }).then((res) => res.data);
export const pageKnowledgeDocuments = (knowledgeBaseId, params) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/documents/page`,
    method: "get",
    params,
  });
export const getKnowledgeDocumentStatistics = (knowledgeBaseId) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/documents/statistics`,
    method: "get",
  }).then((res) => res.data);
export const findKnowledgeDocumentByName = (knowledgeBaseId, name) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/documents/by-name`,
    method: "get",
    params: { name },
  }).then((res) => res.data);
export const uploadKnowledgeDocument = (
  knowledgeBaseId,
  file,
  onUploadProgress,
) => {
  const data = new FormData();
  data.append("file", file);
  return request({
    url: `/ai/knowledge/${knowledgeBaseId}/documents/upload`,
    method: "post",
    data,
    onUploadProgress,
    timeout: 5 * 60 * 1000,
  });
};
export const uploadKnowledgeDocumentVersion = (
  knowledgeBaseId,
  documentId,
  file,
  onUploadProgress,
) => {
  const data = new FormData();
  data.append("file", file);
  return request({
    url: `/ai/knowledge/${knowledgeBaseId}/documents/${documentId}/versions/upload`,
    method: "post",
    data,
    onUploadProgress,
    timeout: 5 * 60 * 1000,
  });
};
export const uploadKnowledgeDocumentMultipart = async (
  knowledgeBaseId,
  documentId,
  file,
  onUploadProgress,
) => {
  const partSize = 8 * 1024 * 1024;
  const session = await request({
    url: `/ai/knowledge/${knowledgeBaseId}/documents/multipart/initiate`,
    method: "post",
    data: {
      documentId,
      fileName: file.name,
      contentType: file.type || "application/octet-stream",
      fileSize: file.size,
      partSize,
    },
  }).then((res) => res.data);
  let uploaded = 0;
  try {
    for (let partNo = 1; partNo <= session.partCount; partNo += 1) {
      const start = (partNo - 1) * session.partSize;
      const chunk = file.slice(
        start,
        Math.min(file.size, start + session.partSize),
      );
      const data = new FormData();
      data.append("file", chunk, `${file.name}.part-${partNo}`);
      await request({
        url: `/ai/knowledge/${knowledgeBaseId}/documents/multipart/${session.uploadId}/parts/${partNo}`,
        method: "put",
        data,
        timeout: 5 * 60 * 1000,
        onUploadProgress: (event) =>
          onUploadProgress?.({
            loaded: uploaded + event.loaded,
            total: file.size,
          }),
      });
      uploaded += chunk.size;
      onUploadProgress?.({ loaded: uploaded, total: file.size });
    }
    return await request({
      url: `/ai/knowledge/${knowledgeBaseId}/documents/multipart/${session.uploadId}/complete`,
      method: "post",
      timeout: 5 * 60 * 1000,
    });
  } catch (error) {
    request({
      url: `/ai/knowledge/${knowledgeBaseId}/documents/multipart/${session.uploadId}`,
      method: "delete",
    }).catch(() => {});
    throw error;
  }
};
export const listKnowledgeDocumentVersions = (
  knowledgeBaseId,
  documentId,
  params,
) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/documents/${documentId}/versions`,
    method: "get",
    params,
  });
export const listKnowledgeDocumentTasks = (
  knowledgeBaseId,
  documentId,
  params,
) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/documents/${documentId}/tasks`,
    method: "get",
    params,
  });
export const getKnowledgeParsedArtifact = (
  knowledgeBaseId,
  documentId,
  versionId,
  disposition = "attachment",
) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/documents/${documentId}/versions/${versionId}/parsed`,
    method: "get",
    params: { disposition },
    responseType: "blob",
  }).then((res) => res.data);
export const getKnowledgeParsedImage = (
  knowledgeBaseId,
  documentId,
  versionId,
  name,
) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/documents/${documentId}/versions/${versionId}/parsed/image`,
    method: "get",
    params: { name },
    responseType: "blob",
  }).then((res) => res.data);
export const retryKnowledgeDocument = (
  knowledgeBaseId,
  documentId,
  versionId,
) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/documents/${documentId}/versions/${versionId}/retry`,
    method: "post",
  });
export const setCurrentKnowledgeDocumentVersion = (
  knowledgeBaseId,
  documentId,
  versionId,
) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/documents/${documentId}/versions/${versionId}/current`,
    method: "put",
  });
export const listKnowledgeDocumentVersionSwitchLogs = (
  knowledgeBaseId,
  documentId,
) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/documents/${documentId}/version-switch-logs`,
    method: "get",
  }).then((res) => res.data);
export const listKnowledgeDocumentVersionCleanupTasks = (
  knowledgeBaseId,
  documentId,
) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/documents/${documentId}/version-cleanup-tasks`,
    method: "get",
  }).then((res) => res.data);
export const cleanupKnowledgeDocumentVersion = (
  knowledgeBaseId,
  documentId,
  versionId,
) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/documents/${documentId}/versions/${versionId}/cleanup`,
    method: "post",
  });
export const deleteKnowledgeDocument = (knowledgeBaseId, documentId) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/documents/${documentId}`,
    method: "delete",
  });
export const listKnowledgeChunks = (knowledgeBaseId, params) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/chunks`,
    method: "get",
    params,
  });
export const getKnowledgeIndexStatus = (knowledgeBaseId) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/index/status`,
    method: "get",
  }).then((res) => res.data);
export const listKnowledgeIngestTasks = (knowledgeBaseId, params) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/ingest/tasks`,
    method: "get",
    params,
  });
export const reindexKnowledgeVersion = (
  knowledgeBaseId,
  documentId,
  versionId,
) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/documents/${documentId}/versions/${versionId}/reindex`,
    method: "post",
  });
export const getKnowledgeDocumentChunkConfig = (knowledgeBaseId, documentId) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/documents/${documentId}/chunk-config`,
    method: "get",
  }).then((res) => res.data);
export const saveKnowledgeDocumentChunkConfig = (
  knowledgeBaseId,
  documentId,
  data,
) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/documents/${documentId}/chunk-config`,
    method: "put",
    data,
  }).then((res) => res.data);
export const previewKnowledgeDocumentChunks = (
  knowledgeBaseId,
  documentId,
  data,
) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/documents/${documentId}/chunk-preview`,
    method: "post",
    data,
  }).then((res) => res.data);
export const rechunkKnowledgeDocument = (knowledgeBaseId, documentId, data) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/documents/${documentId}/rechunk`,
    method: "post",
    data,
  }).then((res) => res.data);
export const rebuildKnowledgeIndex = (knowledgeBaseId) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/index/rebuild`,
    method: "post",
  });
export const retryKnowledgeIndexSwitch = (knowledgeBaseId) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/index/switch/retry`,
    method: "post",
  });
export const rollbackKnowledgeIndex = (knowledgeBaseId) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/index/rollback`,
    method: "post",
  });
export const listKnowledgeIndexSwitchLogs = (knowledgeBaseId) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/index/switch/logs`,
    method: "get",
  }).then((res) => res.data);
export const listKnowledgePhysicalIndices = (knowledgeBaseId) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/index/physical`,
    method: "get",
  }).then((res) => res.data);
export const deleteKnowledgePhysicalIndex = (knowledgeBaseId, generation) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/index/physical/${generation}`,
    method: "delete",
  });
export const deleteKnowledgeVersionIndex = (
  knowledgeBaseId,
  documentId,
  versionId,
  generation,
) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/index/documents/${documentId}/versions/${versionId}`,
    method: "delete",
    params: generation ? { generation } : undefined,
  }).then((res) => res.data);
export const deleteKnowledgeChunkIndex = (
  knowledgeBaseId,
  documentId,
  chunkId,
  generation,
) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/index/documents/${documentId}/chunks/${chunkId}`,
    method: "delete",
    params: generation ? { generation } : undefined,
  }).then((res) => res.data);
export const pruneKnowledgePhysicalIndices = (
  knowledgeBaseId,
  retainCount = 3,
) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/index/physical/prune`,
    method: "post",
    params: { retainCount },
  });
export const checkKnowledgeIndexConsistency = (knowledgeBaseId) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/index/consistency/check`,
    method: "post",
  }).then((res) => res.data);
export const listKnowledgeIndexDocuments = (knowledgeBaseId, params) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/index/documents`,
    method: "get",
    params,
  });
export const listKnowledgeIndexVersions = (
  knowledgeBaseId,
  documentId,
  params,
) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/index/documents/${documentId}/versions`,
    method: "get",
    params,
  });
export const listKnowledgeDocumentChunks = (
  knowledgeBaseId,
  documentId,
  params,
) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/index/documents/${documentId}/chunks`,
    method: "get",
    params,
  });
export const getKnowledgeDocumentChunk = (
  knowledgeBaseId,
  documentId,
  chunkId,
  params,
) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/index/documents/${documentId}/chunks/${chunkId}`,
    method: "get",
    params,
  }).then((res) => res.data);
export const listKnowledgeIndexTasks = (knowledgeBaseId, documentId, params) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/index/documents/${documentId}/tasks`,
    method: "get",
    params,
  });
export const batchReindexKnowledgeDocuments = (knowledgeBaseId, documentIds) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/index/documents/reindex`,
    method: "post",
    data: { documentIds },
  });
export const batchRetryKnowledgeDocuments = (knowledgeBaseId, documentIds) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/index/documents/retry-failed`,
    method: "post",
    data: { documentIds },
  });
export const cancelKnowledgeTasks = (knowledgeBaseId, documentIds) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/ingest/tasks/cancel`,
    method: "post",
    data: { documentIds },
  });
export const exportKnowledgeFailures = (knowledgeBaseId, params) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/ingest/failures/export`,
    method: "get",
    params,
    responseType: "blob",
  });
export const testKnowledgeRetrieval = (knowledgeBaseId, data) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/retrieval/test`,
    method: "post",
    data,
  }).then((res) => res.data);
export const pageKnowledgeQueryAudits = (knowledgeBaseId, params) =>
  request({ url: `/ai/knowledge/${knowledgeBaseId}/query-audits`, method: "get", params });
export const getKnowledgeQueryAuditStats = (knowledgeBaseId, params) =>
  request({ url: `/ai/knowledge/${knowledgeBaseId}/query-audits/stats`, method: "get", params })
    .then((res) => res.data);
export const getKnowledgeQueryAudit = (knowledgeBaseId, auditId) =>
  request({ url: `/ai/knowledge/${knowledgeBaseId}/query-audits/${auditId}`, method: "get" })
    .then((res) => res.data);
export const pageKnowledgeQueryAuditCitations = (knowledgeBaseId, auditId, params) =>
  request({ url: `/ai/knowledge/${knowledgeBaseId}/query-audits/${auditId}/citations`, method: "get", params });
export const exportKnowledgeQueryAudits = (knowledgeBaseId, params) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/query-audits/export`,
    method: "get",
    params,
    responseType: "blob",
  }).then((res) => res.data);
export async function streamKnowledgeChat(
  knowledgeBaseId,
  data,
  handlers = {},
  signal,
) {
  const base = import.meta.env.VITE_APP_BASE_API || "/dev-api";
  const headers = {
    "Content-Type": "application/json",
    Accept: "text/event-stream",
  };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  const tenantId = localStorage.getItem("tenantId");
  if (tenantId) headers["X-Tenant-Id"] = tenantId;
  const response = await fetch(
    `${base}/ai/knowledge/${knowledgeBaseId}/chat/stream`,
    {
      method: "POST",
      headers,
      body: JSON.stringify(data),
      signal,
    },
  );
  if (!response.ok) {
    let message = `问答请求失败（${response.status}）`;
    try {
      message = (await response.json())?.msg || message;
    } catch (_) {}
    throw new Error(message);
  }
  if (!response.body) throw new Error("浏览器不支持流式响应");
  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";
  const dispatch = (block) => {
    let event = "message";
    const values = [];
    block.split(/\r?\n/).forEach((line) => {
      if (line.startsWith("event:")) event = line.slice(6).trim();
      if (line.startsWith("data:")) values.push(line.slice(5).trimStart());
    });
    if (!values.length) return;
    const raw = values.join("\n");
    let payload = raw;
    try {
      payload = JSON.parse(raw);
      for (
        let depth = 0;
        depth < 3 &&
        typeof payload === "string" &&
        /^[\[{]/.test(payload.trim());
        depth += 1
      ) {
        payload = JSON.parse(payload);
      }
    } catch (_) {}
    handlers[event]?.(payload);
    handlers.event?.(event, payload);
  };
  while (true) {
    const { value, done } = await reader.read();
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done });
    const blocks = buffer.split(/\r?\n\r?\n/);
    buffer = blocks.pop() || "";
    blocks.forEach(dispatch);
    if (done) break;
  }
  if (buffer.trim()) dispatch(buffer);
}
export const getKnowledgePermissions = (knowledgeBaseId, documentId) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/permissions`,
    method: "get",
    params: { documentId },
  }).then((res) => res.data);
export const saveKnowledgePermissions = (knowledgeBaseId, documentId, data) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/permissions`,
    method: "put",
    params: { documentId },
    data,
  });

export const getKnowledgeGraphSchema = (knowledgeBaseId) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/schema`,
    method: "get",
  }).then((res) => res.data);
export const saveKnowledgeGraphSchema = (knowledgeBaseId, data) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/schema`,
    method: "put",
    data,
  }).then((res) => res.data);
export const createKnowledgeGraphRun = (knowledgeBaseId, data = {}) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/runs`,
    method: "post",
    data,
  }).then((res) => res.data);
export const listKnowledgeGraphRuns = (knowledgeBaseId, params) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/runs`,
    method: "get",
    params,
  }).then((res) => res.data);
export const getKnowledgeGraphRun = (knowledgeBaseId, runId) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/runs/${runId}`,
    method: "get",
  }).then((res) => res.data);
export const listKnowledgeGraphRunJobs = (knowledgeBaseId, runId, params) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/runs/${runId}/jobs`,
    method: "get",
    params,
  }).then((res) => res.data);
export const retryKnowledgeGraphRun = (knowledgeBaseId, runId) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/runs/${runId}/retry`,
    method: "post",
  });
export const cancelKnowledgeGraphRun = (knowledgeBaseId, runId) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/runs/${runId}/cancel`,
    method: "post",
  });
export const deleteFailedKnowledgeGraphRun = (knowledgeBaseId, runId) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/runs/${runId}`,
    method: "delete",
  });
export const listLegacyKnowledgeGraphJobs = (knowledgeBaseId, params) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/jobs/legacy`,
    method: "get",
    params,
  }).then((res) => res.data);
export const listKnowledgeGraphReviews = (knowledgeBaseId, type, params) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/reviews/${type}`,
    method: "get",
    params,
  }).then((res) => res.data);
export const reviewKnowledgeGraphItem = (knowledgeBaseId, type, id, data) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/reviews/${type}/${id}`,
    method: "put",
    data,
  });
export const publishKnowledgeGraphItems = (knowledgeBaseId, type, ids) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/publish/${type}`,
    method: "post",
    data: { ids },
  });
export const listKnowledgeGraphDocuments = (knowledgeBaseId, params) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/documents`,
    method: "get",
    params,
  }).then((res) => res.data);
export const listKnowledgeGraphFiles = (knowledgeBaseId, params) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/files`,
    method: "get",
    params,
  }).then((res) => res.data);
export const getKnowledgeGraphFile = (knowledgeBaseId, documentId) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/files/${documentId}`,
    method: "get",
  }).then((res) => res.data);
export const buildKnowledgeGraphFiles = (knowledgeBaseId, documentIds, modelId) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/files/build`,
    method: "post",
    data: { documentIds, modelId },
  }).then((res) => res.data);
export const publishKnowledgeGraphFiles = (knowledgeBaseId, ids) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/files/publish`,
    method: "post",
    data: { ids },
  }).then((res) => res.data);
export const listKnowledgeGraphExceptions = (knowledgeBaseId, params) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/exceptions`,
    method: "get",
    params,
  }).then((res) => res.data);
export const listKnowledgeGraphNodes = (knowledgeBaseId, params) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/nodes`,
    method: "get",
    params,
  }).then((res) => res.data);
export const changeKnowledgeGraphNodeStatus = (knowledgeBaseId, nodeId, status) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/nodes/${encodeURIComponent(nodeId)}/status`,
    method: "put",
    data: { status },
  });
export const listKnowledgeGraphDocumentChunks = (
  knowledgeBaseId,
  documentId,
  params,
) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/documents/${documentId}/chunks`,
    method: "get",
    params,
    }).then((res) => res.data);

export const deleteKnowledgeGraphByDocument = (knowledgeBaseId, documentId) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/documents/${documentId}`,
    method: "delete",
  }).then((res) => res.data);
export const aggregateKnowledgeGraphRun = (knowledgeBaseId, runId) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/runs/${runId}/aggregate`,
    method: "post",
  }).then((res) => res.data);
export const getKnowledgeGraphCandidateStats = (knowledgeBaseId, params) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/candidate-groups/stats`,
    method: "get",
    params,
  }).then((res) => res.data);
export const listKnowledgeGraphCandidateGroups = (knowledgeBaseId, params) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/candidate-groups`,
    method: "get",
    params,
  }).then((res) => res.data);
export const getKnowledgeGraphCandidateGroup = (knowledgeBaseId, groupId) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/candidate-groups/${groupId}`,
    method: "get",
  }).then((res) => res.data);
export const reviewKnowledgeGraphCandidateGroups = (knowledgeBaseId, data) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/candidate-groups/review`,
    method: "put",
    data,
  });
export const publishKnowledgeGraphCandidateGroups = (knowledgeBaseId, ids) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/candidate-groups/publish`,
    method: "post",
    data: { ids },
  }).then((res) => res.data);
export const deleteKnowledgeGraphCandidateGroups = (knowledgeBaseId, ids) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/candidate-groups`,
    method: "delete",
    data: { ids },
  });
export const initializeKnowledgeGraph = (knowledgeBaseId) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/initialize`,
    method: "post",
  });
export const getKnowledgeGraphView = (knowledgeBaseId, params) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/view`,
    method: "get",
    params,
  }).then((res) => res.data);
export const getKnowledgeGraphNeighborhood = (knowledgeBaseId, params) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/neighborhood`,
    method: "get",
    params,
  }).then((res) => res.data);
export const getKnowledgeGraphProvenance = (knowledgeBaseId, params) =>
  request({
    url: `/ai/knowledge/${knowledgeBaseId}/graph/provenance`,
    method: "get",
    params,
  }).then((res) => res.data);

export const listTools = (params) => listPage("/ai/tools", params);
export const getTools = (id) => getById("/ai/tools", id);
export const addTools = (data) => add("/ai/tools", data);
export const updateTools = (data) => update("/ai/tools", data);
export const delTools = (ids) => remove("/ai/tools", ids);
export const changeToolsStatus = (id, status) =>
  changeStatus("/ai/tools", { id, status });

export const listSkills = (params) => listPage("/ai/skills", params);
export const getSkills = (id) => getById("/ai/skills", id);
export const addSkills = (data) => add("/ai/skills", data);
export const updateSkills = (data) => update("/ai/skills", data);
export const delSkills = (ids) => remove("/ai/skills", ids);
export const changeSkillsStatus = (id, status) =>
  changeStatus("/ai/skills", { id, status });

export const listMcp = (params) => listPage("/ai/mcp", params);
export const getMcp = (id) => getById("/ai/mcp", id);
export const addMcp = (data) => add("/ai/mcp", data);
export const updateMcp = (data) => update("/ai/mcp", data);
export const delMcp = (ids) => remove("/ai/mcp", ids);
export const changeMcpStatus = (id, status) =>
  changeStatus("/ai/mcp", { id, status });

export const listWorkflow = (params) => listPage("/ai/workflow", params);
export const getWorkflowResources = () =>
  request({ url: "/ai/workflow/resources", method: "get" }).then((res) => res.data);
export const getWorkflow = (id) => getById("/ai/workflow", id);
export const addWorkflow = (data) => add("/ai/workflow", data);
export const updateWorkflow = (data) => update("/ai/workflow", data);
export const delWorkflow = (ids) => remove("/ai/workflow", ids);
export const changeWorkflowStatus = (id, status) =>
  request({ url: "/ai/workflow/changeStatus", method: "put", params: { id, status } });
export const saveWorkflowDraft = (id, data) =>
  request({ url: `/ai/workflow/${id}/draft`, method: "put", data });
export const validateWorkflow = (id, data) =>
  request({ url: `/ai/workflow/${id}/validate`, method: "post", data }).then((res) => res.data);
export const publishWorkflow = (id) =>
  request({ url: `/ai/workflow/${id}/publish`, method: "post" });
export const runWorkflow = (id, data) =>
  request({ url: `/ai/workflow/${id}/run`, method: "post", data }).then((res) => res.data);
export const getWorkflowInputs = (id) =>
  request({ url: `/ai/workflow/${id}/inputs`, method: "get" }).then((res) => res.data);
export const listWorkflowExecutions = (id, params) =>
  request({ url: `/ai/workflow/${id}/executions`, method: "get", params }).then((res) => res.data);
export const getWorkflowExecution = (id, executionId) =>
  request({ url: `/ai/workflow/${id}/executions/${executionId}`, method: "get" }).then((res) => res.data);

export const listAgent = (params) => listPage("/ai/agent", params);
export const getAgent = (id) => getById("/ai/agent", id);
export const addAgent = (data) => add("/ai/agent", data);
export const updateAgent = (data) => update("/ai/agent", data);
export const delAgent = (ids) => remove("/ai/agent", ids);
export const changeAgentStatus = (id, status) =>
  changeStatus("/ai/agent", { id, status });

export const listBot = (params) => listPage("/ai/bot", params);
export const getBot = (id) => getById("/ai/bot", id);
export const addBot = (data) => add("/ai/bot", data);
export const updateBot = (data) => update("/ai/bot", data);
export const delBot = (ids) => remove("/ai/bot", ids);
export const changeBotStatus = (id, status) =>
  changeStatus("/ai/bot", { id, status });
