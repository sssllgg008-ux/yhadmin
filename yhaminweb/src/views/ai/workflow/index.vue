<template>
  <div class="workflow-page">
    <div class="ry-card header-card">
      <div><h2>工作流管理</h2><p>使用 Tinyflow 可视化编排模型、知识库与工具节点。</p></div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增工作流</el-button>
    </div>
    <div class="ry-card table-card">
      <div class="filters"><el-input v-model="query.workflowName" placeholder="名称或编码" clearable @keyup.enter="load" />
        <el-select v-model="query.status" placeholder="全部状态" clearable><el-option label="草稿" value="DRAFT"/><el-option label="已发布" value="PUBLISHED"/><el-option label="已停用" value="DISABLED"/></el-select>
        <el-button type="primary" :icon="Search" @click="load">查询</el-button></div>
      <el-table v-loading="loading" :data="rows">
        <el-table-column prop="workflowName" label="工作流" min-width="180"><template #default="{row}"><div class="name">{{ row.workflowName }}</div><div class="sub">{{ row.workflowCode }}</div></template></el-table-column>
        <el-table-column prop="version" label="草稿版本" width="100"><template #default="{row}">V{{ row.version || '-' }}</template></el-table-column>
        <el-table-column label="结构" width="150"><template #default="{row}">{{ row.nodeCount }} 节点 / {{ row.edgeCount }} 连线</template></el-table-column>
        <el-table-column prop="status" label="状态" width="110"><template #default="{row}"><el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="170" />
        <el-table-column label="操作" width="360" fixed="right"><template #default="{row}"><el-button link type="primary" @click="openEdit(row)">修改</el-button><el-button link type="primary" @click="openDesigner(row)">设计</el-button><el-button link type="success" @click="publish(row)">发布</el-button><el-button link type="warning" :disabled="row.status !== 'PUBLISHED'" @click="run(row)">运行</el-button><el-button link @click="showExecutions(row)">执行记录</el-button><el-button link type="danger" @click="removeRow(row)">删除</el-button></template></el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total" layout="total, sizes, prev, pager, next" @change="load" /></div>
    </div>

    <el-dialog v-model="editorVisible" :title="form.id ? `设计工作流 · ${form.workflowName}` : '新增工作流'" fullscreen destroy-on-close class="workflow-dialog" @opened="designerReady=true" @closed="designerReady=false">
      <div v-if="!form.id" class="basic-form"><el-form label-width="90px"><el-form-item label="工作流名称"><el-input v-model="form.workflowName" /></el-form-item><el-form-item label="工作流编码"><el-input v-model="form.workflowCode" placeholder="例如 order_assistant" /></el-form-item><el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item></el-form></div>
      <div v-else class="designer-shell">
        <div class="designer-tip"><span>拖拽左侧节点到画布进行编排，点击节点可在右侧配置参数。</span><el-tag type="info">草稿 V{{ form.version || 1 }}</el-tag></div>
        <div class="designer-canvas">
          <TinyflowDesigner v-if="designerReady" v-model="form.dsl" />
        </div>
      </div>
      <template #footer><el-button @click="editorVisible=false">取消</el-button><el-button v-if="form.id" @click="check">校验</el-button><el-button type="primary" :loading="saving" @click="save">{{ form.id ? '保存草稿' : '创建并进入设计' }}</el-button></template>
    </el-dialog>

    <el-dialog v-model="runVisible" :title="`运行工作流 · ${runningWorkflow.workflowName || ''}`" width="680px" destroy-on-close>
      <div v-loading="runInputLoading" class="run-form-shell">
        <el-alert
          v-if="runInputFields.length"
          title="输入项来自已发布版本的开始节点，填写后将自动组装为工作流输入。"
          type="info"
          :closable="false"
          show-icon
        />
        <el-form v-if="runInputFields.length" ref="runFormRef" :model="runForm" label-position="top" class="run-form">
          <el-form-item
            v-for="field in runInputFields"
            :key="field.name"
            :label="field.label || field.name"
            :prop="field.name"
            :required="field.required"
          >
            <el-select v-if="field.enums?.length" v-model="runForm[field.name]" clearable filterable :placeholder="field.placeholder">
              <el-option v-for="option in field.enums" :key="option" :label="option" :value="option" />
            </el-select>
            <el-switch v-else-if="isBoolean(field)" v-model="runForm[field.name]" />
            <el-input-number v-else-if="isNumber(field)" v-model="runForm[field.name]" controls-position="right" />
            <el-input
              v-else-if="isJson(field) || field.formType === 'textarea'"
              v-model="runForm[field.name]"
              type="textarea"
              :rows="isJson(field) ? 5 : 3"
              :placeholder="isJson(field) ? '请输入合法 JSON' : field.placeholder"
            />
            <el-input v-else v-model="runForm[field.name]" clearable :placeholder="field.placeholder" />
            <div class="field-meta">
              <el-tag size="small" type="info">{{ field.dataType || 'String' }}</el-tag>
              <span>参数名：{{ field.name }}</span>
            </div>
          </el-form-item>
        </el-form>
        <template v-else-if="!runInputLoading">
          <el-empty description="开始节点尚未声明输入参数" :image-size="70" />
          <el-form label-position="top">
            <el-form-item label="输入 JSON（兼容模式）">
              <el-input v-model="fallbackInputJson" type="textarea" :rows="7" placeholder="{}" />
            </el-form-item>
          </el-form>
        </template>
        <section v-if="running || executionResult" class="execution-result">
          <div class="result-heading">
            <div>
              <h3>执行过程与结果</h3>
              <p v-if="running">工作流正在执行，请稍候……</p>
              <p v-else>请求编号：{{ executionResult.requestId }}</p>
            </div>
            <el-tag v-if="running" type="warning">执行中</el-tag>
            <el-tag v-else :type="executionResult.status === 'SUCCESS' ? 'success' : 'danger'">
              {{ executionResult.status === 'SUCCESS' ? '执行成功' : '执行失败' }}
            </el-tag>
          </div>
          <el-progress v-if="running" :percentage="100" :indeterminate="true" :duration="2" />
          <el-alert v-if="executionResult?.errorMessage" :title="executionResult.errorMessage" type="error" :closable="false" show-icon />
          <el-timeline v-if="executionResult?.steps?.length" class="step-timeline">
            <el-timeline-item
              v-for="(step,index) in executionResult.steps"
              :key="`${step.nodeId}-${index}`"
              :type="step.status === 'SUCCESS' ? 'success' : 'danger'"
              :timestamp="`${step.durationMs || 0} ms`"
              placement="top"
            >
              <div class="step-card">
                <div class="step-title">
                  <strong>{{ index + 1 }}. {{ step.nodeName || step.nodeId }}</strong>
                  <span>{{ nodeTypeText(step.nodeType) }}</span>
                </div>
                <pre v-if="step.outputJson && step.outputJson !== '{}'">{{ prettyJson(step.outputJson) }}</pre>
                <p v-if="step.errorMessage" class="step-error">{{ step.errorMessage }}</p>
              </div>
            </el-timeline-item>
          </el-timeline>
          <div v-if="executionResult?.outputJson" class="final-output">
            <h4>最终输出</h4>
            <pre>{{ prettyJson(executionResult.outputJson) }}</pre>
          </div>
        </section>
      </div>
      <template #footer>
        <el-button @click="runVisible=false">取消</el-button>
        <el-button type="primary" :loading="running" :disabled="runInputLoading" @click="submitRun">开始运行</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="修改工作流" width="680px" destroy-on-close>
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="工作流名称" required><el-input v-model="editForm.workflowName" maxlength="100" show-word-limit /></el-form-item>
        <el-form-item label="工作流编码" required><el-input v-model="editForm.workflowCode" maxlength="64" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="editForm.description" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="备注"><el-input v-model="editForm.remark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="editVisible=false">取消</el-button><el-button type="primary" :loading="editSaving" @click="saveEdit">保存修改</el-button></template>
    </el-dialog>

    <el-drawer v-model="executionVisible" title="执行记录" size="920px">
      <div class="execution-filters">
        <el-input v-model="executionQuery.requestId" clearable placeholder="请求编号" @keyup.enter="loadExecutions" />
        <el-select v-model="executionQuery.status" clearable placeholder="全部状态" @change="loadExecutions"><el-option label="成功" value="SUCCESS"/><el-option label="失败" value="FAILED"/><el-option label="执行中" value="RUNNING"/></el-select>
        <el-date-picker v-model="executionQuery.timeRange" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss" start-placeholder="开始时间" end-placeholder="结束时间" />
        <el-button type="primary" @click="loadExecutions">查询</el-button>
        <el-button @click="resetExecutionQuery">重置</el-button>
      </div>
      <el-table :data="executionRows" stripe>
        <el-table-column prop="requestId" label="请求编号" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="90"><template #default="{row}"><el-tag :type="row.status==='SUCCESS'?'success':'danger'">{{ row.status==='SUCCESS'?'成功':'失败' }}</el-tag></template></el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="170" />
        <el-table-column prop="endTime" label="结束时间" width="170" />
        <el-table-column label="总耗时" width="110"><template #default="{row}">{{ executionDuration(row) }}</template></el-table-column>
        <el-table-column label="节点" width="70"><template #default="{row}">{{ row.steps?.length || 0 }}</template></el-table-column>
        <el-table-column label="操作" width="80" fixed="right"><template #default="{row}"><el-button link type="primary" @click="openExecutionDetail(row)">详情</el-button></template></el-table-column>
      </el-table>
      <div class="pagination"><el-pagination v-model:current-page="executionQuery.pageNum" v-model:page-size="executionQuery.pageSize" :page-sizes="[10,20,50]" :total="executionTotal" layout="total, sizes, prev, pager, next" @change="loadExecutions" /></div>
    </el-drawer>

    <el-dialog v-model="executionDetailVisible" title="执行记录详情" width="900px" destroy-on-close>
      <template v-if="executionDetail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="请求编号" :span="2">{{ executionDetail.requestId }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ executionDetail.status }}</el-descriptions-item>
          <el-descriptions-item label="总耗时">{{ executionDuration(executionDetail) }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ executionDetail.startTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ executionDetail.endTime || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-alert v-if="executionDetail.errorMessage" class="detail-block" :title="executionDetail.errorMessage" type="error" :closable="false" show-icon />
        <div class="detail-block"><h4>输入参数</h4><pre>{{ prettyJson(executionDetail.inputJson || '{}') }}</pre></div>
        <div class="detail-block"><h4>节点执行过程</h4>
          <el-timeline v-if="executionDetail.steps?.length"><el-timeline-item v-for="(step,index) in executionDetail.steps" :key="`${step.nodeId}-${index}`" :type="step.status==='SUCCESS'?'success':'danger'" :timestamp="`${step.durationMs || 0} ms`"><div class="step-card"><div class="step-title"><strong>{{ index + 1 }}. {{ step.nodeName || step.nodeId }}</strong><span>{{ nodeTypeText(step.nodeType) }}</span></div><pre v-if="step.outputJson && step.outputJson!=='{}'">{{ prettyJson(step.outputJson) }}</pre><p v-if="step.errorMessage" class="step-error">{{ step.errorMessage }}</p></div></el-timeline-item></el-timeline>
          <el-empty v-else description="该历史记录没有节点步骤快照" :image-size="70" />
        </div>
        <div class="detail-block"><h4>最终输出</h4><pre>{{ prettyJson(executionDetail.outputJson || '{}') }}</pre></div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Search } from "@element-plus/icons-vue";
