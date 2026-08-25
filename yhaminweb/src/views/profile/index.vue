<template>
  <div class="profile-page">
    <el-row :gutter="16">
      <!-- 左侧：用户卡片 -->
      <el-col :xs="24" :sm="24" :md="8" :lg="7" :xl="6">
        <el-card class="user-card" shadow="never">
          <div class="user-card-header">
            <div class="user-avatar">{{ avatarText }}</div>
            <div class="user-info">
              <div class="user-name">
                {{ form.nickname || userInfo.username }}
              </div>
              <div class="user-role">
                <el-tag
                  v-for="r in roles"
                  :key="r"
                  size="small"
                  effect="plain"
                  >{{ r }}</el-tag
                >
              </div>
            </div>
          </div>
          <el-divider />
          <ul class="user-meta">
            <li>
              <el-icon><User /></el-icon>
              <span class="label">用户名</span>
              <span class="value">{{ userInfo.username }}</span>
            </li>
            <li>
              <el-icon><Iphone /></el-icon>
              <span class="label">手机</span>
              <span class="value">{{ userInfo.phone || "—" }}</span>
            </li>
            <li>
              <el-icon><Message /></el-icon>
              <span class="label">邮箱</span>
              <span class="value">{{ userInfo.email || "—" }}</span>
            </li>
            <li>
              <el-icon><Clock /></el-icon>
              <span class="label">注册时间</span>
              <span class="value">{{ formatTime(userInfo.createTime) }}</span>
            </li>
          </ul>
        </el-card>
      </el-col>

      <!-- 右侧：资料 + 密码 -->
      <el-col :xs="24" :sm="24" :md="16" :lg="17" :xl="18">
        <el-card shadow="never" class="form-card">
          <el-tabs v-model="activeTab">
            <!-- 基本资料 -->
            <el-tab-pane label="基本资料" name="info">
              <el-form
                ref="infoFormRef"
                :model="form"
                :rules="infoRules"
                label-width="80px"
                class="profile-form"
              >
                <el-form-item label="昵称" prop="nickname">
                  <el-input
                    v-model="form.nickname"
                    placeholder="请输入昵称"
                    maxlength="30"
                  />
                </el-form-item>
                <el-form-item label="手机号" prop="phone">
                  <el-input
                    v-model="form.phone"
                    placeholder="请输入手机号"
                    maxlength="11"
                  />
                </el-form-item>
                <el-form-item label="邮箱" prop="email">
                  <el-input
                    v-model="form.email"
                    placeholder="请输入邮箱"
                    maxlength="50"
                  />
                </el-form-item>
                <el-form-item label="备注">
                  <el-input
                    v-model="form.remark"
                    type="textarea"
                    :rows="3"
                    placeholder="请输入备注"
                    maxlength="200"
                    show-word-limit
                  />
                </el-form-item>
                <el-form-item>
                  <el-button
                    type="primary"
                    :loading="saving"
                    @click="submitInfo"
                    >保存</el-button
                  >
                  <el-button @click="resetInfo">重置</el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <!-- 修改密码 -->
            <el-tab-pane label="修改密码" name="password">
              <el-form
                ref="pwdFormRef"
                :model="pwdForm"
                :rules="pwdRules"
                label-width="100px"
                class="profile-form"
              >
                <el-form-item label="原密码" prop="oldPassword">
                  <el-input
                    v-model="pwdForm.oldPassword"
                    type="password"
                    placeholder="请输入原密码"
                    show-password
                    maxlength="50"
                  />
                </el-form-item>
                <el-form-item label="新密码" prop="newPassword">
                  <el-input
                    v-model="pwdForm.newPassword"
                    type="password"
                    placeholder="请输入新密码（5-50 位）"
                    show-password
                    maxlength="50"
                  />
                </el-form-item>
                <el-form-item label="确认密码" prop="confirmPassword">
                  <el-input
                    v-model="pwdForm.confirmPassword"
                    type="password"
                    placeholder="请再次输入新密码"
                    show-password
                    maxlength="50"
                  />
                </el-form-item>
                <el-form-item>
                  <el-button
                    type="primary"
                    :loading="savingPwd"
                    @click="submitPwd"
                    >提交</el-button
                  >
                  <el-button @click="resetPwd">重置</el-button>
                </el-form-item>
              </el-form>
              <el-alert
                class="pwd-tip"
                type="info"
                :closable="false"
                title="修改密码后需重新登录"
                description="为保证账户安全，修改密码后会自动退出当前登录，请使用新密码重新登录。"
                show-icon
              />
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { User, Iphone, Message, Clock } from "@element-plus/icons-vue";
import { useUserStore } from "@/store/modules/user";
import {
  getProfile,
  updateProfile,
  updateProfilePassword,
} from "@/api/profile";

const router = useRouter();
const userStore = useUserStore();

const activeTab = ref("info");
const saving = ref(false);
const savingPwd = ref(false);

