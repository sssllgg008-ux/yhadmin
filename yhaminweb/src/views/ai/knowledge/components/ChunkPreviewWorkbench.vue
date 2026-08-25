<template>
  <div
    v-if="result?.chunks"
    class="chunk-workbench"
    tabindex="0"
    @keydown.left.prevent="move(-1)"
    @keydown.right.prevent="move(1)"
  >
    <section class="preview-summary">
      <article class="summary-card ry-card">
        <span>分片数量</span><strong>{{ result.chunkCount }}</strong
        ><small>当前预览结果</small>
      </article>
      <article class="summary-card ry-card">
        <span>总 Token</span><strong>{{ result.totalTokens }}</strong
        ><small>全部分片合计</small>
      </article>
      <article class="summary-card ry-card">
        <span>平均 Token</span><strong>{{ result.averageTokens }}</strong
        ><small>单个分片平均值</small>
      </article>
      <article class="summary-card ry-card">
        <span>Token 范围</span
        ><strong>{{ result.minTokens }}–{{ result.maxTokens }}</strong
        ><small>最小值至最大值</small>
      </article>
      <article class="summary-card ry-card" :class="{ warning: warningCount }">
        <span>异常分片</span><strong>{{ warningCount }}</strong
        ><small>{{ warningCount ? "建议重点检查" : "未发现结构异常" }}</small>
      </article>
    </section>

    <section class="preview-toolbar ry-card">
      <div class="toolbar-context">
        <el-tag effect="plain">{{ strategyLabel }}</el-tag>
        <span
          >当前第 <b>{{ activeChunk?.chunkNo || 0 }}</b> /
          {{ result.chunkCount }} 片</span
        >
      </div>
      <div class="toolbar-actions">
        <el-button
          v-if="warningCount && !result.optimizationApplied"
          type="warning"
          plain
          @click="$emit('optimize')"
          >自动优化异常分片</el-button
        >
        <el-button :disabled="activeIndex <= 0" @click="move(-1)"
          >上一片</el-button
        >
        <el-button
          type="primary"
          plain
          :disabled="activeIndex >= result.chunks.length - 1"
          @click="move(1)"
          >下一片</el-button
        >
      </div>
    </section>

    <el-alert
      v-if="result.optimizationApplied"
      class="optimization-result"
      type="success"
      :closable="false"
      show-icon
    >
      <template #title>已完成本次预览优化</template>
      <div>
        分片 {{ result.beforeChunkCount }} → {{ result.chunkCount }}，异常
        {{ result.beforeWarningCount }} →
        {{ result.afterWarningCount }}，调整边界
        {{ result.changedBoundaryCount }} 处。
      </div>
      <div v-if="result.afterWarningCount">
        仍有
        {{ result.afterWarningCount }}
        个分片无法安全修复，请查看橙色标记及原因。
      </div>
    </el-alert>

    <div class="preview-layout">
      <section class="source-panel ry-card">
        <header class="panel-header">
          <div class="panel-title">
            <b>原文定位</b>
            <small>蓝色为当前分片，黄色为重叠内容</small>
          </div>
          <div class="source-tools">
            <span v-if="activeChunk" class="range-label">{{
              rangeText(activeChunk)
            }}</span>
            <el-radio-group v-model="sourceMode" size="small">
              <el-radio-button value="source">Markdown 源码</el-radio-button>
              <el-radio-button value="rendered">渲染预览</el-radio-button>
            </el-radio-group>
          </div>
        </header>
        <div v-if="sourceMode === 'source'" ref="sourceRef" class="source-code">
          <div
            v-for="line in sourceLines"
            :key="line.no"
            :data-line="line.no"
            class="source-line"
            :class="lineClass(line)"
            @click="selectByOffset(line.start)"
          >
            <span class="line-no">{{ line.no }}</span
            ><code>{{ line.text || " " }}</code>
          </div>
        </div>
        <div v-else class="rendered-source">
          <MarkdownPreview
            :content="result.normalizedText"
            :knowledge-base-id="knowledgeBaseId"
            :document-id="documentId"
            :version-id="versionId"
          />
        </div>
      </section>

      <section class="result-panel ry-card">
        <header class="panel-header result-header">
          <div class="panel-title">
            <b>分片详情</b>
            <small>点击编号切换，异常分片带有橙色标记</small>
          </div>
          <span>{{ result.chunkCount }} 个分片</span>
        </header>

        <nav ref="chunkListRef" class="chunk-index-list" aria-label="分片导航">
          <button
            v-for="chunk in result.chunks"
            :key="chunk.chunkNo"
            :data-chunk="chunk.chunkNo"
            type="button"
            class="chunk-index"
            :class="{
              active: chunk.chunkNo === activeChunk?.chunkNo,
              warning: chunk.warnings?.length,
            }"
            :title="`第 ${chunk.chunkNo} 片，${chunk.tokenCount} Token`"
            @click="selectChunk(chunk)"
          >
            <span>#{{ chunk.chunkNo }}</span>
            <small>{{ chunk.tokenCount }}</small>
          </button>
        </nav>

        <article v-if="activeChunk" class="chunk-detail">
          <header class="detail-header">
            <div>
              <span>当前分片</span><strong>#{{ activeChunk.chunkNo }}</strong>
            </div>
            <el-tag
              :type="activeChunk.warnings?.length ? 'warning' : 'success'"
              effect="light"
            >
              {{ activeChunk.tokenCount }} Token
            </el-tag>
          </header>

          <div v-if="activeChunk.titlePath?.length" class="title-path">
            <span>标题路径</span>
            <b>{{ activeChunk.titlePath.join(" / ") }}</b>
          </div>

          <dl class="detail-grid">
            <div>
              <dt>原文范围</dt>
              <dd>{{ rangeText(activeChunk) }}</dd>
            </div>
            <div>
              <dt>切分边界</dt>
              <dd>{{ reasonLabel(activeChunk.boundaryReason) }}</dd>
            </div>
            <div>
              <dt>前后重叠</dt>
              <dd>
                {{ activeChunk.previousOverlapTokens || 0 }} /
                {{ activeChunk.nextOverlapTokens || 0 }} Token
              </dd>
            </div>
          </dl>

          <div class="types">
            <el-tag
              v-for="type in activeChunk.structureTypes"
              :key="type"
              size="small"
              type="info"
              effect="plain"
            >
              {{ typeLabel(type) }}
            </el-tag>
          </div>
          <el-alert
            v-if="activeChunk.warnings?.length"
            class="warning-alert"
            type="warning"
            :closable="false"
            :title="activeChunk.warnings.map(warningLabel).join('；')"
          />
          <div class="content-title">分片正文</div>
          <pre class="chunk-content">{{ activeChunk.content }}</pre>
        </article>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from "vue";
