<template>
  <section class="candidate-review">
    <div class="stats-grid">
      <div v-for="item in statCards" :key="item.key" class="stat-card" :class="{ clickable: item.action }" @click="item.action?.()">
        <span>{{ item.label }}</span><strong :class="item.class">{{ stats[item.key] || 0 }}</strong>
      </div>
    </div>
    <el-alert type="info" :closable="false" show-icon>
      <template #title>原始候选 {{ stats.rawCount || 0 }} 条，经确定性聚合后形成 {{ stats.groupCount || 0 }} 个候选组，预计减少 {{ stats.reductionPercentage || 0 }}% 的主审批量。</template>
    </el-alert>
    <article class="ry-card panel-card">
      <div class="toolbar">
        <el-select v-model="query.type" clearable placeholder="全部类型"><el-option label="实体" value="ENTITY" /><el-option label="关系" value="RELATION" /><el-option label="事实" value="FACT" /></el-select>
        <el-select v-model="query.riskLevel" clearable placeholder="全部风险"><el-option label="高可信" value="HIGH" /><el-option label="中可信" value="MEDIUM" /><el-option label="低可信" value="LOW" /></el-select>
        <el-select v-model="query.status" clearable placeholder="全部状态"><el-option label="待审核" value="PENDING" /><el-option label="待发布（已通过）" value="APPROVED" /><el-option label="已拒绝" value="REJECTED" /><el-option label="已合并" value="MERGED" /></el-select>
        <el-input v-model="query.keyword" clearable placeholder="候选名称或内容" @keyup.enter="search" />
        <el-input-number v-model="query.runId" :min="1" controls-position="right" placeholder="批次 ID" />
        <el-checkbox v-model="query.conflictOnly">仅冲突</el-checkbox>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>
      <div class="batch-bar">
        <span>已选择 {{ selected.length }} 个候选组</span>
        <el-button type="success" plain :disabled="!selected.length" @click="batchReview('APPROVE')">批量通过</el-button>
        <el-button type="danger" plain :disabled="!selected.length" @click="batchReview('REJECT')">批量拒绝</el-button>
        <el-button type="primary" :disabled="!publishable.length" @click="publish">发布选中的已通过候选</el-button>
        <el-button type="danger" plain :disabled="!deletable.length" @click="deleteRows(deletable)">删除未发布候选</el-button>
        <el-button v-if="stats.awaitingPublish" link type="primary" @click="showAwaitingPublish">查看待发布（{{ stats.awaitingPublish }}）</el-button>
      </div>
      <el-table v-loading="loading" :data="page.rows" @selection-change="selected = $event">
        <el-table-column type="selection" width="44" />
        <el-table-column prop="id" label="候选组" width="88" />
        <el-table-column label="类型" width="86"><template #default="{ row }">{{ typeLabel(row.candidate_type) }}</template></el-table-column>
        <el-table-column prop="display_name" label="规范候选" min-width="250" show-overflow-tooltip />
        <el-table-column label="来源" width="100"><template #default="{ row }">{{ row.source_count }} 分块</template></el-table-column>
        <el-table-column label="平均置信度" width="115"><template #default="{ row }">{{ Number(row.average_confidence || 0).toFixed(2) }}</template></el-table-column>
        <el-table-column label="风险" width="92"><template #default="{ row }"><el-tag :type="riskType(row.risk_level)">{{ riskLabel(row.risk_level) }}</el-tag></template></el-table-column>
        <el-table-column label="一致性" width="100"><template #default="{ row }"><el-tag :type="row.conflict_state === 'NONE' ? 'success' : 'danger'">{{ conflictLabel(row.conflict_state) }}</el-tag></template></el-table-column>
        <el-table-column label="审核状态" width="110"><template #default="{ row }">{{ statusLabel(row.review_status) }}</template></el-table-column>
        <el-table-column label="发布状态" width="105"><template #default="{ row }"><el-tag :type="publishTagType(row.publish_status)">{{ publishStatusLabel(row.publish_status) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="300" fixed="right"><template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">查看来源</el-button>
          <el-button v-if="row.review_status === 'PENDING'" link type="success" @click="singleReview(row, 'APPROVE')">通过</el-button>
          <el-button v-if="row.review_status === 'PENDING'" link type="danger" @click="singleReview(row, 'REJECT')">拒绝</el-button>
          <el-button v-if="row.review_status === 'PENDING' && row.candidate_type === 'ENTITY'" link @click="mergeEntity(row)">合并</el-button>
          <el-button v-if="row.review_status === 'APPROVED' && row.publish_status !== 'SUCCESS'" link type="primary" @click="publishRows([row])">发布到图谱</el-button>
          <el-button v-if="row.publish_status !== 'SUCCESS'" link type="danger" @click="deleteRows([row])">删除</el-button>
        </template></el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" :total="page.total" layout="total, sizes, prev, pager, next, jumper" @change="load" /></div>
    </article>
    <el-drawer v-model="drawer" title="候选组与来源证据" size="min(880px, 94vw)">
      <template v-if="detail.group">
        <el-descriptions :column="2" border><el-descriptions-item label="规范候选">{{ detail.group.display_name }}</el-descriptions-item><el-descriptions-item label="风险">{{ riskLabel(detail.group.risk_level) }}</el-descriptions-item><el-descriptions-item label="来源数量">{{ detail.group.source_count }}</el-descriptions-item><el-descriptions-item label="聚合规则">{{ detail.group.aggregation_version }}</el-descriptions-item></el-descriptions>
        <h4>原始来源</h4>
        <div v-for="member in detail.members" :key="member.id" class="evidence-card">
          <div><strong>{{ member.documentName }}</strong><span>V{{ member.versionNo }} / 分块 #{{ member.chunkNo }}</span><el-tag size="small">{{ Number(member.confidence).toFixed(2) }}</el-tag></div>
          <p>{{ member.evidence_text }}</p>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup>
