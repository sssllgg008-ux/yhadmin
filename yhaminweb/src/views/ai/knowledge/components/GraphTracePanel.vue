<template>
  <section class="trace-panel">
    <div class="trace-toolbar">
      <div class="query-tools">
        <el-input
          v-model="query.keyword"
          clearable
          placeholder="搜索实体或事实"
          @keyup.enter="loadGraph"
        />
        <el-select
          v-model="query.entityType"
          clearable
          placeholder="全部实体类型"
        >
          <el-option
            v-for="type in entityTypes"
            :key="type"
            :label="type"
            :value="type"
          />
        </el-select>
        <el-select v-model="query.limit" placeholder="显示数量">
          <el-option label="50 个节点" :value="50" />
          <el-option label="80 个节点" :value="80" />
          <el-option label="120 个节点" :value="120" />
          <el-option label="200 个节点" :value="200" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="loadGraph"
          >查询图谱</el-button
        >
        <el-button @click="reset">重置</el-button>
      </div>
      <div class="canvas-tools">
        <el-tooltip content="放大"
          ><el-button :icon="ZoomIn" @click="zoomBy(1.2)"
        /></el-tooltip>
        <el-tooltip content="缩小"
          ><el-button :icon="ZoomOut" @click="zoomBy(0.8)"
        /></el-tooltip>
        <el-tooltip content="适应画布"
          ><el-button :icon="FullScreen" @click="fitView"
        /></el-tooltip>
        <el-tooltip content="重新执行力导向布局"
          ><el-button :icon="Refresh" @click="restartLayout"
            >重新布局</el-button
          ></el-tooltip
        >
      </div>
    </div>

    <div class="trace-stats">
      <div>
        <span>实体</span><strong>{{ graphData.entityCount || 0 }}</strong>
      </div>
      <div>
        <span>事实</span><strong>{{ graphData.factCount || 0 }}</strong>
      </div>
      <div>
        <span>图关系</span><strong>{{ graphData.relationCount || 0 }}</strong>
      </div>
      <div>
        <span>显示范围</span
        ><strong>{{ graphData.nodes.length }} / {{ query.limit }}</strong>
      </div>
    </div>

    <div v-if="currentRoot" class="explore-bar ry-card">
      <div class="root-summary">
        <span>当前根节点</span><strong>{{ currentRoot.label }}</strong
        ><el-tag effect="plain">第 0 层</el-tag>
      </div>
      <div class="explore-options">
        <el-select v-model="explore.depth" aria-label="关系深度"
          ><el-option label="展开 1 层" :value="1" /><el-option
            label="展开 2 层"
            :value="2" /><el-option label="展开 3 层" :value="3"
        /></el-select>
        <el-select v-model="explore.direction" aria-label="关系方向"
          ><el-option label="双向关系" value="BOTH" /><el-option
            label="下游关系"
            value="OUT" /><el-option label="上游关系" value="IN"
        /></el-select>
        <el-select v-model="explore.layoutMode" aria-label="图谱布局"
          ><el-option label="放射布局" value="RADIAL" /><el-option
            label="层级布局"
            value="HIERARCHY"
        /></el-select>
        <el-select
          v-model="explore.relationTypes"
          multiple
          collapse-tags
          clearable
          placeholder="全部关系类型"
          ><el-option
            v-for="type in relationTypes"
            :key="type"
            :label="relationLabel(type)"
            :value="type"
        /></el-select>
        <el-button type="primary" :loading="loading" @click="loadNeighborhood"
          >展开关系</el-button
        >
        <el-button :disabled="history.length < 2" @click="backRoot"
          >返回上一步</el-button
        >
        <el-button @click="returnOverview">返回全图</el-button>
      </div>
    </div>

    <el-alert
      v-if="graphData.truncated"
      type="warning"
      :closable="false"
      show-icon
      title="节点数量超过显示上限，请使用关键词或实体类型缩小范围。"
    />
    <div v-loading="loading" class="graph-workspace ry-card">
      <div
        ref="containerRef"
        class="graph-container"
        :class="{ empty: !graphData.nodes.length }"
      />
      <el-empty
        v-if="!loading && !graphData.nodes.length"
        class="graph-empty"
        description="暂无已发布图谱数据"
      >
        <template #description
          ><p>
            请先在实体、关系或事实审核页通过并发布数据，再刷新图谱。
          </p></template
        >
      </el-empty>
      <template v-if="graphData.nodes.length">
        <div class="graph-legend">
          <span><i class="entity-dot" />实体</span>
          <span><i class="fact-dot" />事实</span>
          <span><i class="relation-line" />关系</span>
        </div>
        <div class="graph-hint">
          单击查看来源 · 双击设为根节点展开 · 第1～3层用不同颜色区分
        </div>
      </template>
    </div>

    <el-drawer
      v-model="drawer"
      title="图谱来源追溯"
      size="min(620px, 94vw)"
      @closed="clearSelection"
    >
      <template v-if="selected">
        <div class="trace-title">
          <el-tag>{{
            selected.kind === "RELATION"
              ? "关系"
              : selected.kind === "FACT"
                ? "事实"
                : "实体"
          }}</el-tag>
          <strong>{{ selected.label || relationLabel(selected.type) }}</strong>
        </div>
        <div v-if="selected.kind !== 'RELATION'" class="trace-actions">
          <el-button type="primary" @click="useSelectedAsRoot"
            >以此节点为根展开关系</el-button
          >
          <span>可继续选择 1～3 层、关系方向和布局方式</span>
        </div>
        <el-descriptions :column="1" border class="property-list">
          <el-descriptions-item
            v-for="(value, key) in provenance.properties"
            :key="key"
            :label="propertyLabel(key)"
            >{{ formatValue(value) }}</el-descriptions-item
          >
        </el-descriptions>
        <div class="source-heading">
          <h4>原始证据</h4>
          <span>共 {{ provenance.sources.length }} 个来源分块</span>
        </div>
        <el-empty
          v-if="!provenanceLoading && !provenance.sources.length"
          description="该图谱数据没有可追溯的有效分块"
        />
        <div v-loading="provenanceLoading" class="source-list">
          <article
            v-for="source in provenance.sources"
            :key="source.chunkId"
            class="source-card ry-card"
          >
            <header>
              <div class="source-identity">
                <strong>{{ source.documentName }}</strong>
                <el-tag size="small" effect="plain"
                  >V{{ source.versionNo }} · 分块 #{{ source.chunkNo }}</el-tag
                >
                <el-tag
                  v-if="removedChunkIds.has(Number(source.chunkId))"
                  size="small"
                  type="info"
                  >已移除 ES 索引</el-tag
                >
              </div>
              <div class="source-actions">
                <el-button
                  type="danger"
                  link
                  :loading="deletingDocumentId === Number(source.documentId)"
                  @click="removeDocumentGraph(source)"
                  >删除该文件图谱</el-button
                >
                <el-button
                  type="danger"
                  link
                  :loading="deletingChunkId === Number(source.chunkId)"
                  :disabled="removedChunkIds.has(Number(source.chunkId))"
                  @click="removeSourceIndex(source)"
                  >移除 ES 索引</el-button
                >
              </div>
            </header>
            <p class="source-meta">
              <span v-if="source.titlePath">{{ source.titlePath }}</span
              ><span v-if="source.pageNumber"
                >第 {{ source.pageNumber }} 页</span
              ><span>{{ source.versionStatus }}</span>
            </p>
            <div class="source-content">
              <template
                v-for="(part, index) in evidenceSegments(source)"
                :key="index"
                ><mark v-if="part.highlight">{{ part.text }}</mark
                ><span v-else>{{ part.text }}</span></template
              >
            </div>
            <p v-if="source.evidenceRanges?.length" class="evidence-tip">
              已高亮 {{ source.evidenceRanges.length }} 处审核证据
            </p>
          </article>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup>
