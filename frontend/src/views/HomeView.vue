<template>
  <div class="home-container">
    <!-- 顶部导航栏 -->
    <el-header class="top-bar">
      <div class="logo">
        <h3>剧本杀</h3>
      </div>
      <div class="user-area" @click="goProfile">
        <el-avatar
          :size="36"
          :src="authStore.userInfo?.avatarUrl || ''"
        >
          <el-icon :size="20"><UserFilled /></el-icon>
        </el-avatar>
        <span class="username">{{ authStore.userInfo?.nickname || '用户' }}</span>
      </div>
    </el-header>

    <!-- 主内容区：预留给后续功能 -->
    <el-main class="main-content">
      <slot name="main-content">
        <!-- 默认占位内容 -->
        <div class="placeholder">
          <el-empty description="游戏功能即将上线，敬请期待">
            <template #image>
              <div class="placeholder-icon">
                <el-icon :size="120" color="#c0c4cc"><Histogram /></el-icon>
              </div>
            </template>
          </el-empty>
        </div>
      </slot>
    </el-main>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { UserFilled, Histogram } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { getUserInfo } from '../api/auth'

const router = useRouter()
const authStore = useAuthStore()

/** 跳转到用户信息页 */
function goProfile() {
  router.push('/profile')
}

/** 加载用户信息 */
onMounted(async () => {
  if (!authStore.userInfo) {
    try {
      const { data } = await getUserInfo()
      authStore.setUserInfo(data)
    } catch {
      // 拦截器会处理 401
    }
  }
})
</script>

<style scoped>
.home-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  height: 56px;
  z-index: 10;
}

.logo h3 {
  margin: 0;
  color: #303133;
  font-size: 20px;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 12px;
  border-radius: 8px;
  transition: background-color 0.2s;
}

.user-area:hover {
  background-color: #f5f7fa;
}

.username {
  font-size: 14px;
  color: #606266;
}

.main-content {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.placeholder {
  text-align: center;
}

.placeholder-icon {
  opacity: 0.5;
}
</style>
