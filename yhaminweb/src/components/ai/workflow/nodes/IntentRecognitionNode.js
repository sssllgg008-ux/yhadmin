/**
 * Tinyflow 意图识别业务节点。
 *
 * 节点类型继续注册为 intentRecognition，保证已有工作流 DSL 无需迁移。
 */
export function createIntentRecognitionNode(modelOptions = []) {
  return {
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
  };
}
