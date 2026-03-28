<template>
  <div class="profile-container">
    <!-- 顶部导航 -->
    <el-header class="top-bar">
      <div class="back-area" @click="goHome">
        <el-icon :size="20"><ArrowLeft /></el-icon>
        <span>返回</span>
      </div>
      <h3>个人中心</h3>
      <div style="width: 80px"></div>
    </el-header>

    <el-main class="profile-main">
      <div class="profile-content">
        <!-- 用户信息卡片 -->
        <el-card class="info-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>用户信息</span>
            </div>
          </template>

          <div v-loading="infoLoading" class="user-info">
            <div class="avatar-section">
              <el-avatar
                :size="80"
                :src="userInfo?.avatarUrl || ''"
              >
                <el-icon :size="40"><UserFilled /></el-icon>
              </el-avatar>
            </div>

            <el-descriptions :column="1" border>
              <el-descriptions-item label="用户名">
                {{ userInfo?.username || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="昵称">
                {{ userInfo?.nickname || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="注册时间">
                {{ formatDate(userInfo?.createdAt) }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-card>

        <!-- 修改密码卡片 -->
        <el-card class="password-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>修改密码</span>
            </div>
          </template>

          <el-form
            ref="passwordFormRef"
            :model="passwordForm"
            :rules="passwordRules"
            label-position="top"
          >
            <el-form-item label="旧密码" prop="oldPassword">
              <el-input
                v-model="passwordForm.oldPassword"
                type="password"
                placeholder="请输入当前密码"
                show-password
                size="large"
              />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input
                v-model="passwordForm.newPassword"
                type="password"
                placeholder="请输入新密码（至少6位）"
                show-password
                size="large"
              />
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input
                v-model="passwordForm.confirmPassword"
                type="password"
                placeholder="请再次输入新密码"
                show-password
                size="large"
              />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                size="large"
                :loading="passwordLoading"
                @click="handleChangePassword"
              >
                确认修改
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 登出按钮 -->
        <el-button
          type="danger"
          size="large"
          class="logout-btn"
          @click="handleLogout"
        >
          退出登录
        </el-button>
      </div>
    </el-main>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { ArrowLeft, UserFilled } from '@element-plus/icons-vue'
import { getUserInfo, changePassword, logout } from '../api/auth'
import { useAuthStore, type UserInfo } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const infoLoading = ref(false)
const passwordLoading = ref(false)
const userInfo = ref<UserInfo | null>(null)
const passwordFormRef = ref<FormInstance>()

// ======================== 用户信息 ========================

onMounted(async () => {
  infoLoading.value = true
  try {
    const { data } = await getUserInfo()
    userInfo.value = data
    authStore.setUserInfo(data)
  } catch {
    // 拦截器处理 401
  } finally {
    infoLoading.value = false
  }
})

function formatDate(dateStr?: string): string {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// ======================== 修改密码 ========================

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '新密码至少6位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (_rule: any, value: string, callback: Function) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

async function handleChangePassword() {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return

  passwordLoading.value = true
  try {
    await changePassword(passwordForm.oldPassword, passwordForm.newPassword)
    ElMessage.success('密码修改成功，请重新登录')
    // 改密后清除状态并跳转登录页
    authStore.clearAuth()
    router.push('/login')
  } catch (error: any) {
    const msg = error.response?.data?.message || '修改密码失败'
    ElMessage.error(msg)
  } finally {
    passwordLoading.value = false
  }
}

// ======================== 登出 ========================

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return // 用户取消
  }

  try {
    await logout()
  } catch {
    // 即使服务端登出失败也清除本地状态
  }
  authStore.clearAuth()
  ElMessage.success('已退出登录')
  router.push('/login')
}

// ======================== 导航 ========================

function goHome() {
  router.push('/home')
}
</script>

<style scoped>
.profile-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f5f7fa;
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  height: 56px;
}

.top-bar h3 {
  margin: 0;
  color: #303133;
}

.back-area {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #606266;
  font-size: 14px;
  width: 80px;
}

.back-area:hover {
  color: #409eff;
}

.profile-main {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.profile-content {
  max-width: 500px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.card-header {
  font-weight: 600;
  font-size: 16px;
}

.avatar-section {
  display: flex;
  justify-content: center;
  margin-bottom: 24px;
}

.logout-btn {
  width: 100%;
}
</style>
