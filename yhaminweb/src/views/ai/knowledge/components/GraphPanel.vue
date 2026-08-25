<template>
  <section class="graph-panel">
    <div class="section-heading">
      <div>
        <h3>知识图谱</h3>
        <p>
          使用本地 Ollama 模型抽取实体、关系和事实，审核通过后发布到 Neo4j。
        </p>
      </div>
      <el-button :icon="Refresh" @click="refreshCurrent">刷新</el-button>
    </div>
    <div class="mode-switch ry-card">
      <div><strong>{{ mode === 'simple' ? '简洁模式' : '高级模式' }}</strong><span>{{ mode === 'simple' ? '按文件构建，只处理异常候选。' : '查看 Schema、抽取批次、原始审核和历史任务。' }}</span></div>
      <el-radio-group v-model="mode"><el-radio-button value="simple">简洁模式</el-radio-button><el-radio-button value="advanced">高级模式</el-radio-button></el-radio-group>
    </div>
    <template v-if="mode === 'simple'">
      <el-tabs v-model="simpleTab">
        <el-tab-pane label="文件中心" name="files"><GraphFileCenter ref="fileCenter" :knowledge-base-id="knowledgeBaseId" @exceptions="showFileExceptions" @view-graph="showFileGraph" /></el-tab-pane>
        <el-tab-pane :label="exceptionDocumentId ? '当前文件异常' : '异常审批'" name="exceptions"><GraphExceptionPanel ref="exceptionPanel" :knowledge-base-id="knowledgeBaseId" :document-id="exceptionDocumentId" /></el-tab-pane>
        <el-tab-pane label="节点管理" name="nodes"><GraphNodeManager ref="nodeManager" :knowledge-base-id="knowledgeBaseId" :entity-types="lines(entityText)" @view-node="showNodeGraph" /></el-tab-pane>
        <el-tab-pane label="图谱查看" name="trace"><GraphTracePanel ref="simpleGraphTrace" :knowledge-base-id="knowledgeBaseId" :entity-types="lines(entityText)" :relation-types="lines(relationText)" /></el-tab-pane>
      </el-tabs>
    </template>
    <template v-else>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="抽取结果不会直接进入正式图谱"
      description="每次抽取形成独立批次；低于 0.6 的结果必须逐条填写审核说明。"
    />

    <el-tabs v-model="tab" @tab-change="refreshCurrent">
      <el-tab-pane label="图谱 Schema" name="schema">
        <article class="ry-card graph-card">
          <el-form label-position="top" class="schema-form">
            <el-form-item label="Schema 版本"
              ><el-input v-model="schema.schemaVersion" placeholder="例如 v2"
            /></el-form-item>
            <el-form-item label="状态"
              ><el-radio-group v-model="schema.status"
                ><el-radio value="DRAFT">草稿</el-radio
                ><el-radio value="ACTIVE">启用</el-radio></el-radio-group
              ></el-form-item
            >
            <el-form-item label="实体类型编码（一行一个）" class="span-2"
              ><el-input v-model="entityText" type="textarea" :rows="6" />
              <div class="code-help">
                <el-tag
                  v-for="code in lines(entityText)"
                  :key="code"
                  effect="plain"
                  >{{ code }}（{{ entityLabel(code) }}）</el-tag
                >
              </div></el-form-item
            >
            <el-form-item label="关系类型编码（一行一个）" class="span-2"
              ><el-input v-model="relationText" type="textarea" :rows="6" />
              <div class="code-help">
                <el-tag
                  v-for="code in lines(relationText)"
                  :key="code"
                  type="success"
                  effect="plain"
                  >{{ code }}（{{ relationLabel(code) }}）</el-tag
                >
              </div></el-form-item
            >
          </el-form>
          <div class="card-actions">
            <span>已被抽取批次使用的版本不可修改，请使用新版本号保存。</span
            ><el-button @click="initializeGraph">初始化 Neo4j 结构</el-button
            ><el-button type="primary" :loading="saving" @click="saveSchema"
              >保存 Schema</el-button
            >
          </div>
        </article>
      </el-tab-pane>

      <el-tab-pane label="抽取批次" name="runs">
        <article class="ry-card graph-card">
          <div class="toolbar">
            <el-select
              v-model="runQuery.status"
              clearable
              placeholder="全部状态"
              @change="queryRuns"
              ><el-option
                v-for="s in runStatuses"
                :key="s"
                :label="statusLabel(s)"
                :value="s"
            /></el-select>
            <el-button
              :type="schema.status === 'ACTIVE' ? 'primary' : 'warning'"
              :loading="creating"
              @click="schema.status === 'ACTIVE' ? openRunCreate() : goSchema()"
              >{{
                schema.status === "ACTIVE"
                  ? "创建抽取批次"
                  : "去启用图谱 Schema"
              }}</el-button
            >
            <el-tag :type="schema.status === 'ACTIVE' ? 'success' : 'warning'"
              >Schema：{{
                schema.status === "ACTIVE" ? "已启用" : "未启用"
              }}</el-tag
            >
          </div>
          <el-table v-loading="loading" :data="runs.rows">
            <el-table-column prop="id" label="批次" width="82" />
            <el-table-column prop="schemaVersion" label="Schema" width="90" />
            <el-table-column prop="modelId" label="模型 ID" width="90" />
            <el-table-column label="完成进度" min-width="190"
              ><template #default="{ row }"
                ><el-progress
                  :percentage="row.completionPercentage || 0"
                  :status="
                    row.failed
                      ? 'exception'
                      : row.completionPercentage === 100
                        ? 'success'
                        : ''
                  " /></template
            ></el-table-column>
            <el-table-column label="成功率" width="95"
              ><template #default="{ row }"
                >{{ row.successPercentage || 0 }}%</template
              ></el-table-column
            >
            <el-table-column label="任务统计" min-width="230"
              ><template #default="{ row }"
                ><span class="success">成功 {{ row.success || 0 }}</span> /
                <span class="warning">警告 {{ row.warning || 0 }}</span> /
                <span class="danger">失败 {{ row.failed || 0 }}</span> /
                已取消 {{ row.cancelled || 0 }} / 待处理
                {{ row.pending || 0 }}</template
              ></el-table-column
            >
            <el-table-column label="状态" width="120"
              ><template #default="{ row }"
                ><el-tag :type="tagType(row.status)">{{
                  statusLabel(row.status)
                }}</el-tag></template
              ></el-table-column
            >
            <el-table-column prop="createTime" label="创建时间" width="170" />
            <el-table-column label="操作" width="210" fixed="right"
              ><template #default="{ row }"
                ><el-button link type="primary" @click="openRun(row)"
                  >任务详情</el-button
                ><el-button
                  v-if="['SUCCESS', 'PARTIAL_FAILED'].includes(row.status)"
                  link
                  type="primary"
                  @click="aggregateRun(row)"
                  >聚合候选</el-button
                ><el-button
                  v-if="row.failed || row.warning"
                  link
                  type="warning"
                  @click="retryRun(row)"
                  >重试异常</el-button
                ><el-button
                  v-if="Number(row.pending || 0) + Number(row.running || 0) > 0 || ['PENDING', 'RUNNING'].includes(row.status)"
                  link
                  type="danger"
                  @click="cancelRun(row)"
                  >停止任务</el-button
                ><el-button
                  v-if="['PARTIAL_FAILED', 'CANCELLED'].includes(row.status) || Number(row.total || 0) === 0"
                  link
                  type="danger"
                  @click="deleteFailedRun(row)"
                  >{{ Number(row.total || 0) === 0 ? "删除空批次" : row.status === "CANCELLED" ? "删除已取消批次" : "删除记录" }}</el-button
                ></template
              ></el-table-column
            >
          </el-table>
          <PageBar :query="runQuery" :total="runs.total" @change="loadRuns" />
        </article>
      </el-tab-pane>

      <el-tab-pane label="候选组审批" name="candidates">
        <GraphCandidateReviewPanel
          ref="candidateReview"
          :knowledge-base-id="knowledgeBaseId"
        />
      </el-tab-pane>

      <el-tab-pane
        v-for="item in reviewTabs"
        :key="item.name"
        :label="item.label"
        :name="item.name"
      >
        <article class="ry-card graph-card">
          <div class="toolbar review-toolbar">
            <el-select
              v-model="reviewQuery.status"
              clearable
              placeholder="全部状态"
              @change="queryReviews"
              ><el-option
                v-for="s in reviewStatuses"
                :key="s"
                :label="statusLabel(s)"
                :value="s"
            /></el-select>
            <el-input
              v-model="reviewQuery.keyword"
              clearable
              placeholder="名称或内容关键词"
              @keyup.enter="queryReviews"
            />
            <el-input-number
              v-model="reviewQuery.runId"
              :min="1"
              controls-position="right"
              placeholder="批次 ID"
            />
            <el-checkbox
              v-model="reviewQuery.lowConfidence"
              @change="queryReviews"
              >仅低可信</el-checkbox
            >
            <el-button type="primary" @click="queryReviews">查询</el-button>
            <el-button type="primary" plain @click="publishSelected"
              >发布选中项</el-button
            >
          </div>
          <el-table
            v-loading="reviewLoading"
            :data="reviews.rows"
            @selection-change="selected = $event"
          >
            <el-table-column type="selection" width="44" />
            <el-table-column prop="id" label="ID" width="72" />
            <el-table-column prop="run_id" label="批次" width="76" />
            <el-table-column
              v-if="reviewType === 'ENTITY'"
              prop="entity_name"
              label="实体名称"
              min-width="150"
            />
            <el-table-column
              v-if="reviewType === 'ENTITY'"
              prop="entity_type"
              label="实体类型"
              width="120"
            />
            <el-table-column
              v-if="reviewType === 'RELATION'"
              prop="subject_name"
              label="主体"
              min-width="130"
            />
            <el-table-column
              v-if="reviewType === 'RELATION'"
              prop="relation_type"
              label="关系"
              width="120"
            />
            <el-table-column
              v-if="reviewType === 'RELATION'"
              prop="object_name"
              label="客体"
              min-width="130"
            />
            <el-table-column
              v-if="reviewType === 'FACT'"
              prop="fact_text"
              label="事实声明"
              min-width="230"
              show-overflow-tooltip
            />
            <el-table-column
              prop="evidence_text"
              label="原文证据"
              min-width="230"
              show-overflow-tooltip
            />
            <el-table-column label="来源" width="145"
              ><template #default="{ row }"
                >V{{ row.document_version_id }} / #{{ row.chunk_id }}</template
              ></el-table-column
            >
            <el-table-column label="置信度" width="92"
              ><template #default="{ row }"
                ><el-tag
                  :type="Number(row.confidence) < 0.6 ? 'warning' : 'success'"
                  >{{ Number(row.confidence || 0).toFixed(2) }}</el-tag
                ></template
              ></el-table-column
            >
            <el-table-column label="状态" width="100"
              ><template #default="{ row }"
                ><el-tag :type="tagType(row.review_status)">{{
                  statusLabel(row.review_status)
                }}</el-tag></template
              ></el-table-column
            >
            <el-table-column label="操作" width="175" fixed="right"
              ><template #default="{ row }"
                ><template v-if="row.review_status === 'PENDING'"
                  ><el-button
                    link
                    type="primary"
                    @click="review(row, 'APPROVE')"
                    >通过</el-button
                  ><el-button
                    v-if="reviewType === 'ENTITY'"
                    link
                    type="warning"
                    @click="review(row, 'MERGE')"
                    >合并</el-button
                  ><el-button link type="danger" @click="review(row, 'REJECT')"
                    >拒绝</el-button
                  ></template
                ></template
              ></el-table-column
            >
          </el-table>
          <PageBar
            :query="reviewQuery"
            :total="reviews.total"
            @change="loadReviews"
          />
        </article>
      </el-tab-pane>

      <el-tab-pane label="图谱追溯" name="trace">
        <GraphTracePanel
          ref="graphTrace"
          :knowledge-base-id="knowledgeBaseId"
          :entity-types="lines(entityText)"
          :relation-types="lines(relationText)"
        />
      </el-tab-pane>

      <el-tab-pane label="历史任务" name="legacy">
        <article class="ry-card graph-card">
          <el-alert
            type="info"
            :closable="false"
            title="patch-16 之前创建的无批次任务仅供查询，不参与新版进度统计。"
          /><el-table :data="legacy.rows" max-height="500"
            ><el-table-column
              prop="id"
              label="任务"
              width="90" /><el-table-column
              prop="chunkId"
              label="分块"
              width="100" /><el-table-column
              prop="schemaVersion"
              label="Schema"
              width="100" /><el-table-column
              prop="status"
              label="状态"
              width="120" /><el-table-column
              prop="errorMessage"
              label="失败原因"
              min-width="260"
              show-overflow-tooltip /></el-table
          ><PageBar
            :query="legacyQuery"
            :total="legacy.total"
            @change="loadLegacy"
          />
        </article>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="runCreateVisible" title="选择图谱抽取范围" width="min(760px, 94vw)">
      <el-form label-position="top">
        <el-form-item label="图谱抽取模型" required>
          <el-select v-model="runScope.modelId" filterable placeholder="请选择已启用的 Ollama 本地模型" style="width:100%">
            <el-option v-for="model in localChatModels" :key="model.id" :label="`${model.modelName}（${model.modelCode}）`" :value="model.id" />
          </el-select>
          <div class="chunk-scope-summary">图谱抽取模型独立于知识库问答模型，不会修改当前对话模型。</div>
        </el-form-item>
        <el-form-item label="抽取范围">
          <el-radio-group v-model="runScope.type" @change="scopeChanged">
            <el-radio-button value="KNOWLEDGE_BASE">整个知识库</el-radio-button>
            <el-radio-button value="DOCUMENT">指定文档</el-radio-button>
            <el-radio-button value="VERSION">当前文档版本</el-radio-button>
            <el-radio-button value="CHUNKS">选择分块</el-radio-button>
            <el-radio-button value="ABNORMAL">仅异常分块</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="['DOCUMENT','VERSION','CHUNKS'].includes(runScope.type)" label="文档">
          <el-select v-model="runScope.documentId" filterable placeholder="请选择文档" style="width:100%" @change="documentChanged">
            <el-option v-for="doc in graphDocuments.rows" :key="doc.id" :label="`${doc.documentName}（V${doc.versionNo || '-'}，${doc.chunkCount || 0} 分块）`" :value="doc.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="runScope.type === 'CHUNKS'" label="选择分块">
          <div class="chunk-scope-summary">已选择 {{ runScope.chunkIds.length }} 个分块，可跨页选择</div>
          <el-table :data="graphChunks.rows" row-key="id" max-height="300" @selection-change="updateChunkSelection">
            <el-table-column type="selection" width="44" /><el-table-column prop="chunkNo" label="序号" width="80" /><el-table-column prop="titlePath" label="标题路径" min-width="220" show-overflow-tooltip /><el-table-column prop="tokenCount" label="Token" width="90" /><el-table-column prop="extractionStatus" label="抽取状态" width="120" />
          </el-table>
          <PageBar :query="graphChunkQuery" :total="graphChunks.total" @change="loadGraphChunks" />
        </el-form-item>
        <el-alert :closable="false" type="info" show-icon title="每个选中分块形成一个独立任务；相邻分块只用于消歧，证据仍必须来自当前分块。" />
      </el-form>
      <template #footer><el-button @click="runCreateVisible=false">取消</el-button><el-button type="primary" :loading="creating" @click="createRun">创建批次</el-button></template>
    </el-dialog>

    <el-drawer
      v-model="runDrawer"
      :title="`抽取批次 #${currentRun?.id || ''}`"
      size="min(860px, 92vw)"
      @closed="stopRunPolling"
    >
      <div v-if="currentRun" class="run-summary ry-card">
        <div>
          <strong>{{ currentRun.completionPercentage }}%</strong
          ><span>完成进度</span>
        </div>
        <div>
          <strong>{{ currentRun.successPercentage }}%</strong
          ><span>成功率</span>
        </div>
        <div>
          <strong>{{ currentRun.warning }}</strong
          ><span>警告</span>
        </div>
        <div>
          <strong>{{ currentRun.failed }}</strong
          ><span>失败</span>
        </div>
      </div>
      <div class="toolbar">
        <el-select
          v-model="jobQuery.status"
          clearable
          placeholder="全部状态"
          @change="queryJobs"
          ><el-option
            v-for="s in jobStatuses"
            :key="s"
            :label="statusLabel(s)"
            :value="s" /></el-select
        ><el-button @click="loadRunDetail">刷新</el-button>
      </div>
      <el-table :data="jobs.rows"
        ><el-table-column prop="id" label="任务" width="80" /><el-table-column
          prop="chunkId"
          label="分块"
          width="90" /><el-table-column label="结果" min-width="180"
          ><template #default="{ row }"
            >实体 {{ row.entityCount }} / 关系 {{ row.relationCount }} / 事实
            {{ row.factCount }} / 丢弃 {{ row.discardedCount }}</template
          ></el-table-column
        ><el-table-column label="状态" width="120"
          ><template #default="{ row }"
            ><el-tag :type="tagType(row.status)">{{
              statusLabel(row.status)
            }}</el-tag></template
          ></el-table-column
        ><el-table-column
          prop="errorMessage"
          label="错误或警告"
          min-width="230"
          show-overflow-tooltip
      /></el-table>
      <PageBar :query="jobQuery" :total="jobs.total" @change="loadJobs" />
    </el-drawer>
    </template>
  </section>