import G6 from "@antv/g6";
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { FullScreen, Refresh, ZoomIn, ZoomOut } from "@element-plus/icons-vue";
import {
  getKnowledgeGraphNeighborhood,
  getKnowledgeGraphProvenance,
  getKnowledgeGraphView,
  deleteKnowledgeGraphByDocument,
  deleteKnowledgeChunkIndex,
} from "@/api/ai";

const props = defineProps({
  knowledgeBaseId: { type: Number, required: true },
  entityTypes: { type: Array, default: () => [] },
  relationTypes: { type: Array, default: () => [] },
});
const containerRef = ref(null);
const loading = ref(false),
  provenanceLoading = ref(false),
  drawer = ref(false),
  selected = ref(null);
const query = reactive({ keyword: "", entityType: "", limit: 80 });
const explore = reactive({
  depth: 1,
  direction: "BOTH",
  layoutMode: "RADIAL",
  relationTypes: [],
  perLevelLimit: 30,
  totalLimit: 120,
});
const graphData = reactive({
  nodes: [],
  edges: [],
  entityCount: 0,
  factCount: 0,
  relationCount: 0,
  truncated: false,
});
const provenance = reactive({ properties: {}, sources: [] });
const removedChunkIds = reactive(new Set());
const deletingChunkId = ref(null);
const deletingDocumentId = ref(null);
const currentRoot = ref(null);
const history = ref([]);
let graphInstance = null;
let resizeObserver = null;

