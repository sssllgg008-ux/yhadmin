<template>
  <section class="index-panel">
    <div class="section-heading">
      <div>
        <h3>分块与索引</h3>
        <p>按文档管理版本、文本分块和处理任务，适用于大规模知识库。</p>
      </div>
      <div class="heading-actions">
        <el-button @click="showLifecycle">索引生命周期</el-button>
        <el-button @click="showSwitchLogs">切换记录</el-button>
        <el-button
          v-if="overview.canRetrySwitch"
          type="warning"
          plain
          :loading="switching"
          @click="retrySwitch"
          >重试切换</el-button
        >
        <el-button
          v-if="overview.canRollback"
          type="danger"
          plain
          :loading="switching"
          @click="rollbackIndex"
          >回滚到 G{{ overview.previousGeneration }}</el-button
        >
        <el-button :icon="Download" @click="exportFailures"
          >导出失败任务</el-button
        >
        <el-button :icon="Refresh" :loading="loading" @click="loadAll(false)"
          >刷新</el-button
        >
        <el-button
          type="primary"
          :loading="rebuilding"
          :disabled="buildInProgress"
          @click="rebuildAll"
          >创建新代次并重建全部</el-button
        >
      </div>
    </div>

    <div class="status-grid">
      <article class="ry-card">
        <span>文档总数</span><strong>{{ overview.documentTotal || 0 }}</strong>
      </article>
      <article class="ry-card generation-card">
        <span>活动代次</span
        ><strong
          >G{{
            overview.activeGeneration || overview.currentGeneration || 1
          }}</strong
        ><small v-if="overview.buildingGeneration"
          >正在构建 G{{ overview.buildingGeneration }} ·
          {{ buildStatusText }}</small
        ><small v-else
          >ES 别名：{{
            overview.aliasGeneration ? `G${overview.aliasGeneration}` : "未识别"
          }}</small
        ><small :class="consistencyClass">{{ consistencyText }}</small>
      </article>
      <article class="ry-card model">
        <span>当前向量模型</span
        ><strong>{{ overview.embeddingModelName || "未配置" }}</strong
        ><small>{{
          overview.dimension ? `${overview.dimension} 维` : "维度未配置"
        }}</small
        ><small v-if="overview.buildingEmbeddingModelId"
          >待生效：{{ overview.buildingEmbeddingModelName }} ·
          {{ overview.buildingDimension }} 维</small
        >
      </article>
      <article class="ry-card success">
        <span>已索引分块</span><strong>{{ overview.indexed || 0 }}</strong>
      </article>
      <article class="ry-card warning">
        <span>处理中</span><strong>{{ overview.processing || 0 }}</strong>
      </article>
      <article class="ry-card danger">
        <span>失败文档</span><strong>{{ overview.failed || 0 }}</strong>
      </article>
    </div>

    <el-alert
      v-if="overview.buildStatus && overview.buildStatus !== 'IDLE'"
      class="build-alert"
      :type="overview.buildStatus === 'FAILED' ? 'error' : 'info'"
      show-icon
      :closable="false"
      :title="`索引构建状态：${buildStatusText}`"
      :description="
        overview.lastError ||
        (buildInProgress
          ? '新代次构建完成并成功切换别名前，当前活动代次继续提供检索服务。'
          : '')
      "
    />
    <el-alert
      v-if="
        overview.consistencyStatus &&
        overview.consistencyStatus !== 'CONSISTENT'
      "
      class="build-alert"
      type="warning"
      show-icon
      :closable="false"
      title="MySQL 与 Elasticsearch 活动代次不一致"
      :description="overview.consistencyMessage"
    />

    <article class="ry-card content-card">
      <el-form class="filters" :inline="true">
        <el-form-item label="文档名称"
          ><el-input
            v-model="query.name"
            clearable
            placeholder="请输入文档名称"
            @keyup.enter="search"
        /></el-form-item>
        <el-form-item label="文档状态"
          ><el-select v-model="query.status" clearable placeholder="全部"
            ><el-option
              v-for="s in documentStatuses"
              :key="s.value"
              v-bind="s" /></el-select
        ></el-form-item>
        <el-form-item label="索引状态"
          ><el-select v-model="query.indexStatus" clearable placeholder="全部"
            ><el-option
              v-for="s in indexStatuses"
              :key="s.value"
              v-bind="s" /></el-select
        ></el-form-item>
        <el-form-item label="处理阶段"
          ><el-select v-model="query.currentStage" clearable placeholder="全部"
            ><el-option
              v-for="s in stages"
              :key="s.value"
              v-bind="s" /></el-select
        ></el-form-item>
        <el-form-item label="索引代次"
          ><el-input-number
            v-model="query.indexGeneration"
            :min="1"
            controls-position="right"
        /></el-form-item>
        <el-form-item
          ><el-checkbox v-model="query.failedOnly"
            >仅失败文档</el-checkbox
          ></el-form-item
        >
        <el-form-item
          ><el-button type="primary" :icon="Search" @click="search"
            >查询</el-button
          ><el-button @click="reset">重置</el-button></el-form-item
        >
      </el-form>

      <div class="toolbar">
        <div>
          <el-button
            type="primary"
            plain
            :disabled="!selectedIds.length"
            @click="batchAction('reindex')"
            >按当前代次重新索引</el-button
          >
          <el-button
            type="warning"
            plain
            :disabled="!selectedIds.length"
            @click="batchAction('retry')"
            >重试失败版本</el-button
          >
          <el-button
            type="danger"
            plain
            :disabled="!selectedIds.length"
            @click="batchAction('cancel')"
            >取消等待任务</el-button
          >
        </div>
        <span>已选择 {{ selectedIds.length }} / 100 份文档</span>
      </div>

      <el-table
        v-loading="loading"
        :data="documents"
        row-key="id"
        @selection-change="selectRows"
        @row-dblclick="openDocument"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column
          prop="name"
          label="文档名称"
          min-width="220"
          show-overflow-tooltip
        >
          <template #default="{ row }"
            ><el-button link type="primary" @click="openDocument(row)">{{
              row.name
            }}</el-button></template
          >
        </el-table-column>
        <el-table-column prop="currentVersionNo" label="当前版本" width="90"
          ><template #default="{ row }"
            >V{{ row.currentVersionNo || "-" }}</template
          ></el-table-column
        >
        <el-table-column prop="status" label="文档状态" width="105"
          ><template #default="{ row }"
            ><el-tag :type="tagType(row.status)">{{
              text(row.status)
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column prop="currentStage" label="当前阶段" width="105"
          ><template #default="{ row }">{{
            stageText(row.currentStage)
          }}</template></el-table-column
        >
        <el-table-column
          prop="chunkCount"
          label="分块"
          width="80"
          align="right"
        />
        <el-table-column
          prop="indexedChunkCount"
          label="已索引"
          width="85"
          align="right"
        />
        <el-table-column
          prop="failedTaskCount"
          label="失败任务"
          width="90"
          align="right"
          ><template #default="{ row }"
            ><span :class="{ dangerText: row.failedTaskCount }">{{
              row.failedTaskCount || 0
            }}</span></template
          ></el-table-column
        >
        <el-table-column prop="indexStatus" label="索引状态" width="120"
          ><template #default="{ row }"
            ><el-tag :type="tagType(row.indexStatus)">{{
              text(row.indexStatus)
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column prop="indexGeneration" label="代次" width="70"
          ><template #default="{ row }"
            >G{{ row.indexGeneration || 1 }}</template
          ></el-table-column
        >
        <el-table-column
          prop="lastIndexTime"
          label="最后索引时间"
          width="170"
        />
        <el-table-column label="操作" width="270" fixed="right"
          ><template #default="{ row }"
            ><el-button link type="primary" @click="openDocument(row)"
              >详情</el-button
            ><el-button link type="primary" @click="openRechunk(row)"
              >重新切片</el-button
            ><el-button link type="primary" @click="singleReindex(row)"
              >当前代次重索引</el-button
            ></template
          ></el-table-column
        >
        <template #empty><el-empty description="暂无文档索引数据" /></template>
      </el-table>
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @change="loadDocuments"
      />
    </article>

    <el-drawer
      v-model="drawer"
      :title="currentDocument?.name || '文档详情'"
      size="min(920px, 96vw)"
      @closed="closeDrawer"
    >
      <div class="drawer-summary">
        <span>当前版本 V{{ currentDocument?.currentVersionNo || "-" }}</span>
        <el-tag :type="tagType(currentDocument?.indexStatus)">{{
          text(currentDocument?.indexStatus)
        }}</el-tag>
        <span
          >{{ currentDocument?.indexedChunkCount || 0 }} /
          {{ currentDocument?.chunkCount || 0 }} 个分块已索引</span
        >
        <el-button
          v-if="currentDocument?.currentVersionId"
          link
          type="primary"
          @click="openRechunk(currentDocument)"
          >重新切片</el-button
        >
        <el-button :icon="Refresh" link type="primary" @click="refreshActiveTab"
          >刷新</el-button
        >
      </div>
      <el-tabs v-model="drawerTab" @tab-change="loadDrawerTab">
        <el-tab-pane label="文件版本" name="versions">
          <el-table v-loading="drawerLoading" :data="versions">
            <el-table-column prop="versionNo" label="版本" width="75"
              ><template #default="{ row }"
                >V{{ row.versionNo }}</template
              ></el-table-column
            >
            <el-table-column
              prop="contentType"
              label="文件类型"
              min-width="150"
              show-overflow-tooltip
            />
            <el-table-column prop="fileSize" label="文件大小" width="110"
              ><template #default="{ row }">{{
                fileSize(row.fileSize)
              }}</template></el-table-column
            >
            <el-table-column prop="status" label="解析状态" width="100"
              ><template #default="{ row }"
                ><el-tag :type="tagType(row.status)">{{
                  text(row.status)
                }}</el-tag></template
              ></el-table-column
            >
            <el-table-column label="当前" width="70"
              ><template #default="{ row }"
                ><el-tag v-if="row.current" type="success"
                  >当前</el-tag
                ></template
              ></el-table-column
            >
            <el-table-column prop="createTime" label="创建时间" width="170" />
            <el-table-column label="操作" width="80"
              ><template #default="{ row }"
                ><el-button
                  v-if="row.status === 'FAILED'"
                  link
                  type="primary"
                  @click="reindexVersion(row)"
                  >重试</el-button
                ></template
              ></el-table-column
            >
          </el-table>
          <Pager
            :query="versionQuery"
            :total="versionTotal"
            @change="loadVersions"
          />
        </el-tab-pane>
        <el-tab-pane label="文本分块" name="chunks">
          <div class="drawer-filter chunk-filter">
            <label class="drawer-filter-item">
              <span>文档版本</span>
              <el-select
                v-model="chunkQuery.versionId"
                clearable
                placeholder="全部版本"
                @change="chunkSearch"
              >
                <el-option
                  v-for="v in versions"
                  :key="v.id"
                  :label="`V${v.versionNo}`"
                  :value="v.id"
                />
              </el-select>
            </label>
            <label class="drawer-filter-item">
              <span>分块状态</span>
              <el-select
                v-model="chunkQuery.status"
                clearable
                placeholder="全部状态"
                @change="chunkSearch"
              >
                <el-option label="有效" value="ACTIVE" />
                <el-option label="已失效" value="EXPIRED" />
              </el-select>
            </label>
            <label class="drawer-filter-item">
              <span>索引状态</span>
              <el-select
                v-model="chunkQuery.indexStatus"
                clearable
                placeholder="全部状态"
                @change="chunkSearch"
              >
                <el-option label="等待索引" value="PENDING" />
                <el-option label="处理中" value="PROCESSING" />
                <el-option label="成功" value="SUCCESS" />
                <el-option label="失败" value="FAILED" />
                <el-option label="已失效" value="EXPIRED" />
              </el-select>
            </label>
            <label class="drawer-filter-item">
              <span>索引代次</span>
              <el-input-number
                v-model="chunkQuery.indexGeneration"
                :min="1"
                controls-position="right"
                placeholder="请输入代次"
                @change="chunkSearch"
              />
            </label>
            <label class="drawer-filter-item chunk-keyword">
              <span>正文关键词</span>
              <el-input
                v-model="chunkQuery.keyword"
                clearable
                placeholder="输入关键词后使用 Elasticsearch 检索"
                @keyup.enter="chunkSearch"
                @clear="chunkSearch"
              />
            </label>
            <div class="chunk-filter-actions">
              <el-button type="primary" :icon="Search" @click="chunkSearch"
                >检索</el-button
              >
              <el-button @click="resetChunkSearch">重置</el-button>
            </div>
          </div>
          <el-table v-loading="drawerLoading" :data="chunks">
            <el-table-column prop="chunkNo" label="序号" width="70" />
            <el-table-column
              prop="titlePath"
              label="标题路径 / 命中片段"
              min-width="260"
            >
              <template #default="{ row }">
                <div class="chunk-title" :title="row.titlePath">
                  {{ row.titlePath || "-" }}
                </div>
                <div
                  v-if="row.highlight"
                  class="chunk-highlight"
                  v-html="highlightHtml(row.highlight)"
                />
              </template>
            </el-table-column>
            <el-table-column prop="pageNumber" label="页码" width="65" />
            <el-table-column prop="tokenCount" label="Token" width="80" />
            <el-table-column prop="status" label="分块状态" width="95"
              ><template #default="{ row }">{{
                text(row.status)
              }}</template></el-table-column
            >
            <el-table-column prop="indexStatus" label="索引状态" width="95"
              ><template #default="{ row }"
                ><el-tag :type="tagType(row.indexStatus)">{{
                  text(row.indexStatus)
                }}</el-tag></template
              ></el-table-column
            >
            <el-table-column prop="indexGeneration" label="代次" width="75"
              ><template #default="{ row }"
                >G{{ row.indexGeneration }}</template
              ></el-table-column
            >
            <el-table-column
              v-if="chunkQuery.keyword"
              prop="score"
              label="相关度"
              width="85"
              ><template #default="{ row }">{{
                row.score?.toFixed(2) || "-"
              }}</template></el-table-column
            >
            <el-table-column label="操作" width="70"
              ><template #default="{ row }"
                ><el-button link type="primary" @click="showChunk(row)"
                  >正文</el-button
                ></template
              ></el-table-column
            >
          </el-table>
          <Pager :query="chunkQuery" :total="chunkTotal" @change="loadChunks" />
        </el-tab-pane>
        <el-tab-pane label="处理任务" name="tasks">
          <div class="drawer-filter">
            <el-select
              v-model="taskQuery.taskType"
              clearable
              placeholder="处理阶段"
              @change="taskSearch"
              ><el-option
                v-for="s in stages"
                :key="s.value"
                v-bind="s" /></el-select
            ><el-select
              v-model="taskQuery.status"
              clearable
              placeholder="任务状态"
              @change="taskSearch"
              ><el-option label="等待中" value="PENDING" /><el-option
                label="等待重试"
                value="RETRY_WAIT" /><el-option
                label="运行中"
                value="RUNNING" /><el-option
                label="成功"
                value="SUCCESS" /><el-option
                label="失败"
                value="FAILED" /><el-option label="已取消" value="CANCELLED"
            /></el-select>
          </div>
          <el-table v-loading="drawerLoading" :data="tasks">
            <el-table-column prop="taskType" label="阶段" width="100"
              ><template #default="{ row }">{{
                stageText(row.taskType)
              }}</template></el-table-column
            >
            <el-table-column prop="status" label="状态" width="95"
              ><template #default="{ row }"
                ><el-tag :type="tagType(row.status)">{{
                  text(row.status)
                }}</el-tag></template
              ></el-table-column
            >
            <el-table-column prop="progress" label="进度" width="150"
              ><template #default="{ row }"
                ><el-progress :percentage="row.progress || 0" /></template
            ></el-table-column>
            <el-table-column prop="retryCount" label="重试" width="65" />
            <el-table-column prop="attemptNo" label="执行" width="65" />
            <el-table-column prop="errorType" label="错误类型" width="135"
              ><template #default="{ row }">{{
                errorTypeText(row.errorType)
              }}</template></el-table-column
            >
            <el-table-column
              prop="nextRetryTime"
              label="下次重试"
              width="170"
            />
            <el-table-column
              prop="leaseExpireTime"
              label="租约到期"
              width="170"
            />
            <el-table-column
              prop="errorMessage"
              label="失败原因"
              min-width="240"
              show-overflow-tooltip
            />
            <el-table-column prop="createTime" label="创建时间" width="170" />
          </el-table>
          <Pager :query="taskQuery" :total="taskTotal" @change="loadTasks" />
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <el-dialog v-model="chunkDialog" title="分块正文" width="min(760px, 92vw)">
      <div v-loading="chunkSwitching">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="序号">{{
            currentChunk?.chunkNo
          }}</el-descriptions-item>
          <el-descriptions-item label="上一分块">
            <el-button
              link
              type="primary"
              :disabled="!currentChunk?.previousChunkId || chunkSwitching"
              @click="switchChunk(currentChunk.previousChunkId)"
            >
              {{
                currentChunk?.previousChunkId
                  ? `查看 #${currentChunk.previousChunkId}`
                  : "无"
              }}
            </el-button>
          </el-descriptions-item>
          <el-descriptions-item label="下一分块">
            <el-button
              link
              type="primary"
              :disabled="!currentChunk?.nextChunkId || chunkSwitching"
              @click="switchChunk(currentChunk.nextChunkId)"
            >
              {{
                currentChunk?.nextChunkId
                  ? `查看 #${currentChunk.nextChunkId}`
                  : "无"
              }}
            </el-button>
          </el-descriptions-item>
        </el-descriptions>
        <div class="chunk-navigation">
          <el-button
            :disabled="!currentChunk?.previousChunkId || chunkSwitching"
            @click="switchChunk(currentChunk.previousChunkId)"
            >上一分块</el-button
          >
          <span>第 {{ currentChunk?.chunkNo || "-" }} 个分块</span>
          <el-button
            type="primary"
            plain
            :disabled="!currentChunk?.nextChunkId || chunkSwitching"
            @click="switchChunk(currentChunk.nextChunkId)"
            >下一分块</el-button
          >
        </div>
        <div class="chunk-content">
          <MarkdownPreview
            v-if="currentChunk?.documentVersionId"
            :content="currentChunk.content"
            :knowledge-base-id="knowledgeBaseId"
            :document-id="currentDocument.id"
            :version-id="currentChunk.documentVersionId"
          />
          <pre v-else>{{ currentChunk?.content }}</pre>
        </div>
      </div>
    </el-dialog>
    <DocumentRechunkDialog
      ref="rechunkDialog"
      :knowledge-base-id="knowledgeBaseId"
      @submitted="onRechunkSubmitted"
      @settled="onRechunkSubmitted"
    />

    <el-dialog
      v-model="switchLogDialog"
      title="索引代次切换记录"
      width="min(1100px, 94vw)"
    >
      <el-table v-loading="switchLogLoading" :data="switchLogs">
        <el-table-column prop="operation" label="操作" width="90"
          ><template #default="{ row }">{{
            operationText(row.operation)
          }}</template></el-table-column
        >
        <el-table-column label="代次" width="100"
          ><template #default="{ row }"
            >G{{ row.fromGeneration }} → G{{ row.toGeneration }}</template
          ></el-table-column
        >
        <el-table-column prop="status" label="结果" width="90"
          ><template #default="{ row }"
            ><el-tag :type="tagType(row.status)">{{
              text(row.status)
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column label="文档/分块" width="130"
          ><template #default="{ row }"
            >{{ row.expectedDocumentCount ?? "-" }} /
            {{ row.expectedChunkCount ?? "-" }}</template
          ></el-table-column
        >
        <el-table-column label="MySQL/ES" width="130"
          ><template #default="{ row }"
            >{{ row.mysqlIndexedCount ?? "-" }} /
            {{ row.elasticsearchDocumentCount ?? "-" }}</template
          ></el-table-column
        >
        <el-table-column label="模型/维度" min-width="130"
          ><template #default="{ row }"
            >{{ row.embeddingModelId ?? "-" }} /
            {{ row.embeddingDimension ?? "-" }}</template
          ></el-table-column
        >
        <el-table-column prop="durationMs" label="耗时" width="90"
          ><template #default="{ row }">{{
            row.durationMs == null ? "-" : `${row.durationMs} ms`
          }}</template></el-table-column
        >
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column
          prop="errorMessage"
          label="失败原因"
          min-width="220"
          show-overflow-tooltip
        />
      </el-table>
    </el-dialog>

    <el-dialog
      v-model="lifecycleDialog"
      title="索引生命周期管理"
      width="min(1180px, 96vw)"
    >
      <div class="lifecycle-toolbar">
        <div>
          <span>默认保留最近</span>
          <el-input-number
            v-model="retainCount"
            :min="2"
            :max="10"
            controls-position="right"
          />
          <span>个成功代次</span>
        </div>
        <div>
          <el-button :loading="lifecycleLoading" @click="runConsistencyCheck"
            >立即检查一致性</el-button
          >
          <el-button
            type="warning"
            plain
            :loading="lifecycleLoading"
            @click="pruneIndices"
            >按策略清理</el-button
          >
          <el-button
            :icon="Refresh"
            :loading="lifecycleLoading"
            @click="loadPhysicalIndices"
            >刷新</el-button
          >
        </div>
      </div>
      <el-alert
        v-if="lifecycleCheck.message"
        class="lifecycle-alert"
        :type="lifecycleCheck.status === 'CONSISTENT' ? 'success' : 'warning'"
        :title="
          lifecycleCheck.status === 'CONSISTENT'
            ? '索引一致性正常'
            : '索引一致性告警'
        "
        :description="lifecycleCheck.message"
        show-icon
        :closable="false"
      />
      <el-table v-loading="lifecycleLoading" :data="physicalIndices">
        <el-table-column
          prop="indexName"
          label="物理索引"
          min-width="220"
          show-overflow-tooltip
        />
        <el-table-column label="代次" width="70"
          ><template #default="{ row }"
            >G{{ row.generation }}</template
          ></el-table-column
        >
        <el-table-column prop="lifecycleStatus" label="生命周期" width="110"
          ><template #default="{ row }"
            ><el-tag :type="lifecycleTag(row.lifecycleStatus)">{{
              lifecycleText(row.lifecycleStatus)
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column label="ES健康" width="95"
          ><template #default="{ row }"
            ><el-tag
              :type="
                row.health === 'green'
                  ? 'success'
                  : row.health === 'yellow'
                    ? 'warning'
                    : 'danger'
              "
              >{{ row.health || "-" }}</el-tag
            ></template
          ></el-table-column
        >
        <el-table-column
          prop="documentCount"
          label="文档数"
          width="90"
          align="right"
        />
        <el-table-column label="大小" width="100" align="right"
          ><template #default="{ row }">{{
            formatBytes(row.sizeBytes)
          }}</template></el-table-column
        >
        <el-table-column label="模型" min-width="150" show-overflow-tooltip
          ><template #default="{ row }">{{
            row.embeddingModelName || row.embeddingModelId || "-"
          }}</template></el-table-column
        >
        <el-table-column label="维度" width="75"
          ><template #default="{ row }">{{
            row.dimension || "-"
          }}</template></el-table-column
        >
        <el-table-column label="别名" width="90"
          ><template #default="{ row }"
            ><el-tag v-if="row.aliasReferenced" type="success">引用中</el-tag
            ><span v-else>-</span></template
          ></el-table-column
        >
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }"
            ><el-button
              link
              type="danger"
              :disabled="!row.deletable"
              :title="row.deleteReason"
              @click="deletePhysical(row)"
              >删除</el-button
            ></template
          >
        </el-table-column>
      </el-table>
    </el-dialog>
  </section>