</template>

<script setup>
import {
  computed,
  defineComponent,
  h,
  onBeforeUnmount,
  reactive,
  ref,
} from "vue";
import { ElMessage, ElMessageBox, ElPagination } from "element-plus";
import { Refresh } from "@element-plus/icons-vue";
import {
  getKnowledgeGraphSchema,
  saveKnowledgeGraphSchema,
  createKnowledgeGraphRun,
  listKnowledgeGraphRuns,
  getKnowledgeGraphRun,
  listKnowledgeGraphRunJobs,
  retryKnowledgeGraphRun,
  cancelKnowledgeGraphRun,
  deleteFailedKnowledgeGraphRun,
  listLegacyKnowledgeGraphJobs,
  listKnowledgeGraphReviews,
  reviewKnowledgeGraphItem,
  publishKnowledgeGraphItems,
  initializeKnowledgeGraph,
  aggregateKnowledgeGraphRun,
  listKnowledgeGraphDocuments,
  listKnowledgeGraphDocumentChunks,
  listModels,
  listModelProviders,
} from "@/api/ai";
import GraphTracePanel from "./GraphTracePanel.vue";
import GraphCandidateReviewPanel from "./GraphCandidateReviewPanel.vue";
import GraphFileCenter from "./GraphFileCenter.vue";
import GraphExceptionPanel from "./GraphExceptionPanel.vue";
import GraphNodeManager from "./GraphNodeManager.vue";
const props = defineProps({
  knowledgeBaseId: { type: Number, required: true },
});
const mode = ref("simple"), simpleTab = ref("files"), exceptionDocumentId = ref(null),
  fileCenter = ref(null), exceptionPanel = ref(null), nodeManager = ref(null), simpleGraphTrace = ref(null),
  tab = ref("schema"),
  loading = ref(false),
  saving = ref(false),
  creating = ref(false),
  reviewLoading = ref(false),
  runDrawer = ref(false),
  runCreateVisible = ref(false),
  selected = ref([]),
  graphTrace = ref(null),
  candidateReview = ref(null);
