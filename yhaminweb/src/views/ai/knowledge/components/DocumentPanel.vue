<template>
  <section class="document-panel">
    <input
      ref="versionFileInput"
      class="hidden-file-input"
      type="file"
      accept=".pdf,.docx,.txt,.md,.markdown,.html,.htm"
      @change="onVersionFileSelected"
    />
    <div class="section-heading">
      <div>
        <h3>文档管理</h3>
        <p>原始文件保存到 MinIO，上传后自动创建解析任务。</p>
      </div>
      <el-upload
        ref="uploadRef"
        multiple
        :show-file-list="false"
        :http-request="upload"
        :before-upload="beforeUpload"
        accept=".pdf,.docx,.txt,.md,.markdown,.html,.htm"
      >
        <el-button type="primary"
          ><el-icon><Upload /></el-icon
          >{{
            uploading ? `继续选择（上传中 ${uploadCount}）` : "上传文档"
          }}</el-button
        >
      </el-upload>
    </div>

    <div class="document-stats">
      <span
        >共 <b>{{ stats.total }}</b> 个文档</span
      >
      <span class="success">成功 {{ stats.success }}</span>
      <span class="warning">处理中 {{ stats.processing }}</span>
      <span class="danger">失败 {{ stats.failed }}</span>
    </div>

    <article class="ry-card document-card">
      <div class="document-toolbar">
        <div class="filters">
          <el-input
            v-model="query.name"
            :prefix-icon="Search"
            clearable
            placeholder="搜索文档名称"
            @keyup.enter="search"
          />
          <el-select v-model="query.status" clearable placeholder="文档状态">
            <el-option label="待处理" value="PENDING" /><el-option
              label="处理中"
              value="RUNNING"
            />
            <el-option label="成功" value="SUCCESS" /><el-option
              label="失败"
              value="FAILED"
            />
          </el-select>
          <el-select
            v-model="query.currentStage"
            clearable
            placeholder="处理阶段"
          >
            <el-option label="解析" value="PARSE" /><el-option
              label="清洗"
              value="CLEAN"
            />
            <el-option label="分块" value="CHUNK" /><el-option
              label="向量化"
              value="EMBEDDING"
            />
            <el-option label="索引" value="INDEX" />
          </el-select>
          <el-select
            v-model="query.indexStatus"
            clearable
            placeholder="索引状态"
          >
            <el-option label="未索引" value="NOT_INDEXED" /><el-option
              label="处理中"
              value="PROCESSING"
            />
            <el-option label="成功" value="SUCCESS" /><el-option
              label="部分失败"
              value="PARTIAL_FAILED"
            />
            <el-option label="失败" value="FAILED" />
          </el-select>
          <el-date-picker
            v-model="uploadRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="上传开始时间"
            end-placeholder="上传结束时间"
          />
          <el-checkbox v-model="query.failedOnly">仅失败文档</el-checkbox>
          <el-button type="primary" :icon="Search" @click="search"
            >查询</el-button
          >
          <el-button @click="reset">重置</el-button>
        </div>
        <el-button
          :icon="Refresh"
          :loading="loading"
          circle
          title="刷新文档"
          @click="load(false)"
        />
      </div>
      <div v-if="uploading" class="upload-progress">
        <div
          v-for="item in uploadItems"
          :key="item.key"
          class="upload-progress-item"
        >
          <div>
            <span>{{ item.name }}</span
            ><small>{{ item.statusText }}</small>
          </div>
          <el-progress
            :percentage="item.progress"
            :stroke-width="6"
            :status="item.status"
          />
        </div>
      </div>
      <el-alert
        class="file-tip"
        type="info"
        :closable="false"
        title="支持 PDF、DOCX、TXT、Markdown 和 HTML，单文件最大 100MB。"
      />
      <el-table v-loading="loading" :data="documents" class="document-table">
        <el-table-column
          prop="name"
          label="文档名称"
          min-width="260"
          show-overflow-tooltip
        >
          <template #default="{ row }"
            ><div class="file-name">
              <el-icon><Document /></el-icon><span>{{ row.name }}</span>
            </div></template
          >
        </el-table-column>
        <el-table-column prop="status" label="处理状态" width="120">
          <template #default="{ row }"
            ><el-tag :type="statusType(row.status)" effect="light">{{
              statusText(row.status)
            }}</el-tag></template
          >
        </el-table-column>
        <el-table-column
          prop="currentVersionId"
          label="当前版本"
          width="110"
          align="center"
        >
          <template #default="{ row }"
            >V{{ row.currentVersionNo || "-" }}</template
          >
        </el-table-column>
        <el-table-column prop="createTime" label="上传时间" width="180" />
        <el-table-column label="操作" width="340" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button link type="primary" @click="chooseVersionFile(row)"
                >上传新版本</el-button
              ><el-button link type="primary" @click="openRechunk(row)"
                >重新切片</el-button
              ><el-button link type="primary" @click="openDetail(row)"
                >版本与任务</el-button
              ><el-button link type="danger" @click="remove(row)"
                >删除</el-button
              >
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty
            :description="hasFilters ? '没有符合条件的文档' : '尚未上传文档'"
          >
            <el-button
              v-if="!hasFilters"
              type="primary"
              plain
              @click="openUploader"
              >上传第一个文档</el-button
            >
          </el-empty>
        </template>
      </el-table>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @change="loadDocuments(false)"
      />
    </article>

    <el-drawer
      v-model="drawer"
      :title="current?.name || '文档详情'"
      size="min(720px, 92vw)"
    >
      <template #header>
        <div class="drawer-header">
          <div>
            <b>{{ current?.name || "文档详情" }}</b
            ><small>查看文件版本及处理任务</small>
          </div>
          <div class="drawer-header-actions">
            <el-button
              v-if="current?.currentVersionId"
              type="primary"
              plain
              @click="openRechunk(current)"
              >重新切片</el-button
            ><el-button
              :icon="Refresh"
              :loading="detailLoading"
              circle
              @click="refreshDetail(false)"
            />
          </div>
        </div>
      </template>
      <h4>文件版本</h4>
      <el-table v-loading="detailLoading" :data="versions">
        <el-table-column prop="versionNo" label="版本" width="110"
          ><template #default="{ row }"
            ><span>V{{ row.versionNo }}</span
            ><el-tag
              v-if="row.id === current?.currentVersionId"
              class="current-tag"
              type="success"
              size="small"
              >当前</el-tag
            ></template
          ></el-table-column
        >
        <el-table-column prop="versionReason" label="版本来源" width="100"
          ><template #default="{ row }">{{
            row.versionReason === "RECHUNK" ? "重新切片" : "文件上传"
          }}</template></el-table-column
        >
        <el-table-column prop="chunkStrategy" label="分块策略" width="130"
          ><template #default="{ row }"
            ><span :title="row.chunkConfigHash || ''">{{
              chunkStrategyText(row.chunkStrategy)
            }}</span></template
          ></el-table-column
        >
        <el-table-column
          prop="contentType"
          label="文件类型"
          min-width="150"
          show-overflow-tooltip
        />
        <el-table-column prop="fileSize" label="文件大小" width="100"
          ><template #default="{ row }">{{
            formatSize(row.fileSize)
          }}</template></el-table-column
        >
        <el-table-column prop="status" label="状态" width="100"
          ><template #default="{ row }"
            ><el-tag :type="statusType(row.status)">{{
              statusText(row.status)
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column label="操作" width="360"
          ><template #default="{ row }"
            ><el-button
              v-if="row.parsedObjectKey"
              link
              type="primary"
              @click="openParsed(row)"
              >查看 Markdown</el-button
            ><el-button
              v-if="row.parsedObjectKey"
              link
              type="primary"
              @click="downloadParsed(row)"
              >下载</el-button
            ><el-button
              v-if="row.status === 'FAILED'"
              link
              type="primary"
              @click="retry(row)"
              >重试</el-button
            ><el-button
              v-else-if="
                row.status === 'SUCCESS' && row.id !== current?.currentVersionId
              "
              link
              type="primary"
              @click="setCurrentVersion(row)"
              >设为当前版本</el-button
            ><template v-else-if="row.status === 'EXPIRED'"
              ><el-button link type="warning" @click="setCurrentVersion(row)"
                >回滚到此版本</el-button
              ><el-button link type="danger" @click="cleanupVersion(row)"
                >清理</el-button
              ></template
            ></template
          ></el-table-column
        >
      </el-table>
      <el-pagination
        v-model:current-page="versionQuery.pageNum"
        v-model:page-size="versionQuery.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="versionTotal"
        layout="total, sizes, prev, pager, next"
        @change="loadVersions(false)"
      />
      <h4 class="task-title">版本切换记录</h4>
      <el-table :data="switchLogs" max-height="240">
        <el-table-column prop="operation" label="类型" width="90">
          <template #default="{ row }">{{
            row.operation === "ROLLBACK" ? "回滚" : "切换"
          }}</template>
        </el-table-column>
        <el-table-column label="版本" min-width="120">
          <template #default="{ row }"
            >V{{ row.fromVersionNo || "-" }} → V{{
              row.toVersionNo || "-"
            }}</template
          >
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }"
            ><el-tag
              :type="
                row.status === 'SUCCESS'
                  ? 'success'
                  : row.status === 'FAILED'
                    ? 'danger'
                    : 'warning'
              "
              >{{ row.status }}</el-tag
            ></template
          >
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column
          prop="errorMessage"
          label="说明"
          min-width="180"
          show-overflow-tooltip
        />
      </el-table>
      <h4 class="task-title">历史版本清理任务</h4>
      <el-table :data="cleanupTasks" max-height="240">
        <el-table-column label="版本" width="80"
          ><template #default="{ row }"
            >V{{ versionNo(row.documentVersionId) }}</template
          ></el-table-column
        >
        <el-table-column prop="cleanupType" label="清理项" min-width="150" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }"
            ><el-tag
              :type="
                row.status === 'SUCCESS'
                  ? 'success'
                  : row.status === 'FAILED'
                    ? 'danger'
                    : 'warning'
              "
              >{{ row.status }}</el-tag
            ></template
          >
        </el-table-column>
        <el-table-column prop="retryCount" label="执行次数" width="90" />
        <el-table-column
          prop="errorMessage"
          label="失败原因"
          min-width="180"
          show-overflow-tooltip
        />
      </el-table>
      <h4 class="task-title">处理任务</h4>
      <div class="task-filters">
        <el-select
          v-model="taskQuery.taskType"
          clearable
          placeholder="处理阶段"
          @change="taskSearch"
        >
          <el-option label="解析" value="PARSE" /><el-option
            label="清洗"
            value="CLEAN"
          />
          <el-option label="分块" value="CHUNK" /><el-option
            label="向量化"
            value="EMBEDDING"
          /><el-option label="索引" value="INDEX" />
        </el-select>
        <el-select
          v-model="taskQuery.status"
          clearable
          placeholder="任务状态"
          @change="taskSearch"
        >
          <el-option label="等待中" value="PENDING" /><el-option
            label="运行中"
            value="RUNNING"
          />
          <el-option label="等待重试" value="RETRY_WAIT" /><el-option
            label="成功"
            value="SUCCESS"
          /><el-option label="失败" value="FAILED" />
        </el-select>
      </div>
      <el-timeline v-if="tasks.length">
        <el-timeline-item
          v-for="task in tasks"
          :key="task.id"
          :timestamp="task.createTime"
          placement="top"
          :type="timelineType(task.status)"
        >
          <el-card shadow="never">
            <div class="task-row">
              <strong>{{ taskTypeText(task.taskType) }}</strong
              ><el-tag :type="statusType(task.status)">{{
                statusText(task.status)
              }}</el-tag>
            </div>
            <el-progress
              :percentage="task.progress || 0"
              :status="
                task.status === 'FAILED'
                  ? 'exception'
                  : task.status === 'SUCCESS'
                    ? 'success'
                    : ''
              "
            />
            <el-alert
              v-if="task.errorMessage"
              class="task-error"
              type="error"
              :closable="false"
              :title="task.errorMessage"
              show-icon
            />
          </el-card>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无处理任务" />
      <el-pagination
        v-model:current-page="taskQuery.pageNum"
        v-model:page-size="taskQuery.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="taskTotal"
        layout="total, sizes, prev, pager, next"
        @change="loadTasks(false)"
      />
    </el-drawer>
    <el-dialog
      v-model="markdownDialog"
      :title="markdownTitle"
      width="min(1000px, 94vw)"
      destroy-on-close
    >
      <div class="markdown-dialog-body">
        <MarkdownPreview
          v-if="markdownVersion"
          :content="markdownContent"
          :knowledge-base-id="knowledgeBaseId"
          :document-id="current.id"
          :version-id="markdownVersion.id"
        />
      </div>
    </el-dialog>
    <DocumentRechunkDialog
      ref="rechunkDialog"
      :knowledge-base-id="knowledgeBaseId"
      @submitted="onRechunkSubmitted"
      @settled="onRechunkSubmitted"
    />
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Upload, Search, Refresh, Document } from "@element-plus/icons-vue";
import MarkdownPreview from "./MarkdownPreview.vue";
import DocumentRechunkDialog from "./DocumentRechunkDialog.vue";
import {
  pageKnowledgeDocuments,
  getKnowledgeDocumentStatistics,
  findKnowledgeDocumentByName,
  uploadKnowledgeDocument,
  uploadKnowledgeDocumentVersion,
  uploadKnowledgeDocumentMultipart,
  listKnowledgeDocumentVersions,
  listKnowledgeDocumentTasks,
  retryKnowledgeDocument,
  getKnowledgeParsedArtifact,
  setCurrentKnowledgeDocumentVersion,
  listKnowledgeDocumentVersionSwitchLogs,
  listKnowledgeDocumentVersionCleanupTasks,
  cleanupKnowledgeDocumentVersion,
  reindexKnowledgeVersion,
  deleteKnowledgeDocument,
} from "@/api/ai";

