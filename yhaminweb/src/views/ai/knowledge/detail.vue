<template>
  <div class="knowledge-workbench">
    <header class="workbench-header ry-card">
      <div class="header-main">
        <el-button link :icon="ArrowLeft" @click="backToList"
          >返回知识库</el-button
        >
        <div class="title-row">
          <h2>{{ form.knowledgeName || "知识库工作台" }}</h2>
          <el-tag
            :type="form.status === '0' ? 'success' : 'info'"
            effect="light"
          >
            {{ form.status === "0" ? "已启用" : "已停用" }}
          </el-tag>
        </div>
        <p>{{ form.description || "管理知识库配置、文档和检索能力" }}</p>
      </div>
      <el-button
        :loading="loading"
        :icon="Refresh"
        circle
        title="刷新工作台"
        @click="refreshWorkbench"
      />
    </header>

    <div class="mobile-nav ry-card">
      <span>当前功能</span>
      <el-select :model-value="activeSection" @change="changeSection">
        <el-option
          v-for="item in navigation"
          :key="item.name"
          :label="item.label"
          :value="item.name"
        />
      </el-select>
    </div>

    <div class="workbench-layout">
      <aside class="workbench-sidebar ry-card">
        <div class="nav-caption">知识库工作台</div>
        <button
          v-for="item in navigation"
          :key="item.name"
          type="button"
          class="nav-item"
          :class="{ active: activeSection === item.name }"
          @click="changeSection(item.name)"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </button>
      </aside>

      <main class="workbench-main">
        <section
          v-if="activeSection === 'overview'"
          v-loading="loading"
          class="overview-page"
        >
          <div class="section-heading">
            <div>
              <h3>工作台概览</h3>
              <p>快速了解配置完整度和文档处理情况。</p>
            </div>
          </div>
          <div class="stat-grid">
            <article class="ry-card stat-card">
              <span>配置完整度</span><strong>{{ completionRate }}%</strong>
              <el-progress :percentage="completionRate" :show-text="false" />
            </article>
            <article class="ry-card stat-card">
              <span>文档总数</span><strong>{{ documentStats.total }}</strong
              ><small>当前知识库</small>
            </article>
            <article class="ry-card stat-card success">
              <span>处理成功</span><strong>{{ documentStats.success }}</strong
              ><small>可参与后续检索</small>
            </article>
            <article class="ry-card stat-card warning">
              <span>处理中</span><strong>{{ documentStats.processing }}</strong
              ><small>包含待处理和运行中</small>
            </article>
            <article class="ry-card stat-card danger">
              <span>处理失败</span><strong>{{ documentStats.failed }}</strong
              ><small>建议及时重试</small>
            </article>
          </div>
          <div class="overview-grid">
            <article class="ry-card overview-card">
              <div class="card-title">
                <h4>关键配置</h4>
                <el-button link type="primary" @click="changeSection('basic')"
                  >编辑配置</el-button
                >
              </div>
              <dl class="config-list">
                <div>
                  <dt>当前向量模型</dt>
                  <dd>
                    {{
                      modelName(
                        form.activeEmbeddingModelId || form.embeddingModelId,
                      )
                    }}
                    · {{ form.activeEmbeddingDimension || "-" }} 维
                  </dd>
                </div>
                <div v-if="form.buildingEmbeddingModelId">
                  <dt>待生效模型</dt>
                  <dd>
                    {{ modelName(form.buildingEmbeddingModelId) }} ·
                    {{ form.buildingEmbeddingDimension || "-" }} 维
                  </dd>
                </div>
                <div>
                  <dt>重排模型</dt>
                  <dd>{{ modelName(form.rerankModelId) }}</dd>
                </div>
                <div>
                  <dt>对话模型</dt>
                  <dd>{{ modelName(form.chatModelId) }}</dd>
                </div>
                <div>
                  <dt>分块参数</dt>
                  <dd>
                    {{ form.chunkSize }} / 重叠 {{ form.chunkOverlap }} Token
                  </dd>
                </div>
                <div>
                  <dt>召回参数</dt>
                  <dd>
                    BM25 {{ form.bm25TopK }} · KNN {{ form.knnTopK }} · 重排
                    {{ form.rerankTopK }}
                  </dd>
                </div>
                <div>
                  <dt>图谱增强</dt>
                  <dd>
                    {{ form.graphRetrievalEnabled ? "已开启" : "未开启" }}
                  </dd>
                </div>
              </dl>
            </article>
            <article class="ry-card overview-card">
              <div class="card-title"><h4>快捷操作</h4></div>
              <div class="quick-actions">
                <button type="button" @click="changeSection('basic')">
                  <el-icon><Setting /></el-icon
                  ><span><b>完善配置</b><small>设置模型与检索参数</small></span>
                </button>
                <button type="button" @click="openDocuments()">
                  <el-icon><Upload /></el-icon
                  ><span
                    ><b>上传文档</b><small>进入文档管理并选择文件</small></span
                  >
                </button>
                <button
                  type="button"
                  :disabled="!documentStats.failed"
                  @click="openDocuments('FAILED')"
                >
                  <el-icon><Warning /></el-icon
                  ><span
                    ><b>查看失败文档</b
                    ><small>{{
                      documentStats.failed
                        ? `${documentStats.failed} 个任务需要处理`
                        : "暂无失败文档"
                    }}</small></span
                  >
                </button>
              </div>
            </article>
          </div>
        </section>

        <el-form
          v-else-if="activeSection === 'basic'"
          ref="formRef"
          v-loading="loading"
          :model="form"
          :rules="rules"
          label-position="top"
          class="basic-page"
        >
          <div class="section-heading">
            <div>
              <h3>基本配置</h3>
              <p>配置知识库基础信息、模型及检索策略。</p>
            </div>
            <el-tag :type="dirty ? 'warning' : 'success'" effect="light">{{
              dirty ? "有未保存修改" : "配置已保存"
            }}</el-tag>
          </div>
          <article class="ry-card form-card">
            <h4>基础信息</h4>
            <p class="card-description">用于识别和说明知识库用途。</p>
            <el-row :gutter="20">
              <el-col :xs="24" :md="16"
                ><el-form-item label="知识库名称" prop="knowledgeName"
                  ><el-input
                    v-model.trim="form.knowledgeName"
                    maxlength="100"
                    show-word-limit /></el-form-item
              ></el-col>
              <el-col :xs="24" :md="8"
                ><el-form-item label="状态"
                  ><el-radio-group v-model="form.status"
                    ><el-radio-button value="0">启用</el-radio-button
                    ><el-radio-button value="1"
                      >停用</el-radio-button
                    ></el-radio-group
                  ></el-form-item
                ></el-col
              >
              <el-col :span="24"
                ><el-form-item label="描述" prop="description"
                  ><el-input
                    v-model.trim="form.description"
                    type="textarea"
                    :rows="3"
                    maxlength="500"
                    show-word-limit /></el-form-item
              ></el-col>
            </el-row>
          </article>
          <article class="ry-card form-card">
            <h4>模型绑定</h4>
            <p class="card-description">
              向量模型负责文档索引，对话模型负责生成回答，重排模型为可选项。
            </p>
            <el-alert
              type="info"
              :closable="false"
              title="更换向量模型后需要创建新的索引代次并重新索引文档。"
            />
            <div class="model-transition">
              <span
                >当前生效：<strong>{{
                  modelName(
                    form.activeEmbeddingModelId || form.embeddingModelId,
                  )
                }}</strong
                >（{{ form.activeEmbeddingDimension || "-" }} 维）</span
              >
              <span v-if="form.buildingEmbeddingModelId"
                >待生效：<strong>{{
                  modelName(form.buildingEmbeddingModelId)
                }}</strong
                >（{{ form.buildingEmbeddingDimension || "-" }} 维）</span
              >
            </div>
            <el-row :gutter="20" class="form-grid">
              <el-col :xs="24" :lg="8"
                ><el-form-item label="向量模型" prop="embeddingModelId"
                  ><el-select v-model="form.embeddingModelId" filterable
                    ><el-option
                      v-for="m in embeddingModels"
                      :key="m.id"
                      :label="m.modelName"
                      :value="m.id" /></el-select
                  ><span class="field-help"
                    >仅显示已启用的向量模型</span
                  ></el-form-item
                ></el-col
              >
              <el-col :xs="24" :lg="8"
                ><el-form-item label="重排模型"
                  ><el-select
                    v-model="form.rerankModelId"
                    clearable
                    filterable
                    placeholder="可选"
                    ><el-option
                      v-for="m in rerankModels"
                      :key="m.id"
                      :label="m.modelName"
                      :value="m.id" /></el-select
                  ><span class="field-help"
                    >不选择时跳过重排环节</span
                  ></el-form-item
                ></el-col
              >
              <el-col :xs="24" :lg="8"
                ><el-form-item label="对话模型" prop="chatModelId"
                  ><el-select v-model="form.chatModelId" filterable
                    ><el-option
                      v-for="m in chatModels"
                      :key="m.id"
                      :label="m.modelName"
                      :value="m.id" /></el-select
                  ><span class="field-help"
                    >仅显示已启用的 LLM 模型</span
                  ></el-form-item
                ></el-col
              >
            </el-row>
          </article>
          <article class="ry-card form-card">
            <h4>分块与召回</h4>
            <p class="card-description">控制文本分块大小和混合检索候选数量。</p>
            <el-alert
              class="chunk-strategy-alert"
              type="info"
              :closable="false"
              :title="chunkStrategyDescription"
            />
            <el-row :gutter="20">
              <el-col :span="24"
                ><el-form-item label="分块策略" prop="chunkStrategy"
                  ><el-select v-model="form.chunkStrategy"
                    ><el-option
                      v-for="item in chunkStrategies"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value" /></el-select></el-form-item
              ></el-col>
              <el-col :xs="24" :md="12" :lg="8"
                ><el-form-item label="分块大小（Token）" prop="chunkSize"
                  ><el-input-number
                    v-model="form.chunkSize"
                    :min="200"
                    :max="4000"
                    controls-position="right" /></el-form-item
              ></el-col>
              <el-col :xs="24" :md="12" :lg="8"
                ><el-form-item label="最小分块（Token）"
                  ><el-input-number
                    v-model="form.chunkStrategyConfig.minTokens"
                    :min="0"
                    :max="3999"
                    controls-position="right" /></el-form-item
              ></el-col>
              <el-col :xs="24" :md="12" :lg="8"
                ><el-form-item label="重叠长度（Token）" prop="chunkOverlap"
                  ><el-input-number
                    v-model="form.chunkOverlap"
                    :min="0"
                    :max="3999"
                    controls-position="right" /></el-form-item
              ></el-col>
              <template v-if="form.chunkStrategy === 'REGEX'">
                <el-col :span="24"
                  ><el-form-item label="正则表达式"
                    ><el-input
                      v-model="form.chunkStrategyConfig.regex"
                      type="textarea"
                      :rows="2"
                      maxlength="500"
                      show-word-limit
                      placeholder="例如：^(?:第[一二三四五六七八九十]+章|[一二三四五六七八九十]+、)" /></el-form-item
                ></el-col>
                <el-col :xs="24" :md="12" :lg="8"
                  ><el-form-item label="忽略大小写"
                    ><el-switch
                      v-model="
                        form.chunkStrategyConfig.regexIgnoreCase
                      " /></el-form-item
                ></el-col>
              </template>
              <el-col
                v-if="form.chunkStrategy === 'CUSTOM_SEPARATOR'"
                :span="24"
                ><el-form-item label="自定义分隔符"
                  ><el-input
                    v-model="separatorsText"
                    type="textarea"
                    :rows="3"
                    placeholder="每行一个分隔符，例如：\\n\\n 或 ---"
                  /><span class="field-help"
                    >每行一个分隔符；支持 \\n、\\r、\\t 转义。</span
                  ></el-form-item
                ></el-col
              >
              <el-col
                v-if="
                  ['REGEX', 'CUSTOM_SEPARATOR', 'PAGE'].includes(
                    form.chunkStrategy,
                  )
                "
                :xs="24"
                :md="12"
                :lg="8"
                ><el-form-item label="分隔内容位置"
                  ><el-select
                    v-model="form.chunkStrategyConfig.delimiterPosition"
                    ><el-option label="保留在后一块" value="NEXT" /><el-option
                      label="保留在前一块"
                      value="PREVIOUS" /><el-option
                      label="丢弃分隔内容"
                      value="DROP" /></el-select></el-form-item
              ></el-col>
              <el-col
                v-if="
                  ['REGEX', 'CUSTOM_SEPARATOR', 'PAGE'].includes(
                    form.chunkStrategy,
                  )
                "
                :xs="24"
                :md="12"
                :lg="8"
                ><el-form-item label="无匹配时回退"
                  ><el-switch
                    v-model="form.chunkStrategyConfig.fallbackToSmart"
                    active-text="智能 Markdown" /></el-form-item
              ></el-col>
              <el-col :span="24"
                ><div class="chunk-preview-action">
                  <el-button type="primary" plain @click="openChunkPreview"
                    >分块预览</el-button
                  ><span>使用当前未保存配置预览，不会写入分块或索引。</span>
                </div></el-col
              >
              <el-col :xs="24" :md="12" :lg="8"
                ><el-form-item label="活动索引代次"
                  ><el-input
                    :model-value="`G${form.activeIndexGeneration || form.indexGeneration || 1}`"
                    disabled /></el-form-item
              ></el-col>
              <el-col
                v-if="form.buildingIndexGeneration"
                :xs="24"
                :md="12"
                :lg="8"
                ><el-form-item label="构建索引代次"
                  ><el-input
                    :model-value="`G${form.buildingIndexGeneration} · ${indexBuildStatusText}`"
                    disabled /></el-form-item
              ></el-col>
              <el-col :xs="24" :md="12" :lg="8"
                ><el-form-item label="BM25 召回数" prop="bm25TopK"
                  ><el-input-number
                    v-model="form.bm25TopK"
                    :min="1"
                    :max="100"
                    controls-position="right" /></el-form-item
              ></el-col>
              <el-col :xs="24" :md="12" :lg="8"
                ><el-form-item label="向量召回数" prop="knnTopK"
                  ><el-input-number
                    v-model="form.knnTopK"
                    :min="1"
                    :max="100"
                    controls-position="right" /></el-form-item
              ></el-col>
              <el-col :xs="24" :md="12" :lg="8"
                ><el-form-item label="重排候选数" prop="rerankTopK"
                  ><el-input-number
                    v-model="form.rerankTopK"
                    :min="1"
                    :max="100"
                    controls-position="right" /></el-form-item
              ></el-col>
            </el-row>
          </article>
          <article class="ry-card form-card">
            <h4>知识图谱增强</h4>
            <p class="card-description">
              图谱能力依赖已审核并发布到 Neo4j 的知识数据。
            </p>
            <div class="switch-list">
              <div>
                <span
                  ><b>图谱检索</b
                  ><small
                    >在 Elasticsearch 标准召回之外扩展实体关系证据</small
                  ></span
                ><el-switch v-model="form.graphRetrievalEnabled" />
              </div>
              <div>
                <span
                  ><b>Text2Cypher</b
                  ><small>允许使用受控的只读图查询</small></span
                ><el-switch
                  v-model="form.text2CypherEnabled"
                  :disabled="!form.graphRetrievalEnabled"
                />
              </div>
              <div>
                <span
                  ><b>社区摘要</b
                  ><small>使用当前有效的图谱社区摘要补充上下文</small></span
                ><el-switch
                  v-model="form.communitySummaryEnabled"
                  :disabled="!form.graphRetrievalEnabled"
                />
              </div>
            </div>
          </article>
          <article class="ry-card form-card">
            <h4>备注</h4>
            <el-input
              v-model.trim="form.remark"
              type="textarea"
              :rows="2"
              maxlength="500"
              show-word-limit
              placeholder="可填写维护说明"
            />
          </article>
          <div class="save-bar ry-card">
            <div>
              <el-icon :class="{ changed: dirty }"
                ><CircleCheck v-if="!dirty" /><Warning v-else /></el-icon
              ><span>{{
                dirty ? "配置已修改，请保存后再离开" : "所有配置均已保存"
              }}</span>
            </div>
            <el-button
              type="primary"
              :loading="saving"
              :disabled="!dirty"
              @click="save"
              >保存配置</el-button
            >
          </div>
        </el-form>

        <DocumentPanel
          v-else-if="activeSection === 'documents'"
          ref="documentPanelRef"
          :knowledge-base-id="form.id"
          @changed="handleDocumentsChanged"
        />

        <ChunkIndexPanel
          v-else-if="activeSection === 'chunks'"
          :knowledge-base-id="form.id"
          :documents="documents"
        />

        <PermissionPanel
          v-else-if="activeSection === 'permissions'"
          :knowledge-base-id="form.id"
        />

        <GraphPanel
          v-else-if="activeSection === 'graph'"
          :knowledge-base-id="form.id"
        />

        <RetrievalTestPanel
          v-else-if="activeSection === 'retrieval'"
          :knowledge-base-id="form.id"
          :defaults="form"
        />

        <RagChatPanel
          v-else-if="activeSection === 'chat'"
          :knowledge-base-id="form.id"
        />

        <QueryAuditPanel
          v-else-if="activeSection === 'audit'"
          :knowledge-base-id="form.id"
        />

		<section v-else class="pending-panel ry-card">
          <div class="pending-icon">
            <el-icon><component :is="currentNavigation.icon" /></el-icon>
          </div>
          <h3>{{ currentNavigation.label }}</h3>
          <p>{{ currentNavigation.description }}</p>
          <el-tag type="info" effect="plain">功能建设中</el-tag>
        </section>
      </main>
    </div>
    <el-dialog
      v-model="chunkPreviewDialog"
      title="分块策略预览"
      width="min(1280px, 96vw)"
      destroy-on-close
    >
      <div v-if="!previewResult" class="preview-source-toolbar">
        <el-select
          v-model="previewDocumentId"
          clearable
          filterable
          placeholder="选择当前知识库文档"
          @change="loadPreviewDocument"
          ><el-option
            v-for="item in previewDocuments"
            :key="item.id"
            :label="item.name"
            :value="item.id"
        /></el-select>
        <span>也可以直接在下方粘贴 Markdown 或纯文本。</span>
      </div>
      <el-input
        v-if="!previewResult"
        v-model="previewText"
        type="textarea"
        :rows="10"
        maxlength="500000"
        show-word-limit
        placeholder="请输入或选择需要预览的文档文本"
      />
      <div v-if="previewResult" class="preview-result-heading ry-card">
        <el-button @click="previewResult = undefined">返回分块设置</el-button>
        <div class="preview-result-title">
          <strong>{{ previewSelectedDocument?.name || "手工输入内容" }}</strong>
          <span>{{
            previewSelectedDocument
              ? `当前版本 V${previewSelectedDocument.currentVersionNo || "-"}`
              : "当前输入内容"
          }}</span>
        </div>
        <el-tag effect="plain">{{
          chunkStrategies.find((item) => item.value === form.chunkStrategy)
            ?.label
        }}</el-tag>
      </div>
      <div v-else class="preview-actions">
        <el-button
          type="primary"
          :loading="previewLoading"
          @click="runChunkPreview(false)"
          >生成预览</el-button
        ><span>确认原文后生成双栏分块结果。</span>
      </div>
      <ChunkPreviewWorkbench
        v-if="previewResult"
        :result="previewResult"
        :strategy="form.chunkStrategy"
        :knowledge-base-id="form.id"
        :document-id="previewDocumentId"
        :version-id="previewSelectedDocument?.currentVersionId"
        @optimize="runChunkPreview(true)"
      />
    </el-dialog>
  </div>