const schema = reactive({ schemaVersion: "v1", status: "DRAFT" }),
  entityText = ref("PERSON\nORGANIZATION\nPRODUCT\nCONCEPT\nLOCATION\nEVENT"),
  relationText = ref(
    "RELATED_TO\nBELONGS_TO\nUSES\nPRODUCES\nLOCATED_IN\nPART_OF",
  );
const runs = reactive({ rows: [], total: 0 }),
  jobs = reactive({ rows: [], total: 0 }),
  reviews = reactive({ rows: [], total: 0 }),
  legacy = reactive({ rows: [], total: 0 }),
  graphDocuments = reactive({ rows: [], total: 0 }),
  graphChunks = reactive({ rows: [], total: 0 });
const runScope = reactive({ type: "KNOWLEDGE_BASE", documentId: null, chunkIds: [], modelId: null });
const localChatModels = ref([]);
const currentRun = ref(null);
const runQuery = reactive({ status: "", pageNum: 1, pageSize: 10 }),
  graphChunkQuery = reactive({ pageNum: 1, pageSize: 10 }),
  jobQuery = reactive({ status: "", pageNum: 1, pageSize: 10 }),
  legacyQuery = reactive({ status: "", pageNum: 1, pageSize: 10 }),
  reviewQuery = reactive({
    status: "PENDING",
    runId: null,
    keyword: "",
    lowConfidence: false,
    pageNum: 1,
    pageSize: 10,
  });
