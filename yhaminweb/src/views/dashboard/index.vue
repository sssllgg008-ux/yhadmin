<template>
  <div class="dashboard">
    <!-- 欢迎横幅 -->
    <div class="ry-card welcome-card">
      <div class="welcome-text">
        <span class="welcome-eyebrow">OVERVIEW · 工作台</span>
        <h2 class="welcome-title">{{ greeting }}，{{ displayName }}</h2>
        <p class="welcome-sub">
          欢迎使用权限管理后台，今日已有 {{ stats.todayCallCount }} 次操作
        </p>
      </div>
      <div class="welcome-illustration">
        <span class="orb orb-one"></span>
        <span class="orb orb-two"></span>
        <el-icon class="welcome-icon"><Cpu /></el-icon>
      </div>
    </div>

    <!-- 1. 统计卡片 -->
    <div class="stat-grid">
      <div
        v-for="s in statCards"
        :key="s.label"
        class="ry-card stat-card"
        :class="`stat-${s.color}`"
      >
        <div class="stat-icon">
          <el-icon><component :is="s.icon" /></el-icon>
        </div>
        <div class="stat-body">
          <div class="stat-label">{{ s.label }}</div>
          <div class="stat-value">{{ s.value }}</div>
          <div class="stat-foot">
            <span :class="['stat-trend', s.trend > 0 ? 'up' : 'down']">
              <el-icon
                ><CaretTop v-if="s.trend > 0" /><CaretBottom v-else
              /></el-icon>
              {{ Math.abs(s.trend) }}%
            </span>
            <span class="stat-trend-label">较昨日</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 2. 趋势图 + 快捷入口 -->
    <div class="row-2">
      <div class="ry-card chart-card">
        <div class="card-header">
          <span class="card-title">7 日调用趋势</span>
          <el-tag type="success" effect="plain" size="small">实时</el-tag>
        </div>
        <div class="chart-wrap">
          <svg
            class="trend-svg"
            viewBox="0 0 700 240"
            preserveAspectRatio="none"
          >
            <!-- 网格线 -->
            <g class="grid">
              <line
                v-for="i in 5"
                :key="i"
                :x1="40"
                :x2="690"
                :y1="i * 40"
                :y2="i * 40"
              />
            </g>
            <!-- 调用区域 -->
            <path :d="callsAreaPath" class="area calls-area" />
            <path :d="callsPath" class="line calls-line" />
            <!-- 成功区域 -->
            <path :d="successAreaPath" class="area success-area" />
            <path :d="successPath" class="line success-line" />
            <!-- 数据点 -->
            <g class="dots">
              <circle
                v-for="(p, i) in callsPoints"
                :key="`c${i}`"
                :cx="p.x"
                :cy="p.y"
                r="3"
                class="dot calls-dot"
              />
              <circle
                v-for="(p, i) in successPoints"
                :key="`s${i}`"
                :cx="p.x"
                :cy="p.y"
                r="3"
                class="dot success-dot"
              />
            </g>
            <!-- x 轴标签 -->
            <g class="x-labels">
              <text
                v-for="(t, i) in trend"
                :key="i"
                :x="callsPoints[i]?.x"
                y="232"
                text-anchor="middle"
              >
                {{ t.date.slice(5) }}
              </text>
            </g>
          </svg>
          <div class="chart-legend">
            <span class="legend-item"
              ><i class="legend-dot calls"></i> 总调用</span
            >
            <span class="legend-item"
              ><i class="legend-dot success"></i> 成功调用</span
            >
          </div>
        </div>
      </div>

      <div class="ry-card quick-card">
        <div class="card-header">
          <span class="card-title">快捷入口</span>
        </div>
        <div class="quick-grid">
          <div
            v-for="q in quickLinks"
            :key="q.path"
            class="quick-item"
            @click="$router.push(q.path)"
          >
            <div class="quick-icon" :class="`quick-${q.color}`">
              <el-icon><component :is="q.icon" /></el-icon>
            </div>
            <span class="quick-label">{{ q.label }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 3. 模块概览（无数据时整个区域不显示） -->
    <div v-if="moduleList.length" class="ry-card module-card">
      <div class="card-header">
        <span class="card-title">AI 资源概览</span>
      </div>
      <div class="module-grid">
        <div
          v-for="m in moduleList"
          :key="m.path"
          class="module-item"
          @click="$router.push(m.path)"
        >
          <div class="module-icon" :class="`m-${m.color}`">
            <el-icon><component :is="m.icon" /></el-icon>
          </div>
          <div class="module-info">
            <div class="module-name">{{ m.name }}</div>
            <div class="module-desc">{{ m.desc }}</div>
          </div>
          <div class="module-count">{{ m.count }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, markRaw } from "vue";
import { useUserStore } from "@/store/modules/user";
import { getDashboardStats } from "@/api/login";
import {
  Cpu,
  CaretTop,
  CaretBottom,
  User,
  UserFilled,
  Service,
  Reading,
  SetUp,
  ChatLineRound,
  DataLine,
  Box,
} from "@element-plus/icons-vue";

const userStore = useUserStore();
const displayName = computed(() => userStore.displayName || "管理员");

const greeting = computed(() => {
  const h = new Date().getHours();
  if (h < 6) return "凌晨好";
  if (h < 9) return "早上好";
  if (h < 12) return "上午好";
  if (h < 14) return "中午好";
  if (h < 18) return "下午好";
  return "晚上好";
});

const stats = ref({
  userCount: 0,
  roleCount: 0,
  llmCount: 0,
  agentCount: 0,
  knowledgeCount: 0,
  workflowCount: 0,
  botCount: 0,
  todayCallCount: 0,
  trend: [],
});

const statCards = computed(() => [
  {
    label: "用户总数",
    value: stats.value.userCount,
    color: "primary",
    icon: markRaw(User),
    trend: 8,
  },
  {
    label: "智能体数量",
    value: stats.value.agentCount,
    color: "warning",
    icon: markRaw(Service),
    trend: 5,
  },
  {
    label: "今日调用",
    value: stats.value.todayCallCount,
    color: "danger",
    icon: markRaw(DataLine),
    trend: -3,
  },
]);

const quickLinks = [
  {
    label: "新增智能体",
    path: "/ai/agent",
    icon: markRaw(Service),
    color: "success",
  },
  {
    label: "工作流编排",
    path: "/ai/workflow",
    icon: markRaw(SetUp),
    color: "warning",
  },
  {
    label: "知识库管理",
    path: "/ai/knowledge",
    icon: markRaw(Reading),
    color: "info",
  },
  {
    label: "Bot 发布",
    path: "/ai/bot",
    icon: markRaw(ChatLineRound),
    color: "danger",
  },
  {
    label: "用户管理",
    path: "/system/user",
    icon: markRaw(UserFilled),
    color: "primary",
  },
];

const moduleList = computed(() =>
  [
    {
      name: "智能体",
      desc: "基于模型与工具的智能体",
      count: stats.value.agentCount,
      path: "/ai/agent",
      icon: markRaw(Service),
      color: "success",
    },
    {
      name: "知识库",
      desc: "文档向量检索",
      count: stats.value.knowledgeCount,
      path: "/ai/knowledge",
      icon: markRaw(Reading),
      color: "warning",
    },
    {
      name: "工作流",
      desc: "多节点编排执行",
      count: stats.value.workflowCount,
      path: "/ai/workflow",
      icon: markRaw(SetUp),
      color: "danger",
    },
    {
      name: "Bot 聊天",
      desc: "发布到多渠道",
      count: stats.value.botCount,
      path: "/ai/bot",
      icon: markRaw(ChatLineRound),
      color: "info",
    },
    {
      name: "角色权限",
      desc: "角色与权限分配",
      count: stats.value.roleCount,
      path: "/system/role",
      icon: markRaw(UserFilled),
      color: "primary",
    },
  ].filter((m) => m.count > 0),
);

// ===== 趋势图计算 =====
const trend = computed(() => stats.value.trend || []);

const callsPoints = computed(() =>
  buildPoints(trend.value.map((t) => t.calls)),
);
const successPoints = computed(() =>
  buildPoints(trend.value.map((t) => t.success)),
);

function buildPoints(values) {
  if (!values.length) return [];
  const W = 650,
    H = 200,
    PAD_L = 40,
    PAD_T = 20;
  const max = Math.max(...values, 1) * 1.1;
  const min = 0;
  const stepX = values.length > 1 ? W / (values.length - 1) : W;
  return values.map((v, i) => {
    const x = PAD_L + i * stepX;
    const y = PAD_T + H - ((v - min) / (max - min)) * H;
    return { x, y, value: v };
  });
}

function buildPath(points) {
  if (!points.length) return "";
  return points.map((p, i) => `${i === 0 ? "M" : "L"}${p.x},${p.y}`).join(" ");
}

function buildAreaPath(points) {
  if (!points.length) return "";
  const baseY = 220;
  return `${buildPath(points)} L${points[points.length - 1].x},${baseY} L${points[0].x},${baseY} Z`;
}

const callsPath = computed(() => buildPath(callsPoints.value));
const callsAreaPath = computed(() => buildAreaPath(callsPoints.value));
const successPath = computed(() => buildPath(successPoints.value));
const successAreaPath = computed(() => buildAreaPath(successPoints.value));

async function loadStats() {
  try {
    const res = await getDashboardStats();
    stats.value = { ...stats.value, ...res };
  } catch (err) {
    console.error("[Dashboard] load stats failed:", err);
  }
}

onMounted(() => loadStats());
</script>

<style lang="scss" scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 1680px;
  margin: 0 auto;
}