const relationLabel = (type) =>
  ({
    RELATED_TO: "相关",
    BELONGS_TO: "属于",
    USES: "使用",
    PRODUCES: "产生/生产",
    LOCATED_IN: "位于",
    PART_OF: "组成部分",
    MENTIONS: "提及",
    ASSERTS: "声明",
    SUPPORTED_BY: "证据来源",
  })[type] || type;
const propertyLabel = (key) =>
  ({
    canonicalName: "规范名称",
    entityType: "实体类型",
    content: "事实内容",
    status: "状态",
    schemaVersion: "Schema 版本",
    sourceChunkIds: "来源分块",
    documentVersionIds: "文档版本",
    createdTime: "创建时间",
    updatedTime: "更新时间",
    semanticKey: "语义键",
  })[key] || key;
const compact = (value, length) =>
  String(value || "").length > length
    ? `${String(value).slice(0, length)}…`
    : String(value || "");
const formatValue = (value) =>
  Array.isArray(value)
    ? value.join("、")
    : value && typeof value === "object"
      ? JSON.stringify(value)
      : String(value ?? "-");

function graphSize() {
  return {
    width: Math.max(320, containerRef.value?.clientWidth || 1000),
    height: Math.max(500, containerRef.value?.clientHeight || 580),
  };
}

function createGraph() {
  if (graphInstance || !containerRef.value) return;
  const { width, height } = graphSize();
  graphInstance = new G6.Graph({
    container: containerRef.value,
    width,
    height,
    fitView: true,
    fitViewPadding: 48,
    minZoom: 0.15,
    maxZoom: 4,
    modes: {
      default: ["drag-canvas", "zoom-canvas", "drag-node"],
    },
    layout: layoutConfig(),
    defaultNode: {
      type: "circle",
      size: 58,
      style: {
        fill: "#409eff",
        stroke: "#fff",
        lineWidth: 3,
        shadowColor: "rgba(31,45,61,.2)",
        shadowBlur: 10,
      },
      labelCfg: {
        position: "center",
        style: { fill: "#fff", fontSize: 11, cursor: "pointer" },
      },
    },
    defaultEdge: {
      type: "line",
      style: {
        stroke: "#a8b1c2",
        lineWidth: 1.4,
        endArrow: { path: G6.Arrow.triangle(7, 9, 4), fill: "#a8b1c2" },
      },
      labelCfg: {
        autoRotate: true,
        refY: -7,
        style: {
          fill: "#697386",
          fontSize: 11,
          background: { fill: "#fff", padding: [2, 4, 2, 4], radius: 2 },
        },
      },
    },
    nodeStateStyles: {
      selected: { lineWidth: 5, stroke: "#f5a623" },
    },
    edgeStateStyles: {
      selected: { stroke: "#f5a623", lineWidth: 2.6 },
    },
  });
  graphInstance.on("node:click", (event) => selectNode(event.item?.getModel()));
  graphInstance.on("node:dblclick", (event) =>
    setRootFromModel(event.item?.getModel()),
  );
  graphInstance.on("edge:click", (event) => selectEdge(event.item?.getModel()));
  graphInstance.on("canvas:click", clearSelection);
  resizeObserver = new ResizeObserver(() => {
    if (!graphInstance || graphInstance.get("destroyed")) return;
    const size = graphSize();
    graphInstance.changeSize(size.width, size.height);
  });
  resizeObserver.observe(containerRef.value);
}