const runStatuses = [
    "PENDING",
    "RUNNING",
    "SUCCESS",
    "PARTIAL_FAILED",
    "CANCELLED",
  ],
  jobStatuses = [
    "PENDING",
    "RUNNING",
    "RETRY_WAIT",
    "SUCCESS",
    "SUCCESS_WITH_WARNINGS",
    "FAILED",
    "CANCELLED",
  ],
  reviewStatuses = ["PENDING", "APPROVED", "REJECTED", "MERGED", "EXPIRED"];
const reviewTabs = [
    { name: "entity", label: "实体审核" },
    { name: "relation", label: "关系审核" },
    { name: "fact", label: "事实审核" },
  ],
  reviewType = computed(
    () =>
      ({ entity: "ENTITY", relation: "RELATION", fact: "FACT" })[tab.value] ||
      "ENTITY",
  );
let runTimer;
const lines = (v) => [
  ...new Set(
    v
      .split(/[\n,]/)
      .map((x) => x.trim().toUpperCase())
      .filter(Boolean),
  ),
];
const entityLabel = (c) =>
  ({
    PERSON: "人员",
    ORGANIZATION: "组织机构",
    PRODUCT: "产品",
    CONCEPT: "概念",
    LOCATION: "地点",
    EVENT: "事件",
  })[c] || "自定义实体";