const userInfo = reactive({
  id: null,
  username: "",
  nickname: "",
  phone: "",
  email: "",
  remark: "",
  createTime: null,
});
const roles = computed(() => userStore.roles || []);

const avatarText = computed(() => {
  const n = userInfo.nickname || userInfo.username || "U";
  return n.slice(0, 1).toUpperCase();
});

const form = reactive({ nickname: "", phone: "", email: "", remark: "" });
const infoFormRef = ref();
const infoRules = {
  nickname: [{ required: true, message: "昵称不能为空", trigger: "blur" }],
  phone: [
    {
      pattern: /^1[3-9]\d{9}$|^$/,
      message: "手机号格式不正确",
      trigger: "blur",
    },
  ],
  email: [
    { type: "email", message: "邮箱格式不正确", trigger: "blur" },
    { pattern: /^$|.+@.+/, message: "邮箱格式不正确", trigger: "blur" },
  ],
};

const pwdForm = reactive({
  oldPassword: "",
  newPassword: "",
  confirmPassword: "",
});
const pwdFormRef = ref();
const equalToNew = (rule, value, callback) => {
  if (value !== pwdForm.newPassword)
    callback(new Error("两次输入的密码不一致"));
  else callback();
};
const pwdRules = {
  oldPassword: [{ required: true, message: "原密码不能为空", trigger: "blur" }],
  newPassword: [
    { required: true, message: "新密码不能为空", trigger: "blur" },
    { min: 5, max: 50, message: "长度在 5 到 50 个字符", trigger: "blur" },
  ],
  confirmPassword: [
    { required: true, message: "确认密码不能为空", trigger: "blur" },
    { validator: equalToNew, trigger: "blur" },
  ],
};

function formatTime(t) {
  if (!t) return "—";
  return String(t).replace("T", " ").split(".")[0];
}

async function loadProfile() {
  const res = await getProfile();
  const data = res.data || res;
  Object.assign(userInfo, {
    id: data.id,
    username: data.username,
    nickname: data.nickname,
    phone: data.phone,
    email: data.email,
    remark: data.remark,
    createTime: data.createTime,
  });
  Object.assign(form, {
    nickname: data.nickname || "",
    phone: data.phone || "",
    email: data.email || "",
    remark: data.remark || "",
  });
}

async function submitInfo() {
  await infoFormRef.value.validate();
  saving.value = true;
  try {
    const res = await updateProfile({
      nickname: form.nickname,
      phone: form.phone,
      email: form.email,
      remark: form.remark,
    });
    const data = res.data || res;
    Object.assign(userInfo, {
      nickname: data.nickname,
      phone: data.phone,
      email: data.email,
      remark: data.remark,
    });
    // 同步更新顶栏显示名
    userStore.userInfo.nickname = data.nickname;
    ElMessage.success("修改成功");
  } finally {
    saving.value = false;
  }
}

function resetInfo() {
  Object.assign(form, {
    nickname: userInfo.nickname || "",
    phone: userInfo.phone || "",
    email: userInfo.email || "",
    remark: userInfo.remark || "",
  });
}

async function submitPwd() {
  await pwdFormRef.value.validate();
  savingPwd.value = true;
  try {
    await updateProfilePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    });
    ElMessage.success("密码修改成功，请重新登录");
    setTimeout(async () => {
      await userStore.logout();
      router.push("/login");
    }, 1200);
  } finally {
    savingPwd.value = false;
  }
}

function resetPwd() {
  pwdForm.oldPassword = "";
  pwdForm.newPassword = "";
  pwdForm.confirmPassword = "";
  pwdFormRef.value?.clearValidate();
}

onMounted(loadProfile);
</script>

<style lang="scss" scoped>
.profile-page {
  padding: 16px;
}

.user-card {
  border-radius: var(--ry-radius-medium);
  :deep(.el-card__body) {
    padding: 20px;
  }
}
.user-card-header {
  display: flex;
  align-items: center;
  gap: 16px;
}
.user-avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: var(--ry-primary);
  color: #fff;
  font-size: 26px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.user-info {
  min-width: 0;
}
.user-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--ry-foreground);
  margin-bottom: 8px;
}
.user-role {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.user-meta {
  list-style: none;
  margin: 0;
  padding: 0;
  li {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 0;
    font-size: 14px;
    color: var(--ry-foreground);
    .el-icon {
      color: var(--ry-neutral-500);
      font-size: 16px;
    }
    .label {
      color: var(--ry-neutral-500);
      width: 70px;
      flex-shrink: 0;
    }
    .value {
      color: var(--ry-foreground);
      word-break: break-all;
    }
  }
}

.form-card {
  border-radius: var(--ry-radius-medium);
  :deep(.el-card__body) {
    padding: 20px;
  }
}
.profile-form {
  max-width: 520px;
  padding-top: 12px;
}
.pwd-tip {
  margin-top: 16px;
  max-width: 520px;
}

@media (max-width: 768px) {
  .profile-page {
    padding: 10px;
  }
  .profile-form {
    max-width: 100%;
  }
}
</style>
