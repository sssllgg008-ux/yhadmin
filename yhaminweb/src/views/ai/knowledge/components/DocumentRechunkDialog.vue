<template>
  <el-dialog
    v-model="visible"
    :title="`重新切片 · ${document?.name || ''}`"
    width="min(1280px, 96vw)"
    destroy-on-close
  >
    <el-alert
      v-if="!previewResult"
      type="warning"
      :closable="false"
      title="将创建新的处理版本；分块、Embedding 和索引全部成功后才自动切换，失败不影响当前版本。"
    />
    <div v-if="!previewResult" class="source-row">
      <span
        >当前配置来源：<el-tag>{{ sourceLabel }}</el-tag></span
      >
      <span>当前版本：V{{ configView.currentVersionNo || "-" }}</span>
      <span class="hash">配置哈希：{{ shortHash(configView.configHash) }}</span>
    </div>
    <el-form v-if="!previewResult" label-width="150px" class="chunk-form">
      <el-form-item label="分块策略"
        ><el-select v-model="config.strategy" @change="syncStrategy"
          ><el-option
            v-for="item in strategies"
            :key="item.value"
            :label="item.label"
            :value="item.value" /></el-select
      ></el-form-item>
      <div class="token-grid">
        <label class="token-field"
          ><span>最大 Token</span
          ><el-input-number
            v-model="config.maxTokens"
            :min="50"
            :max="8000"
            controls-position="right"
        /></label>
        <label class="token-field"
          ><span>最小 Token</span
          ><el-input-number
            v-model="config.minTokens"
            :min="0"
            :max="7999"
            controls-position="right"
        /></label>
        <label class="token-field"
          ><span>重叠 Token</span
          ><el-input-number
            v-model="config.overlapTokens"
            :min="0"
            :max="7999"
            controls-position="right"
        /></label>
      </div>
      <template v-if="config.strategy === 'REGEX'">
        <el-form-item label="正则表达式"
          ><el-input
            v-model="config.regex"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
        /></el-form-item>
        <el-form-item label="忽略大小写"
          ><el-switch v-model="config.regexIgnoreCase"
        /></el-form-item>
      </template>
      <el-form-item
        v-if="config.strategy === 'CUSTOM_SEPARATOR'"
        label="自定义分隔符"
      >
        <el-input
          v-model="separatorText"
          type="textarea"
          :rows="3"
          placeholder="每行一个分隔符，例如：\n\n 或 ---"
        />
      </el-form-item>
      <template
        v-if="['REGEX', 'CUSTOM_SEPARATOR', 'PAGE'].includes(config.strategy)"
      >
        <el-form-item label="分隔内容位置"
          ><el-radio-group v-model="config.delimiterPosition"
            ><el-radio-button value="NEXT">后一块</el-radio-button
            ><el-radio-button value="PREVIOUS">前一块</el-radio-button
            ><el-radio-button value="DROP"
              >丢弃</el-radio-button
            ></el-radio-group
          ></el-form-item
        >
        <el-form-item label="无匹配时回退"
          ><el-switch
            v-model="config.fallbackToSmart"
            active-text="智能 Markdown"
        /></el-form-item>
      </template>
      <el-form-item label="保存范围"
        ><el-checkbox v-model="saveAsDefault"
          >同时保存为该文档未来版本的默认规则</el-checkbox
        ></el-form-item
      >
    </el-form>
    <div v-if="previewResult" class="result-heading ry-card">
      <el-button @click="previewResult = null">返回分块设置</el-button>
      <div class="result-title">
        <strong>{{ document?.name }}</strong>
        <span>当前版本 V{{ configView.currentVersionNo || "-" }}</span>
      </div>
      <div class="result-meta">
        <el-tag effect="plain">{{
          strategies.find((item) => item.value === config.strategy)?.label
        }}</el-tag>
        <el-tag type="info" effect="plain">{{ sourceLabel }}</el-tag>
      </div>
    </div>
    <div v-else class="preview-head">
      <el-button type="primary" :loading="previewing" @click="preview"
        >生成分块预览</el-button
      ><span>先确认分块方式，再查看双栏结果。</span>
    </div>
    <ChunkPreviewWorkbench
      v-if="previewResult"
      :result="previewResult"
      :strategy="config.strategy"
      :knowledge-base-id="knowledgeBaseId"
      :document-id="document?.id"
      :version-id="configView.currentVersionId"
      @optimize="optimizePreview"
    />
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        v-if="!previewResult && configView.documentMode === 'CUSTOM'"
        @click="restoreInheritance"
        >恢复继承</el-button
      >
      <el-tooltip
        v-if="!previewResult"
        content="请先生成并确认分块预览"
        placement="top"
      >
        <span><el-button type="primary" disabled>创建重切版本</el-button></span>
      </el-tooltip>
      <el-button v-else type="primary" :loading="submitting" @click="submit"
        >确认并创建重切版本</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  getKnowledgeDocumentChunkConfig,
  previewKnowledgeDocumentChunks,
  rechunkKnowledgeDocument,
  saveKnowledgeDocumentChunkConfig,
  listKnowledgeDocumentVersions,
} from "@/api/ai";
import ChunkPreviewWorkbench from "./ChunkPreviewWorkbench.vue";
const props = defineProps({
  knowledgeBaseId: { type: [Number, String], required: true },
});
const emit = defineEmits(["submitted", "settled"]);
const visible = ref(false),
  document = ref(null),
  previewing = ref(false),
  submitting = ref(false),
  saveAsDefault = ref(false),
  previewResult = ref(null);