const relationLabel = (c) =>
  ({
    RELATED_TO: "相关",
    BELONGS_TO: "属于",
    USES: "使用",
    PRODUCES: "产生/生产",
    LOCATED_IN: "位于",
    PART_OF: "组成部分",
  })[c] || "自定义关系";
const statusLabel = (v) =>
  ({
    PENDING: "待处理",
    RUNNING: "处理中",
    RETRY_WAIT: "等待重试",
    SUCCESS: "成功",
    SUCCESS_WITH_WARNINGS: "成功但有警告",
    FAILED: "失败",
    PARTIAL_FAILED: "部分失败",
    CANCELLED: "已取消",
    APPROVED: "已通过",
    REJECTED: "已拒绝",
    MERGED: "已合并",
    EXPIRED: "已失效",
    DRAFT: "草稿",
    ACTIVE: "启用",
  })[v] || v;
const tagType = (v) =>
  ({
    SUCCESS: "success",
    APPROVED: "success",
    ACTIVE: "success",
    RUNNING: "warning",
    RETRY_WAIT: "warning",
    SUCCESS_WITH_WARNINGS: "warning",
    PARTIAL_FAILED: "danger",
    FAILED: "danger",
    REJECTED: "danger",
    EXPIRED: "info",
    CANCELLED: "info",
  })[v] || "info";
