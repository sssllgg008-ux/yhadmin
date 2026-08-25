<template>
  <el-drawer
    v-model="visible"
    title="布局设置"
    direction="rtl"
    size="300px"
    :with-header="true"
  >
    <div class="setting-drawer">
      <!-- 主题色 -->
      <div class="setting-section">
        <div class="section-title">主题色</div>
        <div class="theme-colors">
          <div
            v-for="preset in THEME_PRESETS"
            :key="preset.color"
            class="color-item"
            :class="{ 'is-active': appStore.themeColor === preset.color }"
            :style="{ background: preset.color }"
            :title="preset.name"
            @click="appStore.setThemeColor(preset.color)"
          >
            <el-icon
              v-if="appStore.themeColor === preset.color"
              class="check-icon"
              ><Check
            /></el-icon>
          </div>
        </div>
        <div class="custom-color">
          <span class="custom-label">自定义</span>
          <el-color-picker
            :model-value="appStore.themeColor"
            @update:model-value="(v) => v && appStore.setThemeColor(v)"
          />
        </div>
      </div>

      <el-divider />

      <!-- 侧栏配色 -->
      <div class="setting-section">
        <div class="section-title">侧栏配色</div>
        <div class="sidebar-theme-switch">
          <div
            class="theme-option"
            :class="{ 'is-active': appStore.sidebarTheme === 'dark' }"
            @click="appStore.setSidebarTheme('dark')"
          >
            <div class="theme-preview theme-preview-dark">
              <div class="preview-bar"></div>
              <div class="preview-content"></div>
            </div>
            <span>深色</span>
          </div>
          <div
            class="theme-option"
            :class="{ 'is-active': appStore.sidebarTheme === 'light' }"
            @click="appStore.setSidebarTheme('light')"
          >
            <div class="theme-preview theme-preview-light">
              <div class="preview-bar"></div>
              <div class="preview-content"></div>
            </div>
            <span>浅色</span>
          </div>
        </div>
      </div>

      <el-divider />

      <!-- 界面显示 -->
      <div class="setting-section">
        <div class="section-title">界面显示</div>
        <div class="setting-row">
          <span>显示模块切换栏</span>
          <el-switch v-model="appStore.showModuleBar" />
        </div>
        <div class="setting-row">
          <span>显示标签栏</span>
          <el-switch v-model="appStore.showTabs" />
        </div>
        <div class="setting-row">
          <span>显示面包屑</span>
          <el-switch v-model="appStore.showBreadcrumb" />
        </div>
        <div class="setting-row">
          <span>固定头部</span>
          <el-switch v-model="appStore.fixedHeader" />
        </div>
      </div>

      <el-divider />

      <!-- 组件尺寸 -->
      <div class="setting-section">
        <div class="section-title">组件尺寸</div>
        <el-radio-group v-model="sizeValue" size="small">
          <el-radio-button value="large">宽松</el-radio-button>
          <el-radio-button value="default">默认</el-radio-button>
          <el-radio-button value="small">紧凑</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <template #footer>
      <div class="drawer-footer">
        <el-button size="small" @click="handleReset">恢复默认</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed } from "vue";
import { Check } from "@element-plus/icons-vue";
import { useAppStore, THEME_PRESETS } from "@/store/modules/app";

const appStore = useAppStore();

const visible = computed({
  get: () => appStore.settingDrawerOpen,
  set: (v) => {
    v ? appStore.openSettingDrawer() : appStore.closeSettingDrawer();
  },
});

const sizeValue = computed({
  get: () => appStore.size,
  set: (v) => appStore.setSize(v),
});

function handleReset() {
  appStore.setThemeColor("#1677FF");
  appStore.setSidebarTheme("dark");
  appStore.showModuleBar = true;
  appStore.showTabs = true;
  appStore.showBreadcrumb = true;
  appStore.fixedHeader = true;
  appStore.setSize("default");
}
</script>

<style lang="scss" scoped>
.setting-drawer {
  padding: 4px 0;
}

.setting-section {
  padding: 0 4px;
}

.section-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--ry-foreground);
  margin-bottom: 12px;
}

/* 主题色 */
.theme-colors {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}
.color-item {
  width: 24px;
  height: 24px;
  border-radius: var(--ry-radius-medium);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid transparent;
  transition: all 0.15s ease;
  &:hover {
    transform: scale(1.1);
  }
  &.is-active {
    border-color: var(--ry-neutral-500);
  }
  .check-icon {
    color: #fff;
    font-size: 14px;
  }
}
.custom-color {
  display: flex;
  align-items: center;
  gap: 10px;
}
.custom-label {
  font-size: 13px;
  color: var(--ry-neutral-600);
}

/* 侧栏配色 */
.sidebar-theme-switch {
  display: flex;
  gap: 12px;
}
.theme-option {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 6px;
  border-radius: var(--ry-radius-medium);
  border: 2px solid transparent;
  transition: all 0.15s ease;
  &:hover {
    background: var(--ry-neutral-100);
  }
  &.is-active {
    border-color: var(--ry-primary);
  }
  span {
    font-size: 12px;
    color: var(--ry-neutral-600);
  }
  &.is-active span {
    color: var(--ry-primary);
    font-weight: 500;
  }
}
.theme-preview {
  width: 48px;
  height: 36px;
  border-radius: var(--ry-radius-small);
  overflow: hidden;
  display: flex;
  border: 1px solid var(--ry-border);
}
.preview-bar {
  width: 14px;
  height: 100%;
}
.preview-content {
  flex: 1;
}
.theme-preview-dark .preview-bar {
  background: var(--ry-sidebar-bg);
}
.theme-preview-dark .preview-content {
  background: var(--ry-background);
}
.theme-preview-light .preview-bar {
  background: var(--ry-card);
  border-right: 1px solid var(--ry-border);
}
.theme-preview-light .preview-content {
  background: var(--ry-background);
}

/* 界面显示 */
.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  font-size: 13px;
  color: var(--ry-neutral-700);
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
}
</style>