const configView = reactive({
  documentMode: "INHERIT",
  effectiveSource: "KNOWLEDGE_BASE",
  currentVersionNo: null,
  currentVersionId: null,
  configHash: "",
});
const defaults = () => ({
  strategy: "SMART_MARKDOWN",
  maxTokens: 800,
  minTokens: 100,
  overlapTokens: 120,
  regex: "",
  regexIgnoreCase: false,
  delimiterPosition: "NEXT",
  separators: ["\\n\\n", "---"],
  fallbackToSmart: true,
  autoOptimize: false,
});
const config = reactive(defaults());
const strategies = [
  { label: "智能 Markdown", value: "SMART_MARKDOWN" },
  { label: "固定 Token", value: "FIXED_TOKEN" },
  { label: "正则表达式", value: "REGEX" },
  { label: "自定义分隔符", value: "CUSTOM_SEPARATOR" },
  { label: "按页面", value: "PAGE" },
];
const separatorText = computed({
  get: () => config.separators.join("\n"),
  set: (value) => {
    config.separators = value
      .split("\n")
      .map((v) => v.trim())
      .filter(Boolean);
  },
});
const sourceLabel = computed(
  () =>
    ({
      VERSION: "当前版本快照",
      DOCUMENT: "文档自定义",
      KNOWLEDGE_BASE: "知识库默认",
    })[configView.effectiveSource] || configView.effectiveSource,
);
function syncStrategy() {
  previewResult.value = null;
}
function payload() {
  return {
    strategy: config.strategy,
    maxTokens: config.maxTokens,
    minTokens: config.minTokens,
    overlapTokens: config.overlapTokens,
    regex: config.regex || "",
    regexIgnoreCase: Boolean(config.regexIgnoreCase),
    delimiterPosition: config.delimiterPosition || "NEXT",
    separators: config.separators || [],
    fallbackToSmart: config.fallbackToSmart !== false,
    autoOptimize: Boolean(config.autoOptimize),
  };
}
async function open(row) {
  document.value = row;
  previewResult.value = null;
  saveAsDefault.value = false;
  const view = await getKnowledgeDocumentChunkConfig(
    props.knowledgeBaseId,
    row.id,
  );
  Object.assign(configView, view);
  Object.assign(config, defaults(), view.effective || {});
  visible.value = true;
}
async function preview() {
  previewing.value = true;
  try {
    const result = await previewKnowledgeDocumentChunks(
      props.knowledgeBaseId,
      document.value.id,
      { config: payload() },
    );
    if (!result?.chunks) throw new Error("预览接口未返回分块数据");
    previewResult.value = result;
  } catch (error) {
    previewResult.value = null;
    ElMessage.error(error?.message || "生成分块预览失败，请稍后重试");
  } finally {
    previewing.value = false;
  }
}
async function optimizePreview() {
  config.autoOptimize = true;
  await preview();
}
async function submit() {
  if (!previewResult.value) {
    ElMessage.warning("请先生成并确认分块预览");
    return;
  }
  const optimized = previewResult.value.optimizationApplied
    ? "，并应用当前预览的异常分片优化"
    : "";
  await ElMessageBox.confirm(
    `确认按当前预览创建新的重新切片版本${optimized}？成功前当前版本仍继续提供检索。`,
    "重新切片",
    { type: "warning" },
  );
  submitting.value = true;
  try {
    const documentId = document.value.id;
    const result = await rechunkKnowledgeDocument(
      props.knowledgeBaseId,
      documentId,
      { config: payload(), saveAsDocumentDefault: saveAsDefault.value },
    );
    ElMessage.success(`V${result.versionNo} 重切任务已创建`);
    visible.value = false;
    emit("submitted", { ...result, documentId });
    watchResult(documentId, result.versionId);
  } finally {
    submitting.value = false;
  }
}
async function restoreInheritance() {
  await ElMessageBox.confirm("确认恢复继承知识库默认分块规则？", "恢复继承", {
    type: "warning",
  });
  const view = await saveKnowledgeDocumentChunkConfig(
    props.knowledgeBaseId,
    document.value.id,
    { mode: "INHERIT" },
  );
  Object.assign(configView, view);
  Object.assign(config, defaults(), view.effective || {});
  ElMessage.success("已恢复继承");
}
watch(
  config,
  () => {
    if (previewResult.value) previewResult.value = null;
  },
  { deep: true },
);
const shortHash = (value) => (value ? `${value.slice(0, 10)}…` : "-");
let watchTimer;
function watchResult(documentId, versionId) {
  clearInterval(watchTimer);
  let attempts = 0;
  watchTimer = setInterval(async () => {
    if (++attempts > 360) {
      clearInterval(watchTimer);
      return;
    }
    try {
      const page = await listKnowledgeDocumentVersions(
        props.knowledgeBaseId,
        documentId,
        { pageNum: 1, pageSize: 50 },
      );
      const target = (page.rows || []).find((item) => item.id === versionId);
      if (target && ["SUCCESS", "FAILED", "EXPIRED"].includes(target.status)) {
        clearInterval(watchTimer);
        emit("settled", { documentId, versionId, status: target.status });
      }
    } catch (e) {}
  }, 5000);
}
onBeforeUnmount(() => clearInterval(watchTimer));
defineExpose({ open });
</script>