function layoutConfig() {
  if (!currentRoot.value)
    return {
      type: "force",
      preventOverlap: true,
      nodeSpacing: 28,
      linkDistance: 145,
      nodeStrength: -80,
      edgeStrength: 0.15,
      collideStrength: 0.9,
      alphaDecay: 0.035,
      alphaMin: 0.01,
    };
  if (explore.layoutMode === "HIERARCHY")
    return {
      type: "dagre",
      rankdir: "LR",
      nodesep: 42,
      ranksep: 100,
      controlPoints: true,
    };
  return {
    type: "radial",
    focusNode: String(currentRoot.value.id),
    unitRadius: 150,
    linkDistance: 150,
    preventOverlap: true,
    nodeSpacing: 30,
    strictRadial: true,
  };
}

function hopColor(node) {
  if (node.root) return "#f59e0b";
  if (node.kind === "FACT") return "#67c23a";
  return (
    { 1: "#409eff", 2: "#7b61ff", 3: "#9c6ade" }[Number(node.hop)] || "#409eff"
  );
}

function transformData() {
  return {
    nodes: graphData.nodes.map((node) => ({
      id: String(node.id),
      rawId: node.id,
      rawLabel: node.label,
      kind: node.kind || "ENTITY",
      properties: node.properties || {},
      size: node.kind === "FACT" ? 50 : 60,
      hop: Number(node.hop || 0),
      neighborCount: node.neighborCount || 0,
      loadedNeighborCount: node.loadedNeighborCount || 0,
      expandable: !!node.expandable,
      label: `${compact(node.label, node.kind === "FACT" ? 12 : 10)}${node.expandable ? ` +${Math.max(0, (node.neighborCount || 0) - (node.loadedNeighborCount || 0))}` : ""}`,
      style: {
        fill: hopColor(node),
        stroke: node.root ? "#fff3cd" : "#fff",
        lineWidth: node.root ? 5 : 3,
        shadowColor: "rgba(31,45,61,.2)",
        shadowBlur: node.root ? 16 : 10,
      },
    })),
    edges: graphData.edges.map((edge) => ({
      id: String(edge.id),
      rawId: edge.id,
      source: String(edge.source),
      target: String(edge.target),
      kind: "RELATION",
      type: edge.type,
      label: relationLabel(edge.type),
      properties: edge.properties || {},
    })),
  };
}

async function renderGraph() {
  await nextTick();
  createGraph();
  if (!graphInstance) return;
  graphInstance.clear();
  if (!graphData.nodes.length) return;
  graphInstance.updateLayout(layoutConfig());
  graphInstance.data(transformData());
  graphInstance.render();
  window.setTimeout(() => {
    if (graphInstance && !graphInstance.get("destroyed"))
      graphInstance.fitView(48);
  }, 500);
}

async function loadGraph() {
  loading.value = true;
  try {
    Object.assign(
      graphData,
      await getKnowledgeGraphView(props.knowledgeBaseId, query),
    );
    await renderGraph();
  } catch (error) {
    ElMessage.error(error?.message || "图谱加载失败");
  } finally {
    loading.value = false;
  }
}