/* 欢迎横幅 */
.welcome-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 152px;
  padding: 30px 34px;
  background:
    radial-gradient(
      circle at 86% 18%,
      rgba(var(--ry-primary-rgb), 0.12),
      transparent 25%
    ),
    linear-gradient(120deg, #ffffff 0%, var(--ry-primary-50) 100%);
  color: var(--ry-foreground);
  border: 1px solid var(--ry-primary-100);
  overflow: hidden;
  position: relative;
}
.welcome-text {
  position: relative;
  z-index: 2;
}
.welcome-eyebrow {
  display: inline-block;
  margin-bottom: 10px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.5px;
  color: var(--ry-primary);
}

.welcome-title {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  color: var(--ry-foreground);
}

.welcome-sub {
  margin: 8px 0 0;
  font-size: 14px;
  color: var(--ry-muted-foreground);
}

.welcome-illustration {
  position: relative;
  width: 150px;
  height: 100px;
  display: grid;
  place-items: center;
  font-size: 68px;
  color: var(--ry-primary);
  z-index: 1;
}
.welcome-icon {
  position: relative;
  z-index: 2;
  filter: drop-shadow(0 8px 14px rgba(var(--ry-primary-rgb), 0.16));
}
.orb {
  position: absolute;
  border-radius: 50%;
  background: rgba(var(--ry-primary-rgb), 0.08);
}
.orb-one {
  width: 120px;
  height: 120px;
}
.orb-two {
  width: 76px;
  height: 76px;
  right: -18px;
  top: -24px;
}