<style scoped>
.source-row {
  display: flex;
  gap: 24px;
  align-items: center;
  margin: 16px 0;
  padding: 12px 16px;
  background: #f7f9fc;
  border-radius: 6px;
}
.hash {
  color: #8492a6;
}
.chunk-form {
  padding-top: 4px;
}
.chunk-form :deep(.el-select),
.chunk-form :deep(.el-input-number) {
  width: 100%;
}
.token-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin: 4px 0 18px;
  padding-left: 150px;
}
.token-field {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 8px;
}
.token-field > span {
  font-weight: 600;
  color: #303133;
}
.preview-head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 4px 0 12px;
  color: #606266;
}
.result-heading {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 14px;
  margin-bottom: 12px;
  padding: 10px 12px;
}
.result-title {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 3px;
}
.result-title strong {
  overflow: hidden;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.result-title span {
  color: var(--ry-muted-foreground, var(--el-text-color-secondary));
  font-size: 12px;
}
.result-meta {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}
@media (max-width: 760px) {
  .token-grid {
    grid-template-columns: 1fr;
    padding-left: 0;
  }
  .source-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }
  .result-heading {
    align-items: flex-start;
    flex-wrap: wrap;
  }
  .result-title {
    order: 1;
    flex-basis: calc(100% - 140px);
  }
  .result-meta {
    order: 2;
    width: 100%;
  }
}
</style>
