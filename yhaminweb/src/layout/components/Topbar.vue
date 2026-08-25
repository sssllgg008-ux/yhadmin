<template>
  <header class="topbar">
    <div class="topbar-left">
      <button
        class="icon-btn collapse-btn"
        type="button"
        :aria-label="appStore.sidebarCollapsed ? '展开侧栏' : '折叠侧栏'"
        @click="appStore.toggleSidebar()"
      >
        <el-icon
          ><Fold
            v-if="
              !appStore.sidebarCollapsed || appStore.device === 'mobile'
            " /><Expand v-else
        /></el-icon>
      </button>
      <!-- 顶部模块切换（仅模块分组，不含"系统首页"等独立叶菜单） -->
      <ModuleSwitchBar
        v-if="appStore.showModuleBar"
        class="ry-hide-mobile"
        :active-key="activeModuleKey"
        @switch="(key) => $emit('module-switch', key)"
      />
    </div>

    <div class="topbar-right">
      <button
        class="icon-btn ry-hide-mobile"
        type="button"
        aria-label="全屏"
        @click="toggleFullscreen"
      >
        <el-icon><FullScreen /></el-icon>
      </button>
      <button
        class="icon-btn ry-hide-mobile"
        type="button"
        aria-label="搜索"
        @click="searchVisible = true"
      >
        <el-icon><Search /></el-icon>
      </button>
      <el-popover placement="bottom-end" :width="390" trigger="click" @show="loadNotices">
        <template #reference>
          <el-badge :value="unreadCount" :max="99" :hidden="unreadCount < 1" class="notice-badge ry-hide-mobile">
            <button class="icon-btn" type="button" aria-label="通知公告"><el-icon><Bell /></el-icon></button>
          </el-badge>
        </template>
        <div class="notice-panel" v-loading="noticeLoading">
          <div class="notice-panel-head">
            <strong>通知公告</strong>
            <el-button v-if="unreadCount" link type="primary" size="small" @click="readAllNotices">全部已读</el-button>
          </div>
          <div v-if="notices.length" class="notice-list">
            <button v-for="notice in notices" :key="notice.id" type="button" class="notice-item" :class="{ unread: !notice.isRead }" @click="openNotice(notice)">
              <span class="notice-unread-dot" />
              <span class="notice-main">
                <span class="notice-title">{{ notice.noticeTitle }}</span>
                <span class="notice-meta">{{ notice.noticeType === '1' ? '通知' : '公告' }} · {{ formatNoticeTime(notice.updateTime || notice.createTime) }}</span>
              </span>
            </button>
          </div>
          <el-empty v-else :image-size="52" description="暂无已发布公告" />
        </div>
      </el-popover>
      <span class="topbar-divider" />
      <el-dropdown trigger="click" @command="handleCommand">
        <div class="user-trigger">
          <span class="user-avatar">{{ userStore.avatarText }}</span>
          <span class="user-name ry-hide-mobile">{{
            userStore.displayName || "异火管理员"
          }}</span>
          <el-icon class="caret"><CaretBottom /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile" :icon="User"
              >个人中心</el-dropdown-item
            >
            <el-dropdown-item command="settings" :icon="Setting"
              >布局设置</el-dropdown-item
            >
            <el-dropdown-item command="logout" :icon="SwitchButton" divided
              >退出登录</el-dropdown-item
            >
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 顶部搜索 -->
    <el-dialog
      v-model="searchVisible"
      title="全局搜索"
      width="520px"
      append-to-body
    >
      <el-input
        v-model="searchKeyword"
        placeholder="搜索菜单 / 用户 / Agent..."
        clearable
      >
        <template #prefix
          ><el-icon><Search /></el-icon
        ></template>
      </el-input>
      <div class="search-result">
        <div
          v-for="item in searchResults"
          :key="item.path"
          class="search-result-item"
          @click="goSearch(item)"
        >
          <el-icon><component :is="resolveIcon(item.icon)" /></el-icon>
          <span>{{ item.title }}</span>
        </div>
        <div v-if="searchKeyword && !searchResults.length" class="search-empty">
          未找到匹配项
        </div>
      </div>
    </el-dialog>
    <el-dialog v-model="noticeDetailVisible" :title="activeNotice?.noticeTitle || '通知公告'" width="min(620px, 92vw)" append-to-body>
      <div v-if="activeNotice" class="notice-detail">
        <div class="notice-detail-meta">
          <el-tag size="small" :type="activeNotice.noticeType === '1' ? 'primary' : 'success'">{{ activeNotice.noticeType === '1' ? '通知' : '公告' }}</el-tag>
          <span>{{ formatNoticeTime(activeNotice.updateTime || activeNotice.createTime) }}</span>
          <span>{{ activeNotice.createBy || '' }}</span>
        </div>
        <div class="notice-content">{{ activeNotice.noticeContent || '暂无正文' }}</div>
      </div>
      <template #footer><el-button @click="noticeDetailVisible = false">关闭</el-button></template>
    </el-dialog>
  </header>
