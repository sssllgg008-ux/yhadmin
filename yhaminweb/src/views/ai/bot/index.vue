<template>
  <CrudList
    entity-name="Bot"
    name-field="botName"
    :search-fields="searchFields"
    :columns="columns"
    :form-fields="formFields"
    :rules="rules"
    :api="api"
    :default-form="defaultForm"
  >
    <template #channel="{ row }">
      <el-tag :type="channelTag(row.channel)" effect="light">{{
        channelText(row.channel)
      }}</el-tag>
    </template>
    <template #agentName="{ row }">
      <el-tag type="primary" effect="plain" size="small">{{
        row.agentName
      }}</el-tag>
    </template>
    <template #endpoint="{ row }">
      <span class="ry-mono">{{ row.endpoint }}</span>
    </template>
  </CrudList>
</template>

<script setup>
import CrudList from "@/components/CrudList.vue";
import { listBot, addBot, updateBot, delBot, changeBotStatus } from "@/api/ai";

const api = {
  list: listBot,
  add: addBot,
  update: updateBot,
  remove: delBot,
  changeStatus: changeBotStatus,
};

const channels = [
  { label: "Web", value: "web" },
  { label: "API", value: "api" },
  { label: "IDE", value: "ide" },
];

const searchFields = [
  {
    prop: "botName",
    label: "Bot 名称",
    type: "input",
    placeholder: "请输入 Bot 名称",
  },
  { prop: "channel", label: "渠道", type: "select", options: channels },
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
  { prop: "botName", label: "Bot 名称", minWidth: 160 },
  { prop: "description", label: "描述", minWidth: 200 },
  { prop: "agentName", label: "绑定智能体", width: 160, slot: "agentName" },
  {
    prop: "channel",
    label: "渠道",
    width: 100,
    align: "center",
    slot: "channel",
  },
  { prop: "endpoint", label: "访问地址", minWidth: 220, slot: "endpoint" },
  { prop: "remark", label: "备注", minWidth: 140 },
];

const formFields = [
  { prop: "botName", label: "Bot 名称", type: "input", span: 12 },
  {
    prop: "agentName",
    label: "绑定智能体",
    type: "input",
    span: 12,
    placeholder: "请输入智能体名称",
  },
  {
    prop: "channel",
    label: "发布渠道",
    type: "select",
    span: 12,
    options: channels,
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
  { prop: "endpoint", label: "访问地址", type: "input", span: 24 },
  { prop: "description", label: "描述", type: "textarea", span: 24, rows: 2 },
  { prop: "remark", label: "备注", type: "textarea", span: 24, rows: 2 },
];

const rules = {
  botName: [{ required: true, message: "请输入 Bot 名称", trigger: "blur" }],
  agentName: [{ required: true, message: "请输入绑定智能体", trigger: "blur" }],
  channel: [{ required: true, message: "请选择发布渠道", trigger: "change" }],
};

const defaultForm = () => ({
  id: undefined,
  botName: "",
  description: "",
  agentId: undefined,
  agentName: "",
  channel: "web",
  endpoint: "",
  status: "0",
  remark: "",
});

function channelText(c) {
  return { web: "Web", api: "API", ide: "IDE" }[c] || c;
}

function channelTag(c) {
  return { web: "primary", api: "success", ide: "warning" }[c] || "info";
}
</script>

<style lang="scss" scoped>
.ry-mono {
  font-family: var(--ry-font-mono);
  font-size: 13px;
}
</style>
