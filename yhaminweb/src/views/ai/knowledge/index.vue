<template>
  <CrudList
    entity-name="知识库"
    name-field="knowledgeName"
    dialog-width="680px"
    :create-time-width="180"
    :action-width="230"
    :search-fields="searchFields"
    :columns="columns"
    :form-fields="formFields"
    :rules="rules"
    :api="api"
    :default-form="defaultForm"
  >
    <template #docCount="{ row }">
      <el-tag type="info" effect="light">{{ row.docCount }} 篇</el-tag>
    </template>
    <template #actions="{ row }">
      <el-button type="primary" link @click="openWorkbench(row)"
        >进入工作台</el-button
      >
    </template>
  </CrudList>
</template>

<script setup>
import CrudList from "@/components/CrudList.vue";
import { useRouter } from "vue-router";
import {
  listKnowledge,
  addKnowledge,
  updateKnowledge,
  delKnowledge,
  changeKnowledgeStatus,
} from "@/api/ai";

const api = {
  list: listKnowledge,
  add: addKnowledge,
  update: updateKnowledge,
  remove: delKnowledge,
  changeStatus: changeKnowledgeStatus,
};
const router = useRouter();
const openWorkbench = (row) => router.push(`/ai/knowledge/${row.id}`);

const searchFields = [
  {
    prop: "knowledgeName",
    label: "知识库名称",
    type: "input",
    placeholder: "请输入知识库名称",
  },
  {
    prop: "status",
    label: "状态",
    type: "select",
    options: [
      { label: "启用", value: "0" },
      { label: "停用", value: "1" },
    ],
  },
];

const columns = [
  { prop: "id", label: "编号", width: 80, align: "center" },
  { prop: "knowledgeName", label: "知识库名称", minWidth: 160 },
  { prop: "description", label: "描述", minWidth: 200 },
  {
    prop: "docCount",
    label: "文档数",
    width: 110,
    align: "center",
    slot: "docCount",
  },
  { prop: "chunkSize", label: "分块大小", width: 100, align: "right" },
  { prop: "remark", label: "备注", minWidth: 140 },
];

const formFields = [
  { prop: "knowledgeName", label: "知识库名称", type: "input", span: 12 },
  { prop: "description", label: "描述", type: "input", span: 24 },
  {
    prop: "chunkSize",
    label: "分块大小（Token）",
    type: "number",
    span: 12,
    min: 200,
    max: 4000,
  },
  {
    prop: "chunkOverlap",
    label: "重叠长度（Token）",
    type: "number",
    span: 12,
    min: 0,
    max: 3999,
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
  knowledgeName: [
    { required: true, message: "请输入知识库名称", trigger: "blur" },
  ],
  description: [{ required: true, message: "请输入描述", trigger: "blur" }],
};

const defaultForm = () => ({
  id: undefined,
  knowledgeName: "",
  description: "",
  chunkSize: 800,
  chunkOverlap: 120,
  status: "0",
  remark: "",
});
</script>