const props = defineProps({
  knowledgeBaseId: { type: Number, required: true },
});
const emit = defineEmits(["changed"]);
const loading = ref(false);
const uploads = reactive({});
const uploadItems = computed(() => Object.values(uploads));
const uploadCount = computed(
  () => uploadItems.value.filter((item) => item.status === "").length,
);
const uploading = computed(() => uploadCount.value > 0);
const documents = ref([]);
const total = ref(0);
const stats = ref({
  total: 0,
  success: 0,
  processing: 0,
  failed: 0,
  pending: 0,
});
const query = reactive({
  name: "",
  status: "",
  currentStage: "",
  indexStatus: "",
  failedOnly: false,
  pageNum: 1,
  pageSize: 10,
});
const uploadRange = ref([]);
const uploadRef = ref();
const versionFileInput = ref();
const versionUploadTarget = ref();
const rechunkDialog = ref();
const openRechunk = (row) => rechunkDialog.value?.open(row);
const onRechunkSubmitted = () => {
  load(false);
  if (drawer.value) refreshDetail(false);
  emit("changed");
};
const chunkStrategyText = (value) =>
  ({
    SMART_MARKDOWN: "智能 Markdown",
    FIXED_TOKEN: "固定 Token",
    REGEX: "正则表达式",
    CUSTOM_SEPARATOR: "自定义分隔符",
    PAGE: "按页面",
  })[value] ||
  value ||
  "历史默认";
