<template>
  <section class="retrieval-panel">
    <header class="section-heading">
      <div>
        <h2>检索测试</h2>
        <p>
          逐阶段查看 BM25、KNN、RRF、重排和最终上下文，结果不会写入问答记录。
        </p>
      </div>
    </header>
    <div class="query-card ry-card">
      <el-input
        v-model="form.query"
        type="textarea"
        :rows="3"
        maxlength="2000"
        show-word-limit
        placeholder="输入需要检索的问题，例如：系统如何保证索引切换期间旧版本仍可用？"
        @keydown.ctrl.enter.prevent="run"
      />
      <div class="query-options">
        <label
          >BM25<el-input-number
            v-model="form.bm25TopK"
            :min="1"
            :max="100"
            controls-position="right"
        /></label>
        <label
          >KNN<el-input-number
            v-model="form.knnTopK"
            :min="1"
            :max="100"
            controls-position="right"
        /></label>
        <label
          >RRF<el-input-number
            v-model="form.rrfTopK"
            :min="1"
            :max="100"
            controls-position="right"
        /></label>
        <label
          >重排<el-input-number
            v-model="form.rerankTopK"
            :min="1"
            :max="100"
            controls-position="right"
        /></label>
        <el-button type="primary" :loading="loading" @click="run"
          >开始检索</el-button
        >
      </div>
    </div>

    <template v-if="result">
      <el-alert
        v-if="result.degradedReason"
        type="warning"
        :closable="false"
        show-icon
        :title="result.degradedReason"
      />
      <div class="overview-grid">
        <article class="ry-card">
          <span>活动代次</span><strong>G{{ result.activeGeneration }}</strong>
        </article>
        <article class="ry-card">
          <span>向量模型</span
          ><strong>{{ result.embeddingModel || "未配置" }}</strong>
        </article>
        <article class="ry-card">
          <span>重排模型</span
          ><strong>{{
            result.rerankApplied ? result.rerankModel : "未执行"
          }}</strong>
        </article>
        <article class="ry-card">
          <span>总耗时</span><strong>{{ result.totalElapsedMs }} ms</strong>
        </article>
      </div>
      <div class="filter-card ry-card">
        <div>
          <b>前置过滤条件</b><span>以下条件在 Elasticsearch 召回阶段执行</span>
        </div>
        <el-tag
          v-for="item in result.permissionFilters"
          :key="item.field"
          effect="plain"
          >{{ item.field }} {{ item.operator }} {{ item.value }} ·
          {{ item.source }}</el-tag
        >
      </div>
      <el-tabs v-model="activeStage" class="stage-tabs ry-card">
        <el-tab-pane
          v-for="stage in result.stages"
          :key="stage.code"
          :name="stage.code"
        >
          <template #label
            ><span class="tab-label"
              >{{ stage.name
              }}<el-tag size="small" :type="statusType(stage.status)">{{
                stage.resultCount
              }}</el-tag></span
            ></template
          >
          <div class="stage-head">
            <div>
              <b>{{ stage.name }}</b
              ><span>{{ stage.elapsedMs }} ms</span>
            </div>
            <el-tag :type="statusType(stage.status)">{{
              statusText(stage.status)
            }}</el-tag>
          </div>
          <el-alert
            v-if="stage.message"
            :type="stage.status === 'FAILED' ? 'error' : 'info'"
            :closable="false"
            :title="stage.message"
          />
          <el-table
            :data="stage.hits"
            border
            stripe
            empty-text="当前阶段没有结果"
          >
            <el-table-column prop="rank" label="#" width="58" align="center" />
            <el-table-column label="分块" width="100"
              ><template #default="{ row }"
                >#{{ row.chunkId }}</template
              ></el-table-column
            >
            <el-table-column label="文档/版本" width="145"
              ><template #default="{ row }"
                >D{{ row.documentId }} / V{{ row.documentVersionId }}</template
              ></el-table-column
            >
            <el-table-column
              prop="titlePath"
              label="标题路径"
              min-width="170"
              show-overflow-tooltip
            />
            <el-table-column label="通道" width="130"
              ><template #default="{ row }"
                ><el-tag
                  v-for="item in row.channels"
                  :key="item"
                  size="small"
                  effect="plain"
                  >{{ item }}</el-tag
                ></template
              ></el-table-column
            >
            <el-table-column label="得分" width="105"
              ><template #default="{ row }">{{
                score(row.score)
              }}</template></el-table-column
            >
            <el-table-column label="内容" min-width="360"
              ><template #default="{ row }"
                ><div class="content" v-html="safeHighlight(row)" /></template
            ></el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </template>
    <el-empty v-else description="输入问题后开始检索，可逐阶段核对召回质量" />
  </section>
