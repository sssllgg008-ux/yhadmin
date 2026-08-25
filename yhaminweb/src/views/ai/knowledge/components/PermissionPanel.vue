<template>
  <section class="permission-panel">
    <header class="section-heading">
      <div>
        <h2>访问权限</h2>
        <p>
          授权条件会在 BM25 和 KNN 召回前过滤；没有配置授权时，默认租户内公开。
        </p>
      </div>
    </header>
    <el-alert
      title="管理员角色不受知识库授权限制。文档自定义授权优先于知识库授权。"
      type="info"
      :closable="false"
      show-icon
    />
    <div class="scope-card ry-card">
      <el-radio-group v-model="scopeType" @change="changeScope"
        ><el-radio-button value="KNOWLEDGE">知识库权限</el-radio-button
        ><el-radio-button value="DOCUMENT"
          >文档权限</el-radio-button
        ></el-radio-group
      >
      <el-select
        v-if="scopeType === 'DOCUMENT'"
        v-model="documentId"
        filterable
        placeholder="请选择文档"
        @change="loadPermissions"
      >
        <el-option
          v-for="item in documents"
          :key="item.id"
          :label="item.name"
          :value="item.id"
        />
      </el-select>
      <el-tag :type="permissionCount ? 'warning' : 'success'">{{
        permissionCount ? `已限制 ${permissionCount} 个主体` : "租户内公开"
      }}</el-tag>
    </div>
    <div class="permission-grid" v-loading="loading">
      <article class="ry-card permission-card">
        <h3>用户</h3>
        <p>指定用户可以读取当前范围。</p>
        <el-select
          v-model="selectedUsers"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          placeholder="选择用户"
          ><el-option
            v-for="item in users"
            :key="item.id"
            :label="item.nickname || item.username"
            :value="item.id"
        /></el-select>
      </article>
      <article class="ry-card permission-card">
        <h3>角色</h3>
        <p>拥有任一选中角色的用户可以读取。</p>
        <el-select
          v-model="selectedRoles"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          placeholder="选择角色"
          ><el-option
            v-for="item in roles"
            :key="item.id"
            :label="item.roleName"
            :value="item.id"
        /></el-select>
      </article>
      <article class="ry-card permission-card">
        <h3>部门</h3>
        <p>属于任一选中部门的用户可以读取。</p>
        <el-select
          v-model="selectedDepts"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          placeholder="选择部门"
          ><el-option
            v-for="item in depts"
            :key="item.id"
            :label="item.label || item.deptName"
            :value="item.id"
        /></el-select>
      </article>
    </div>
    <div class="save-bar ry-card">
      <span>保存后将同步更新活动 Elasticsearch 索引中的权限标记。</span
      ><el-button
        type="primary"
        :loading="saving"
        :disabled="scopeType === 'DOCUMENT' && !documentId"
        @click="save"
        >保存权限</el-button
      >
    </div>
  </section>
</template>
<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import {
  getKnowledgePermissions,
  pageKnowledgeDocuments,
  saveKnowledgePermissions,
} from "@/api/ai";
import { listDept, listRole, listUser } from "@/api/system";
const props = defineProps({
  knowledgeBaseId: { type: Number, required: true },
});
const scopeType = ref("KNOWLEDGE"),
  documentId = ref(),
  documents = ref([]),
  users = ref([]),
  roles = ref([]),
  depts = ref([]);
const selectedUsers = ref([]),
  selectedRoles = ref([]),
  selectedDepts = ref([]),
  loading = ref(false),
  saving = ref(false);
const permissionCount = computed(
  () =>
    selectedUsers.value.length +
    selectedRoles.value.length +
    selectedDepts.value.length,
);
const rows = (value) => value?.rows || value?.data?.rows || value?.data || [];
async function initialize() {
  const [docs, userRows, roleRows, deptRows] = await Promise.all([
    pageKnowledgeDocuments(props.knowledgeBaseId, {
      pageNum: 1,
      pageSize: 100,
    }),
    listUser({ pageNum: 1, pageSize: 100, status: "0" }),
    listRole({ pageNum: 1, pageSize: 100, status: "0" }),
    listDept({}),
  ]);
  documents.value = rows(docs);
  users.value = rows(userRows);
  roles.value = rows(roleRows);
  depts.value = rows(deptRows);
  await loadPermissions();
}
async function changeScope() {
  documentId.value = undefined;
  clear();
  if (scopeType.value === "KNOWLEDGE") await loadPermissions();
}
function clear() {
  selectedUsers.value = [];
  selectedRoles.value = [];
  selectedDepts.value = [];
}
async function loadPermissions() {
  if (scopeType.value === "DOCUMENT" && !documentId.value) return clear();
  loading.value = true;
  try {
    const data = await getKnowledgePermissions(
      props.knowledgeBaseId,
      scopeType.value === "DOCUMENT" ? documentId.value : undefined,
    );
    clear();
    for (const item of data || []) {
      if (item.subjectType === "USER") selectedUsers.value.push(item.subjectId);
      else if (item.subjectType === "ROLE")
        selectedRoles.value.push(item.subjectId);
      else if (item.subjectType === "DEPT")
        selectedDepts.value.push(item.subjectId);
    }
  } finally {
    loading.value = false;
  }
}
async function save() {
  saving.value = true;
  try {
    const data = [
      ...selectedUsers.value.map((subjectId) => ({
        subjectType: "USER",
        subjectId,
        permission: "READ",
      })),
      ...selectedRoles.value.map((subjectId) => ({
        subjectType: "ROLE",
        subjectId,
        permission: "READ",
      })),
      ...selectedDepts.value.map((subjectId) => ({
        subjectType: "DEPT",
        subjectId,
        permission: "READ",
      })),
    ];
    await saveKnowledgePermissions(
      props.knowledgeBaseId,
      scopeType.value === "DOCUMENT" ? documentId.value : undefined,
      data,
    );
    ElMessage.success("权限已保存并同步到活动索引");
    await loadPermissions();
  } finally {
    saving.value = false;
  }
}
onMounted(initialize);
</script>
<style scoped>
.permission-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.section-heading h2 {
  margin: 0 0 6px;
}
.section-heading p,
.permission-card p {
  margin: 0;
  color: #667085;
}
.scope-card {
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 16px;
}
.scope-card .el-select {
  width: min(420px, 100%);
}
.permission-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
.permission-card {
  padding: 18px;
}
.permission-card h3 {
  margin: 0 0 6px;
}
.permission-card p {
  min-height: 40px;
  font-size: 13px;
}
.permission-card .el-select {
  width: 100%;
  margin-top: 12px;
}
.save-bar {
  position: sticky;
  bottom: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  z-index: 2;
}
@media (max-width: 900px) {
  .permission-grid {
    grid-template-columns: 1fr;
  }
  .scope-card {
    align-items: stretch;
    flex-direction: column;
  }
  .save-bar {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