</template>

<script setup>
import {
  computed,
  nextTick,
  onMounted,
  onBeforeUnmount,
  reactive,
  ref,
  watch,
} from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  ArrowLeft,
  Refresh,
  Setting,
  Document,
  Files,
  Lock,
  Share,
  Search,
  ChatDotRound,
  Tickets,
  Grid,
  Upload,
  Warning,
  CircleCheck,
} from "@element-plus/icons-vue";
import {
  getKnowledge,
  updateKnowledge,
  listModels,
  getKnowledgeDocumentStatistics,
  previewKnowledgeChunks,
  pageKnowledgeDocuments,
  getKnowledgeParsedArtifact,
} from "@/api/ai";
import DocumentPanel from "./components/DocumentPanel.vue";
import ChunkIndexPanel from "./components/ChunkIndexPanel.vue";
import ChunkPreviewWorkbench from "./components/ChunkPreviewWorkbench.vue";
import RetrievalTestPanel from "./components/RetrievalTestPanel.vue";
import PermissionPanel from "./components/PermissionPanel.vue";
import RagChatPanel from "./components/RagChatPanel.vue";
import GraphPanel from "./components/GraphPanel.vue";
import QueryAuditPanel from "./components/QueryAuditPanel.vue";

const route = useRoute();
const router = useRouter();
const validSections = [
  "overview",
  "basic",
  "documents",
  "chunks",
  "permissions",
  "graph",
  "retrieval",
  "chat",
  "audit",
];
const activeSection = ref(
  validSections.includes(route.query.section)
    ? route.query.section
    : "overview",
);
const loading = ref(false);
const saving = ref(false);
const allowRouteLeave = ref(false);
const formRef = ref();
const documentPanelRef = ref();
const models = ref([]);
const documentStats = ref({
  total: 0,
  success: 0,
  processing: 0,
  failed: 0,
  pending: 0,
});
const chunkPreviewDialog = ref(false);
const previewLoading = ref(false);
const previewText = ref("");
const previewResult = ref();
const previewDocuments = ref([]);
const previewDocumentId = ref();
const previewSelectedDocument = computed(() =>
  previewDocuments.value.find((item) => item.id === previewDocumentId.value),
);
const baseline = ref("");
const form = reactive({
  id: Number(route.params.id),
  knowledgeName: "",
  description: "",
  status: "0",
  remark: "",
  chunkSize: 800,
  chunkOverlap: 120,
  chunkStrategy: "SMART_MARKDOWN",
  chunkStrategyConfig: {
    minTokens: 100,
    regex: "",
    regexIgnoreCase: false,
    delimiterPosition: "NEXT",
    separators: ["\\n\\n", "---"],
    fallbackToSmart: true,
    autoOptimize: false,
  },
  embeddingModelId: undefined,
  rerankModelId: undefined,
  chatModelId: undefined,
  bm25TopK: 30,
  knnTopK: 30,
  rerankTopK: 20,
  graphRetrievalEnabled: false,
  text2CypherEnabled: false,
  communitySummaryEnabled: false,
  indexGeneration: 1,
  activeIndexGeneration: 1,
  buildingIndexGeneration: undefined,
  indexBuildStatus: "IDLE",
  lastIndexSwitchTime: undefined,
  lastIndexError: "",
  activeEmbeddingModelId: undefined,
  buildingEmbeddingModelId: undefined,
  activeEmbeddingDimension: undefined,
  buildingEmbeddingDimension: undefined,
});
watch(
  () => [
    form.chunkSize,
    form.chunkOverlap,
    form.chunkStrategy,
    JSON.stringify(form.chunkStrategyConfig),
  ],
  () => {
    if (chunkPreviewDialog.value && previewResult.value)
      previewResult.value = undefined;
  },
);
const navigation = [
  { name: "overview", label: "工作台概览", icon: Grid },
  { name: "basic", label: "基本配置", icon: Setting },
  { name: "documents", label: "文档管理", icon: Document },
  {
    name: "chunks",
    label: "分块与索引",
    icon: Files,
    description:
      "查看文档分块、Embedding 状态、Elasticsearch 索引代次及重建任务。",
  },
  {
    name: "permissions",
    label: "访问权限",
    icon: Lock,
    description: "按用户、角色和部门管理知识库及文档的访问范围。",
  },
  {
    name: "graph",
    label: "知识图谱",
    icon: Share,
    description: "维护图谱 Schema，审核实体关系并管理 Neo4j 发布和社区摘要。",
  },
  {
    name: "retrieval",
    label: "检索测试",
    icon: Search,
    description: "对比 BM25、KNN、RRF、重排和图关系证据，验证检索质量。",
  },
  {
    name: "chat",
    label: "问答测试",
    icon: ChatDotRound,
    description: "使用 SSE 流式问答验证回答、来源引用及图谱降级行为。",
  },
  {
    name: "audit",
    label: "查询审计",
    icon: Tickets,
    description: "查看召回通道、引用分块、耗时、模型调用和降级原因。",
  },
];
const chunkStrategies = [
  {
    label: "智能 Markdown（推荐）",
    value: "SMART_MARKDOWN",
    description:
      "优先保护标题、段落、表格、代码块、公式块和图片引用，超长普通文本再按 Token 切分。",
  },
  {
    label: "固定 Token",
    value: "FIXED_TOKEN",
    description:
      "严格按照最大 Token 和重叠长度滑动切分，适合结构较弱的纯文本。",
  },
  {
    label: "正则表达式",
    value: "REGEX",
    description:
      "使用安全边界表达式切分章节或编号；限制复杂回溯语法和500字符长度。",
  },
  {
    label: "自定义分隔符",
    value: "CUSTOM_SEPARATOR",
    description: "按多个固定分隔符切分，支持空行、制表符和 --- 等边界。",
  },
  {
    label: "按页面",
    value: "PAGE",
    description:
      "识别分页符和 Markdown 页面标记；单页超长时继续按 Token 兜底。",
  },
];
const chunkStrategyDescription = computed(
  () =>
    chunkStrategies.find((item) => item.value === form.chunkStrategy)
      ?.description || "",
);
const separatorsText = computed({
  get: () => (form.chunkStrategyConfig?.separators || []).join("\n"),
  set: (value) => {
    form.chunkStrategyConfig.separators = value
      .split("\n")
      .map((item) => item.trim())
      .filter(Boolean);
  },
});
const currentNavigation = computed(
  () =>
    navigation.find((item) => item.name === activeSection.value) ||
    navigation[0],
);
const serializeForm = () =>
  JSON.stringify({
    knowledgeName: form.knowledgeName,
    description: form.description,
    status: form.status,
    remark: form.remark,
    chunkSize: form.chunkSize,
    chunkOverlap: form.chunkOverlap,
    chunkStrategy: form.chunkStrategy,
    chunkStrategyConfig: {
      strategy: form.chunkStrategy,
      maxTokens: form.chunkSize,
      minTokens: form.chunkStrategyConfig.minTokens,
      overlapTokens: form.chunkOverlap,
      regex: form.chunkStrategyConfig.regex || "",
      regexIgnoreCase: Boolean(form.chunkStrategyConfig.regexIgnoreCase),
      delimiterPosition: form.chunkStrategyConfig.delimiterPosition || "NEXT",
      separators: form.chunkStrategyConfig.separators || [],
      fallbackToSmart: form.chunkStrategyConfig.fallbackToSmart !== false,
      autoOptimize: Boolean(form.chunkStrategyConfig.autoOptimize),
    },
    embeddingModelId: form.embeddingModelId ?? null,
    rerankModelId: form.rerankModelId ?? null,
    chatModelId: form.chatModelId ?? null,
    bm25TopK: form.bm25TopK,
    knnTopK: form.knnTopK,
    rerankTopK: form.rerankTopK,
    graphRetrievalEnabled: form.graphRetrievalEnabled,
    text2CypherEnabled: form.text2CypherEnabled,
    communitySummaryEnabled: form.communitySummaryEnabled,
  });