</template>

<script setup>
import { useRouter } from "vue-router";
import { ElMessageBox, ElMessage } from "element-plus";
import { User, Setting, SwitchButton } from "@element-plus/icons-vue";
import { useAppStore } from "@/store/modules/app";
import { useUserStore } from "@/store/modules/user";
import { usePermissionStore } from "@/store/modules/permission";
import * as ElIcons from "@element-plus/icons-vue";
import ModuleSwitchBar from "./ModuleSwitchBar.vue";
import { getNoticeInbox, markNoticeRead, markAllNoticesRead } from "@/api/system";

defineProps({
  // 当前选中模块的 key（用于顶部模块切换高亮）
  activeModuleKey: { type: String, default: "" },
});
defineEmits(["module-switch"]);

const appStore = useAppStore();
const userStore = useUserStore();
const permissionStore = usePermissionStore();
const router = useRouter();

const searchVisible = ref(false);
const searchKeyword = ref("");
const notices = ref([]);
const unreadCount = ref(0);
const noticeLoading = ref(false);
const noticeDetailVisible = ref(false);
const activeNotice = ref(null);
let noticeTimer = null;
let lastNoticeLoadAt = 0;

async function loadNotices() {
  if (!userStore.isLogin || noticeLoading.value) return;
  // Browsers may emit several focus events while switching between the page and
  // developer tools. Collapse the burst without changing the 60-second poll.
  if (Date.now() - lastNoticeLoadAt < 10000) return;
  noticeLoading.value = true;
  try {
    const res = await getNoticeInbox(10);
    const data = res.data || {};
    notices.value = data.items || [];
    unreadCount.value = Number(data.unreadCount || 0);
    lastNoticeLoadAt = Date.now();
  } catch (error) {
    // Keep the last successful snapshot while pluginSystem is unavailable.
  } finally {
    noticeLoading.value = false;
  }
}

async function openNotice(notice) {
  activeNotice.value = notice;
  noticeDetailVisible.value = true;
  if (!notice.isRead) {
    try {
      await markNoticeRead(notice.id);
      notice.isRead = true;
      unreadCount.value = Math.max(0, unreadCount.value - 1);
    } catch (error) {
      // The current snapshot can still be displayed.
    }
  }
}

async function readAllNotices() {
  await markAllNoticesRead();
  notices.value.forEach((notice) => { notice.isRead = true; });
  unreadCount.value = 0;
}

function formatNoticeTime(value) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  const pad = (n) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function stopNoticePolling() {
  if (noticeTimer) window.clearInterval(noticeTimer);
  noticeTimer = null;
  window.removeEventListener("focus", loadNotices);
}

function startNoticePolling() {
  stopNoticePolling();
  loadNotices();
  noticeTimer = window.setInterval(loadNotices, 60000);
  window.addEventListener("focus", loadNotices);
}

onMounted(startNoticePolling);
onBeforeUnmount(stopNoticePolling);

const menuTree = computed(() => permissionStore.menus);

// 扁平化所有可路由菜单项（有 path 的）
const allMenuItems = computed(() => {
  const list = [];
  function walk(items) {
    items.forEach((item) => {
      if (item.path) list.push(item);
      if (item.children && item.children.length) walk(item.children);
    });
  }
  walk(menuTree.value);
  return list;
});

const searchResults = computed(() => {
  if (!searchKeyword.value) return allMenuItems.value.slice(0, 6);
  const kw = searchKeyword.value.toLowerCase();
  return allMenuItems.value.filter((i) => i.title.toLowerCase().includes(kw));
});

function goSearch(item) {
  router.push(item.path);
  searchVisible.value = false;
  searchKeyword.value = "";
}

