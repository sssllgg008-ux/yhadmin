<template>
  <section class="file-center">
    <el-alert type="info" :closable="false" show-icon title="按文件构建知识图谱">
      <template #default>系统会自动完成分块抽取、候选聚合和安全预审；您只需处理异常并确认发布。</template>
    </el-alert>
    <article class="ry-card panel-card">
      <div class="toolbar">
        <el-input v-model="query.keyword" clearable placeholder="搜索文件名称" @keyup.enter="search" />
        <el-select v-model="query.status" clearable placeholder="全部图谱状态" @change="search">
          <el-option label="未构建" value="NOT_BUILT" /><el-option label="处理中" value="RUNNING" />
          <el-option label="已完成" value="SUCCESS" /><el-option label="部分失败" value="PARTIAL_FAILED" />
        </el-select>
        <el-select v-model="modelId" filterable :loading="modelLoading" placeholder="选择本地抽取模型">
          <el-option v-for="item in models" :key="item.id" :label="item.modelName" :value="item.id" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button>
      </div>
      <div class="batch-bar">
        <span>已选择 {{ selected.length }} 份文件</span>
        <el-tooltip :content="buildDisabledReason(selected)" :disabled="!buildDisabledReason(selected)">
          <span><el-button type="primary" :disabled="!!buildDisabledReason(selected)" @click="build(selected)">批量构建</el-button></span>
        </el-tooltip>
        <el-button type="success" plain :disabled="!publishable.length" @click="publish(publishable)">确认发布安全候选</el-button>
      </div>
      <el-table v-loading="loading" :data="page.rows" @selection-change="selected = $event">
        <el-table-column type="selection" width="44" />
        <el-table-column prop="documentName" label="文件" min-width="260" show-overflow-tooltip />
        <el-table-column label="版本/分块" width="110"><template #default="{ row }">V{{ row.versionNo || '-' }} / {{ row.chunkCount || 0 }}</template></el-table-column>
        <el-table-column label="抽取进度" width="150"><template #default="{ row }"><el-progress :percentage="row.progress || 0" /></template></el-table-column>
        <el-table-column label="安全候选" width="95"><template #default="{ row }"><span class="success">{{ row.safeCount || 0 }}</span></template></el-table-column>
        <el-table-column label="待处理异常" width="105"><template #default="{ row }"><el-button v-if="row.exceptionCount" link type="warning" @click="$emit('exceptions', row.id)">{{ row.exceptionCount }}</el-button><span v-else>0</span></template></el-table-column>
        <el-table-column prop="publishedCount" label="已发布" width="85" />
        <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="statusType(row.graphStatus)">{{ statusLabel(row.graphStatus) }}</el-tag></template></el-table-column>
        <el-table-column prop="lastUpdateTime" label="最后更新" width="170" />
        <el-table-column label="操作" width="310" fixed="right"><template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">查看结果</el-button>
          <el-tooltip :content="buildDisabledReason([row])" :disabled="!buildDisabledReason([row])">
            <span><el-button link type="primary" :disabled="!!buildDisabledReason([row])" @click="build([row])">{{ row.runId ? '更新图谱' : '构建图谱' }}</el-button></span>
          </el-tooltip>
          <el-button link type="success" :disabled="!row.safeCount" @click="publish([row])">确认发布</el-button>
          <el-button link @click="$emit('view-graph', row.id)">查看图谱</el-button>
          <el-button link type="danger" :disabled="!row.publishedCount" @click="remove(row)">移除</el-button>
        </template></el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :page-sizes="[10,20,50]" :total="page.total" layout="total, sizes, prev, pager, next, jumper" @change="load" /></div>
    </article>
    <el-drawer v-model="drawer" title="文件图谱结果" size="min(720px,94vw)">
      <template v-if="detail.summary">
        <h3>{{ detail.summary.documentName }} · V{{ detail.summary.versionNo }}</h3>
        <div class="result-grid"><div v-for="item in detail.summary.counts || []" :key="`${item.candidateType}-${item.reviewStatus}-${item.publishStatus}`"><strong>{{ item.amount }}</strong><span>{{ typeLabel(item.candidateType) }} · {{ reviewLabel(item.reviewStatus) }} · {{ publishLabel(item.publishStatus) }}</span></div></div>
        <h4>来源版本</h4><div v-for="item in detail.sources" :key="item.versionId" class="source-row"><b>V{{ item.versionNo }}</b><span>{{ item.evidenceCount }} 个证据分块</span><span>{{ item.candidateCount }} 个候选</span></div>
      </template>
    </el-drawer>
  </section>
