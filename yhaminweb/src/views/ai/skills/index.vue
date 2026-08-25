<template>
  <CrudList
    entity-name="技能"
    name-field="skillName"
    :search-fields="searchFields"
    :columns="columns"
    :form-fields="formFields"
    :rules="rules"
    :api="api"
    :default-form="defaultForm"
  >
    <template #category="{ row }">
      <el-tag type="info" effect="light">{{ row.category }}</el-tag>
    </template>
    <template #version="{ row }">
      <span class="ry-mono">v{{ row.version }}</span>
    </template>
  </CrudList>
</template>

<script setup>
import CrudList from "@/components/CrudList.vue";
import {
  listSkills,
  addSkills,
  updateSkills,
  delSkills,
  changeSkillsStatus,
} from "@/api/ai";

const api = {
  list: listSkills,
  add: addSkills,
  update: updateSkills,
  remove: delSkills,
  changeStatus: changeSkillsStatus,
};

const categories = ["文档处理", "语言处理", "开发辅助", "数据分析", "办公辅助"];

const searchFields = [
  {
    prop: "skillName",
    label: "技能名称",
    type: "input",
    placeholder: "请输入技能名称",
  },
  {
    prop: "category",
    label: "分类",
    type: "select",
    options: categories.map((c) => ({ label: c, value: c })),
  },
];

const columns = [
  { prop: "id", label: "编号", width: 80, align: "center" },
  { prop: "skillName", label: "技能名称", minWidth: 160 },
  { prop: "description", label: "描述", minWidth: 220 },
  {
    prop: "category",
    label: "分类",
    width: 130,
    align: "center",
    slot: "category",
  },
  {
    prop: "version",
    label: "版本",
    width: 100,
    align: "center",
    slot: "version",
  },
  { prop: "remark", label: "备注", minWidth: 140 },
];

const formFields = [
  { prop: "skillName", label: "技能名称", type: "input", span: 12 },
  {
    prop: "category",
    label: "分类",
    type: "select",
    span: 12,
    options: categories.map((c) => ({ label: c, value: c })),
  },
  {
    prop: "version",
    label: "版本",
    type: "input",
    span: 12,
    placeholder: "如 1.0.0",
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
  { prop: "description", label: "描述", type: "textarea", span: 24, rows: 2 },
  { prop: "remark", label: "备注", type: "textarea", span: 24, rows: 2 },
];

const rules = {
  skillName: [{ required: true, message: "请输入技能名称", trigger: "blur" }],
  category: [{ required: true, message: "请选择分类", trigger: "change" }],
};

const defaultForm = () => ({
  id: undefined,
  skillName: "",
  description: "",
  category: "",
  version: "1.0.0",
  status: "0",
  remark: "",
});
</script>

<style lang="scss" scoped>
.ry-mono {
  font-family: var(--ry-font-mono);
  font-size: 13px;
}
</style>
