<template>
  <CrudList
    entity-name="MCP"
    name-field="mcpName"
    :search-fields="searchFields"
    :columns="columns"
    :form-fields="formFields"
    :rules="rules"
    :api="api"
    :default-form="defaultForm"
  >
    <template #mcpType="{ row }">
      <el-tag
        :type="row.mcpType === 'stdio' ? 'success' : 'warning'"
        effect="light"
        >{{ row.mcpType.toUpperCase() }}</el-tag
      >
    </template>
    <template #args="{ row }">
      <span class="ry-mono">{{ row.args }}</span>
    </template>
  </CrudList>
</template>

<script setup>
import CrudList from "@/components/CrudList.vue";
import { listMcp, addMcp, updateMcp, delMcp, changeMcpStatus } from "@/api/ai";

const api = {
  list: listMcp,
  add: addMcp,
  update: updateMcp,
  remove: delMcp,
  changeStatus: changeMcpStatus,
};

const mcpTypes = [
  { label: "Stdio", value: "stdio" },
  { label: "SSE", value: "sse" },
];

const searchFields = [
  {
    prop: "mcpName",
    label: "MCP 名称",
    type: "input",
    placeholder: "请输入 MCP 名称",
  },
  { prop: "mcpType", label: "类型", type: "select", options: mcpTypes },
];

const columns = [
  { prop: "id", label: "编号", width: 80, align: "center" },
  { prop: "mcpName", label: "MCP 名称", minWidth: 160 },
  { prop: "description", label: "描述", minWidth: 200 },
  {
    prop: "mcpType",
    label: "类型",
    width: 100,
    align: "center",
    slot: "mcpType",
  },
  { prop: "command", label: "命令", width: 120 },
  { prop: "args", label: "参数", minWidth: 220, slot: "args" },
  { prop: "remark", label: "备注", minWidth: 140 },
];

const formFields = [
  { prop: "mcpName", label: "MCP 名称", type: "input", span: 12 },
  {
    prop: "mcpType",
    label: "类型",
    type: "select",
    span: 12,
    options: mcpTypes,
  },
  {
    prop: "command",
    label: "命令",
    type: "input",
    span: 12,
    placeholder: "如 npx",
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
  {
    prop: "args",
    label: "参数",
    type: "input",
    span: 24,
    placeholder: "如 @modelcontextprotocol/server-filesystem /data",
  },
  { prop: "description", label: "描述", type: "textarea", span: 24, rows: 2 },
  { prop: "remark", label: "备注", type: "textarea", span: 24, rows: 2 },
];

const rules = {
  mcpName: [{ required: true, message: "请输入 MCP 名称", trigger: "blur" }],
  mcpType: [{ required: true, message: "请选择类型", trigger: "change" }],
};

const defaultForm = () => ({
  id: undefined,
  mcpName: "",
  description: "",
  mcpType: "stdio",
  command: "",
  args: "",
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
