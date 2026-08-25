<template>
  <section class="rag-chat">
    <header class="section-heading">
      <div>
        <h2>问答测试</h2>
        <p>
          基于当前有效文档进行混合检索、重排和流式回答，每条结论均可追溯来源。
        </p>
      </div>
      <el-button :disabled="generating || !messages.length" @click="clear"
        >清空对话</el-button
      >
    </header>

    <div class="chat-layout">
      <article class="ry-card conversation">
        <div v-if="!messages.length" class="empty-state">
          <el-icon><ChatDotRound /></el-icon>
          <h3>向知识库提问</h3>
          <p>
            系统仅依据有权限访问的当前有效分块作答；没有可靠证据时会明确拒答。
          </p>
        </div>
        <div
          v-for="(message, index) in messages"
          :key="index"
          class="message"
          :class="message.role"
        >
          <div class="message-label">
            {{ message.role === "user" ? "你" : "知识库助手" }}
          </div>
          <div class="bubble">
            <MarkdownPreview
              v-if="message.role === 'assistant'"
              :content="message.content"
            />
            <div v-else class="user-text">{{ message.content }}</div>
            <span v-if="message.streaming" class="cursor" />
          </div>
          <div v-if="message.meta" class="message-meta">
            <el-tag
              v-if="
                message.meta.rewrittenQuestion !== message.meta.originalQuestion
              "
              size="small"
              effect="plain"
              >已改写查询</el-tag
            >
            <span>检索 {{ message.meta.retrievalMs ?? "-" }} ms</span
            ><span>生成 {{ message.done?.generationMs ?? "-" }} ms</span>
            <span>输入 {{ message.done?.promptTokens ?? "-" }} Token</span
            ><span>输出 {{ message.done?.completionTokens ?? "-" }} Token</span>
            <el-tag v-if="message.done?.degraded" size="small" type="warning"
              >已降级</el-tag
            >
			<el-tag v-if="message.intent" size="small" effect="plain">
			  {{ intentLabel(message.intent.intent) }} · {{ formatConfidence(message.intent.confidence) }}
			</el-tag>
          </div>
          <div v-if="message.citations?.length" class="citations">
            <div class="citation-title">
              文档来源（{{ message.citations.length }}）
            </div>
            <details v-for="item in message.citations" :key="item.chunkId">
              <summary>
                [{{ item.number }}]
                {{ item.titlePath || `分块 #${item.chunkId}` }}
              </summary>
              <p>{{ item.excerpt }}</p>
              <small
                >分块 #{{ item.chunkId }} · 版本 {{ item.documentVersionId }} ·
                得分 {{ formatScore(item.score) }}</small
              >
            </details>
          </div>
		  <div v-if="message.graphEvidence?.length" class="graph-evidence">
			<div class="citation-title">图谱证据（{{ message.graphEvidence.length }}）</div>
			<details v-for="item in message.graphEvidence" :key="`${item.kind}-${item.number}`">
			  <summary>[G{{ item.number }}] {{ item.summary }}</summary>
			  <div class="graph-meta">
				<el-tag size="small" type="success" effect="plain">{{ graphKindLabel(item.kind) }}</el-tag>
				<span>{{ item.hop ?? 0 }} 跳 · {{ item.sources?.length || 0 }} 个原文来源</span>
			  </div>
			  <div v-for="source in item.sources" :key="source.chunkId" class="graph-source">
				<strong>{{ source.titlePath || `分块 #${source.chunkId}` }}</strong>
				<p>{{ source.excerpt }}</p>
				<small>分块 #{{ source.chunkId }} · 版本 {{ source.documentVersionId }}</small>
			  </div>
			</details>
		  </div>
		  <el-alert
			v-else-if="message.intent && !message.intent.graphTriggered"
			class="graph-skipped"
			type="info"
			:closable="false"
			:title="message.intent.message || '本次未启用图谱检索'"
		  />
        </div>
      </article>

      <aside class="ry-card run-info">
        <h3>本次运行</h3>
        <template v-if="currentAssistant?.meta">
          <dl>
            <div>
              <dt>对话模型</dt>
              <dd>{{ currentAssistant.meta.model }}</dd>
            </div>
            <div>
              <dt>上下文</dt>
              <dd>{{ currentAssistant.meta.contextCount }} 份证据</dd>
            </div>
			<div v-if="currentAssistant.intent">
			  <dt>问题意图</dt>
			  <dd>{{ intentLabel(currentAssistant.intent.intent) }}（{{ formatConfidence(currentAssistant.intent.confidence) }}）</dd>
			</div>
			<div v-if="currentAssistant.intent">
			  <dt>图谱检索</dt>
			  <dd>{{ currentAssistant.intent.graphTriggered ? `已触发 · ${currentAssistant.graphEvidence?.length || 0} 条证据` : '本次未启用' }}</dd>
			</div>
            <div>
              <dt>查询改写</dt>
              <dd>{{ currentAssistant.meta.rewrittenQuestion }}</dd>
            </div>
          </dl>
          <el-alert
            v-if="currentAssistant.done?.degraded"
            type="warning"
            :closable="false"
            :title="
              currentAssistant.done.degradedReason || '部分检索能力已降级'
            "
          />
        </template>
        <el-empty
          v-else
          :image-size="60"
          description="发送问题后显示检索与生成指标"
        />
      </aside>
    </div>

    <div class="composer ry-card">
      <el-input
        v-model="question"
        type="textarea"
        :rows="3"
        maxlength="4000"
        show-word-limit
        placeholder="请输入问题，Ctrl + Enter 发送"
        :disabled="generating"
        @keydown.ctrl.enter.prevent="send"
      />
      <div class="composer-footer">
        <span
          >最多携带最近 20 条对话；回答仅引用当前有效且有权访问的证据。</span
        >
        <el-button v-if="generating" type="danger" plain @click="stop"
          >停止生成</el-button
        >
        <el-button
          v-else
          type="primary"
          :disabled="!question.trim()"
          @click="send"
          >发送</el-button
        >
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, ref } from "vue";
import { ElMessage } from "element-plus";
import { ChatDotRound } from "@element-plus/icons-vue";
import { streamKnowledgeChat } from "@/api/ai";
import MarkdownPreview from "./MarkdownPreview.vue";