function resolveIcon(name) {
  return ElIcons[name] || ElIcons.Menu;
}

function toggleFullscreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen?.();
  } else {
    document.exitFullscreen?.();
  }
}

async function handleCommand(cmd) {
  if (cmd === "logout") {
    try {
      await ElMessageBox.confirm("确定要退出登录吗？", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      });
      await userStore.logout();
      stopNoticePolling();
      ElMessage.success("已退出登录");
      router.push("/login");
    } catch (e) {
      /* 用户取消 */
    }
  } else if (cmd === "profile") {
    router.push("/profile");
  } else if (cmd === "settings") {
    appStore.openSettingDrawer();
  }
}
</script>

<style lang="scss" scoped>
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: var(--ry-topbar-height);
  flex-shrink: 0;
  background: var(--ry-card);
  border-bottom: 1px solid var(--ry-border-light);
  padding: 0 18px 0 8px;
  box-shadow: 0 4px 18px rgba(30, 41, 59, 0.035);
  z-index: 10;
}
.topbar-left {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  flex: 1;
  height: 100%;
}
.topbar-right {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
  height: 100%;
}

.icon-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  color: var(--ry-neutral-500);
  cursor: pointer;
  border-radius: 10px;
  transition:
    color 0.2s ease,
    background 0.2s ease;
  padding: 0;
  font-size: 16px;
  &:hover {
    color: var(--ry-foreground);
    background: var(--ry-neutral-100);
  }
}
.collapse-btn {
  width: 38px;
}

.notice-badge { display: inline-flex; }
.notice-badge :deep(.el-badge__content) { top: 5px; right: 7px; transform: translate(55%, -45%) scale(0.88); }
.notice-panel-head { display: flex; align-items: center; justify-content: space-between; padding: 4px 4px 10px; border-bottom: 1px solid var(--ry-border-light); }
.notice-list { max-height: 390px; overflow-y: auto; }
.notice-item { width: 100%; display: flex; gap: 10px; align-items: flex-start; padding: 12px 6px; border: 0; border-bottom: 1px solid var(--ry-border-light); background: transparent; text-align: left; cursor: pointer; }
.notice-item:hover { background: var(--ry-neutral-100); }
.notice-unread-dot { width: 7px; height: 7px; margin-top: 7px; border-radius: 50%; background: transparent; flex: none; }
.notice-item.unread .notice-unread-dot { background: var(--state-error); }
.notice-main { min-width: 0; display: grid; gap: 5px; }
.notice-title { color: var(--ry-foreground); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.notice-item.unread .notice-title { font-weight: 600; }
.notice-meta { color: var(--ry-neutral-500); font-size: 12px; }
.notice-detail-meta { display: flex; align-items: center; gap: 10px; color: var(--ry-neutral-500); font-size: 13px; margin-bottom: 16px; }
.notice-content { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.8; color: var(--ry-foreground); }

.topbar-divider {
  width: 1px;
  height: 20px;
  background: var(--ry-border);
  flex-shrink: 0;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 0 10px;
  height: 38px;
  border-radius: 12px;
  transition: background 0.2s ease;
  &:hover {
    background: var(--ry-neutral-100);
  }
}
.user-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: var(--ry-radius-full);
  background: var(--ry-primary-50);
  color: var(--ry-primary);
  font-size: 13px;
  font-weight: 600;
  box-shadow: inset 0 0 0 1px var(--ry-primary-100);
  line-height: 1;
}
.user-name {
  font-size: 14px;
  color: var(--ry-foreground);
  white-space: nowrap;
}
.caret {
  color: var(--ry-neutral-500);
  font-size: 12px;
}

.search-result {
  margin-top: 12px;
  max-height: 320px;
  overflow-y: auto;
}
.search-result-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: var(--ry-radius-medium);
  cursor: pointer;
  font-size: 14px;
  color: var(--ry-foreground);
  &:hover {
    background: var(--ry-primary-50);
    color: var(--ry-primary);
  }
}
.search-empty {
  text-align: center;
  padding: 24px;
  color: var(--ry-neutral-500);
  font-size: 13px;
}

@media (max-width: 768px) {
  .topbar {
    padding: 0 10px;
    gap: 8px;
  }
  .topbar-left {
    gap: 8px;
  }
  .topbar-right {
    gap: 8px;
  }
}
</style>