const drawer = ref(false);
const current = ref();
const versions = ref([]);
const tasks = ref([]);
const switchLogs = ref([]);
const cleanupTasks = ref([]);
const versionTotal = ref(0);
const taskTotal = ref(0);
const versionQuery = reactive({ pageNum: 1, pageSize: 10 });
const taskQuery = reactive({
  taskType: "",
  status: "",
  pageNum: 1,
  pageSize: 10,
});
const detailLoading = ref(false);
const markdownDialog = ref(false);
const markdownContent = ref("");
const markdownVersion = ref();
const markdownTitle = computed(() =>
  markdownVersion.value
    ? `Markdown 预览 · V${markdownVersion.value.versionNo}`
    : "Markdown 预览",
);
const allowed = ["pdf", "docx", "txt", "md", "markdown", "html", "htm"];
let pollTimer;
let pollWarningShown = false;
const uploadQueue = [];
let activeUploadRequests = 0;
const MAX_CONCURRENT_UPLOADS = 2;
const hasFilters = computed(() =>
  Boolean(
    query.name ||
    query.status ||
    query.currentStage ||
    query.indexStatus ||
    query.failedOnly ||
    uploadRange.value?.length,
  ),
);

function beforeUpload(file) {
  const ext = file.name.split(".").pop()?.toLowerCase();
  if (!allowed.includes(ext)) {
    ElMessage.error("不支持该文件类型");
    return false;
  }
  if (file.size > 100 * 1024 * 1024) {
    ElMessage.error("文件不能超过 100MB");
    return false;
  }
  return true;
}
async function load(silent = false) {
  return loadAll(silent);
}
function pageParams() {
  const [start, end] = uploadRange.value || [];
  return {
    ...query,
    startTime: formatLocalDateTime(start),
    endTime: formatLocalDateTime(end),
  };
}
function formatLocalDateTime(value) {
  if (!value) return undefined;
  const pad = (number) => String(number).padStart(2, "0");
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`;
}
async function loadDocuments(silent = false) {
  if (!silent) loading.value = true;
  try {
    const result = await pageKnowledgeDocuments(
      props.knowledgeBaseId,
      pageParams(),
    );
    documents.value = result.rows || [];
    total.value = result.total || 0;
    pollWarningShown = false;
  } catch (error) {
    if (!silent || !pollWarningShown) {
      ElMessage.warning(error?.message || "文档状态刷新失败，已保留当前数据");
      pollWarningShown = true;
    }
  } finally {
    if (!silent) loading.value = false;
  }
}
async function loadStats() {
  stats.value = (await getKnowledgeDocumentStatistics(
    props.knowledgeBaseId,
  )) || { total: 0, success: 0, processing: 0, failed: 0, pending: 0 };
  emit("changed", stats.value);
}
async function loadAll(silent = false) {
  await Promise.all([loadDocuments(silent), loadStats()]);
  syncPolling();
}
function search() {
  query.pageNum = 1;
  loadAll(false);
}
function reset() {
  Object.assign(query, {
    name: "",
    status: "",
    currentStage: "",
    indexStatus: "",
    failedOnly: false,
    pageNum: 1,
    pageSize: 10,
  });
  uploadRange.value = [];
  loadAll(false);
}
function upload(options) {
  return new Promise((resolve, reject) => {
    const file = options.file;
    const key = `${file.uid || Date.now()}-${file.name}`;
    uploads[key] = {
      key,
      name: file.name,
      progress: 0,
      status: "",
      statusText: "等待上传",
    };
    uploadQueue.push({ options, key, resolve, reject });
    drainUploadQueue();
  });
}
function enqueueVersionUpload(file, documentId) {
  return new Promise((resolve, reject) => {
    const key = `${file.uid || Date.now()}-${documentId}-${file.name}`;
    uploads[key] = {
      key,
      name: `${file.name}（新版本）`,
      progress: 0,
      status: "",
      statusText: "等待上传",
    };
    uploadQueue.push({ options: { file, documentId }, key, resolve, reject });
    drainUploadQueue();
  });
}
function chooseVersionFile(row) {
  versionUploadTarget.value = row;
  versionFileInput.value?.click();
}
function onVersionFileSelected(event) {
  const file = event.target.files?.[0];
  const target = versionUploadTarget.value;
  event.target.value = "";
  if (!file || !target || !beforeUpload(file)) return;
  enqueueVersionUpload(file, target.id).catch(() => {});
}
function drainUploadQueue() {
  while (activeUploadRequests < MAX_CONCURRENT_UPLOADS && uploadQueue.length) {
    const item = uploadQueue.shift();
    activeUploadRequests += 1;
    executeUpload(item.options, item.key)
      .then(item.resolve, item.reject)
      .finally(() => {
        activeUploadRequests -= 1;
        drainUploadQueue();
        if (activeUploadRequests === 0 && uploadQueue.length === 0) load(true);
      });
  }
}
async function executeUpload({ file, documentId }, key) {
  try {
    uploads[key].statusText = "正在上传";
    let targetDocumentId = documentId;
    if (!targetDocumentId) {
      const existing = await findKnowledgeDocumentByName(
        props.knowledgeBaseId,
        file.name,
      );
      if (existing) {
        try {
          await ElMessageBox.confirm(
            `已存在同名文档“${file.name}”，请选择如何上传。`,
            "发现同名文档",
            {
              confirmButtonText: "上传为新版本",
              cancelButtonText: "仍创建新文档",
              distinguishCancelAndClose: true,
              type: "warning",
            },
          );
          targetDocumentId = existing.id;
        } catch (action) {
          if (action !== "cancel") throw action;
        }
      }
    }
    const uploader =
      file.size > 8 * 1024 * 1024
        ? uploadKnowledgeDocumentMultipart.bind(
            null,
            props.knowledgeBaseId,
            targetDocumentId || null,
          )
        : targetDocumentId
          ? uploadKnowledgeDocumentVersion.bind(
              null,
              props.knowledgeBaseId,
              targetDocumentId,
            )
          : uploadKnowledgeDocument.bind(null, props.knowledgeBaseId);
    await uploader(file, (event) => {
      uploads[key].progress = event.total
        ? Math.round((event.loaded / event.total) * 100)
        : 0;
    });
    Object.assign(uploads[key], {
      progress: 100,
      status: "success",
      statusText: "上传成功，正在后台处理",
    });
    if (targetDocumentId) {
      const target = documents.value.find(
        (item) => item.id === targetDocumentId,
      );
      current.value = target || { id: targetDocumentId, name: file.name };
      drawer.value = true;
      versionQuery.pageNum = 1;
      taskQuery.pageNum = 1;
      taskQuery.taskType = "";
      taskQuery.status = "";
      await refreshDetail(false);
      ElMessage.success(
        "新版本已创建，当前版本保持不变；可在版本与任务中查看处理进度",
      );
    }
  } catch (error) {
    Object.assign(uploads[key], {
      status: "exception",
      statusText: error?.message || "上传失败",
    });
    throw error;
  } finally {
    window.setTimeout(() => {
      delete uploads[key];
    }, 5000);
  }
}
async function openDetail(row) {
  current.value = row;
  drawer.value = true;
  versionQuery.pageNum = 1;
  taskQuery.pageNum = 1;
  await refreshDetail();
}
async function refreshDetail(silent = false) {
  if (!current.value) return;
  if (!silent) detailLoading.value = true;
  try {
    await Promise.all([
      loadVersions(silent),
      loadTasks(silent),
      loadSwitchLogs(),
      loadCleanupTasks(),
    ]);
    syncPolling();
  } catch (error) {
    if (!silent) ElMessage.error(error?.message || "文档详情加载失败");
  } finally {
    if (!silent) detailLoading.value = false;
  }
}
async function loadVersions(silent = false) {
  const result = await listKnowledgeDocumentVersions(
    props.knowledgeBaseId,
    current.value.id,
    versionQuery,
  );
  versions.value = result.rows || [];
  versionTotal.value = result.total || 0;
}
async function loadTasks(silent = false) {
  const result = await listKnowledgeDocumentTasks(
    props.knowledgeBaseId,
    current.value.id,
    taskQuery,
  );
  tasks.value = result.rows || [];
  taskTotal.value = result.total || 0;
}
async function fetchParsed(version, disposition) {
  try {
    const source = await getKnowledgeParsedArtifact(
      props.knowledgeBaseId,
      current.value.id,
      version.id,
      disposition,
    );
    const bytes = await source.arrayBuffer();
    const text = new TextDecoder("utf-8", { fatal: false }).decode(bytes);
    if (disposition === "inline") {
      markdownVersion.value = version;
      markdownContent.value = text;
      markdownDialog.value = true;
    } else {
      const blob = new Blob(["\ufeff", text], {
        type: "text/markdown;charset=utf-8",
      });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `V${version.versionNo}-content.md`;
      link.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    }
  } catch (error) {
    ElMessage.error(error?.message || "解析产物获取失败");
  }
}
const openParsed = (version) => fetchParsed(version, "inline");
const downloadParsed = (version) => fetchParsed(version, "attachment");
async function loadSwitchLogs() {
  switchLogs.value =
    (await listKnowledgeDocumentVersionSwitchLogs(
      props.knowledgeBaseId,
      current.value.id,
    )) || [];
}
async function loadCleanupTasks() {
  cleanupTasks.value =
    (await listKnowledgeDocumentVersionCleanupTasks(
      props.knowledgeBaseId,
      current.value.id,
    )) || [];
}
function versionNo(versionId) {
  return versions.value.find((item) => item.id === versionId)?.versionNo || "-";
}
function taskSearch() {
  taskQuery.pageNum = 1;
  loadTasks(false);
}
async function retry(version) {
  try {
    await retryKnowledgeDocument(
      props.knowledgeBaseId,
      current.value.id,
      version.id,
    );
    ElMessage.success("已创建重试任务");
    await Promise.all([refreshDetail(), load()]);
  } catch (error) {
    ElMessage.error(error?.message || "重试失败");
  }
}
async function setCurrentVersion(version) {
  try {
    const rollback = version.status === "EXPIRED";
    await ElMessageBox.confirm(
      `${rollback ? "确认回滚到" : "确认将"} V${version.versionNo} ${rollback ? "" : "设为当前版本"}？系统会先校验分块和 Elasticsearch 数据，失败时当前版本保持不变。`,
      rollback ? "回滚历史版本" : "切换当前版本",
      {
        confirmButtonText: rollback ? "校验并回滚" : "校验并切换",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
    await setCurrentKnowledgeDocumentVersion(
      props.knowledgeBaseId,
      current.value.id,
      version.id,
    );
    current.value.currentVersionId = version.id;
    current.value.currentVersionNo = version.versionNo;
    ElMessage.success(`当前版本已安全切换为 V${version.versionNo}`);
    await Promise.all([loadAll(false), refreshDetail(false)]);
  } catch (error) {
    if (error !== "cancel" && error !== "close") {
      const message = error?.response?.data?.msg || error?.message || "";
      if (message.includes("重新索引") || message.includes("数据不完整")) {
        try {
          await ElMessageBox.confirm(
            "该历史版本的分块或索引数据不完整，是否先创建目标版本重新索引任务？任务成功后请再次执行回滚。",
            "需要重建目标版本",
            {
              confirmButtonText: "创建重新索引任务",
              cancelButtonText: "取消",
              type: "warning",
            },
          );
          await reindexKnowledgeVersion(
            props.knowledgeBaseId,
            current.value.id,
            version.id,
          );
          ElMessage.success(
            `V${version.versionNo} 重新索引任务已创建，完成后可再次回滚`,
          );
          await refreshDetail(false);
        } catch (action) {
          if (action !== "cancel" && action !== "close")
            ElMessage.error(action?.message || "创建重新索引任务失败");
        }
      } else {
        ElMessage.error(message || "版本切换失败，原当前版本保持不变");
      }
    }
  }
}
async function cleanupVersion(version) {
  try {
    await ElMessageBox.confirm(
      `确认清理历史版本 V${version.versionNo}？系统将依次清理 MinIO、Elasticsearch 和 MySQL；外部资源清理失败时会保留主数据，可再次点击重试。`,
      "清理历史版本",
      {
        confirmButtonText: "开始清理",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
    await cleanupKnowledgeDocumentVersion(
      props.knowledgeBaseId,
      current.value.id,
      version.id,
    );
    ElMessage.success("清理任务已执行，请查看各清理项状态");
    await refreshDetail(false);
  } catch (error) {
    if (error !== "cancel" && error !== "close")
      ElMessage.error(error?.message || "历史版本清理失败，主数据已保留");
  }
}
async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `删除文档「${row.name}」及其全部版本？此操作不可恢复。`,
      "删除确认",
      {
        confirmButtonText: "确认删除",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
    await deleteKnowledgeDocument(props.knowledgeBaseId, row.id);
    ElMessage.success("文档已删除");
    await load();
  } catch (error) {
    if (error !== "cancel" && error !== "close")
      ElMessage.error(error?.message || "删除失败");
  }
}
function openUploader() {
  uploadRef.value?.$el?.querySelector("input[type=file]")?.click();
}
function hasProcessing() {
  return (
    stats.value.processing > 0 ||
    tasks.value.some((task) =>
      ["PENDING", "RETRY_WAIT", "RUNNING"].includes(task.status),
    )
  );
}
function syncPolling() {
  window.clearInterval(pollTimer);
  if (!document.hidden && !uploading.value && hasProcessing()) {
    pollTimer = window.setInterval(async () => {
      await load(true);
      if (drawer.value && current.value) await refreshDetail(true);
    }, 3000);
  }
}
function handleVisibility() {
  if (document.hidden) window.clearInterval(pollTimer);
  else {
    load(true);
    syncPolling();
  }
}
function setStatusFilter(status) {
  query.status = status === "FAILED" ? "" : status;
  query.failedOnly = status === "FAILED";
  search();
}
const statusText = (status) =>
  ({
    UPLOADING: "上传中",
    PENDING: "待处理",
    RETRY_WAIT: "等待重试",
    RUNNING: "处理中",
    SUCCESS: "成功",
    FAILED: "失败",
    EXPIRED: "已失效",
  })[status] ||
  status ||
  "未知";
const statusType = (status) =>
  ({
    SUCCESS: "success",
    FAILED: "danger",
    RUNNING: "warning",
    RETRY_WAIT: "warning",
    PENDING: "info",
    UPLOADING: "info",
    EXPIRED: "info",
  })[status] || "info";
const timelineType = (status) =>
  ({ SUCCESS: "success", FAILED: "danger", RUNNING: "warning" })[status] ||
  "info";
const taskTypeText = (type) =>
  ({
    PARSE: "文档解析",
    EMBEDDING: "向量生成",
    INDEX: "索引写入",
    GRAPH_EXTRACT: "图谱抽取",
  })[type] || type;
const formatSize = (size = 0) =>
  size >= 1024 * 1024
    ? `${(size / 1024 / 1024).toFixed(1)} MB`
    : `${Math.max(1, Math.round(size / 1024))} KB`;
defineExpose({ reload: load, openUploader, setStatusFilter });
onMounted(() => {
  load();
  document.addEventListener("visibilitychange", handleVisibility);
});
onBeforeUnmount(() => {
  window.clearInterval(pollTimer);
  document.removeEventListener("visibilitychange", handleVisibility);
});
</script>

<style scoped lang="scss">
.hidden-file-input {
  display: none;
}
.document-panel {
  min-width: 0;
}
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding: 4px 2px;
}
.section-heading h3 {
  margin: 0;
  font-size: 18px;
}
.section-heading p {
  margin: 5px 0 0;
  color: var(--ry-muted-foreground);
}
.section-heading .el-button {
  gap: 6px;
}
.document-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  margin-bottom: 12px;
  padding: 0 2px;
  color: var(--ry-muted-foreground);
  font-size: 13px;
}
.document-stats b {
  color: var(--ry-foreground);
}
.document-stats .success {
  color: var(--el-color-success);
}
.document-stats .warning {
  color: var(--el-color-warning);
}
.document-stats .danger {
  color: var(--el-color-danger);
}
.document-card {
  padding: 16px;
}
.document-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.filters {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: 10px;
}
.filters .el-input {
  width: 240px;
}
.filters .el-select {
  width: 145px;
}
.filters .el-date-editor {
  width: 350px;
}
.upload-progress {
  display: grid;
  gap: 10px;
  margin: 14px 0;
  padding: 12px;
  border-radius: 6px;
  background: var(--el-fill-color-lighter);
}
.upload-progress-item > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 6px;
}
.upload-progress span {
  overflow: hidden;
  color: var(--ry-foreground);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.upload-progress small {
  flex: none;
  color: var(--ry-muted-foreground);
}
.file-tip {
  margin-top: 14px;
}
.document-table {
  margin-top: 14px;
}
.file-name {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}
.file-name .el-icon {
  flex: none;
  color: var(--el-color-primary);
}
.file-name span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row-actions {
  white-space: nowrap;
}
.el-pagination {
  justify-content: flex-end;
  margin-top: 14px;
}
.drawer-header {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  padding-right: 12px;
}
.drawer-header > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}
.drawer-header > .drawer-header-actions {
  flex-direction: row;
  align-items: center;
  flex-shrink: 0;
}
.drawer-header b {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.drawer-header small,
.muted {
  color: var(--ry-muted-foreground);
}
.task-title {
  margin-top: 28px;
}
.task-filters {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.task-filters .el-select {
  width: 150px;
}
.task-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.task-error {
  margin-top: 12px;
}
.current-tag {
  margin-left: 6px;
}
.markdown-dialog-body {
  max-height: 72vh;
  overflow: auto;
  padding: 4px 8px 18px;
}
@media (max-width: 700px) {
  .section-heading {
    align-items: flex-start;
  }
  .document-toolbar {
    align-items: flex-end;
  }
  .filters {
    min-width: 0;
    flex: 1;
    flex-direction: column;
  }
  .filters .el-input,
  .filters .el-select {
    width: 100%;
  }
  .document-card {
    padding: 12px;
  }
}
</style>