/* 统计卡片 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 22px;
  min-height: 126px;
  border-top: 3px solid var(--ry-primary-100);
  &:hover {
    transform: translateY(-1px);
    box-shadow: var(--ry-shadow-sm);
    border-color: var(--ry-primary-200);
  }
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  flex-shrink: 0;
}

.stat-primary .stat-icon {
  background: var(--ry-primary-50);
  color: var(--ry-primary);
}
.stat-success .stat-icon {
  background: var(--state-success-soft);
  color: var(--state-success);
}
.stat-warning .stat-icon {
  background: var(--state-warning-soft);
  color: var(--state-warning);
}
.stat-danger .stat-icon {
  background: var(--state-error-soft);
  color: var(--state-error);
}

.stat-label {
  font-size: 13px;
  color: var(--ry-neutral-500);
}

.stat-value {
  margin-top: 4px;
  font-size: 26px;
  font-weight: 600;
  color: var(--ry-neutral-700);
  font-family: var(--ry-font-title);
}

.stat-foot {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.stat-trend {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-weight: 500;
}

.stat-trend.up {
  color: var(--state-success);
}
.stat-trend.down {
  color: var(--state-error);
}

.stat-trend-label {
  color: var(--ry-neutral-400);
}

/* 行布局 */
.row-2 {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--ry-border-light);
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ry-neutral-700);
}