const props = defineProps({
  knowledgeBaseId: { type: [Number, String], required: true },
});
const question = ref("");
const messages = ref([]);
const generating = ref(false);
let controller;
const currentAssistant = computed(() =>
  [...messages.value].reverse().find((item) => item.role === "assistant"),
);
const formatScore = (value) =>
  Number.isFinite(Number(value)) ? Number(value).toFixed(6) : "-";
const formatConfidence = (value) =>
  Number.isFinite(Number(value)) ? `${(Number(value) * 100).toFixed(0)}%` : "-";
const intentLabels = {
  DOCUMENT_SEARCH: "文档查询",
  ENTITY_LOOKUP: "实体查询",
  RELATION_QUERY: "关系查询",
  PATH_QUERY: "路径查询",
  FACT_QUERY: "事实查询",
  STATISTICS_QUERY: "统计查询",
  SOURCE_TRACE: "来源追溯",
  HYBRID_QUERY: "混合查询",
  GENERAL_CHAT: "普通交流",
  UNSUPPORTED: "未识别",
};
const intentLabel = (value) => intentLabels[value] || value || "未识别";
const graphKindLabel = (value) =>
  ({ ENTITY: "实体", RELATION: "关系", PATH: "路径", FACT: "事实" })[value] || value;
const scrollBottom = () =>
  nextTick(() =>
    document
      .querySelector(".conversation")
      ?.scrollTo({ top: 999999, behavior: "smooth" }),
  );
const normalizeMeta = (data) =>
  Array.isArray(data)
    ? {
        originalQuestion: data[0],
        rewrittenQuestion: data[1],
        model: data[2],
        contextCount: data[3],
        retrievalMs: data[4],
        refused: data[5],
        message: data[6],
      }
    : data || {};