</template>

<script setup>
import {
  computed,
  defineComponent,
  h,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
} from "vue";
import { ElMessage, ElMessageBox, ElPagination } from "element-plus";
import { Download, Refresh, Search } from "@element-plus/icons-vue";
import MarkdownPreview from "./MarkdownPreview.vue";
import DocumentRechunkDialog from "./DocumentRechunkDialog.vue";
import {
  batchReindexKnowledgeDocuments,
  batchRetryKnowledgeDocuments,
  cancelKnowledgeTasks,
  exportKnowledgeFailures,
  getKnowledgeDocumentChunk,
  getKnowledgeIndexStatus,
  listKnowledgeDocumentChunks,
  listKnowledgeIndexDocuments,
  listKnowledgeIndexSwitchLogs,
  listKnowledgeIndexTasks,
  listKnowledgeIndexVersions,
  rebuildKnowledgeIndex,
  reindexKnowledgeVersion,
  retryKnowledgeIndexSwitch,
  rollbackKnowledgeIndex,
  listKnowledgePhysicalIndices,
  deleteKnowledgePhysicalIndex,
  pruneKnowledgePhysicalIndices,
  checkKnowledgeIndexConsistency,
} from "@/api/ai";
const props = defineProps({
  knowledgeBaseId: { type: Number, required: true },
});
const Pager = defineComponent({
  props: { query: Object, total: Number },
  emits: ["change"],
  setup(p, { emit }) {
    return () =>
      h(ElPagination, {
        currentPage: p.query.pageNum,
        pageSize: p.query.pageSize,
        total: p.total,
        pageSizes: [10, 20, 50],
        layout: "total, sizes, prev, pager, next",
        "onUpdate:currentPage": (v) => (p.query.pageNum = v),
        "onUpdate:pageSize": (v) => (p.query.pageSize = v),
        onChange: () => emit("change"),
      });
  },
});
const documentStatuses = [
  { label: "等待处理", value: "PENDING" },
  { label: "处理中", value: "RUNNING" },
  { label: "成功", value: "SUCCESS" },
  { label: "失败", value: "FAILED" },
];
const indexStatuses = [
  { label: "未索引", value: "NOT_INDEXED" },
  { label: "处理中", value: "PROCESSING" },
  { label: "成功", value: "SUCCESS" },
  { label: "部分失败", value: "PARTIAL_FAILED" },
  { label: "失败", value: "FAILED" },
  { label: "已失效", value: "EXPIRED" },
];
const stages = [
  { label: "解析", value: "PARSE" },
  { label: "清洗", value: "CLEAN" },
  { label: "分块", value: "CHUNK" },
  { label: "向量化", value: "EMBEDDING" },
  { label: "索引", value: "INDEX" },
];
const loading = ref(false),
  drawerLoading = ref(false),
  rebuilding = ref(false),
  switching = ref(false),
  drawer = ref(false),
  chunkDialog = ref(false),
  chunkSwitching = ref(false);
