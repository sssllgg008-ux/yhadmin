<template>
  <div v-loading="loading" class="markdown-preview" v-html="html" />
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from "vue";
import MarkdownIt from "markdown-it";
import texmath from "markdown-it-texmath";
import katex from "katex";
import DOMPurify from "dompurify";
import "katex/dist/katex.min.css";
import { getKnowledgeParsedImage } from "@/api/ai";

const props = defineProps({
  content: { type: String, default: "" },
  knowledgeBaseId: { type: [Number, String], default: undefined },
  documentId: { type: [Number, String], default: undefined },
  versionId: { type: [Number, String], default: undefined },
});
const loading = ref(false);
const rendered = ref("");
const objectUrls = new Set();
const md = new MarkdownIt({ html: true, linkify: true, breaks: true }).use(
  texmath,
  {
    engine: katex,
    delimiters: "dollars",
    katexOptions: { throwOnError: false, strict: "ignore" },
  },
);
const defaultLinkOpen =
  md.renderer.rules.link_open ||
  ((tokens, idx, options, env, self) => self.renderToken(tokens, idx, options));
md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  tokens[idx].attrSet("target", "_blank");
  tokens[idx].attrSet("rel", "noopener noreferrer");
  return defaultLinkOpen(tokens, idx, options, env, self);
};
const html = computed(() => rendered.value);

function normalizeMath(source) {
  return source
    .replace(
      /\\\[([\s\S]*?)\\\]/g,
      (_, formula) => `\n$$\n${formula.trim()}\n$$\n`,
    )
    .replace(/\\\((.+?)\\\)/g, (_, formula) => `$${formula}$`);
}
function renderTableMath(source) {
  return source.replace(/<table\b[\s\S]*?<\/table>/gi, (table) =>
    table
      .replace(/\$\$([\s\S]*?)\$\$/g, (_, formula) =>
        katex.renderToString(formula.trim(), {
          displayMode: true,
          throwOnError: false,
          strict: "ignore",
        }),
      )
      .replace(/\$(?!\$)([^$\n]+?)\$/g, (_, formula) =>
        katex.renderToString(formula.trim(), {
          displayMode: false,
          throwOnError: false,
          strict: "ignore",
        }),
      ),
  );
}
function sanitize(source) {
  return DOMPurify.sanitize(source, {
    USE_PROFILES: { html: true, mathMl: true, svg: false },
    ADD_TAGS: ["semantics", "annotation"],
    ADD_ATTR: ["colspan", "rowspan", "align", "aria-hidden", "encoding"],
    ALLOWED_URI_REGEXP:
      /^(?:(?:https?|mailto):|blob:|[^a-z]|[a-z+.-]+(?:[^a-z+.-:]|$))/i,
  });
}

function clearUrls() {
  objectUrls.forEach((url) => URL.revokeObjectURL(url));
  objectUrls.clear();
}
async function render() {
  clearUrls();
  loading.value = true;
  try {
    const source = renderTableMath(normalizeMath(props.content || ""));
    const names = [
      ...new Set(
        [
          ...source.matchAll(
            /!\[[^\]]*]\(\s*(?:\.\/)?images\/([^\s)]+)(?:\s+["'][^"']*["'])?\s*\)/gi,
          ),
        ].map((match) => decodeURIComponent(match[1])),
      ),
    ];
    const replacements = new Map();
    await Promise.all(
      names.map(async (name) => {
        try {
          if (!props.knowledgeBaseId || !props.documentId || !props.versionId)
            throw new Error("missing document context");
          const blob = await getKnowledgeParsedImage(
            props.knowledgeBaseId,
            props.documentId,
            props.versionId,
            name,
          );
          const url = URL.createObjectURL(blob);
          objectUrls.add(url);
          replacements.set(name, url);
        } catch (_) {
          replacements.set(name, "");
        }
      }),
    );
    const rewritten = source.replace(
      /(!\[[^\]]*]\(\s*)(?:\.\/)?images\/([^\s)]+)((?:\s+["'][^"']*["'])?\s*\))/gi,
      (all, prefix, encodedName, suffix) =>
        `${prefix}${replacements.get(decodeURIComponent(encodedName)) || ""}${suffix}`,
    );
    rendered.value = sanitize(md.render(rewritten));
  } finally {
    loading.value = false;
  }
}
watch(
  () => [
    props.content,
    props.knowledgeBaseId,
    props.documentId,
    props.versionId,
  ],
  render,
  { immediate: true },
);
onBeforeUnmount(clearUrls);
</script>

<style scoped lang="scss">
.markdown-preview {
  min-height: 120px;
  color: var(--el-text-color-primary);
  font-size: 14px;
  line-height: 1.75;
  word-break: break-word;
}
.markdown-preview :deep(h1),
.markdown-preview :deep(h2),
.markdown-preview :deep(h3) {
  margin: 1.2em 0 0.55em;
  line-height: 1.35;
}
.markdown-preview :deep(h1) {
  font-size: 24px;
}
.markdown-preview :deep(h2) {
  font-size: 20px;
}
.markdown-preview :deep(h3) {
  font-size: 17px;
}
.markdown-preview :deep(p) {
  margin: 0.65em 0;
}
.markdown-preview :deep(img) {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 14px auto;
  border-radius: 6px;
  box-shadow: 0 1px 5px rgb(0 0 0 / 10%);
}
.markdown-preview :deep(pre) {
  overflow: auto;
  padding: 14px;
  border-radius: 6px;
  background: #1f2937;
  color: #e5e7eb;
  white-space: pre;
}
.markdown-preview :deep(code) {
  font-family: Consolas, "Courier New", monospace;
}
.markdown-preview :deep(:not(pre) > code) {
  padding: 2px 5px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
}
.markdown-preview :deep(table) {
  display: block;
  max-width: 100%;
  overflow: auto;
  border-collapse: collapse;
}
.markdown-preview :deep(th),
.markdown-preview :deep(td) {
  padding: 7px 10px;
  border: 1px solid var(--el-border-color);
}
.markdown-preview :deep(table) {
  width: max-content;
  min-width: 100%;
  margin: 14px 0;
  background: var(--el-bg-color);
}
.markdown-preview :deep(th) {
  background: var(--el-fill-color-light);
  font-weight: 600;
}
.markdown-preview :deep(td) {
  vertical-align: top;
}
.markdown-preview :deep(.katex-display) {
  max-width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 6px 0;
}
.markdown-preview :deep(.katex) {
  font-size: 1.05em;
}
.markdown-preview :deep(blockquote) {
  margin: 12px 0;
  padding: 2px 14px;
  border-left: 4px solid var(--el-color-primary);
  color: var(--el-text-color-secondary);
}
</style>