const dirty = computed(
  () => Boolean(baseline.value) && serializeForm() !== baseline.value,
);
const enabled = (model) => model.status === "0";
const embeddingModels = computed(() =>
  models.value.filter((m) => enabled(m) && m.modelType === "EMBEDDING"),
);
const rerankModels = computed(() =>
  models.value.filter((m) => enabled(m) && m.modelType === "RERANK"),
);
const chatModels = computed(() =>
  models.value.filter((m) => enabled(m) && m.modelType === "CHAT"),
);
const indexBuildStatusText = computed(
  () =>
    ({
      IDLE: "空闲",
      BUILDING: "构建中",
      SWITCHING: "切换中",
      FAILED: "构建失败",
    })[form.indexBuildStatus] ||
    form.indexBuildStatus ||
    "空闲",
);
const completionRate = computed(() => {
  const checks = [
    form.knowledgeName,
    form.description,
    form.embeddingModelId,
    form.chatModelId,
    form.chunkSize,
    form.chunkOverlap !== undefined,
  ];
  return Math.round((checks.filter(Boolean).length / checks.length) * 100);
});
const rules = {
  knowledgeName: [
    { required: true, message: "请输入知识库名称", trigger: "blur" },
  ],
  description: [
    { required: true, message: "请输入知识库描述", trigger: "blur" },
  ],
  embeddingModelId: [
    { required: true, message: "请选择向量模型", trigger: "change" },
  ],
  chatModelId: [
    { required: true, message: "请选择对话模型", trigger: "change" },
  ],
  chunkSize: [
    {
      required: true,
      type: "number",
      min: 200,
      max: 4000,
      message: "分块大小必须为 200～4000",
      trigger: "change",
    },
  ],
  bm25TopK: [
    {
      required: true,
      type: "number",
      min: 1,
      max: 100,
      message: "BM25 召回数必须为 1～100",
      trigger: "change",
    },
  ],
  knnTopK: [
    {
      required: true,
      type: "number",
      min: 1,
      max: 100,
      message: "向量召回数必须为 1～100",
      trigger: "change",
    },
  ],
  rerankTopK: [
    {
      required: true,
      type: "number",
      min: 1,
      max: 100,
      message: "重排候选数必须为 1～100",
      trigger: "change",
    },
  ],
};