const PageBar = defineComponent({
  props: ["query", "total"],
  emits: ["change"],
  setup(p, { emit }) {
    return () =>
      h("div", { class: "table-pagination" }, [
        h(ElPagination, {
          currentPage: p.query.pageNum,
          pageSize: p.query.pageSize,
          pageSizes: [10, 20, 50],
          total: p.total,
          layout: "total, sizes, prev, pager, next, jumper",
          "onUpdate:currentPage": (v) => (p.query.pageNum = v),
          "onUpdate:pageSize": (v) => {
            p.query.pageSize = v;
            p.query.pageNum = 1;
          },
          onChange: () => emit("change"),
        }),
      ]);
  },
});
async function loadSchema() {
  const d = await getKnowledgeGraphSchema(props.knowledgeBaseId);
  Object.assign(schema, d);
  entityText.value = (d.entityTypes || []).join("\n");
  relationText.value = (d.relationTypes || []).join("\n");
}
async function saveSchema() {
  saving.value = true;
  try {
    Object.assign(
      schema,
      await saveKnowledgeGraphSchema(props.knowledgeBaseId, {
        ...schema,
        entityTypes: lines(entityText.value),
        relationTypes: lines(relationText.value),
      }),
    );
    ElMessage.success("图谱 Schema 已保存");
  } catch (e) {
    ElMessage.error(e?.message || "保存失败");
  } finally {
    saving.value = false;
  }
}
async function initializeGraph() {
  await ElMessageBox.confirm(
    "将显式创建 Neo4j 图谱约束和索引，不会清理现有数据。是否继续？",
    "初始化 Neo4j",
    { type: "warning" },
  );
  await initializeKnowledgeGraph(props.knowledgeBaseId);
  ElMessage.success("Neo4j 图谱结构初始化完成");
}
async function loadRuns() {
  loading.value = true;
  try {
    Object.assign(
      runs,
      await listKnowledgeGraphRuns(props.knowledgeBaseId, runQuery),
    );
  } finally {
    loading.value = false;
  }
}
function queryRuns() {
  runQuery.pageNum = 1;
  loadRuns();
}
async function openRunCreate() {
  Object.assign(runScope, { type: "KNOWLEDGE_BASE", documentId: null, chunkIds: [], modelId: null });
  const [documents, modelPage, providers] = await Promise.all([
    listKnowledgeGraphDocuments(props.knowledgeBaseId, { pageNum: 1, pageSize: 50 }),
    listModels({ pageNum: 1, pageSize: 1000, modelType: "CHAT" }),
    listModelProviders(),
  ]);
  Object.assign(graphDocuments, documents);
  const providerRows = providers?.data || providers || [];
  const ollamaProviderIds = new Set(
    providerRows
      .filter((provider) => provider.providerType === "OLLAMA" && provider.status === "0")
      .map((provider) => provider.id),
  );
  localChatModels.value = (modelPage.rows || []).filter(
    (model) => model.modelType === "CHAT" && model.status === "0" && ollamaProviderIds.has(model.providerId),
  );
  if (localChatModels.value.length === 1) runScope.modelId = localChatModels.value[0].id;
  runCreateVisible.value = true;
}
function scopeChanged() { runScope.chunkIds = []; if (!['DOCUMENT','VERSION','CHUNKS'].includes(runScope.type)) runScope.documentId = null; }
async function documentChanged() {
  runScope.chunkIds = [];
  graphChunkQuery.pageNum = 1;
  if (runScope.type === 'CHUNKS' && runScope.documentId) await loadGraphChunks();
}
async function loadGraphChunks() { Object.assign(graphChunks, await listKnowledgeGraphDocumentChunks(props.knowledgeBaseId, runScope.documentId, graphChunkQuery)); }
function updateChunkSelection(rows) { const currentIds = new Set(graphChunks.rows.map((x) => x.id)); const selectedIds = new Set(rows.map((x) => x.id)); runScope.chunkIds = [...new Set([...runScope.chunkIds.filter((id) => !currentIds.has(id)), ...selectedIds])]; }
async function createRun() {
  try {
    if (!runScope.modelId) return ElMessage.warning('请选择图谱抽取模型');
    if (['DOCUMENT','VERSION','CHUNKS'].includes(runScope.type) && !runScope.documentId) return ElMessage.warning('请选择文档');
    if (runScope.type === 'CHUNKS' && !runScope.chunkIds.length) return ElMessage.warning('请选择至少一个分块');
    creating.value = true;
    const doc = graphDocuments.rows.find((x) => x.id === runScope.documentId);
    const run = await createKnowledgeGraphRun(props.knowledgeBaseId, {
      documentId: runScope.documentId,
      documentVersionId: runScope.type === 'VERSION' ? doc?.currentVersionId : undefined,
      chunkIds: runScope.type === 'CHUNKS' ? runScope.chunkIds : [],
      retryAbnormalOnly: runScope.type === 'ABNORMAL',
      modelId: runScope.modelId,
    });
    ElMessage.success(`批次 #${run.id} 已创建`);
    runCreateVisible.value = false;
    await loadRuns();
    openRun(run);
  } catch (e) {
    if (e !== "cancel" && e !== "close")
      ElMessage.error(e?.message || "创建批次失败");
  } finally {
    creating.value = false;
  }
}
function goSchema() {
  tab.value = "schema";
  ElMessage.warning("请先启用并保存图谱 Schema");
}
async function openRun(row) {
  currentRun.value = row;
  runDrawer.value = true;
  jobQuery.pageNum = 1;
  await loadRunDetail();
}
async function loadRunDetail() {
  if (!currentRun.value) return;
  currentRun.value = await getKnowledgeGraphRun(
    props.knowledgeBaseId,
    currentRun.value.id,
  );
  await loadJobs();
  manageRunPolling();
}
async function loadJobs() {
  Object.assign(
    jobs,
    await listKnowledgeGraphRunJobs(
      props.knowledgeBaseId,
      currentRun.value.id,
      jobQuery,
    ),
  );
}
function queryJobs() {
  jobQuery.pageNum = 1;
  loadJobs();
}
function manageRunPolling() {
  stopRunPolling();
  if (
    currentRun.value &&
    ["PENDING", "RUNNING"].includes(currentRun.value.status) &&
    !document.hidden
  )
    runTimer = setInterval(loadRunDetail, 5000);
}
function stopRunPolling() {
  clearInterval(runTimer);
}
async function retryRun(row) {
  await retryKnowledgeGraphRun(props.knowledgeBaseId, row.id);
  ElMessage.success("异常任务已重新进入队列");
  await loadRuns();
  if (currentRun.value?.id === row.id) loadRunDetail();
}
async function aggregateRun(row) {
  const result = await aggregateKnowledgeGraphRun(props.knowledgeBaseId, row.id);
  ElMessage.success(
    `聚合完成：${result.rawCount} 条原始候选形成 ${result.groupCount} 个候选组`,
  );
  tab.value = "candidates";
  candidateReview.value?.refresh();
  loadRuns();
}
async function cancelRun(row) {
  await ElMessageBox.confirm(
    "将取消等待任务，并停止接收正在执行任务的抽取结果。已完成的任务不会被删除，是否继续？",
    "停止抽取任务",
    { type: "warning" },
  );
  await cancelKnowledgeGraphRun(props.knowledgeBaseId, row.id);
  ElMessage.success("已提交停止任务请求");
  loadRuns();
}
async function deleteFailedRun(row) {
  const empty = Number(row.total || 0) === 0;
  const cancelled = row.status === "CANCELLED";
  await ElMessageBox.confirm(
    empty
      ? "该批次没有生成任何分块任务，将删除这条异常遗留记录。此操作不可恢复，是否继续？"
      : cancelled
        ? "将删除该已取消批次及其已取消任务记录。若存在已审核或已发布数据，系统会拒绝删除。此操作不可恢复，是否继续？"
        : "将删除该失败批次、分块任务、原始候选和未审核候选组。此操作不可恢复，是否继续？",
    empty ? "删除空批次" : cancelled ? "删除已取消批次" : "删除失败批次",
    { type: "warning", confirmButtonText: "确认删除" },
  );
  await deleteFailedKnowledgeGraphRun(props.knowledgeBaseId, row.id);
  if (currentRun.value?.id === row.id) {
    runDrawer.value = false;
    currentRun.value = null;
    stopRunPolling();
  }
  ElMessage.success(empty ? "空抽取批次已删除" : cancelled ? "已取消批次已删除" : "失败抽取批次已删除");
  await loadRuns();
}
async function loadReviews() {
  reviewLoading.value = true;
  try {
    Object.assign(
      reviews,
      await listKnowledgeGraphReviews(props.knowledgeBaseId, reviewType.value, {
        status: reviewQuery.status,
        runId: reviewQuery.runId,
        keyword: reviewQuery.keyword,
        maxConfidence: reviewQuery.lowConfidence ? 0.5999 : undefined,
        pageNum: reviewQuery.pageNum,
        pageSize: reviewQuery.pageSize,
      }),
    );
  } finally {
    reviewLoading.value = false;
  }
}
function queryReviews() {
  reviewQuery.pageNum = 1;
  loadReviews();
}
async function review(row, action) {
  let note = "",
    mergeTargetId = null;
  if (action === "MERGE") {
    const r = await ElMessageBox.prompt(
      "请输入要合并到的实体审核记录 ID",
      "合并实体",
      { inputPattern: /^\d+$/, inputErrorMessage: "请输入有效数字" },
    );
    mergeTargetId = Number(r.value);
  }
  if (action === "APPROVE" && Number(row.confidence) < 0.6) {
    const r = await ElMessageBox.prompt(
      "该结果置信度低于 0.6，请填写人工确认说明",
      "低可信结果审核",
      { inputPattern: /.+/, inputErrorMessage: "审核说明不能为空" },
    );
    note = r.value;
  }
  await reviewKnowledgeGraphItem(
    props.knowledgeBaseId,
    reviewType.value,
    row.id,
    { action, note, mergeTargetId },
  );
  ElMessage.success(
    action === "APPROVE"
      ? "已审核通过"
      : action === "MERGE"
        ? "已合并"
        : "已拒绝",
  );
  loadReviews();
}
async function publishSelected() {
  const ids = selected.value
    .filter((x) => x.review_status === "APPROVED")
    .map((x) => x.id);
  if (!ids.length) return ElMessage.warning("请选择已审核通过的数据");
  const result = await publishKnowledgeGraphItems(
    props.knowledgeBaseId,
    reviewType.value,
    ids,
  );
  ElMessage.success(
    `发布完成：成功 ${result.data?.success || 0}，跳过 ${result.data?.skipped || 0}，失败 ${result.data?.failed || 0}`,
  );
  loadReviews();
}
async function loadLegacy() {
  Object.assign(
    legacy,
    await listLegacyKnowledgeGraphJobs(props.knowledgeBaseId, legacyQuery),
  );
}
function refreshCurrent() {
  if (mode.value === "simple") {
    if (simpleTab.value === "files") fileCenter.value?.refresh();
    else if (simpleTab.value === "exceptions") exceptionPanel.value?.refresh();
    else if (simpleTab.value === "nodes") nodeManager.value?.refresh();
    else simpleGraphTrace.value?.refresh();
    return;
  }
  if (tab.value === "schema") loadSchema();
  else if (tab.value === "runs") loadRuns();
  else if (tab.value === "legacy") loadLegacy();
  else if (tab.value === "trace") graphTrace.value?.refresh();
  else if (tab.value === "candidates") candidateReview.value?.refresh();
  else loadReviews();
}
function showFileExceptions(documentId) { exceptionDocumentId.value = documentId; simpleTab.value = "exceptions"; }
function showFileGraph() { simpleTab.value = "trace"; }
function showNodeGraph() { simpleTab.value = "trace"; }
document.addEventListener("visibilitychange", () => {
  if (document.hidden) stopRunPolling();
  else manageRunPolling();
});
loadSchema();
onBeforeUnmount(stopRunPolling);
</script>