const switchLogDialog = ref(false),
  switchLogLoading = ref(false),
  switchLogs = ref([]);
const lifecycleDialog = ref(false),
  lifecycleLoading = ref(false),
  physicalIndices = ref([]),
  retainCount = ref(3),
  lifecycleCheck = ref({});
const rechunkDialog = ref();
const overview = ref({}),
  documents = ref([]),
  total = ref(0),
  selectedIds = ref([]),
  currentDocument = ref(),
  currentChunk = ref(),
  drawerTab = ref("versions");
const buildInProgress = computed(() =>
  ["BUILDING", "SWITCHING"].includes(overview.value.buildStatus),
);
const buildStatusText = computed(
  () =>
    ({
      IDLE: "空闲",
      BUILDING: "构建中",
      SWITCHING: "切换中",
      FAILED: "构建失败",
    })[overview.value.buildStatus] ||
    overview.value.buildStatus ||
    "空闲",
);
const consistencyText = computed(
  () =>
    ({
      CONSISTENT: "代次一致",
      MISMATCH: "代次不一致",
      MISSING: "别名缺失",
      MULTIPLE: "别名异常",
      INVALID: "索引异常",
      UNAVAILABLE: "检查不可用",
    })[overview.value.consistencyStatus] ||
    overview.value.consistencyStatus ||
    "检查中",
);
const consistencyClass = computed(() =>
  overview.value.consistencyStatus === "CONSISTENT"
    ? "consistency-ok"
    : "consistency-warning",
);
const versions = ref([]),
  versionTotal = ref(0),
  chunks = ref([]),
  chunkTotal = ref(0),
  tasks = ref([]),
  taskTotal = ref(0);