watch(
  () => form.graphRetrievalEnabled,
  (enabledValue) => {
    if (!enabledValue) {
      form.text2CypherEnabled = false;
      form.communitySummaryEnabled = false;
    }
  },
);
watch(
  () => route.query.section,
  (section) => {
    if (validSections.includes(section) && section !== activeSection.value)
      activeSection.value = section;
  },
);

async function load(silent = false) {
  if (!silent) loading.value = true;
  try {
    const [knowledge, modelPage, stats] = await Promise.all([
      getKnowledge(form.id),
      listModels({ pageNum: 1, pageSize: 1000 }),
      getKnowledgeDocumentStatistics(form.id),
    ]);
    if (!knowledge?.id) throw new Error("知识库详情数据无效");
    Object.assign(form, knowledge);
    form.chunkStrategy = knowledge.chunkStrategy || "SMART_MARKDOWN";
    form.chunkStrategyConfig = {
      minTokens: 100,
      regex: "",
      regexIgnoreCase: false,
      delimiterPosition: "NEXT",
      separators: ["\\n\\n", "---"],
      fallbackToSmart: true,
      autoOptimize: false,
      ...(knowledge.chunkStrategyConfig || {}),
    };
    models.value = modelPage.rows || [];
    documentStats.value = stats || {
      total: 0,
      success: 0,
      processing: 0,
      failed: 0,
      pending: 0,
    };
    baseline.value = serializeForm();
  } catch (error) {
    ElMessage.error(error?.message || "知识库工作台加载失败");
  } finally {
    loading.value = false;
  }
}
async function confirmDiscard() {
  if (!dirty.value) return true;
  try {
    await ElMessageBox.confirm(
      "当前配置尚未保存，离开后修改将丢失。",
      "未保存修改",
      {
        confirmButtonText: "放弃修改",
        cancelButtonText: "继续编辑",
        type: "warning",
      },
    );
    return true;
  } catch {
    return false;
  }
}
async function changeSection(section) {
  if (section === activeSection.value) return;
  if (activeSection.value === "basic" && !(await confirmDiscard())) return;
  if (activeSection.value === "basic" && dirty.value) {
    const knowledge = await getKnowledge(form.id);
    Object.assign(form, knowledge);
    baseline.value = serializeForm();
  }
  activeSection.value = section;
  await router.replace({ query: { ...route.query, section } });
}
async function backToList() {
  if (!(await confirmDiscard())) return;
  allowRouteLeave.value = true;
  router.push("/ai/knowledge");
}
async function refreshWorkbench() {
  if (!(await confirmDiscard())) return;
  await load();
  if (activeSection.value === "documents") documentPanelRef.value?.reload();
}
async function save() {
  try {
    await formRef.value?.validate();
  } catch {
    await nextTick();
    document
      .querySelector(".basic-page .is-error")
      ?.scrollIntoView({ behavior: "smooth", block: "center" });
    return;
  }
  if (form.chunkOverlap >= form.chunkSize) {
    ElMessage.warning("重叠长度必须小于分块大小");
    return;
  }
  saving.value = true;
  try {
    const activeModelId = form.activeEmbeddingModelId || form.embeddingModelId;
    const selectedModel = models.value.find(
      (model) => model.id === form.embeddingModelId,
    );
    const embeddingChanged =
      activeModelId !== form.embeddingModelId ||
      form.activeEmbeddingDimension !== selectedModel?.dimension;
    const before = baseline.value ? JSON.parse(baseline.value) : {};
    const currentPayload = JSON.parse(serializeForm());
    const chunkingChanged =
      JSON.stringify({
        chunkSize: before.chunkSize,
        chunkOverlap: before.chunkOverlap,
        chunkStrategy: before.chunkStrategy,
        chunkStrategyConfig: before.chunkStrategyConfig,
      }) !==
      JSON.stringify({
        chunkSize: currentPayload.chunkSize,
        chunkOverlap: currentPayload.chunkOverlap,
        chunkStrategy: currentPayload.chunkStrategy,
        chunkStrategyConfig: currentPayload.chunkStrategyConfig,
      });
    if (embeddingChanged) {
      const oldModel = models.value.find((model) => model.id === activeModelId);
      const newModel = selectedModel;
      await ElMessageBox.confirm(
        `向量模型将从“${oldModel?.modelName || "未配置"}（${form.activeEmbeddingDimension || "-"} 维）”更换为“${newModel?.modelName || "未配置"}（${newModel?.dimension || "-"} 维）”。系统会立即创建新索引代次并重建全部文档，完成前继续使用旧模型和旧索引。是否继续？`,
        "更换向量模型并重建索引",
        {
          type: "warning",
          confirmButtonText: "更换并重建",
          cancelButtonText: "取消",
        },
      );
    }
    if (chunkingChanged && documentStats.value.total > 0) {
      await ElMessageBox.confirm(
        "分块策略已修改。保存后系统会创建新索引代次，对当前文档重新分块、生成 Embedding 并建立索引；新代次成功前旧索引继续服务。是否继续？",
        "修改分块策略并重建索引",
        {
          type: "warning",
          confirmButtonText: "保存并重建",
          cancelButtonText: "取消",
        },
      );
    }
    const response = await updateKnowledge({
      id: form.id,
      ...currentPayload,
      rebuildOnEmbeddingChange: embeddingChanged,
      rebuildOnChunkingChange: chunkingChanged,
    });
    if (response?.data) Object.assign(form, response.data);
    baseline.value = serializeForm();
    ElMessage.success("知识库配置已保存");
  } catch (error) {
    if (error === "cancel" || error === "close") return;
    ElMessage.error(error?.message || "保存失败");
  } finally {
    saving.value = false;
  }
}
async function openChunkPreview() {
  chunkPreviewDialog.value = true;
  previewResult.value = undefined;
  try {
    const result = await pageKnowledgeDocuments(form.id, {
      pageNum: 1,
      pageSize: 50,
    });
    previewDocuments.value = result.rows || [];
  } catch {
    previewDocuments.value = [];
  }
}
async function loadPreviewDocument(documentId) {
  if (!documentId) return;
  const selected = previewDocuments.value.find(
    (item) => item.id === documentId,
  );
  if (!selected?.currentVersionId)
    return ElMessage.warning("该文档没有可预览的当前版本");
  previewLoading.value = true;
  try {
    const blob = await getKnowledgeParsedArtifact(
      form.id,
      selected.id,
      selected.currentVersionId,
      "inline",
    );
    previewText.value = new TextDecoder("utf-8", { fatal: false }).decode(
      await blob.arrayBuffer(),
    );
    previewResult.value = undefined;
  } catch (error) {
    ElMessage.error(error?.message || "解析文本加载失败");
  } finally {
    previewLoading.value = false;
  }
}
async function runChunkPreview(autoOptimize = false) {
  if (!previewText.value.trim())
    return ElMessage.warning("请输入或选择需要预览的文本");
  previewLoading.value = true;
  try {
    const payload = JSON.parse(serializeForm());
    previewResult.value = await previewKnowledgeChunks(form.id, {
      text: previewText.value,
      config: { ...payload.chunkStrategyConfig, autoOptimize },
    });
  } catch (error) {
    ElMessage.error(error?.message || "分块预览失败");
  } finally {
    previewLoading.value = false;
  }
}
function modelName(id) {
  if (!id) return "未配置";
  return (
    models.value.find((model) => model.id === id)?.modelName || `模型 #${id}`
  );
}
async function openDocuments(status = "") {
  await changeSection("documents");
  await nextTick();
  if (status) documentPanelRef.value?.setStatusFilter(status);
  else documentPanelRef.value?.openUploader();
}
async function handleDocumentsChanged(stats) {
  documentStats.value = stats ||
    (await getKnowledgeDocumentStatistics(form.id)) || {
      total: 0,
      success: 0,
      processing: 0,
      failed: 0,
      pending: 0,
    };
}
function beforeUnload(event) {
  if (!dirty.value) return;
  event.preventDefault();
  event.returnValue = "";
}
onBeforeRouteLeave(async () => allowRouteLeave.value || confirmDiscard());
onMounted(() => {
  window.addEventListener("beforeunload", beforeUnload);
  if (!route.query.section)
    router.replace({ query: { ...route.query, section: activeSection.value } });
  load();
});
onBeforeUnmount(() => window.removeEventListener("beforeunload", beforeUnload));
</script>