async function loadNeighborhood() {
  if (!currentRoot.value) return;
  loading.value = true;
  try {
    const data = await getKnowledgeGraphNeighborhood(props.knowledgeBaseId, {
      rootElementId: currentRoot.value.id,
      depth: explore.depth,
      direction: explore.direction,
      relationTypes: explore.relationTypes.join(","),
      perLevelLimit: explore.perLevelLimit,
      totalLimit: explore.totalLimit,
      layoutMode: explore.layoutMode,
    });
    Object.assign(graphData, data, {
      entityCount: (data.nodes || []).filter((x) => x.kind === "ENTITY").length,
      factCount: (data.nodes || []).filter((x) => x.kind === "FACT").length,
      relationCount: (data.edges || []).length,
    });
    await renderGraph();
  } catch (error) {
    ElMessage.error(error?.message || "关系层级加载失败");
  } finally {
    loading.value = false;
  }
}

function setRoot(item, pushHistory = true) {
  if (!item?.id) return;
  currentRoot.value = {
    id: item.id,
    label: item.label || item.rawLabel || "未命名节点",
    kind: item.kind || "ENTITY",
  };
  if (pushHistory && history.value.at(-1)?.id !== item.id)
    history.value.push({ ...currentRoot.value });
  loadNeighborhood();
}
function setRootFromModel(model) {
  if (model)
    setRoot({ id: model.rawId, label: model.rawLabel, kind: model.kind });
}
function useSelectedAsRoot() {
  if (selected.value) {
    drawer.value = false;
    setRoot(selected.value);
  }
}
function backRoot() {
  if (history.value.length < 2) return;
  history.value.pop();
  setRoot(history.value.at(-1), false);
}
function returnOverview() {
  currentRoot.value = null;
  history.value = [];
  loadGraph();
}

function reset() {
  Object.assign(query, { keyword: "", entityType: "", limit: 80 });
  returnOverview();
}
function zoomBy(ratio) {
  if (!graphInstance || !graphData.nodes.length) return;
  const point = { x: graphSize().width / 2, y: graphSize().height / 2 };
  graphInstance.zoom(ratio, point);
}
function fitView() {
  if (graphInstance && graphData.nodes.length) graphInstance.fitView(48);
}
function restartLayout() {
  if (!graphInstance || !graphData.nodes.length) return;
  graphInstance.layout();
  window.setTimeout(fitView, 500);
}
function clearSelection() {
  if (graphInstance && !graphInstance.get("destroyed"))
    graphInstance
      .findAllByState("node", "selected")
      .concat(graphInstance.findAllByState("edge", "selected"))
      .forEach((item) => graphInstance.setItemState(item, "selected", false));
  selected.value = null;
}

async function showProvenance(item, graphItem) {
  clearSelection();
  if (graphItem && graphInstance)
    graphInstance.setItemState(graphItem, "selected", true);
  selected.value = item;
  drawer.value = true;
  provenanceLoading.value = true;
  Object.assign(provenance, { properties: item.properties || {}, sources: [] });
  try {
    Object.assign(
      provenance,
      await getKnowledgeGraphProvenance(props.knowledgeBaseId, {
        kind: item.kind,
        elementId: item.id,
      }),
    );
  } catch (error) {
    ElMessage.error(error?.message || "来源追溯失败");
  } finally {
    provenanceLoading.value = false;
  }
}

function selectNode(model) {
  if (!model) return;
  const graphItem = graphInstance.findById(model.id);
  showProvenance(
    {
      id: model.rawId,
      kind: model.kind,
      label: model.rawLabel,
      properties: model.properties,
    },
    graphItem,
  );
}
function selectEdge(model) {
  if (!model) return;
  const graphItem = graphInstance.findById(model.id);
  showProvenance(
    {
      id: model.rawId,
      kind: "RELATION",
      type: model.type,
      label: relationLabel(model.type),
      properties: model.properties,
    },
    graphItem,
  );
}

