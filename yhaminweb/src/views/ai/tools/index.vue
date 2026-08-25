<template>
  <CrudList
    entity-name="工具"
    name-field="toolName"
    :search-fields="searchFields"
    :columns="columns"
    :form-fields="formFields"
    :rules="rules"
    :api="api"
    :default-form="defaultForm"
  >
    <template #toolType="{ row }">
      <el-tag :type="toolTypeTag(row.toolType)" effect="light">{{
        toolTypeText(row.toolType)
      }}</el-tag>
    </template>
    <template #endpoint="{ row }">
      <span class="ry-mono">{{ row.endpoint }}</span>
    </template>
  </CrudList>
</template>

<script setup>
import CrudList from "@/components/CrudList.vue";
import {
  listTools,
  addTools,
  updateTools,
  delTools,
  changeToolsStatus,
} from "@/api/ai";

const api = {
  list: listTools,
  add: addTools,
  update: updateTools,
  remove: delTools,
  changeStatus: changeToolsStatus,
};

const toolTypes = [
  { label: "检索", value: "retrieval" },
  { label: "动作", value: "action" },
  { label: "查询", value: "query" },
];

const searchFields = [
  {
    prop: "toolName",
    label: "工具名称",
    type: "input",
    placeholder: "请输入工具名称",
  },
  { prop: "toolType", label: "工具类型", type: "select", options: toolTypes },
];

const columns = [
  { prop: "id", label: "编号", width: 80, align: "center" },
  { prop: "toolName", label: "工具名称", minWidth: 160 },
  { prop: "description", label: "描述", minWidth: 200 },
  {
    prop: "toolType",
    label: "类型",
    width: 110,
    align: "center",
    slot: "toolType",
  },
  { prop: "endpoint", label: "调用地址", minWidth: 220, slot: "endpoint" },
  { prop: "remark", label: "备注", minWidth: 140 },
];

const formFields = [
  { prop: "toolName", label: "工具名称", type: "input", span: 12 },
  {
    prop: "toolType",
    label: "工具类型",
    type: "select",
    span: 12,
    options: toolTypes,
  },
  { prop: "description", label: "描述", type: "input", span: 24 },
  { prop: "endpoint", label: "调用地址", type: "input", span: 24 },
  {
    prop: "inputSchema",
    label: "入参 Schema",
    type: "textarea",
    span: 24,
    rows: 4,
    placeholder: "JSON 格式",
  },
  {
    prop: "status",
    label: "状态",
    type: "radio",
    span: 12,
    options: [
      { label: "启用", value: "0" },
      { label: "停用", value: "1" },
    ],
  },
  { prop: "remark", label: "备注", type: "textarea", span: 24, rows: 2 },
];

const rules = {
  toolName: [{ required: true, message: "请输入工具名称", trigger: "blur" }],
  toolType: [{ required: true, message: "请选择工具类型", trigger: "change" }],
  endpoint: [{ required: true, message: "请输入调用地址", trigger: "blur" }],
};

const defaultForm = () => ({
  id: undefined,
  toolName: "",
  description: "",
  toolType: "retrieval",
  endpoint: "",
  inputSchema: "",
  status: "0",
  remark: "",
});

function toolTypeText(t) {
  return { retrieval: "检索", action: "动作", query: "查询" }[t] || t;
}

function toolTypeTag(t) {
  return (
    { retrieval: "success", action: "warning", query: "primary" }[t] || "info"
  );
}
</script>

<style lang="scss" scoped>
.ry-mono {
  font-family: var(--ry-font-mono);
  font-size: 13px;
}
</style>