import MarkdownPreview from "./MarkdownPreview.vue";

const props = defineProps({
  result: { type: Object, default: null },
  strategy: { type: String, default: "SMART_MARKDOWN" },
  knowledgeBaseId: { type: [Number, String], default: undefined },
  documentId: { type: [Number, String], default: undefined },
  versionId: { type: [Number, String], default: undefined },
});
defineEmits(["optimize"]);

const sourceMode = ref("source");
const activeChunk = ref();
const sourceRef = ref();
const chunkListRef = ref();
const strategyLabels = {
  SMART_MARKDOWN: "智能 Markdown",
  FIXED_TOKEN: "固定 Token",
  REGEX: "正则表达式",
  CUSTOM_SEPARATOR: "自定义分隔符",
  PAGE: "按页面",
};
const strategyLabel = computed(
  () => strategyLabels[props.strategy] || props.strategy,
);
const warningCount = computed(
  () =>
    props.result?.chunks?.filter((item) => item.warnings?.length).length || 0,
);
const activeIndex = computed(
  () =>
    props.result?.chunks?.findIndex(
      (item) => item.chunkNo === activeChunk.value?.chunkNo,
    ) ?? -1,
);
const sourceLines = computed(() => {
  let offset = 0;
  return (props.result?.normalizedText || "").split("\n").map((text, index) => {
    const line = {
      no: index + 1,
      text,
      start: offset,
      end: offset + text.length,
    };
    offset += text.length + 1;
    return line;
  });
});

watch(
  () => props.result,
  () => {
    activeChunk.value = props.result?.chunks?.[0];
    sourceMode.value = "source";
    nextTick(scrollToActive);
  },
  { immediate: true },
);

function intersects(line, range) {
  return range.startOffset < line.end + 1 && range.endOffset > line.start;
}

function lineClass(line) {
  if (!activeChunk.value) return {};
  const current = activeChunk.value.sourceRanges?.some((range) =>
    intersects(line, range),
  );
  if (!current) return {};
  const overlap =
    (activeChunk.value.previousOverlapTokens ||
      activeChunk.value.nextOverlapTokens) &&
    props.result.chunks.some(
      (item) =>
        item.chunkNo !== activeChunk.value.chunkNo &&
        item.sourceRanges?.some((range) => intersects(line, range)),
    );
  return { selected: true, overlap };
}

function selectByOffset(offset) {
  const candidates = (props.result?.chunks || []).filter((item) =>
    item.sourceRanges?.some(
      (range) => offset >= range.startOffset && offset < range.endOffset,
    ),
  );
  if (candidates.length) selectChunk(candidates[0], false);
}