function evidenceSegments(source) {
  const content = String(source?.content || "");
  if (!content) return [];
  const ranges = (source?.evidenceRanges || [])
    .map((item) => {
      let start = Number(item.start),
        end = Number(item.end);
      if (
        !Number.isFinite(start) ||
        !Number.isFinite(end) ||
        start < 0 ||
        end <= start ||
        end > content.length
      ) {
        const text = String(item.text || "");
        start = text ? content.indexOf(text) : -1;
        end = start < 0 ? -1 : start + text.length;
      }
      return { start, end };
    })
    .filter((x) => x.start >= 0 && x.end > x.start)
    .sort((a, b) => a.start - b.start);
  if (!ranges.length) return [{ text: content, highlight: false }];
  const merged = [];
  for (const range of ranges) {
    const last = merged.at(-1);
    if (last && range.start <= last.end)
      last.end = Math.max(last.end, range.end);
    else merged.push({ ...range });
  }
  const parts = [];
  let cursor = 0;
  for (const range of merged) {
    if (range.start > cursor)
      parts.push({
        text: content.slice(cursor, range.start),
        highlight: false,
      });
    parts.push({
      text: content.slice(range.start, range.end),
      highlight: true,
    });
    cursor = range.end;
  }
  if (cursor < content.length)
    parts.push({ text: content.slice(cursor), highlight: false });
  return parts;
}

async function removeSourceIndex(source) {
  const chunkId = Number(source?.chunkId);
  const documentId = Number(source?.documentId);
  if (!chunkId || !documentId) {
    ElMessage.error("来源分块信息不完整，无法删除索引");
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认从当前活动 Elasticsearch 索引中移除“${source.documentName}”V${source.versionNo} 的分块 #${source.chunkNo}？原始文件、Markdown、MySQL 分块和 Neo4j 图谱不会删除，可通过重新索引恢复。`,
      "移除 Elasticsearch 索引",
      {
        type: "warning",
        confirmButtonText: "确认移除",
        cancelButtonText: "取消",
        distinguishCancelAndClose: true,
      },
    );
  } catch (action) {
    if (action === "cancel" || action === "close") return;
    throw action;
  }
  deletingChunkId.value = chunkId;
  try {
    const result = await deleteKnowledgeChunkIndex(
      props.knowledgeBaseId,
      documentId,
      chunkId,
    );
    removedChunkIds.add(chunkId);
    ElMessage.success(`已移除 ${result?.deletedDocuments || 0} 条 ES 索引记录`);
  } catch (error) {
    if (error !== "cancel" && error !== "close") {
      ElMessage.error(error?.message || "移除 Elasticsearch 索引失败");
    }
  } finally {
    deletingChunkId.value = null;
  }
}

