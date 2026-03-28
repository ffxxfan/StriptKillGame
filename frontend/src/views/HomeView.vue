<template>
  <div class="home-container">
    <!-- Header -->
    <header class="home-header">
      <div class="header-left">
        <span class="logo-text">剧本杀</span>
      </div>
      <div class="header-right" @click="router.push('/profile')" style="cursor: pointer;">
        <el-avatar :size="36" :src="authStore.userInfo?.avatarUrl">
          <el-icon><UserFilled /></el-icon>
        </el-avatar>
        <span class="nickname">{{ authStore.userInfo?.nickname || '玩家' }}</span>
      </div>
    </header>

    <!-- Body -->
    <div class="home-body">
      <!-- Sidebar -->
      <aside class="home-sidebar">
        <div
          v-for="item in menuItems"
          :key="item.key"
          class="sidebar-item"
          :class="{ active: activeMenu === item.key }"
          @click="activeMenu = item.key"
        >
          <el-icon :size="20"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </div>
      </aside>

      <!-- Content -->
      <main class="home-content">
        <!-- 剧本杀游戏 -->
        <div v-if="activeMenu === 'game'" class="content-panel">
          <div class="panel-header">
            <h2>剧本杀游戏</h2>
            <p>选择剧本，邀请好友，开始一场烧脑之旅</p>
          </div>
          <div class="panel-body">
            <el-empty description="点击右下角创建房间，开始游戏" />
          </div>
          <div class="panel-footer">
            <el-button type="primary" size="large" @click="handleCreateRoom" :loading="creating">
              创建房间
            </el-button>
          </div>
        </div>

        <!-- 剧本杀创作 -->
        <div v-else-if="activeMenu === 'create'" class="content-panel">
          <div class="panel-body placeholder">
            <el-empty description="剧本创作功能即将上线，敬请期待" />
          </div>
        </div>

        <!-- 剧本杀上传 -->
        <div v-else-if="activeMenu === 'upload'" class="content-panel">
          <div class="panel-body placeholder">
            <el-empty description="剧本上传功能即将上线，敬请期待" />
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, markRaw } from 'vue'
import { useRouter } from 'vue-router'
import { UserFilled, Opportunity, EditPen, UploadFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { useGameStore } from '../stores/game'
import { getUserInfo } from '../api/auth'
import { createRoom } from '../api/room'

const router = useRouter()
const authStore = useAuthStore()
const gameStore = useGameStore()

const activeMenu = ref('game')
const creating = ref(false)

const menuItems = [
  { key: 'game', label: '剧本杀游戏', icon: markRaw(Opportunity) },
  { key: 'create', label: '剧本杀创作', icon: markRaw(EditPen) },
  { key: 'upload', label: '剧本杀上传', icon: markRaw(UploadFilled) },
]

onMounted(async () => {
  try {
    const { data } = await getUserInfo()
    authStore.setUserInfo(data)
  } catch (e) {
    // handled by interceptor
  }
})

async function handleCreateRoom() {
  creating.value = true
  try {
    const { data } = await createRoom()
    gameStore.setRoom(data.roomId)
    router.push(`/game/${data.roomId}/scripts`)
  } catch (e) {
    console.error('创建房间失败', e)
  } finally {
    creating.value = false
  }
}
</script>

<style scoped>
.home-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #1a1a2e;
  color: #e0e0e0;
}

.home-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 24px;
  background: #16213e;
  border-bottom: 1px solid #0f3460;
}

.header-left .logo-text {
  font-size: 22px;
  font-weight: bold;
  color: #e94560;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nickname {
  font-size: 14px;
  color: #a0a0b0;
}

.home-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.home-sidebar {
  width: 200px;
  background: #16213e;
  padding: 16px 0;
  border-right: 1px solid #0f3460;
}

.sidebar-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 24px;
  cursor: pointer;
  transition: all 0.2s;
  color: #a0a0b0;
}

.sidebar-item:hover {
  background: #1a1a40;
  color: #fff;
}

.sidebar-item.active {
  background: #0f3460;
  color: #e94560;
  border-right: 3px solid #e94560;
}

.home-content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}

.content-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.panel-header {
  margin-bottom: 24px;
}

.panel-header h2 {
  font-size: 24px;
  color: #fff;
  margin: 0 0 8px;
}

.panel-header p {
  color: #a0a0b0;
  margin: 0;
}

.panel-body {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.panel-footer {
  display: flex;
  justify-content: flex-end;
  padding: 16px 0;
}
</style>