function selectChunk(chunk, scrollSource = true) {
  activeChunk.value = chunk;
  nextTick(() => {
    if (scrollSource && sourceMode.value === "source") {
      sourceRef.value
        ?.querySelector(`[data-line="${chunk.sourceRanges?.[0]?.startLine}"]`)
        ?.scrollIntoView({ block: "center" });
    }
    chunkListRef.value
      ?.querySelector(`[data-chunk="${chunk.chunkNo}"]`)
      ?.scrollIntoView({ block: "nearest", inline: "nearest" });
  });
}

function scrollToActive() {
  if (activeChunk.value) selectChunk(activeChunk.value);
}

function move(step) {
  const chunks = props.result?.chunks || [];
  const next = Math.min(
    chunks.length - 1,
    Math.max(0, activeIndex.value + step),
  );
  if (chunks[next]) selectChunk(chunks[next]);
}

function rangeText(chunk) {
  const ranges = chunk.sourceRanges || [];
  if (!ranges.length) return "未映射";
  return ranges
    .map((range) => `第 ${range.startLine}–${range.endLine} 行`)
    .join("，");
}

function reasonLabel(value) {
  return (
    {
      STRUCTURE: "结构边界",
      MAX_TOKEN: "达到 Token 上限",
      REGEX: "正则边界",
      SEPARATOR: "自定义分隔符",
      PAGE: "页面边界",
      END: "文档结尾",
      MIN_TOKEN_MERGE: "合并过小分片",
    }[value] ||
    value ||
    "-"
  );
}

function typeLabel(value) {
  return (
    {
      HEADING: "标题",
      PARAGRAPH: "段落",
      TABLE: "表格",
      CODE: "代码",
      FORMULA: "公式",
      IMAGE: "图片",
      LIST: "列表",
      PAGE_BREAK: "分页",
    }[value] || value
  );
}

function warningLabel(value) {
  return (
    {
      BELOW_MIN_TOKENS: "低于最小 Token",
      ABOVE_MAX_TOKENS: "超过最大 Token",
      SOURCE_MAPPING_MISSING: "原文映射缺失",
      UNMERGEABLE_BELOW_MIN: "无法在 Token 上限内与相邻分片安全合并",
      PROTECTED_STRUCTURE_OVER_MAX:
        "完整表格、代码、公式或图片结构禁止自动拆分",
    }[value] || value
  );
}
</script>

<style scoped>
.chunk-workbench {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 12px;
  outline: none;
}

.preview-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  display: flex;
  min-width: 0;
  min-height: 92px;
  flex-direction: column;
  padding: 13px 15px;
}

.summary-card span,
.summary-card small {
  color: var(--ry-muted-foreground, var(--el-text-color-secondary));
}