const normalizeDone = (data) =>
  Array.isArray(data)
    ? {
        totalMs: data[0],
        generationMs: data[1],
        promptTokens: data[2],
        completionTokens: data[3],
        degraded: data[4],
        degradedReason: data[5],
      }
    : data || {};
const normalizeIntent = (data) =>
  Array.isArray(data)
    ? {
        intent: data[0],
        confidence: data[1],
        entities: data[2] || [],
        graphEnabled: data[3],
        graphTriggered: data[4],
        message: data[5],
      }
    : data || {};
const normalizeGraphEvidence = (item, index) => {
  const value = Array.isArray(item)
    ? {
        number: item[0],
        kind: item[1],
        subject: item[2],
        predicate: item[3],
        object: item[4],
        summary: item[5],
        hop: item[6],
        sources: item[7],
      }
    : item || {};
  return { ...value, number: value.number ?? index + 1, sources: value.sources || [] };
};
const normalizeCitation = (item, index) => {
  const value = Array.isArray(item)
    ? {
        number: item[0],
        chunkId: item[1],
        documentId: item[2],
        documentVersionId: item[3],
        titlePath: item[4],
        excerpt: item[5],
        score: item[6],
      }
    : item || {};
  return {
    ...value,
    number: value.number ?? index + 1,
    chunkId: value.chunkId ?? "-",
  };
};
const normalizeDelta = (data) =>
  Array.isArray(data)
    ? Object.assign(
        {},
        ...data.filter(
          (item) => item && typeof item === "object" && !Array.isArray(item),
        ),
      )
    : data || {};
const streamErrorMessage = (data) => {
  if (typeof data === "string") {
    try {
      return streamErrorMessage(JSON.parse(data));
    } catch (_) {
      return data || "流式生成失败";
    }
  }
  if (Array.isArray(data)) {
    for (const item of data) {
      const message =
        typeof item === "string"
          ? item
          : item?.message || item?.msg || item?.error?.message || item?.error;
      if (message) return streamErrorMessage(message);
    }
    return "流式生成失败";
  }
  return (
    data?.message ||
    data?.msg ||
    data?.error?.message ||
    data?.error ||
    "流式生成失败"
  );
};

async function send() {
  const value = question.value.trim();
  if (!value || generating.value) return;
  const history = messages.value
    .filter((item) => item.content && !item.streaming)
    .slice(-20)
    .map((item) => ({ role: item.role, content: item.content }));
  messages.value.push({ role: "user", content: value });
  const answer = {
    role: "assistant",
    content: "",
    streaming: true,
    citations: [],
    graphEvidence: [],
    intent: undefined,
    meta: undefined,
    done: undefined,
  };
  messages.value.push(answer);
  question.value = "";
  generating.value = true;
  controller = new AbortController();
  scrollBottom();
  try {
    await streamKnowledgeChat(
      props.knowledgeBaseId,
      { question: value, history, maxContextChunks: 8 },
      {
        meta: (data) => {
          answer.meta = normalizeMeta(data);
        },
        intent: (data) => {
          answer.intent = normalizeIntent(data);
        },
        delta: (data) => {
          answer.content += normalizeDelta(data).content || "";
          scrollBottom();
        },
        citations: (data) => {
          answer.citations = Array.isArray(data)
            ? data.map(normalizeCitation)
            : [];
        },
        graphEvidence: (data) => {
          answer.graphEvidence = Array.isArray(data)
            ? data.map(normalizeGraphEvidence)
            : [];
        },
        done: (data) => {
          answer.done = normalizeDone(data);
        },
        error: (data) => {
          throw new Error(streamErrorMessage(data));
        },
      },
      controller.signal,
    );
    if (!answer.content) answer.content = "未收到模型回答。";
  } catch (error) {
    if (error?.name === "AbortError") answer.content += "\n\n> 已停止生成。";
    else {
      answer.content += `\n\n> 生成失败：${error?.message || "未知错误"}`;
      ElMessage.error(error?.message || "问答失败");
    }
  } finally {
    answer.streaming = false;
    generating.value = false;
    controller = undefined;
    scrollBottom();
  }
}
function stop() {
  controller?.abort();
}
function clear() {
  messages.value = [];
}
</script>