<style scoped lang="scss">
.model-transition {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 24px;
  margin: 12px 0 2px;
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  color: var(--ry-muted-foreground);
}
.model-transition strong {
  color: var(--ry-text);
}
.knowledge-workbench {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}
.workbench-header {
  display: flex;
  min-height: 72px;
  align-items: center;
  justify-content: space-between;
  padding: 9px 18px;
}
.header-main {
  min-width: 0;
}
.header-main > .el-button {
  height: 22px;
  padding: 0;
}
.title-row {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-top: 2px;
}
.title-row h2 {
  overflow: hidden;
  margin: 0;
  font-size: 18px;
  line-height: 26px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.workbench-header p {
  overflow: hidden;
  margin: 1px 0 0;
  color: var(--ry-muted-foreground);
  font-size: 13px;
  line-height: 20px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.workbench-layout {
  display: grid;
  grid-template-columns: 210px minmax(0, 1fr);
  align-items: start;
  gap: 12px;
}
.workbench-sidebar {
  position: sticky;
  top: 12px;
  padding: 10px;
}
.nav-caption {
  padding: 8px 12px 10px;
  color: var(--ry-muted-foreground);
  font-size: 12px;
}
.nav-item {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 10px;
  margin: 2px 0;
  padding: 10px 12px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--ry-foreground);
  cursor: pointer;
  text-align: left;
  transition: 0.2s;
}
.nav-item:hover {
  background: var(--el-fill-color-light);
}
.nav-item.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}
.nav-item .el-icon {
  font-size: 16px;
}
.workbench-main {
  min-width: 0;
}
.mobile-nav {
  display: none;
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
.stat-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}
.stat-card {
  display: flex;
  min-height: 116px;
  flex-direction: column;
  padding: 18px;
}
.stat-card span,
.stat-card small {
  color: var(--ry-muted-foreground);
}
.stat-card strong {
  margin: 9px 0;
  font-size: 28px;
}
.stat-card.success strong {
  color: var(--el-color-success);
}
.stat-card.warning strong {
  color: var(--el-color-warning);
}
.stat-card.danger strong {
  color: var(--el-color-danger);
}
.overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(300px, 0.65fr);
  gap: 12px;
  margin-top: 12px;
}
.overview-card {
  padding: 20px;
}
.card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-title h4,
.form-card h4 {
  margin: 0;
  font-size: 15px;
}
.config-list {
  margin: 14px 0 0;
}
.config-list > div {
  display: grid;
  grid-template-columns: 100px minmax(0, 1fr);
  padding: 11px 0;
  border-bottom: 1px solid var(--ry-border-light);
}
.config-list > div:last-child {
  border-bottom: 0;
}
.config-list dt {
  color: var(--ry-muted-foreground);
}
.config-list dd {
  overflow: hidden;
  margin: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.quick-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 15px;
}
.quick-actions button {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px;
  border: 1px solid var(--ry-border);
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  text-align: left;
}
.quick-actions button:hover:not(:disabled) {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.quick-actions button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
.quick-actions .el-icon {
  font-size: 20px;
  color: var(--el-color-primary);
}
.quick-actions span {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.quick-actions small {
  color: var(--ry-muted-foreground);
}
.basic-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: 76px;
}
.form-card {
  padding: 20px;
}
.card-description {
  margin: 6px 0 18px;
  color: var(--ry-muted-foreground);
}
.form-grid {
  margin-top: 18px;
}
.basic-page :deep(.el-select),
.basic-page :deep(.el-input-number) {
  width: 100%;
}
.field-help {
  display: block;
  margin-top: 5px;
  color: var(--ry-muted-foreground);
  font-size: 12px;
  line-height: 1.3;
}
.switch-list > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 2px;
  border-bottom: 1px solid var(--ry-border-light);
}
.switch-list > div:last-child {
  border-bottom: 0;
}
.switch-list span {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.switch-list small {
  color: var(--ry-muted-foreground);
  font-weight: 400;
}
.save-bar {
  position: sticky;
  z-index: 5;
  bottom: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  box-shadow: 0 -4px 18px rgba(15, 23, 42, 0.08);
}
.save-bar > div {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--ry-muted-foreground);
}
.save-bar .el-icon {
  color: var(--el-color-success);
}
.save-bar .el-icon.changed {
  color: var(--el-color-warning);
}
.chunk-strategy-alert {
  margin-bottom: 18px;
}
.chunk-preview-action {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0 0 18px;
}
.chunk-preview-action span,
.preview-source-toolbar span,
.preview-actions span {
  color: var(--ry-muted-foreground);
  font-size: 13px;
}
.preview-source-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.preview-source-toolbar .el-select {
  width: min(420px, 55vw);
}
.preview-actions {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 14px 0;
}
.preview-result-heading {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 14px;
  margin-bottom: 12px;
  padding: 10px 12px;
}
.preview-result-title {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 3px;
}
.preview-result-title strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.preview-result-title span {
  color: var(--ry-muted-foreground);
  font-size: 12px;
}
.preview-content {
  overflow: hidden;
  max-height: 92px;
  margin: 0;
  color: var(--el-text-color-regular);
  font-family: inherit;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}