async function removeDocumentGraph(source) {
  const documentId = Number(source?.documentId);
  if (!documentId) {
    ElMessage.error("来源文档信息不完整，无法删除知识图谱");
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认删除文件“${source.documentName}”产生的全部知识图谱？系统会移除该文件对实体、关系和事实的来源支持；仍有其他文件支持的知识会保留。原始文件、Markdown、MySQL 分块和 Elasticsearch 索引不会删除。`,
      "按文件删除知识图谱",
      {
        type: "warning",
        confirmButtonText: "确认删除图谱",
        cancelButtonText: "取消",
        distinguishCancelAndClose: true,
      },
    );
  } catch (action) {
    if (action === "cancel" || action === "close") return;
    throw action;
  }
  deletingDocumentId.value = documentId;
  try {
    const result = await deleteKnowledgeGraphByDocument(
      props.knowledgeBaseId,
      documentId,
    );
    ElMessage.success(
      `文件图谱已删除：移除 ${result?.deletedNodes || 0} 个独占节点、${result?.deletedRelations || 0} 条独占关系`,
    );
    drawer.value = false;
    await loadGraph();
  } catch (error) {
    ElMessage.error(error?.message || "按文件删除知识图谱失败");
  } finally {
    deletingDocumentId.value = null;
  }
}

defineExpose({ refresh: loadGraph });
onMounted(loadGraph);
onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  if (graphInstance && !graphInstance.get("destroyed")) graphInstance.destroy();
  graphInstance = null;
});
</script>

<style scoped>
.trace-panel {
  display: grid;
  gap: 14px;
}
.trace-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.query-tools,
.canvas-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.query-tools .el-input {
  width: 260px;
}
.query-tools .el-select {
  width: 180px;
}
.canvas-tools {
  padding-left: 12px;
  border-left: 1px solid var(--el-border-color-lighter);
}
.trace-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.trace-stats > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--ry-radius, 8px);
  background: var(--el-bg-color);
}
.trace-stats span {
  color: var(--ry-muted-foreground);
}
.trace-stats strong {
  font-size: 22px;
}
.explore-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 12px 14px;
}
.root-summary,
.explore-options {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.root-summary span {
  color: var(--ry-muted-foreground);
}
.root-summary strong {
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.explore-options .el-select {
  width: 145px;
}
.explore-options .el-select:last-of-type {
  width: 190px;
}
.graph-workspace {
  position: relative;
  min-height: 580px;
  overflow: hidden;
  padding: 0;
  background: radial-gradient(
    circle at center,
    #fff 0,
    #fafcff 70%,
    #f5f7fa 100%
  );
}
.graph-container {
  width: 100%;
  height: 580px;
}
.graph-container.empty {
  visibility: hidden;
}
.graph-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.graph-legend,
.graph-hint {
  position: absolute;
  z-index: 2;
  padding: 8px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--ry-muted-foreground);
  font-size: 12px;
  box-shadow: 0 2px 8px rgba(31, 45, 61, 0.06);
}
.graph-legend {
  left: 16px;
  bottom: 14px;
  display: flex;
  gap: 18px;
}
.graph-hint {
  right: 16px;
  bottom: 14px;
}
.graph-legend span {
  display: flex;
  align-items: center;
  gap: 6px;
}
.graph-legend i {
  display: inline-block;
}
.entity-dot,
.fact-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
.entity-dot {
  background: #409eff;
}
.fact-dot {
  background: #67c23a;
}
.relation-line {
  width: 18px;
  height: 2px;
  background: #a8b1c2;
}
.trace-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  font-size: 17px;
}
.trace-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
}
.trace-actions span,
.evidence-tip {
  color: var(--ry-muted-foreground);
  font-size: 12px;
}
.property-list {
  margin-bottom: 20px;
}
.source-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.source-heading h4 {
  margin: 0;
}
.source-heading span,
.source-meta {
  color: var(--ry-muted-foreground);
  font-size: 12px;
}
.source-list {
  display: grid;
  gap: 12px;
}
.source-card {
  padding: 14px;
}
.source-card header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.source-identity {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.source-identity strong {
  min-width: 0;
  overflow-wrap: anywhere;
}
.source-actions {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 4px;
}
.source-meta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.source-content {
  max-height: 260px;
  overflow: auto;
  padding: 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  white-space: pre-wrap;
  line-height: 1.7;
}
.source-content mark {
  padding: 1px 2px;
  border-radius: 2px;
  background: #fff1a8;
  color: inherit;
  box-shadow: 0 0 0 1px #f5d76e;
}
.evidence-tip {
  margin: 8px 0 0;
}
@media (max-width: 1200px) {
  .explore-bar {
    align-items: flex-start;
    flex-direction: column;
  }
  .explore-options {
    width: 100%;
  }
}
@media (max-width: 1100px) {
  .trace-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
  .canvas-tools {
    padding-left: 0;
    border-left: 0;
  }
  .graph-hint {
    display: none;
  }
}
@media (max-width: 900px) {
  .trace-stats {
    grid-template-columns: repeat(2, 1fr);
  }
  .query-tools {
    width: 100%;
  }
  .query-tools .el-input,
  .query-tools .el-select,
  .explore-options .el-select,
  .explore-options .el-select:last-of-type {
    width: 100%;
  }
  .graph-workspace,
  .graph-container {
    min-height: 500px;
    height: 500px;
  }
}
@media (max-width: 520px) {
  .trace-stats {
    grid-template-columns: 1fr 1fr;
  }
  .source-card header,
  .trace-actions {
    align-items: flex-start;
    flex-direction: column;
  }
  .source-actions {
    width: 100%;
    justify-content: flex-end;
  }
  .canvas-tools {
    width: 100%;
  }
}
</style>