const query = reactive({
  name: "",
  status: "",
  indexStatus: "",
  currentStage: "",
  indexGeneration: undefined,
  failedOnly: false,
  pageNum: 1,
  pageSize: 10,
});
const versionQuery = reactive({ pageNum: 1, pageSize: 10 }),
  chunkQuery = reactive({
    versionId: undefined,
    status: "",
    indexStatus: "",
    indexGeneration: undefined,
    keyword: "",
    pageNum: 1,
    pageSize: 10,
  }),
  taskQuery = reactive({ taskType: "", status: "", pageNum: 1, pageSize: 10 });
let timer,
  warned = false;
async function loadOverview() {
  overview.value = (await getKnowledgeIndexStatus(props.knowledgeBaseId)) || {};
}
async function loadDocuments(silent = false) {
  if (!silent) loading.value = true;
  try {
    const r = await listKnowledgeIndexDocuments(props.knowledgeBaseId, query);
    documents.value = r.rows || [];
    total.value = r.total || 0;
    warned = false;
    syncPolling();
  } catch (e) {
    if (!silent || !warned) {
      ElMessage.warning("索引状态刷新失败，已保留当前数据");
      warned = true;
    }
  } finally {
    loading.value = false;
  }
}
async function loadAll(silent = true) {
  await Promise.all([loadOverview(), loadDocuments(silent)]);
}
function search() {
  query.pageNum = 1;
  loadDocuments();
}
function reset() {
  Object.assign(query, {
    name: "",
    status: "",
    indexStatus: "",
    currentStage: "",
    indexGeneration: undefined,
    failedOnly: false,
    pageNum: 1,
    pageSize: 10,
  });
  loadDocuments();
}
function selectRows(rows) {
  selectedIds.value = rows.slice(0, 100).map((x) => x.id);
}
function openRechunk(row) {
  rechunkDialog.value?.open(row);
}
function onRechunkSubmitted() {
  loadAll(false);
  if (drawer.value) refreshActiveTab();
}
async function openDocument(row) {
  currentDocument.value = row;
  drawer.value = true;
  drawerTab.value = "versions";
  versionQuery.pageNum = 1;
  Object.assign(chunkQuery, {
    versionId: row.currentVersionId,
    status: "",
    indexStatus: "",
    indexGeneration:
      overview.value.activeGeneration || overview.value.currentGeneration || 1,
    keyword: "",
    pageNum: 1,
  });
  await loadVersions();
}
function closeDrawer() {
  currentDocument.value = undefined;
}
async function loadVersions() {
  if (!currentDocument.value) return;
  drawerLoading.value = true;
  try {
    const r = await listKnowledgeIndexVersions(
      props.knowledgeBaseId,
      currentDocument.value.id,
      versionQuery,
    );
    versions.value = r.rows || [];
    versionTotal.value = r.total || 0;
  } finally {
    drawerLoading.value = false;
  }
}
async function loadChunks() {
  drawerLoading.value = true;
  try {
    const r = await listKnowledgeDocumentChunks(
      props.knowledgeBaseId,
      currentDocument.value.id,
      chunkQuery,
    );
    chunks.value = r.rows || [];
    chunkTotal.value = r.total || 0;
  } finally {
    drawerLoading.value = false;
  }
}
async function loadTasks() {
  drawerLoading.value = true;
  try {
    const r = await listKnowledgeIndexTasks(
      props.knowledgeBaseId,
      currentDocument.value.id,
      taskQuery,
    );
    tasks.value = r.rows || [];
    taskTotal.value = r.total || 0;
  } finally {
    drawerLoading.value = false;
  }
}
function loadDrawerTab(name) {
  if (name === "versions") loadVersions();
  else if (name === "chunks") loadChunks();
  else loadTasks();
}
function refreshActiveTab() {
  loadDrawerTab(drawerTab.value);
  loadDocuments(true);
}
function chunkSearch() {
  chunkQuery.pageNum = 1;
  loadChunks();
}
function taskSearch() {
  taskQuery.pageNum = 1;
  loadTasks();
}
function resetChunkSearch() {
  Object.assign(chunkQuery, {
    versionId: currentDocument.value?.currentVersionId,
    status: "",
    indexStatus: "",
    indexGeneration:
      overview.value.activeGeneration || overview.value.currentGeneration || 1,
    keyword: "",
    pageNum: 1,
  });
  loadChunks();
}
function highlightHtml(value = "") {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;")
    .replaceAll("\uE000", "<mark>")
    .replaceAll("\uE001", "</mark>");
}
function showChunk(row) {
  currentChunk.value = row;
  chunkDialog.value = true;
}
async function switchChunk(chunkId) {
  if (!chunkId || chunkSwitching.value || !currentDocument.value) return;
  chunkSwitching.value = true;
  try {
    currentChunk.value = await getKnowledgeDocumentChunk(
      props.knowledgeBaseId,
      currentDocument.value.id,
      chunkId,
      {
        indexGeneration:
          currentChunk.value?.indexGeneration || chunkQuery.indexGeneration,
      },
    );
  } catch (e) {
    ElMessage.error(e?.message || "分块加载失败");
  } finally {
    chunkSwitching.value = false;
  }
}
async function singleReindex(row) {
  await confirmAction("确认重新索引该文档？");
  await batchReindexKnowledgeDocuments(props.knowledgeBaseId, [row.id]);
  ElMessage.success("重新索引任务已创建");
  loadAll(false);
}
async function reindexVersion(row) {
  await reindexKnowledgeVersion(
    props.knowledgeBaseId,
    currentDocument.value.id,
    row.id,
  );
  ElMessage.success("重试任务已创建");
  refreshActiveTab();
}
async function batchAction(type) {
  await confirmAction(
    type === "cancel"
      ? "只能取消尚未开始的等待任务，确认继续？"
      : "批量操作将在后台创建任务，确认继续？",
  );
  const fn = {
    reindex: batchReindexKnowledgeDocuments,
    retry: batchRetryKnowledgeDocuments,
    cancel: cancelKnowledgeTasks,
  }[type];
  await fn(props.knowledgeBaseId, selectedIds.value);
  ElMessage.success("批量操作已提交");
  loadAll(false);
}
async function rebuildAll() {
  const next =
    (overview.value.activeGeneration || overview.value.currentGeneration || 1) +
    1;
  await confirmAction(
    `本操作将创建新索引代次 G${next}，并重建全部文档；“当前代次重新索引”不会创建新代次。G${next} 完成前旧索引继续服务。确认继续？`,
  );
  rebuilding.value = true;
  try {
    await rebuildKnowledgeIndex(props.knowledgeBaseId);
    ElMessage.success(`G${next} 重建任务已创建`);
    await loadAll(false);
  } finally {
    rebuilding.value = false;
  }
}
async function retrySwitch() {
  await confirmAction(
    `将重新校验 G${overview.value.buildingGeneration} 的文档数、分块数、模型和维度，全部通过后重试原子别名切换。确认继续？`,
  );
  switching.value = true;
  try {
    await retryKnowledgeIndexSwitch(props.knowledgeBaseId);
    ElMessage.success("索引别名切换成功");
    await loadAll(false);
  } finally {
    switching.value = false;
  }
}
async function rollbackIndex() {
  const target = overview.value.previousGeneration;
  await confirmAction(
    `将活动索引从 G${overview.value.activeGeneration} 回滚到上一成功代次 G${target}，回滚前会执行完整一致性校验。确认继续？`,
  );
  switching.value = true;
  try {
    await rollbackKnowledgeIndex(props.knowledgeBaseId);
    ElMessage.success(`已回滚到 G${target}`);
    await loadAll(false);
  } finally {
    switching.value = false;
  }
}
async function showSwitchLogs() {
  switchLogDialog.value = true;
  switchLogLoading.value = true;
  try {
    switchLogs.value =
      (await listKnowledgeIndexSwitchLogs(props.knowledgeBaseId)) || [];
  } finally {
    switchLogLoading.value = false;
  }
}
async function showLifecycle() {
  lifecycleDialog.value = true;
  await Promise.all([loadPhysicalIndices(), runConsistencyCheck(false)]);
}
async function loadPhysicalIndices() {
  lifecycleLoading.value = true;
  try {
    physicalIndices.value =
      (await listKnowledgePhysicalIndices(props.knowledgeBaseId)) || [];
  } finally {
    lifecycleLoading.value = false;
  }
}
async function runConsistencyCheck(notify = true) {
  lifecycleLoading.value = true;
  try {
    lifecycleCheck.value =
      (await checkKnowledgeIndexConsistency(props.knowledgeBaseId)) || {};
    if (notify) ElMessage.success("一致性检查已完成");
    await loadOverview();
  } finally {
    lifecycleLoading.value = false;
  }
}
async function deletePhysical(row) {
  await confirmAction(
    `确认删除物理索引 ${row.indexName}？删除前系统会再次检查活动代次、构建代次和别名引用，删除后不可恢复。`,
  );
  await deleteKnowledgePhysicalIndex(props.knowledgeBaseId, row.generation);
  ElMessage.success(`物理索引 G${row.generation} 已删除`);
  await loadPhysicalIndices();
}
async function pruneIndices() {
  await confirmAction(
    `将保留最近 ${retainCount.value} 个成功代次，并安全删除更早且未被别名引用的索引。确认继续？`,
  );
  const response = await pruneKnowledgePhysicalIndices(
    props.knowledgeBaseId,
    retainCount.value,
  );
  const removed = response.data || [];
  ElMessage.success(
    removed.length
      ? `已清理代次：${removed.map((x) => `G${x}`).join("、")}`
      : "没有符合清理条件的索引",
  );
  await loadPhysicalIndices();
}
async function exportFailures() {
  const blob = await exportKnowledgeFailures(props.knowledgeBaseId, {
    status: "FAILED",
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "knowledge-failures.csv";
  a.click();
  URL.revokeObjectURL(url);
}
const confirmAction = (message) =>
  ElMessageBox.confirm(message, "操作确认", {
    type: "warning",
    confirmButtonText: "确认",
    cancelButtonText: "取消",
  });
function syncPolling() {
  clearInterval(timer);
  if (
    !document.hidden &&
    (buildInProgress.value ||
      documents.value.some(
        (x) =>
          x.indexStatus === "PROCESSING" ||
          ["PENDING", "RUNNING"].includes(x.status),
      ))
  )
    timer = setInterval(() => {
      loadAll(true);
      if (drawer.value && drawerTab.value === "tasks") loadTasks();
    }, 5000);
}
function visibility() {
  if (document.hidden) clearInterval(timer);
  else {
    loadDocuments(true);
    syncPolling();
  }
}
const text = (s) =>
  ({
    PENDING: "等待中",
    RETRY_WAIT: "等待重试",
    RUNNING: "处理中",
    SUCCESS: "成功",
    FAILED: "失败",
    PARTIAL_FAILED: "部分失败",
    NOT_INDEXED: "未索引",
    PROCESSING: "处理中",
    EXPIRED: "已失效",
    ACTIVE: "有效",
    CANCELLED: "已取消",
  })[s] ||
  s ||
  "-";
const stageText = (s) =>
  ({
    UPLOAD: "上传",
    PARSE: "解析",
    CLEAN: "清洗",
    CHUNK: "分块",
    EMBEDDING: "向量化",
    INDEX: "索引",
  })[s] ||
  s ||
  "-";
const operationText = (s) =>
  ({ SWITCH: "自动切换", RETRY: "重试切换", ROLLBACK: "回滚" })[s] || s || "-";
const errorTypeText = (s) =>
  ({
    TIMEOUT: "超时",
    REMOTE_THROTTLED: "服务限流/暂不可用",
    DEPENDENCY_UNAVAILABLE: "依赖不可用",
    PARSED_ARTIFACT_MISSING: "解析产物缺失",
    VALIDATION: "配置或数据校验",
    DOCUMENT_INVALID: "文档无效",
    LEASE_EXPIRED: "任务租约过期",
    UNKNOWN: "未分类异常",
  })[s] ||
  s ||
  "-";
const lifecycleText = (s) =>
  ({
    ACTIVE: "活动",
    BUILDING: "构建中",
    SUCCESS: "历史成功",
    ORPHAN: "孤立/未完成",
  })[s] ||
  s ||
  "-";
const lifecycleTag = (s) =>
  ({
    ACTIVE: "success",
    BUILDING: "warning",
    SUCCESS: "info",
    ORPHAN: "danger",
  })[s] || "info";
const tagType = (s) =>
  ({
    SUCCESS: "success",
    ACTIVE: "success",
    FAILED: "danger",
    PARTIAL_FAILED: "danger",
    PROCESSING: "warning",
    RUNNING: "warning",
    RETRY_WAIT: "warning",
    PENDING: "info",
    NOT_INDEXED: "info",
    EXPIRED: "info",
    CANCELLED: "info",
  })[s] || "info";
const fileSize = (n) =>
  !n
    ? "-"
    : n < 1024
      ? `${n} B`
      : n < 1048576
        ? `${(n / 1024).toFixed(1)} KB`
        : `${(n / 1048576).toFixed(1)} MB`;
const formatBytes = (n) =>
  !n
    ? "0 B"
    : n < 1024
      ? `${n} B`
      : n < 1048576
        ? `${(n / 1024).toFixed(1)} KB`
        : n < 1073741824
          ? `${(n / 1048576).toFixed(1)} MB`
          : `${(n / 1073741824).toFixed(2)} GB`;
onMounted(() => {
  loadAll(false);
  document.addEventListener("visibilitychange", visibility);
});
onBeforeUnmount(() => {
  clearInterval(timer);
  document.removeEventListener("visibilitychange", visibility);
});
</script>

<style scoped lang="scss">
.build-alert {
  margin-top: 12px;
}
.generation-card small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.consistency-ok {
  color: var(--el-color-success) !important;
}
.consistency-warning {
  color: var(--el-color-warning) !important;
}
.lifecycle-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.lifecycle-toolbar > div {
  display: flex;
  align-items: center;
  gap: 10px;
}
.lifecycle-toolbar .el-input-number {
  width: 110px;
}
.lifecycle-alert {
  margin-bottom: 14px;
}
.index-panel {
  min-width: 0;
}
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.section-heading h3 {
  margin: 0;
  font-size: 18px;
}
.section-heading p {
  margin: 5px 0 0;
  color: var(--ry-muted-foreground);
}
.heading-actions,
.toolbar > div,
.drawer-filter {
  display: flex;
  gap: 8px;
}
.status-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
}
.status-grid article {
  display: flex;
  min-height: 96px;
  flex-direction: column;
  padding: 15px;
}
.status-grid span,
.status-grid small,
.toolbar > span,
.drawer-summary {
  color: var(--ry-muted-foreground);
}
.status-grid strong {
  margin: 7px 0;
  font-size: 24px;
}
.status-grid .model strong {
  overflow: hidden;
  font-size: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.success strong {
  color: var(--el-color-success);
}
.warning strong {
  color: var(--el-color-warning);
}
.danger strong,
.dangerText {
  color: var(--el-color-danger);
}
.content-card {
  margin-top: 12px;
  padding: 16px;
}
.filters {
  display: grid;
  grid-template-columns: repeat(3, minmax(260px, 1fr));
  column-gap: 24px;
  row-gap: 14px;
  margin-bottom: 16px;
}
.filters :deep(.el-form-item) {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  align-items: center;
  width: 100%;
  margin: 0;
}
.filters :deep(.el-form-item__label) {
  width: 72px;
  height: 32px;
  justify-content: flex-start;
  padding: 0 10px 0 0;
  line-height: 32px;
  white-space: nowrap;
}
.filters :deep(.el-form-item__content) {
  min-width: 0;
}
.filters :deep(.el-input),
.filters :deep(.el-select),
.filters :deep(.el-input-number) {
  width: 100%;
}
.filters :deep(.el-checkbox) {
  height: 32px;
}
.filters :deep(.el-form-item:nth-last-child(2)) {
  grid-template-columns: 72px minmax(0, 1fr);
}
.filters :deep(.el-form-item:nth-last-child(2))::before {
  content: "";
  display: block;
}
.filters :deep(.el-form-item:last-child) {
  display: flex;
  grid-column: span 2;
  justify-content: flex-end;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 4px 0 14px;
}
.el-pagination {
  justify-content: flex-end;
  margin-top: 14px;
}
.drawer-summary {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 8px;
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
}
.drawer-filter {
  margin-bottom: 12px;
}
.drawer-filter .el-select {
  width: 160px;
}
.chunk-filter {
  display: grid;
  grid-template-columns: repeat(4, minmax(140px, 1fr));
  gap: 14px;
  margin: 4px 0 16px;
  padding: 14px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-extra-light);
}
.drawer-filter-item {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 7px;
}
.drawer-filter-item > span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 18px;
}
.chunk-filter :deep(.el-select),
.chunk-filter :deep(.el-input-number) {
  width: 100%;
}
.chunk-keyword {
  grid-column: span 3;
}
.chunk-filter-actions {
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
  padding-bottom: 1px;
}
.chunk-title {
  overflow: hidden;
  color: var(--el-text-color-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chunk-highlight {
  display: -webkit-box;
  overflow: hidden;
  margin-top: 5px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 18px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.chunk-highlight :deep(mark) {
  padding: 0 2px;
  border-radius: 2px;
  background: var(--el-color-warning-light-7);
  color: var(--el-color-danger);
}
.chunk-navigation {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 14px 0 10px;
}
.chunk-navigation span {
  color: var(--ry-muted-foreground);
  font-size: 13px;
}
.chunk-content {
  max-height: 55vh;
  overflow: auto;
  padding: 16px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
@media (max-width: 1200px) {
  .status-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .section-heading {
    align-items: flex-start;
  }
  .heading-actions {
    flex-wrap: wrap;
    justify-content: flex-end;
  }
  .filters {
    grid-template-columns: repeat(2, minmax(260px, 1fr));
  }
  .filters :deep(.el-form-item:last-child) {
    grid-column: span 1;
  }
  .chunk-filter {
    grid-template-columns: repeat(2, minmax(180px, 1fr));
  }
  .chunk-keyword {
    grid-column: span 1;
  }
}
@media (max-width: 720px) {
  .section-heading,
  .toolbar {
    align-items: stretch;
    flex-direction: column;
    gap: 10px;
  }
  .heading-actions {
    justify-content: flex-start;
  }
  .status-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .filters {
    grid-template-columns: 1fr;
  }
  .filters :deep(.el-form-item) {
    grid-template-columns: 72px minmax(0, 1fr);
  }
  .filters :deep(.el-form-item:last-child) {
    grid-column: auto;
    justify-content: flex-start;
    padding-left: 72px;
  }
  .drawer-summary {
    align-items: flex-start;
    flex-direction: column;
  }
  .chunk-filter {
    grid-template-columns: 1fr;
    padding: 12px;
  }
  .chunk-filter-actions {
    justify-content: flex-start;
  }
}
.chunk-content {
  white-space: normal;
}
.chunk-content > pre {
  margin: 0;
  white-space: pre-wrap;
}
</style>