</template>

<script setup>
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import DOMPurify from "dompurify";
import { testKnowledgeRetrieval } from "@/api/ai";
const props = defineProps({
  knowledgeBaseId: { type: [Number, String], required: true },
  defaults: { type: Object, default: () => ({}) },
});
const loading = ref(false),
  result = ref(),
  activeStage = ref("BM25");
const form = reactive({
  query: "",
  bm25TopK: props.defaults.bm25TopK || 30,
  knnTopK: props.defaults.knnTopK || 30,
  rrfTopK: props.defaults.rerankTopK || 20,
  rerankTopK: props.defaults.rerankTopK || 20,
});
async function run() {
  if (!form.query.trim()) return ElMessage.warning("请输入检索问题");
  loading.value = true;
  try {
    result.value = await testKnowledgeRetrieval(props.knowledgeBaseId, {
      ...form,
      query: form.query.trim(),
    });
    activeStage.value = result.value?.stages?.[0]?.code || "BM25";
  } catch (error) {
    ElMessage.error(error?.message || "检索测试失败");
  } finally {
    loading.value = false;
  }
}
const score = (value) => Number(value || 0).toFixed(6);
const escape = (value) =>
  String(value || "").replace(
    /[&<>"']/g,
    (char) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        char
      ],
  );
function safeHighlight(row) {
  const value = row.highlight || escape(row.content);
  return DOMPurify.sanitize(value, { ALLOWED_TAGS: ["em"], ALLOWED_ATTR: [] });
}
const statusText = (value) =>
  ({ SUCCESS: "成功", DEGRADED: "已降级", FAILED: "失败", SKIPPED: "已跳过" })[
    value
  ] || value;
const statusType = (value) =>
  ({
    SUCCESS: "success",
    DEGRADED: "warning",
    FAILED: "danger",
    SKIPPED: "info",
  })[value] || "info";
</script>

<style scoped>
.retrieval-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.query-card {
  padding: 16px;
}
.query-options {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 14px;
}
.query-options label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.query-options .el-input-number {
  width: 125px;
}
.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.overview-grid article {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 9px;
  padding: 14px;
}
.overview-grid span,
.filter-card span,
.stage-head span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.overview-grid strong {
  overflow: hidden;
  font-size: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.filter-card {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 9px;
  padding: 12px 14px;
}
.filter-card > div {
  display: flex;
  flex-direction: column;
  margin-right: 8px;
}
.stage-tabs {
  padding: 0 14px 14px;
}
.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}
.stage-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.stage-head > div {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.content {
  display: -webkit-box;
  overflow: hidden;
  line-height: 1.55;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  white-space: normal;
}
.content :deep(em) {
  padding: 0 2px;
  background: var(--el-color-warning-light-7);
  color: inherit;
  font-style: normal;
}
.el-alert {
  margin-bottom: 12px;
}
@media (max-width: 900px) {
  .overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 560px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }
  .query-options > * {
    width: 100% !important;
  }
  .query-options .el-input-number {
    width: 100%;
  }
}
</style>
