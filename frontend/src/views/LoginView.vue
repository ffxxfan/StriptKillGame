<template>
  <div class="login-container">
    <el-card class="login-card" shadow="hover">
      <!-- 标题 -->
      <template #header>
        <div class="login-header">
          <h2>剧本杀</h2>
          <p class="subtitle">登录你的账户</p>
        </div>
      </template>

      <!-- 登录 / 注册 Tab -->
      <el-tabs v-model="activeTab" stretch>
        <!-- 登录 Tab -->
        <el-tab-pane label="登录" name="login">
          <el-form
            ref="loginFormRef"
            :model="loginForm"
            :rules="loginRules"
            label-position="top"
            @submit.prevent="handleLogin"
          >
            <el-form-item label="用户名" prop="username">
              <el-input
                v-model="loginForm.username"
                placeholder="请输入用户名"
                prefix-icon="User"
                size="large"
              />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                prefix-icon="Lock"
                size="large"
                show-password
                @keyup.enter="handleLogin"
              />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                size="large"
                :loading="loading"
                class="login-btn"
                @click="handleLogin"
              >
                登录
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- 注册 Tab -->
        <el-tab-pane label="注册" name="register">
          <el-form
            ref="registerFormRef"
            :model="registerForm"
            :rules="registerRules"
            label-position="top"
            @submit.prevent="handleRegister"
          >
            <el-form-item label="用户名" prop="username">
              <el-input
                v-model="registerForm.username"
                placeholder="请输入用户名（3-20字符）"
                prefix-icon="User"
                size="large"
              />
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input
                v-model="registerForm.nickname"
                placeholder="请输入昵称（选填）"
                prefix-icon="UserFilled"
                size="large"
              />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="registerForm.password"
                type="password"
                placeholder="请输入密码（至少6位）"
                prefix-icon="Lock"
                size="large"
                show-password
              />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input
                v-model="registerForm.confirmPassword"
                type="password"
                placeholder="请再次输入密码"
                prefix-icon="Lock"
                size="large"
                show-password
                @keyup.enter="handleRegister"
              />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                size="large"
                :loading="loading"
                class="login-btn"
                @click="handleRegister"
              >
                注册
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { login, register } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const activeTab = ref('login')
const loading = ref(false)
const loginFormRef = ref<FormInstance>()
const registerFormRef = ref<FormInstance>()

// ======================== 登录表单 ========================
const loginForm = reactive({
  username: '',
  password: '',
})

const loginRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  const valid = await loginFormRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const { data } = await login(loginForm.username, loginForm.password)
    authStore.setTokens(data.accessToken, data.refreshToken)
    ElMessage.success('登录成功')
    router.push('/home')
  } catch (error: any) {
    const msg = error.response?.data?.message || '登录失败'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}

// ======================== 注册表单 ========================
const registerForm = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: '',
})

const registerRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度需在3-20之间', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (_rule: any, value: string, callback: Function) => {
        if (value !== registerForm.password) {
          callback(new Error('两次密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

async function handleRegister() {
  const valid = await registerFormRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await register(
      registerForm.username,
      registerForm.password,
      registerForm.nickname || undefined
    )
    ElMessage.success('注册成功，请登录')
    activeTab.value = 'login'
    loginForm.username = registerForm.username
    loginForm.password = ''
  } catch (error: any) {
    const msg = error.response?.data?.message || '注册失败'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 420px;
  border-radius: 12px;
}

.login-header {
  text-align: center;
}

.login-header h2 {
  margin: 0;
  font-size: 28px;
  color: #303133;
}

.subtitle {
  margin-top: 8px;
  color: #909399;
  font-size: 14px;
}

.login-btn {
  width: 100%;
  margin-top: 8px;
}
</style>