import TinyflowDesigner from "@/components/ai/workflow/TinyflowDesigner.vue";
import { addWorkflow, delWorkflow, getWorkflow, getWorkflowExecution, getWorkflowInputs, listWorkflow, listWorkflowExecutions, publishWorkflow, runWorkflow, saveWorkflowDraft, updateWorkflow, validateWorkflow } from "@/api/ai";

const loading=ref(false), saving=ref(false), editorVisible=ref(false), designerReady=ref(false), executionVisible=ref(false), rows=ref([]), total=ref(0), executionRows=ref([]), executionTotal=ref(0);
const runVisible=ref(false), runInputLoading=ref(false), running=ref(false), runInputFields=ref([]), runFormRef=ref();
const runningWorkflow=reactive({id:null,workflowName:""}), runForm=reactive({});
const fallbackInputJson=ref("{}");
const executionResult=ref(null);
const editVisible=ref(false), editSaving=ref(false), executionDetailVisible=ref(false), executionDetail=ref(null);
const editForm=reactive({id:null,workflowName:"",workflowCode:"",description:"",remark:"",dsl:"",schemaVersion:"tinyflow-2"});
const query=reactive({pageNum:1,pageSize:10,workflowName:"",status:""});
const executionQuery=reactive({workflowId:null,requestId:"",status:"",timeRange:[],pageNum:1,pageSize:10});
const emptyDsl=()=>JSON.stringify({nodes:[],edges:[],viewport:{x:0,y:0,zoom:1}});
const form=reactive({id:null,workflowName:"",workflowCode:"",description:"",dsl:emptyDsl(),schemaVersion:"tinyflow-2",remark:""});
const reset=()=>Object.assign(form,{id:null,workflowName:"",workflowCode:"",description:"",dsl:emptyDsl(),schemaVersion:"tinyflow-2",remark:""});
async function load(){loading.value=true;try{const res=await listWorkflow(query);rows.value=res.rows||[];total.value=res.total||0;}finally{loading.value=false;}}
function openCreate(){reset();editorVisible.value=true;}
async function openDesigner(row){const res=await getWorkflow(row.id);Object.assign(form,res.data);editorVisible.value=true;}
async function openEdit(row){const res=await getWorkflow(row.id);Object.assign(editForm,res.data);editVisible.value=true;}
async function saveEdit(){if(!editForm.workflowName||!editForm.workflowCode)return ElMessage.warning("请填写工作流名称和编码");editSaving.value=true;try{await updateWorkflow({...editForm,dsl:null});ElMessage.success("工作流信息已修改");editVisible.value=false;await load();}finally{editSaving.value=false;}}
async function save(){if(!form.workflowName||!form.workflowCode)return ElMessage.warning("请填写名称和编码");saving.value=true;try{if(!form.id){const res=await addWorkflow({...form});Object.assign(form,res.data);ElMessage.success("已创建，请继续编排");}else{await saveWorkflowDraft(form.id,{dsl:form.dsl,schemaVersion:form.schemaVersion});ElMessage.success("草稿已保存");}await load();}finally{saving.value=false;}}
async function check(){const result=await validateWorkflow(form.id,{dsl:form.dsl,schemaVersion:form.schemaVersion});if(result.valid)ElMessage.success(`校验通过：${result.nodeCount}个节点，${result.edgeCount}条连线`);else ElMessage.error(result.errors.map(i=>i.message).join("；"));}
async function publish(row){await ElMessageBox.confirm("发布后运行将固定使用该版本，是否继续？","发布工作流");await publishWorkflow(row.id);ElMessage.success("发布成功");load();}
const normalizedType=(field)=>String(field.dataType||"String").toLowerCase();
const isBoolean=(field)=>normalizedType(field)==="boolean";
const isNumber=(field)=>["number","integer","long","double","float"].includes(normalizedType(field));
const isJson=(field)=>["object","array","json"].includes(normalizedType(field)) || (field.children?.length||0)>0;
function initialValue(field){
  if(field.defaultValue!==undefined&&field.defaultValue!==null){
    return isJson(field)&&typeof field.defaultValue!=="string"?JSON.stringify(field.defaultValue,null,2):field.defaultValue;
  }
  if(isBoolean(field))return false;
  if(isNumber(field))return undefined;
  if(isJson(field))return field.children?.length?JSON.stringify(Object.fromEntries(field.children.map(child=>[child.name,initialValue(child)??""])),null,2):"{}";
  return "";
}
async function run(row){
  Object.assign(runningWorkflow,{id:row.id,workflowName:row.workflowName});
  Object.keys(runForm).forEach(key=>delete runForm[key]);
  fallbackInputJson.value="{}";
  executionResult.value=null;
  runInputFields.value=[];
  runVisible.value=true;
  runInputLoading.value=true;
  try{
    runInputFields.value=await getWorkflowInputs(row.id)||[];
    runInputFields.value.forEach(field=>{runForm[field.name]=initialValue(field);});
  }finally{runInputLoading.value=false;}
}
function buildRunInputs(){
  if(!runInputFields.value.length)return JSON.parse(fallbackInputJson.value||"{}");
  const inputs={};
  for(const field of runInputFields.value){
    const value=runForm[field.name];
    if(field.required&&(value===undefined||value===null||value===""))throw new Error(`请填写${field.label||field.name}`);
    if(value===""||value===undefined)continue;
    if(isJson(field)){
      try{inputs[field.name]=typeof value==="string"?JSON.parse(value):value;}catch{throw new Error(`${field.label||field.name}必须是合法 JSON`);}
    }else{inputs[field.name]=value;}
  }
  return inputs;
}
async function submitRun(){
  let inputs;
  try{inputs=buildRunInputs();}catch(error){return ElMessage.warning(error.message);}
  running.value=true;
  try{
    const result=await runWorkflow(runningWorkflow.id,inputs);
    executionResult.value=result;
    if(result.status==="SUCCESS"){ElMessage.success("工作流运行成功");}
    else ElMessage.error(result.errorMessage||"工作流运行失败");
  }finally{running.value=false;}
}
function prettyJson(value){try{return JSON.stringify(typeof value==="string"?JSON.parse(value):value,null,2);}catch{return value;}}
function nodeTypeText(type){return ({StartNode:"开始节点",EndNode:"结束节点",LlmNode:"大模型节点",KnowledgeNode:"知识库节点",SearchEngineNode:"搜索引擎节点",CodeNode:"动态代码节点",HttpNode:"HTTP节点",TemplateNode:"内容模板节点",IntentRecognitionNode:"意图识别节点"})[type]||type||"流程节点";}
async function removeRow(row){await ElMessageBox.confirm(`确认删除“${row.workflowName}”？`,"删除工作流",{type:"warning"});await delWorkflow(row.id);ElMessage.success("删除成功");load();}
async function showExecutions(row){Object.assign(executionQuery,{workflowId:row.id,requestId:"",status:"",timeRange:[],pageNum:1,pageSize:10});executionVisible.value=true;await loadExecutions();}
async function loadExecutions(){
  if(!executionQuery.workflowId)return;
  const page=await listWorkflowExecutions(executionQuery.workflowId,{requestId:executionQuery.requestId,status:executionQuery.status,startTime:executionQuery.timeRange?.[0],endTime:executionQuery.timeRange?.[1],pageNum:executionQuery.pageNum,pageSize:executionQuery.pageSize});
  executionRows.value=page?.rows||[];executionTotal.value=page?.total||0;
}
async function resetExecutionQuery(){Object.assign(executionQuery,{requestId:"",status:"",timeRange:[],pageNum:1});await loadExecutions();}
async function openExecutionDetail(row){executionDetail.value=await getWorkflowExecution(executionQuery.workflowId,row.id);executionDetailVisible.value=true;}
function executionDuration(row){if(!row?.startTime||!row?.endTime)return "-";const value=new Date(row.endTime).getTime()-new Date(row.startTime).getTime();return Number.isFinite(value)?`${Math.max(0,value)} ms`:"-";}
const statusText=s=>({DRAFT:"草稿",PUBLISHED:"已发布",DISABLED:"已停用"}[s]||s); const statusType=s=>s==="PUBLISHED"?"success":s==="DISABLED"?"info":"warning";
onMounted(load);
</script>