/* 图表 */
.chart-card {
  display: flex;
  flex-direction: column;
}

.chart-wrap {
  padding: 16px 20px;
}

.trend-svg {
  width: 100%;
  height: 240px;
  display: block;
}

.trend-svg .grid line {
  stroke: var(--ry-border-light);
  stroke-width: 1;
  stroke-dasharray: 3 3;
}

.trend-svg .area {
  opacity: 0.15;
}

.trend-svg .calls-area {
  fill: var(--ry-primary);
}

.trend-svg .success-area {
  fill: var(--state-success);
}

.trend-svg .line {
  fill: none;
  stroke-width: 2;
}

.trend-svg .calls-line {
  stroke: var(--ry-primary);
}

.trend-svg .success-line {
  stroke: var(--state-success);
}

.trend-svg .dot {
  stroke-width: 2;
  stroke: #fff;
}

.trend-svg .calls-dot {
  fill: var(--ry-primary);
}

.trend-svg .success-dot {
  fill: var(--state-success);
}

.trend-svg .x-labels text {
  font-size: 11px;
  fill: var(--ry-neutral-500);
}

.chart-legend {
  margin-top: 8px;
  display: flex;
  gap: 24px;
  justify-content: center;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--ry-neutral-600);
}

.legend-dot {
  display: inline-block;
  width: 12px;
  height: 4px;
  border-radius: 2px;

  &.calls {
    background: var(--ry-primary);
  }

  &.success {
    background: var(--state-success);
  }
}

/* 快捷入口 */
.quick-card {
  display: flex;
  flex-direction: column;
}

.quick-grid {
  padding: 16px 20px;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 14px 8px;
  border: 1px solid transparent;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.2s ease;

  &:hover {
    background: var(--ry-primary-50);
    border-color: var(--ry-primary-100);
    transform: translateY(-2px);
  }
}

.quick-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.quick-primary {
  background: var(--ry-primary-50);
  color: var(--ry-primary);
}
.quick-success {
  background: var(--state-success-soft);
  color: var(--state-success);
}
.quick-warning {
  background: var(--state-warning-soft);
  color: var(--state-warning);
}
.quick-danger {
  background: var(--state-error-soft);
  color: var(--state-error);
}
.quick-info {
  background: var(--state-info-soft);
  color: var(--state-info);
}

.quick-label {
  font-size: 13px;
  color: var(--ry-neutral-700);
}

/* 模块概览 */
.module-grid {
  padding: 8px 20px 16px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.module-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--ry-border-light);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    border-color: var(--ry-primary-200);
    background: var(--ry-primary-50);
    box-shadow: var(--ry-shadow-sm);
    transform: translateY(-2px);
  }
}

.module-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.m-primary {
  background: var(--ry-primary-50);
  color: var(--ry-primary);
}
.m-success {
  background: var(--state-success-soft);
  color: var(--state-success);
}
.m-warning {
  background: var(--state-warning-soft);
  color: var(--state-warning);
}
.m-danger {
  background: var(--state-error-soft);
  color: var(--state-error);
}
.m-info {
  background: var(--state-info-soft);
  color: var(--state-info);
}

.module-info {
  flex: 1;
  min-width: 0;
}

.module-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--ry-neutral-700);
}

.module-desc {
  margin-top: 2px;
  font-size: 12px;
  color: var(--ry-neutral-500);
}

.module-count {
  font-size: 22px;
  font-weight: 600;
  color: var(--ry-primary);
  font-family: var(--ry-font-title);
}

/* 响应式 */
@media (max-width: 1200px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .row-2 {
    grid-template-columns: 1fr;
  }

  .module-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .stat-grid {
    grid-template-columns: 1fr;
  }

  .module-grid {
    grid-template-columns: 1fr;
  }

  .welcome-card {
    padding: 20px;

    .welcome-illustration {
      display: none;
    }
  }
}
</style>