<style scoped>
.graph-panel {
  display: grid;
  gap: 16px;
}
.mode-switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
}
.mode-switch > div { display: grid; gap: 4px; }
.mode-switch span { color: var(--ry-muted-foreground); font-size: 13px; }
.section-heading,
.toolbar,
.card-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}
.section-heading h3 {
  margin: 0;
}
.section-heading p {
  margin: 4px 0 0;
  color: var(--ry-muted-foreground);
}
.graph-card {
  padding: 20px;
}
.schema-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 20px;
}
.span-2 {
  grid-column: span 2;
}
.code-help {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  width: 100%;
  margin-top: 10px;
}
.toolbar {
  justify-content: flex-start;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.toolbar .el-select {
  width: 180px;
}
.review-toolbar .el-input {
  width: 220px;
}
.card-actions {
  justify-content: flex-end;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.card-actions span {
  margin-right: auto;
  color: var(--ry-muted-foreground);
  font-size: 13px;
}
.table-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}
.run-summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
  padding: 14px;
}
.run-summary div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.run-summary strong {
  font-size: 22px;
}
.run-summary span {
  color: var(--ry-muted-foreground);
}
.success {
  color: var(--el-color-success);
}
.warning {
  color: var(--el-color-warning);
}
.danger {
  color: var(--el-color-danger);
}
@media (max-width: 760px) {
  .mode-switch { align-items: stretch; flex-direction: column; }
  .schema-form {
    grid-template-columns: 1fr;
  }
  .span-2 {
    grid-column: auto;
  }
  .graph-card {
    padding: 14px;
  }
  .run-summary {
    grid-template-columns: repeat(2, 1fr);
  }
  .table-pagination {
    overflow: auto;
    justify-content: flex-start;
  }
}
</style>