.summary-card span {
  font-size: 13px;
}
.summary-card strong {
  overflow: hidden;
  margin: 7px 0 4px;
  color: var(--ry-foreground, var(--el-text-color-primary));
  font-size: 22px;
  line-height: 1;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.summary-card small {
  font-size: 11px;
}
.summary-card.warning strong,
.summary-card.warning small {
  color: var(--el-color-warning);
}

.preview-toolbar {
  display: flex;
  min-height: 48px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px;
}

.toolbar-context,
.toolbar-actions,
.source-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.toolbar-context span {
  color: var(--ry-muted-foreground, var(--el-text-color-secondary));
  font-size: 13px;
}
.toolbar-context b {
  color: var(--el-color-primary);
}

.preview-layout {
  display: grid;
  grid-template-columns: minmax(0, 55fr) minmax(390px, 45fr);
  gap: 12px;
  height: min(650px, 66vh);
  min-height: 480px;
}
.optimization-result {
  margin: 0;
}
.optimization-result div + div {
  margin-top: 4px;
}

.source-panel,
.result-panel {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  display: flex;
  min-height: 54px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 14px;
  border-bottom: 1px solid var(--ry-border-light, var(--el-border-color-light));
  background: var(--ry-card, var(--el-bg-color));
}

.panel-title {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}
.panel-title b {
  color: var(--ry-foreground, var(--el-text-color-primary));
  font-size: 14px;
}
.panel-title small,
.result-header > span,
.range-label {
  color: var(--ry-muted-foreground, var(--el-text-color-secondary));
  font-size: 12px;
}

.source-code,
.rendered-source {
  min-height: 0;
  flex: 1;
  overflow: auto;
}

.source-code {
  padding: 10px 0 24px;
  background: var(--ry-neutral-50, #fbfcfe);
  font: 13px/1.7 var(--ry-font-mono, Consolas, "Courier New", monospace);
}

.source-line {
  display: flex;
  min-height: 24px;
  cursor: pointer;
  transition: background-color 0.15s ease;
}
.source-line:hover {
  background: var(--el-fill-color);
}
.source-line.selected {
  background: var(--el-color-primary-light-8);
}
.source-line.selected.overlap {
  background: var(--el-color-warning-light-8);
}
.line-no {
  flex: 0 0 54px;
  padding-right: 12px;
  color: var(--el-text-color-placeholder);
  text-align: right;
  user-select: none;
}
.source-line code {
  min-width: 0;
  padding-right: 14px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.rendered-source {
  padding: 10px 18px 30px;
}

.result-panel {
  background: var(--ry-card, var(--el-bg-color));
}
.chunk-index-list {
  display: flex;
  max-height: 116px;
  flex: 0 0 auto;
  flex-wrap: wrap;
  align-content: flex-start;
  gap: 7px;
  overflow-y: auto;
  padding: 10px 12px;
  border-bottom: 1px solid var(--ry-border-light, var(--el-border-color-light));
  background: var(--el-fill-color-extra-light);
}

.chunk-index {
  position: relative;
  display: inline-flex;
  height: 32px;
  align-items: center;
  gap: 6px;
  padding: 0 9px;
  border: 1px solid var(--ry-border, var(--el-border-color));
  border-radius: var(--ry-radius-medium, 6px);
  background: var(--ry-card, var(--el-bg-color));
  color: var(--ry-foreground, var(--el-text-color-regular));
  cursor: pointer;
  transition: all 0.18s ease;
}
.chunk-index span {
  color: var(--el-color-primary);
  font-weight: 600;
}
.chunk-index small {
  color: var(--ry-muted-foreground, var(--el-text-color-secondary));
  font-size: 11px;
}
.chunk-index:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}
.chunk-index.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  box-shadow: 0 0 0 1px var(--el-color-primary-light-7);
}
.chunk-index.warning::after {
  position: absolute;
  top: -3px;
  right: -3px;
  width: 7px;
  height: 7px;
  border: 2px solid var(--ry-card, #fff);
  border-radius: 50%;
  background: var(--el-color-warning);
  content: "";
}

.chunk-detail {
  min-height: 0;
  flex: 1;
  overflow: auto;
  padding: 16px;
}
.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid
    var(--ry-border-light, var(--el-border-color-lighter));
}
.detail-header > div {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.detail-header span,
.title-path span,
.detail-grid dt {
  color: var(--ry-muted-foreground, var(--el-text-color-secondary));
  font-size: 12px;
}
.detail-header strong {
  color: var(--el-color-primary);
  font-size: 22px;
}
.title-path {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin: 14px 0;
}
.title-path b {
  color: var(--ry-foreground, var(--el-text-color-primary));
  font-size: 14px;
}
.detail-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 12px 0;
}
.detail-grid > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;
  padding: 10px;
  border-radius: var(--ry-radius-medium, 6px);
  background: var(--el-fill-color-lighter);
}
.detail-grid dt,
.detail-grid dd {
  margin: 0;
}
.detail-grid dd {
  overflow-wrap: anywhere;
  color: var(--ry-foreground, var(--el-text-color-primary));
  font-size: 12px;
  font-weight: 600;
}
.types {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 10px 0;
}
.warning-alert {
  margin: 10px 0;
}
.content-title {
  margin: 16px 0 8px;
  color: var(--ry-foreground, var(--el-text-color-primary));
  font-weight: 600;
}
.chunk-content {
  min-height: 150px;
  max-height: 330px;
  overflow: auto;
  margin: 0;
  padding: 14px;
  border: 1px solid var(--ry-border-light, var(--el-border-color-light));
  border-radius: var(--ry-radius-medium, 6px);
  background: var(--ry-neutral-50, #f7f9fc);
  color: var(--ry-foreground, var(--el-text-color-primary));
  font: 13px/1.75 var(--ry-font-mono, Consolas, "Courier New", monospace);
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 1000px) {
  .preview-summary {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .preview-layout {
    display: flex;
    height: auto;
    max-height: 74vh;
    overflow-y: auto;
    flex-direction: column;
  }
  .source-panel {
    min-height: 380px;
  }
  .result-panel {
    min-height: 450px;
  }
  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .preview-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }
  .summary-card {
    min-height: 82px;
    padding: 11px 12px;
  }
  .preview-toolbar,
  .panel-header {
    align-items: flex-start;
    flex-direction: column;
  }
  .toolbar-context,
  .toolbar-actions,
  .source-tools {
    width: 100%;
  }
  .toolbar-actions .el-button {
    flex: 1;
  }
  .source-tools {
    align-items: flex-start;
    flex-direction: column;
  }
  .source-tools :deep(.el-radio-group) {
    width: 100%;
  }
  .source-tools :deep(.el-radio-button) {
    flex: 1;
  }
  .source-tools :deep(.el-radio-button__inner) {
    width: 100%;
  }
  .chunk-index-list {
    max-height: 150px;
  }
}
</style>