import { computed, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { deleteKnowledgeGraphCandidateGroups, getKnowledgeGraphCandidateGroup, getKnowledgeGraphCandidateStats, listKnowledgeGraphCandidateGroups, publishKnowledgeGraphCandidateGroups, reviewKnowledgeGraphCandidateGroups } from "@/api/ai";
const props = defineProps({ knowledgeBaseId: { type: Number, required: true } });
const loading = ref(false), selected = ref([]), drawer = ref(false);
const stats = reactive({}), page = reactive({ rows: [], total: 0 }), detail = reactive({ group: null, members: [] });
const query = reactive({ type: "", riskLevel: "", status: "PENDING", runId: null, conflictOnly: false, keyword: "", pageNum: 1, pageSize: 10 });
const statCards = computed(() => [{ key: "pending", label: "待审核" }, { key: "awaitingPublish", label: "待发布", class: "warning", action: showAwaitingPublish }, { key: "published", label: "已发布", class: "success" }, { key: "publishFailed", label: "发布失败", class: "danger" }, { key: "conflicts", label: "冲突", class: "danger" }]);
const publishable = computed(() => selected.value.filter((x) => x.review_status === "APPROVED"));
const deletable = computed(() => selected.value.filter((x) => x.publish_status !== "SUCCESS"));
const typeLabel = (v) => ({ ENTITY: "实体", RELATION: "关系", FACT: "事实" })[v] || v;
const riskLabel = (v) => ({ HIGH: "高可信", MEDIUM: "中可信", LOW: "低可信" })[v] || v;
const riskType = (v) => ({ HIGH: "success", MEDIUM: "warning", LOW: "danger" })[v] || "info";
const conflictLabel = (v) => ({ NONE: "一致", POSSIBLE_DUPLICATE: "疑似重复", CONFLICT: "冲突" })[v] || v;
const statusLabel = (v) => ({ PENDING: "待审核", APPROVED: "已通过", REJECTED: "已拒绝", MERGED: "已合并", EXPIRED: "已失效" })[v] || v;
const publishStatusLabel = (v) => ({ NOT_PUBLISHED: "未发布", SUCCESS: "已发布", FAILED: "发布失败" })[v] || v || "未发布";
const publishTagType = (v) => ({ SUCCESS: "success", FAILED: "danger", NOT_PUBLISHED: "info" })[v] || "info";
async function load() { loading.value = true; try { Object.assign(page, await listKnowledgeGraphCandidateGroups(props.knowledgeBaseId, query)); Object.assign(stats, await getKnowledgeGraphCandidateStats(props.knowledgeBaseId, { runId: query.runId || undefined })); } finally { loading.value = false; } }
function search() { query.pageNum = 1; load(); }
function reset() { Object.assign(query, { type: "", riskLevel: "", status: "PENDING", runId: null, conflictOnly: false, keyword: "", pageNum: 1 }); load(); }
async function openDetail(row) { Object.assign(detail, await getKnowledgeGraphCandidateGroup(props.knowledgeBaseId, row.id)); drawer.value = true; }
async function noteFor(rows, action) { const strict = rows.some((x) => x.risk_level === "LOW" || x.conflict_state !== "NONE"); if (!strict) return ""; const result = await ElMessageBox.prompt(`低可信或冲突候选${action === "APPROVE" ? "通过" : "拒绝"}必须填写审核说明`, "审核说明", { inputPattern: /.+/, inputErrorMessage: "审核说明不能为空" }); return result.value; }
async function submit(rows, action) { const note = await noteFor(rows, action); await reviewKnowledgeGraphCandidateGroups(props.knowledgeBaseId, { ids: rows.map((x) => x.id), action, note }); ElMessage.success("候选组审核完成"); await load(); }
function showAwaitingPublish() { query.status = "APPROVED"; query.pageNum = 1; load(); }
async function batchReview(action) { await submit(selected.value, action); }
async function singleReview(row, action) { await submit([row], action); }
async function mergeEntity(row) { const result = await ElMessageBox.prompt("请输入已通过或已合并的目标实体候选组 ID", "合并实体", { inputPattern: /^[1-9]\d*$/, inputErrorMessage: "请输入有效的候选组 ID" }); await reviewKnowledgeGraphCandidateGroups(props.knowledgeBaseId, { ids: [row.id], action: "MERGE", note: `合并至候选组 #${result.value}`, mergeTargetId: Number(result.value) }); ElMessage.success("实体候选组已合并"); await load(); }
async function publishRows(rows) { await ElMessageBox.confirm(`确认发布选中的 ${rows.length} 个候选组到 Neo4j？审核通过本身不会出现在图谱追溯中，只有发布成功后才会显示。`, "发布候选组", { type: "warning" }); const result = await publishKnowledgeGraphCandidateGroups(props.knowledgeBaseId, rows.map((x) => x.id)); if (result.failed) ElMessage.warning(`发布完成：成功 ${result.success}，跳过 ${result.skipped}，失败 ${result.failed}。请查看发布状态后重试。`); else ElMessage.success(`发布完成：成功 ${result.success}，跳过 ${result.skipped}`); await load(); }
async function publish() { await publishRows(publishable.value); }
async function deleteRows(rows) { await ElMessageBox.confirm(`确认永久删除选中的 ${rows.length} 个未发布候选组及其原始候选和来源关联？该操作不能恢复。`, "删除候选数据", { type: "warning" }); await deleteKnowledgeGraphCandidateGroups(props.knowledgeBaseId, rows.map((x) => x.id)); ElMessage.success("候选数据已删除"); selected.value = []; await load(); }
defineExpose({ refresh: load });
load();
</script>

<style scoped>
.candidate-review { display: grid; gap: 14px; }.stats-grid { display: grid; grid-template-columns: repeat(5, minmax(120px, 1fr)); gap: 12px; }.stat-card { padding: 14px 16px; border: 1px solid var(--el-border-color-lighter); border-radius: var(--ry-radius, 8px); background: var(--el-bg-color); display: grid; gap: 8px; }.stat-card.clickable { cursor:pointer; border-color:var(--el-color-primary-light-7); }.stat-card.clickable:hover { background:var(--el-color-primary-light-9); }.stat-card span { color: var(--ry-muted-foreground); }.stat-card strong { font-size: 24px; }.success { color: var(--el-color-success); }.warning { color: var(--el-color-warning); }.danger { color: var(--el-color-danger); }.panel-card { padding: 18px; }.toolbar,.batch-bar { display:flex;align-items:center;gap:10px;flex-wrap:wrap;margin-bottom:14px }.toolbar .el-select { width: 150px; }.toolbar .el-input { width: 220px; }.batch-bar { padding: 10px 12px; background: var(--el-fill-color-lighter); }.pagination { display:flex;justify-content:flex-end;margin-top:14px }.evidence-card { margin-top:12px;padding:14px;border:1px solid var(--el-border-color-lighter);border-radius:8px }.evidence-card>div { display:flex;align-items:center;gap:12px }.evidence-card span { color:var(--ry-muted-foreground) }.evidence-card p { white-space:pre-wrap;line-height:1.7;margin:10px 0 0 } @media(max-width:900px){.stats-grid{grid-template-columns:repeat(2,1fr)}}
</style>