</template>
<script setup>
import { computed, onBeforeUnmount, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { buildKnowledgeGraphFiles, deleteKnowledgeGraphByDocument, getKnowledgeGraphFile, listKnowledgeGraphFiles, publishKnowledgeGraphFiles, listModels, listModelProviders } from '@/api/ai';
const props=defineProps({knowledgeBaseId:{type:Number,required:true}});defineEmits(['exceptions','view-graph']);
const loading=ref(false),modelLoading=ref(false),drawer=ref(false),selected=ref([]),modelId=ref(null),models=ref([]);const page=reactive({rows:[],total:0}),detail=reactive({summary:null,sources:[]});const query=reactive({keyword:'',status:'',pageNum:1,pageSize:10});let refreshTimer=null;
const publishable=computed(()=>selected.value.filter(x=>Number(x.safeCount)>0));const running=r=>['PENDING','RUNNING'].includes(r.graphStatus);
const statusLabel=v=>({NOT_BUILT:'未构建',PENDING:'待处理',RUNNING:'处理中',SUCCESS:'已完成',PARTIAL_FAILED:'部分失败',CANCELLED:'已取消'})[v]||v;
const statusType=v=>({SUCCESS:'success',RUNNING:'warning',PARTIAL_FAILED:'danger',CANCELLED:'info'})[v]||'info';
const typeLabel=v=>({ENTITY:'实体',RELATION:'关系',FACT:'事实'})[v]||v;const reviewLabel=v=>({PENDING:'异常待处理',APPROVED:'安全待发布',REJECTED:'已拒绝',MERGED:'已合并'})[v]||v;const publishLabel=v=>({NOT_PUBLISHED:'未发布',SUCCESS:'已发布',FAILED:'发布失败'})[v]||v;
function scheduleRefresh(){clearTimeout(refreshTimer);if(document.visibilityState==='visible'&&page.rows.some(r=>running(r)))refreshTimer=setTimeout(load,5000)}
async function load(){loading.value=true;try{Object.assign(page,await listKnowledgeGraphFiles(props.knowledgeBaseId,query));}finally{loading.value=false;scheduleRefresh()}}
async function loadModels(){modelLoading.value=true;try{const [modelPage,providers]=await Promise.all([listModels({pageNum:1,pageSize:1000,modelType:'CHAT'}),listModelProviders()]);const providerRows=providers?.data||providers||[];const ids=new Set(providerRows.filter(x=>x.providerType==='OLLAMA'&&x.status==='0').map(x=>Number(x.id)));const modelRows=modelPage?.rows||modelPage?.data?.rows||[];models.value=modelRows.filter(x=>x.modelType==='CHAT'&&x.status==='0'&&ids.has(Number(x.providerId)));const preferred=models.value.find(x=>x.isDefault)||models.value[0];modelId.value=preferred?.id??null;if(!preferred)ElMessage.warning('未找到已启用的 Ollama 对话模型，请先在模型管理中完成配置');}finally{modelLoading.value=false}}
function buildDisabledReason(rows){if(!rows.length)return '请先选择文件';if(!modelId.value)return models.value.length?'请选择本地抽取模型':'没有可用的 Ollama 对话模型';if(rows.some(r=>running(r)))return '文件正在构建中，请等待当前任务完成';return ''}
function search(){query.pageNum=1;load()}function reset(){Object.assign(query,{keyword:'',status:'',pageNum:1});load()}
async function build(rows){await buildKnowledgeGraphFiles(props.knowledgeBaseId,rows.map(x=>x.id),modelId.value);ElMessage.success(`已为 ${rows.length} 份文件创建图谱构建任务`);load()}
async function publish(rows){await ElMessageBox.confirm(`将发布 ${rows.length} 份文件的安全候选，未处理异常会被排除。是否继续？`,'确认构建图谱',{type:'warning'});const r=await publishKnowledgeGraphFiles(props.knowledgeBaseId,rows.map(x=>x.id));(r.failed?ElMessage.warning:ElMessage.success)(`发布完成：成功 ${r.success}，跳过 ${r.skipped}，失败 ${r.failed}`);load()}
async function openDetail(row){Object.assign(detail,await getKnowledgeGraphFile(props.knowledgeBaseId,row.id));drawer.value=true}
async function remove(row){await ElMessageBox.confirm(`只移除“${row.documentName}”提供的图谱来源；共享节点仍由其他文件保留。是否继续？`,'按文件移除图谱',{type:'warning'});const r=await deleteKnowledgeGraphByDocument(props.knowledgeBaseId,row.id);ElMessage.success(`已处理：删除节点 ${r.deletedNodes||0}，保留并更新节点 ${r.updatedNodes||0}`);load()}
defineExpose({refresh:load});load();loadModels();
onBeforeUnmount(()=>clearTimeout(refreshTimer));
</script>
<style scoped>.file-center{display:grid;gap:14px}.panel-card{padding:18px}.toolbar,.batch-bar{display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin-bottom:14px}.toolbar .el-input{width:240px}.toolbar .el-select{width:190px}.batch-bar{padding:10px 12px;background:var(--el-fill-color-lighter)}.pagination{display:flex;justify-content:flex-end;margin-top:14px}.success{color:var(--el-color-success)}.result-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:12px}.result-grid>div,.source-row{padding:14px;border:1px solid var(--el-border-color-lighter);border-radius:8px;display:grid;gap:6px}.result-grid strong{font-size:24px}.result-grid span,.source-row span{color:var(--ry-muted-foreground)}.source-row{grid-template-columns:70px 1fr 1fr;margin-bottom:10px}@media(max-width:760px){.result-grid{grid-template-columns:1fr}.toolbar .el-input,.toolbar .el-select{width:100%}}</style>
