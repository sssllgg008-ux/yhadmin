<template>
  <section class="audit-panel">
    <div class="section-heading">
      <div>
        <h3>查询审计</h3>
        <p>追踪权限过滤、召回通道、模型调用、来源引用及降级原因。</p>
      </div>
      <el-button :icon="Download" @click="exportRows">导出审计</el-button>
    </div>

    <div class="audit-stats">
      <article class="ry-card"><span>查询总数</span><strong>{{ stats.total || 0 }}</strong></article>
      <article class="ry-card success"><span>成功</span><strong>{{ stats.success || 0 }}</strong></article>
      <article class="ry-card warning"><span>降级</span><strong>{{ stats.degraded || 0 }}</strong></article>
      <article class="ry-card danger"><span>失败</span><strong>{{ stats.failed || 0 }}</strong></article>
      <article class="ry-card"><span>平均检索</span><strong>{{ ms(stats.averageRetrievalMs) }}</strong></article>
      <article class="ry-card"><span>平均生成</span><strong>{{ ms(stats.averageGenerationMs) }}</strong></article>
    </div>

    <article class="ry-card audit-card">
      <div class="filters">
        <el-input v-model="query.keyword" clearable placeholder="搜索脱敏问题" @keyup.enter="search" />
        <el-input v-model="query.requestId" clearable placeholder="请求编号" @keyup.enter="search" />
        <el-input-number v-model="query.userId" :min="1" controls-position="right" placeholder="用户 ID" />
        <el-select v-model="query.queryType" clearable placeholder="查询类型">
          <el-option label="检索测试" value="RETRIEVAL_TEST" />
          <el-option label="问答测试" value="CHAT_TEST" />
          <el-option label="知识问答" value="KNOWLEDGE_CHAT" />
        </el-select>
        <el-select v-model="query.channel" clearable placeholder="召回通道">
          <el-option v-for="item in channels" :key="item" :label="item" :value="item" />
        </el-select>
        <el-select v-model="query.status" clearable placeholder="执行状态">
          <el-option label="运行中" value="RUNNING" /><el-option label="成功" value="SUCCESS" />
          <el-option label="已降级" value="DEGRADED" /><el-option label="失败" value="FAILED" />
        </el-select>
        <el-date-picker v-model="timeRange" type="datetimerange" range-separator="至"
          start-placeholder="开始时间" end-placeholder="结束时间" />
        <el-checkbox v-model="query.degradedOnly">仅看降级</el-checkbox>
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column prop="maskedQuestion" label="脱敏问题" min-width="240" show-overflow-tooltip />
        <el-table-column prop="userName" label="用户" width="110" show-overflow-tooltip />
        <el-table-column label="类型" width="105"><template #default="{ row }">{{ typeName(row.queryType) }}</template></el-table-column>
        <el-table-column label="通道" min-width="180"><template #default="{ row }">
          <el-tag v-for="item in jsonArray(row.channels)" :key="item" size="small" effect="plain">{{ item }}</el-tag>
        </template></el-table-column>
        <el-table-column prop="intentType" label="意图" width="130" show-overflow-tooltip />
        <el-table-column label="引用/图谱" width="100"><template #default="{ row }">{{ row.citationCount }}/{{ row.graphEvidenceCount }}</template></el-table-column>
        <el-table-column label="耗时" width="145"><template #default="{ row }">{{ ms(row.retrievalMs) }} / {{ ms(row.generationMs) }}</template></el-table-column>
        <el-table-column label="Token" width="105"><template #default="{ row }">{{ row.inputTokens || 0 }}/{{ row.outputTokens || 0 }}</template></el-table-column>
        <el-table-column label="状态" width="95"><template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusName(row.status) }}</el-tag>
        </template></el-table-column>
        <el-table-column label="操作" width="80" fixed="right"><template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
        </template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
        :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
    </article>

    <el-drawer v-model="drawer" title="查询审计详情" size="min(860px, 96vw)">
      <div v-loading="detailLoading" v-if="detail" class="audit-detail">
        <el-alert v-if="detail.summary.degraded || detail.errorMessage" :type="detail.errorMessage ? 'error' : 'warning'"
          :title="detail.errorMessage ? '执行失败' : '发生降级'"
          :description="detail.errorMessage || detail.summary.degradeReason" show-icon :closable="false" />
        <h4>基本信息</h4>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="请求编号">{{ detail.summary.requestId }}</el-descriptions-item>
          <el-descriptions-item label="执行状态">{{ statusName(detail.summary.status) }}</el-descriptions-item>
          <el-descriptions-item label="脱敏问题" :span="2">{{ detail.summary.maskedQuestion || '-' }}</el-descriptions-item>
          <el-descriptions-item label="查询改写" :span="2">{{ detail.rewrittenQuestion || '-' }}</el-descriptions-item>
          <el-descriptions-item label="意图">{{ detail.summary.intentType || '-' }} {{ percent(detail.intentConfidence) }}</el-descriptions-item>
          <el-descriptions-item label="模型">{{ detail.modelName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="权限命中">{{ detail.permissionMatchCount }}</el-descriptions-item>
          <el-descriptions-item label="上下文/图谱">{{ detail.contextCount }}/{{ detail.summary.graphEvidenceCount }}</el-descriptions-item>
          <el-descriptions-item label="耗时">检索 {{ ms(detail.summary.retrievalMs) }}，生成 {{ ms(detail.summary.generationMs) }}</el-descriptions-item>
          <el-descriptions-item label="Token">输入 {{ detail.summary.inputTokens }}，输出 {{ detail.summary.outputTokens }}</el-descriptions-item>
        </el-descriptions>
        <h4>执行阶段</h4>
        <el-timeline v-if="stages.length">
          <el-timeline-item v-for="stage in stages" :key="stage.code" :type="statusType(stage.status)"
            :timestamp="`${stage.elapsedMs || 0} ms · ${stage.resultCount || 0} 条`">
            <b>{{ stage.name || stage.code }}</b><p v-if="stage.message">{{ stage.message }}</p>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="该历史记录暂无阶段明细" :image-size="60" />
        <h4>权限与图谱</h4>
        <pre>{{ pretty(detail.permissionFilters) }}</pre><pre>{{ pretty(detail.graphDetails) }}</pre>
        <h4>来源引用</h4>
        <el-table :data="citations" size="small">
          <el-table-column prop="documentName" label="文档" min-width="180" show-overflow-tooltip />
          <el-table-column label="版本/分块" width="110"><template #default="{ row }">V{{ row.versionNo }} / #{{ row.chunkId }}</template></el-table-column>
          <el-table-column prop="titlePath" label="标题路径" min-width="160" show-overflow-tooltip />
          <el-table-column label="权限" width="90"><template #default="{ row }"><el-tag :type="row.accessible ? 'success' : 'danger'">{{ row.accessible ? '可访问' : '已失效' }}</el-tag></template></el-table-column>
        </el-table>
        <el-pagination v-model:current-page="citationQuery.pageNum" :page-size="citationQuery.pageSize"
          :total="citationTotal" layout="total, prev, pager, next" @change="loadCitations" />
      </div>
    </el-drawer>
  </section>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from "vue";
import { Download, Search } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import {
  pageKnowledgeQueryAudits, getKnowledgeQueryAuditStats, getKnowledgeQueryAudit,
  pageKnowledgeQueryAuditCitations, exportKnowledgeQueryAudits,
} from "@/api/ai";

const props = defineProps({ knowledgeBaseId: { type: Number, required: true } });
const channels = ["BM25", "KNN", "RRF", "RERANK", "INTENT", "GRAPH_ENTITY", "GRAPH_RELATION", "GRAPH_PATH"];
const loading = ref(false), rows = ref([]), total = ref(0), timeRange = ref([]);
const stats = ref({}), drawer = ref(false), detailLoading = ref(false), detail = ref(null);
const citations = ref([]), citationTotal = ref(0), citationQuery = reactive({ pageNum: 1, pageSize: 10 });
const query = reactive({ keyword: "", requestId: "", userId: undefined, queryType: "", channel: "", status: "", degradedOnly: false, pageNum: 1, pageSize: 10 });
const localTime = value => { if (!value) return undefined; const pad = n => String(n).padStart(2, "0"); return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`; };
const params = computed(() => ({ ...query, startTime: localTime(timeRange.value?.[0]), endTime: localTime(timeRange.value?.[1]) }));
async function load() { loading.value = true; try { const [page, summary] = await Promise.all([pageKnowledgeQueryAudits(props.knowledgeBaseId, params.value), getKnowledgeQueryAuditStats(props.knowledgeBaseId, params.value)]); rows.value = page.rows || []; total.value = page.total || 0; stats.value = summary || {}; } finally { loading.value = false; } }
function search() { query.pageNum = 1; load(); }
function reset() { Object.assign(query, { keyword: "", requestId: "", userId: undefined, queryType: "", channel: "", status: "", degradedOnly: false, pageNum: 1 }); timeRange.value = []; load(); }
async function openDetail(row) { drawer.value = true; detailLoading.value = true; citationQuery.pageNum = 1; try { detail.value = await getKnowledgeQueryAudit(props.knowledgeBaseId, row.id); await loadCitations(); } finally { detailLoading.value = false; } }
async function loadCitations() { if (!detail.value) return; const res = await pageKnowledgeQueryAuditCitations(props.knowledgeBaseId, detail.value.summary.id, citationQuery); citations.value = res.rows || []; citationTotal.value = res.total || 0; }
async function exportRows() { const blob = await exportKnowledgeQueryAudits(props.knowledgeBaseId, params.value); const url = URL.createObjectURL(blob); const link = document.createElement("a"); link.href = url; link.download = "query-audit.csv"; link.click(); URL.revokeObjectURL(url); ElMessage.success("审计记录已导出"); }
const jsonArray = value => { try { return JSON.parse(value || "[]"); } catch { return []; } };
const pretty = value => { try { return JSON.stringify(JSON.parse(value || "{}"), null, 2); } catch { return value || "-"; } };
const stages = computed(() => jsonArray(detail.value?.stageDetails));
const ms = value => value == null ? "-" : `${Math.round(value)} ms`;
const percent = value => value == null ? "" : `(${Math.round(value * 100)}%)`;
const typeName = value => ({ RETRIEVAL_TEST: "检索测试", CHAT_TEST: "问答测试", KNOWLEDGE_CHAT: "知识问答" })[value] || value || "-";
const statusName = value => ({ RUNNING: "运行中", SUCCESS: "成功", DEGRADED: "已降级", FAILED: "失败", INTERRUPTED: "执行中断" })[value] || value || "-";
const statusType = value => ({ SUCCESS: "success", DEGRADED: "warning", RUNNING: "primary", FAILED: "danger", INTERRUPTED: "danger", SKIPPED: "info" })[value] || "info";
onMounted(load);
</script>

<style scoped>
.section-heading,.filters{display:flex;align-items:center;justify-content:space-between;gap:12px}.section-heading p{margin:4px 0 0;color:#64748b}.audit-stats{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:12px;margin:16px 0}.audit-stats article{padding:16px}.audit-stats span{display:block;color:#64748b}.audit-stats strong{display:block;margin-top:10px;font-size:24px}.success strong{color:#22a06b}.warning strong{color:#d97706}.danger strong{color:#ef4444}.audit-card{padding:16px}.filters{justify-content:flex-start;flex-wrap:wrap;margin-bottom:14px}.filters .el-input{width:190px}.filters .el-select{width:150px}.filters .el-date-editor{width:330px}.el-tag+.el-tag{margin-left:4px}.el-pagination{justify-content:flex-end;margin-top:14px}.audit-detail h4{margin:20px 0 10px}.audit-detail pre{padding:12px;white-space:pre-wrap;word-break:break-word;background:#f8fafc;border:1px solid #e5e7eb;border-radius:6px}.audit-detail p{margin:5px 0;color:#64748b}@media(max-width:1200px){.audit-stats{grid-template-columns:repeat(3,1fr)}}@media(max-width:700px){.audit-stats{grid-template-columns:repeat(2,1fr)}.filters>*{width:100%!important}}
</style>