<style scoped lang="scss">
.rag-chat {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: calc(100vh - 190px);
}
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.section-heading h2 {
  margin: 0 0 5px;
  font-size: 20px;
}
.section-heading p {
  margin: 0;
  color: var(--el-text-color-secondary);
}
.chat-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 14px;
  min-height: 480px;
}
.conversation {
  height: calc(100vh - 380px);
  min-height: 440px;
  overflow: auto;
  padding: 22px;
}
.run-info {
  padding: 18px;
  height: max-content;
}
.run-info h3 {
  margin: 0 0 16px;
}
.run-info dl {
  margin: 0;
}
.run-info dl div {
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.run-info dt {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.run-info dd {
  margin: 5px 0 0;
  word-break: break-word;
}
.empty-state {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
  text-align: center;
}
.empty-state .el-icon {
  font-size: 42px;
  color: var(--el-color-primary);
}
.empty-state h3 {
  color: var(--el-text-color-primary);
  margin: 12px 0 4px;
}
.empty-state p {
  max-width: 520px;
}
.message {
  margin-bottom: 24px;
}
.message-label {
  margin: 0 8px 7px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.message.user {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}
.bubble {
  max-width: 88%;
  padding: 12px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
}
.user .bubble {
  background: var(--el-color-primary);
  color: #fff;
  border-color: var(--el-color-primary);
}
.user-text {
  white-space: pre-wrap;
  line-height: 1.7;
}
.cursor {
  display: inline-block;
  width: 7px;
  height: 16px;
  margin-left: 3px;
  background: var(--el-color-primary);
  animation: blink 1s infinite;
  vertical-align: middle;
}
.message-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.citations {
  max-width: 88%;
  margin-top: 10px;
  padding: 12px;
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}
.citation-title {
  font-weight: 600;
  margin-bottom: 6px;
}
.citations details {
  padding: 7px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}
.citations summary {
  cursor: pointer;
  color: var(--el-color-primary);
}
.citations p {
  margin: 8px 0;
  line-height: 1.6;
}
.citations small {
  color: var(--el-text-color-secondary);
}
.graph-evidence {
  max-width: 88%;
  margin-top: 10px;
  padding: 12px;
  border: 1px solid var(--el-color-success-light-7);
  border-radius: 8px;
  background: var(--el-color-success-light-9);
}
.graph-evidence details {
  padding: 8px 0;
  border-top: 1px solid var(--el-color-success-light-7);
}
.graph-evidence summary {
  cursor: pointer;
  color: var(--el-color-success-dark-2);
}
.graph-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 8px 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.graph-source {
  margin-top: 8px;
  padding: 9px 10px;
  border-radius: 6px;
  background: var(--el-bg-color);
}
.graph-source p {
  margin: 6px 0;
  line-height: 1.6;
}
.graph-source small {
  color: var(--el-text-color-secondary);
}
.graph-skipped {
  max-width: 88%;
  margin-top: 10px;
}
.composer {
  padding: 14px 16px;
}
.composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
@keyframes blink {
  50% {
    opacity: 0;
  }
}
@media (max-width: 1000px) {
  .chat-layout {
    grid-template-columns: 1fr;
  }
  .run-info {
    order: -1;
  }
  .conversation {
    height: 52vh;
  }
}
@media (max-width: 640px) {
  .conversation {
    padding: 14px;
  }
  .bubble,
  .citations,
  .graph-evidence,
  .graph-skipped {
    max-width: 96%;
  }
  .composer-footer {
    align-items: flex-end;
    flex-direction: column;
  }
}
</style>