<style scoped lang="scss">
.workflow-page{padding:16px;display:flex;flex-direction:column;gap:14px}.header-card{display:flex;align-items:center;justify-content:space-between;padding:18px 20px}.header-card h2{margin:0 0 6px}.header-card p{margin:0;color:#7b8496}.table-card{padding:16px}.filters{display:flex;gap:12px;margin-bottom:16px}.filters .el-input{width:260px}.filters .el-select{width:170px}.pagination{display:flex;justify-content:flex-end;margin-top:16px}.name{font-weight:600}.sub{font-size:12px;color:#9098a8;margin-top:4px}.basic-form{max-width:720px;margin:20px auto}.designer-shell{height:100%;min-height:0;display:flex;flex-direction:column;border:1px solid var(--el-border-color-light);border-radius:8px;overflow:hidden}.designer-tip{height:42px;flex:none;display:flex;align-items:center;justify-content:space-between;padding:0 14px;color:var(--el-text-color-secondary);background:var(--el-fill-color-lighter);border-bottom:1px solid var(--el-border-color-light)}.designer-canvas{flex:1;min-width:0;min-height:0;overflow:hidden}.designer-canvas>div{width:100%;height:100%}.run-form-shell{min-height:180px;max-height:68vh;overflow:auto;padding-right:4px}.run-form{margin-top:18px}.run-form :deep(.el-select),.run-form :deep(.el-input-number){width:100%}.field-meta{display:flex;align-items:center;gap:8px;margin-top:7px;color:var(--el-text-color-secondary);font-size:12px}.execution-result{margin-top:22px;padding-top:18px;border-top:1px solid var(--el-border-color-light)}.result-heading,.step-title{display:flex;align-items:center;justify-content:space-between;gap:12px}.result-heading h3,.final-output h4{margin:0}.result-heading p{margin:5px 0 0;color:var(--el-text-color-secondary);font-size:12px}.step-timeline{margin-top:20px}.step-card{padding:12px 14px;border:1px solid var(--el-border-color-light);border-radius:8px;background:var(--el-bg-color)}.step-title span{color:var(--el-text-color-secondary);font-size:12px}.step-card pre,.final-output pre{max-height:220px;margin:10px 0 0;padding:12px;overflow:auto;white-space:pre-wrap;word-break:break-word;border-radius:6px;background:var(--el-fill-color-lighter);font-size:12px}.step-error{color:var(--el-color-danger)}.final-output{margin-top:16px}:global(.workflow-dialog .el-dialog__body){height:calc(100vh - 118px);box-sizing:border-box;padding:0 16px 10px;overflow:hidden}:global(.workflow-dialog .el-dialog__header){height:56px;box-sizing:border-box;margin:0;padding:16px 20px;border-bottom:1px solid var(--el-border-color-light)}:global(.workflow-dialog .el-dialog__footer){height:62px;box-sizing:border-box;padding:12px 20px;border-top:1px solid var(--el-border-color-light)}@media(max-width:768px){.filters{flex-wrap:wrap}.filters .el-input,.filters .el-select{width:100%}.designer-tip span{display:none}}
.detail-block{margin-top:18px}.detail-block h4{margin:0 0 10px}.detail-block>pre{max-height:260px;margin:0;padding:14px;overflow:auto;white-space:pre-wrap;word-break:break-word;border-radius:8px;background:var(--el-fill-color-lighter);font-size:12px}
.execution-filters{display:flex;align-items:center;flex-wrap:wrap;gap:10px;margin-bottom:14px}.execution-filters .el-input{width:220px}.execution-filters .el-select{width:140px}.execution-filters :deep(.el-date-editor){width:340px}
</style>