.pending-panel {
  display: flex;
  min-height: 560px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 30px;
  text-align: center;
}
.pending-icon {
  display: grid;
  width: 68px;
  height: 68px;
  place-items: center;
  border-radius: 18px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 30px;
}
.pending-panel h3 {
  margin: 18px 0 8px;
}
.pending-panel p {
  max-width: 560px;
  margin: 0 0 18px;
  color: var(--ry-muted-foreground);
  line-height: 1.7;
}
@media (max-width: 1200px) {
  .stat-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .overview-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 900px) {
  .workbench-layout {
    display: block;
  }
  .workbench-sidebar {
    display: none;
  }
  .mobile-nav {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 14px;
  }
  .mobile-nav .el-select {
    width: min(260px, 65vw);
  }
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .workbench-header {
    min-height: 68px;
    padding: 8px 14px;
  }
}
@media (max-width: 560px) {
  .stat-grid {
    grid-template-columns: 1fr;
  }
  .save-bar > div span {
    display: none;
  }
  .config-list > div {
    grid-template-columns: 1fr;
    gap: 5px;
  }
  .workbench-header p {
    max-width: 75vw;
  }
  .preview-result-heading {
    align-items: flex-start;
    flex-wrap: wrap;
  }
  .preview-result-title {
    order: 1;
    flex-basis: calc(100% - 140px);
  }
  .preview-result-heading > .el-tag {
    order: 2;
  }
}
</style>
