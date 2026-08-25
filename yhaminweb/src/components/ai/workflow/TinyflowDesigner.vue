<template>
  <div ref="root" class="tinyflow-root" />
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import { Tinyflow } from "@tinyflow-ai/ui";
import "@tinyflow-ai/ui/dist/index.css";
import { getWorkflowResources } from "@/api/ai";
import { createIntentRecognitionNode as createIntentRecognitionNodeComponent } from "./nodes/IntentRecognitionNode";

const props = defineProps({
  modelValue: { type: [String, Object], default: "" },
});
const emit = defineEmits(["update:modelValue", "change"]);
const root = ref();
let designer;
let resizeObserver;
let applying = false;
let resourcePromise;

const parseData = (value) => {
  if (!value) {
    return { nodes: [], edges: [], viewport: { x: 0, y: 0, zoom: 1 } };
  }
  if (typeof value === "object") {
    return value;
  }
  try {
    return JSON.parse(value);
  } catch {
    return { nodes: [], edges: [] };
  }
};

const loadResources = () => {
  if (!resourcePromise) {
    resourcePromise = getWorkflowResources().catch((error) => {
      resourcePromise = null;
      throw error;
    });
  }
  return resourcePromise;
};

const loadModelOptions = async () => {
  const resources = await loadResources();
  return resources?.models || [];
};

const loadKnowledgeOptions = async () => {
  const resources = await loadResources();
  return resources?.knowledgeBases || [];
};

// tinyflow-core 当前内置且能够实际执行的搜索引擎。
// 密钥只在后端运行环境配置，设计器仅保存引擎编码。
const loadSearchEngineOptions = async () => {
  const resources = await loadResources();
  return resources?.searchEngines || [];
};

/*
 * 历史内嵌定义保留为迁移参考；设计器实际注册统一使用
 * nodes/IntentRecognitionNode 中的独立节点组件。
 */
const legacyIntentRecognitionNodeDefinition = (modelOptions) => ({
  title: "意图识别",
  description: "识别输入文本的业务意图，并输出置信度、实体和图谱检索建议。",
  sortNo: 10,
  group: "tools",
  parametersEnable: true,
  parametersAddEnable: false,
  parameters: [
    {
      name: "text",
      nameDisabled: true,
      dataType: "String",
      dataTypeDisabled: true,
      required: true,
      deleteDisabled: true,
      formType: "textarea",
      formLabel: "待识别文本",
      formDescription: "可直接输入文本，也可引用开始节点或上游节点的输出变量。",
      formPlaceholder: "请输入文本或引用上游变量",
    },
  ],
  outputDefsEnable: true,
  outputDefsAddEnable: false,
  outputDefs: [
    { name: "intent", dataType: "String", description: "标准意图编码" },
    { name: "confidence", dataType: "Number", description: "识别置信度（0～1）" },
    { name: "entities", dataType: "Array", description: "候选实体列表" },
    { name: "reason", dataType: "String", description: "识别理由" },
    {
      name: "graphRecommended",
      dataType: "Boolean",
      description: "是否建议进入知识图谱检索",
    },
  ],
  forms: [
    {
      type: "select",
      label: "识别模型",
      description: "使用当前租户已启用的对话模型。",
      name: "modelId",
      placeholder: "请选择对话模型",
      defaultValue: modelOptions[0]?.value ?? "",
      options: modelOptions,
      attrs: { filterable: true },
    },
    {
      type: "slider",
      label: "图谱触发阈值",
      description: "图谱类意图达到该置信度后，输出图谱检索建议。",
      name: "confidenceThreshold",
      defaultValue: 0.7,
      attrs: { min: 0, max: 1, step: 0.05 },
    },
  ],
});

onMounted(async () => {
  let resources = {};
  try {
    resources = await loadResources();
  } catch {
    // 资源接口暂时不可用时仍允许打开设计器，节点执行时由后端再次校验。
  }
  designer = new Tinyflow({
    element: root.value,
    data: parseData(props.modelValue),
    defaultTheme: "light",
    provider: {
      llm: loadModelOptions,
      knowledge: loadKnowledgeOptions,
      searchEngine: loadSearchEngineOptions,
    },
    customNodes: {
      intentRecognition: createIntentRecognitionNodeComponent(resources?.models || []),
    },
    onDataChange: (data) => {
      if (applying) {
        return;
      }
      const value = JSON.stringify(data);
      emit("update:modelValue", value);
      emit("change", value);
    },
  });
  resizeObserver = new ResizeObserver(() => {
    // Tinyflow/XYFlow 依赖容器真实尺寸；弹窗或侧栏变化后触发重新布局。
    root.value?.dispatchEvent(new Event("resize"));
    window.dispatchEvent(new Event("resize"));
  });
  resizeObserver.observe(root.value);
});

watch(
  () => props.modelValue,
  (value) => {
    if (!designer) {
      return;
    }
    const current = JSON.stringify(designer.getData());
    const next = JSON.stringify(parseData(value));
    if (current === next) {
      return;
    }
    applying = true;
    designer.setData(parseData(value));
    queueMicrotask(() => {
      applying = false;
    });
  },
);

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  designer?.destroy();
});

defineExpose({
  getData: () => designer?.getData(),
});
</script>

<style scoped>
.tinyflow-root {
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: #f7f9fc;
}

.tinyflow-root :deep(> div) {
  width: 100%;
  height: 100%;
}

/*
 * Tinyflow 的 Select 使用 Portal 挂载到 body，组件默认 z-index 只有 50。
 * 流程设计器位于 Element Plus 全屏 Dialog（z-index 2000+）中，如果不提升
 * Portal 浮层层级，下拉其实已经打开，但会被遮罩层完全盖住。
 */
:global(.tf-select-content) {
  z-index: 10000 !important;
}
</style>
