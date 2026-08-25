<template>
  <div class="ry-page">
    <div class="ry-card ry-search-card">
      <el-form :model="query" inline @submit.prevent>
        <el-form-item label="关键词"><el-input v-model.trim="query.keyword" clearable placeholder="请求编号 / 地址 / 错误消息" @keyup.enter="search" /></el-form-item>
        <el-form-item label="用户"><el-input v-model.trim="query.username" clearable placeholder="用户名" @keyup.enter="search" /></el-form-item>
        <el-form-item label="功能"><el-input v-model.trim="query.feature" clearable placeholder="功能名称" @keyup.enter="search" /></el-form-item>
        <el-form-item label="状态码"><el-select v-model="query.httpStatus" clearable placeholder="全部" style="width:120px"><el-option label="400" :value="400"/><el-option label="401" :value="401"/><el-option label="403" :value="403"/><el-option label="404" :value="404"/><el-option label="500" :value="500"/></el-select></el-form-item>
        <el-form-item><el-button type="primary" :icon="Search" @click="search">查询</el-button><el-button :icon="Refresh" @click="reset">重置</el-button></el-form-item>
      </el-form>
    </div>
    <div class="ry-card ry-table-card">
      <div class="ry-toolbar"><div/><el-button circle :icon="Refresh" @click="load" /></div>
      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column prop="id" label="编号" width="80" align="center"/>
        <el-table-column prop="errorTime" label="发生时间" width="165"/>
        <el-table-column prop="username" label="用户" width="110"/>
        <el-table-column prop="feature" label="功能" min-width="180" show-overflow-tooltip/>
        <el-table-column prop="requestMethod" label="方法" width="85" align="center"/>
        <el-table-column prop="requestUri" label="请求地址" min-width="220" show-overflow-tooltip/>
        <el-table-column prop="httpStatus" label="状态码" width="90" align="center"><template #default="{row}"><el-tag type="danger">{{ row.httpStatus }}</el-tag></template></el-table-column>
        <el-table-column prop="exceptionType" label="异常类型" min-width="180" show-overflow-tooltip/>
        <el-table-column prop="errorMessage" label="错误消息" min-width="220" show-overflow-tooltip/>
        <el-table-column label="操作" width="80" fixed="right"><template #default="{row}"><el-button link type="primary" @click="detail=row; drawer=true">详情</el-button></template></el-table-column>
      </el-table>
      <div class="ry-pagination"><el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="load" @current-change="load"/></div>
    </div>
    <el-drawer v-model="drawer" title="错误日志详情" size="min(760px, 96vw)">
      <el-descriptions v-if="detail" :column="1" border><el-descriptions-item label="请求编号">{{ detail.requestId }}</el-descriptions-item><el-descriptions-item label="用户">{{ detail.username }}</el-descriptions-item><el-descriptions-item label="功能">{{ detail.feature }}</el-descriptions-item><el-descriptions-item label="请求">{{ detail.requestMethod }} {{ detail.requestUri }}</el-descriptions-item><el-descriptions-item label="IP">{{ detail.requestIp }}</el-descriptions-item><el-descriptions-item label="错误编码">{{ detail.errorCode || '-' }}</el-descriptions-item><el-descriptions-item label="异常类型">{{ detail.exceptionType }}</el-descriptions-item><el-descriptions-item label="错误消息">{{ detail.errorMessage }}</el-descriptions-item></el-descriptions>
      <h4>异常堆栈</h4><pre class="stack">{{ detail?.stackTrace || '无' }}</pre>
    </el-drawer>
  </div>
</template>
<script setup>
import { onMounted, reactive, ref } from "vue";
import { Search, Refresh } from "@element-plus/icons-vue";
import { listErrorlog } from "@/api/system";
const query=reactive({pageNum:1,pageSize:10,keyword:"",username:"",feature:"",httpStatus:null});
const rows=ref([]),total=ref(0),loading=ref(false),drawer=ref(false),detail=ref(null);
async function load(){loading.value=true;try{const r=await listErrorlog({...query});rows.value=r.rows||[];total.value=r.total||0;}finally{loading.value=false;}}
function search(){query.pageNum=1;load();} function reset(){Object.assign(query,{pageNum:1,keyword:"",username:"",feature:"",httpStatus:null});load();}
onMounted(load);
</script>
<style scoped>.ry-page{display:flex;flex-direction:column;gap:16px}.stack{padding:14px;background:#101827;color:#dbeafe;border-radius:6px;white-space:pre-wrap;word-break:break-word;max-height:55vh;overflow:auto;font-size:12px}</style>
