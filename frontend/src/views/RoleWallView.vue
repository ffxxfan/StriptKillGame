<template>
  <div class="role-wall-container">
    <header class="rw-header">
      <h2>角色墙</h2>
      <p>选择你要扮演的角色</p>
    </header>

    <div class="role-grid" :class="gridClass">
      <div
        v-for="role in roles"
        :key="role.id"
        class="role-card"
        :class="{
          selected: selectedId === role.id,
          unavailable: !role.isAvailable,
          npc: role.isNpc
        }"
        @click="role.isAvailable && !role.isNpc && handleSelect(role)"
      >
        <div class="role-avatar">
          <el-avatar :size="64" :src="role.avatar">
            {{ role.name.charAt(0) }}
          </el-avatar>
        </div>
        <div class="role-name">{{ role.name }}</div>
        <div v-if="role.isNpc" class="role-tag">NPC</div>
        <div v-else-if="!role.isAvailable" class="role-tag taken">已选</div>
      </div>
    </div>

    <div class="rw-footer">
      <el-button @click="router.back()">返回</el-button>
      <el-button
        type="primary"
        size="large"
        :disabled="!selectedId"
        :loading="submitting"
        @click="handleConfirm"
      >
        开始游戏
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getRoles, selectRole, startGame } from '../api/room'
import { useGameStore } from '../stores/game'

interface RoleItem {
  id: string
  name: string
  avatar: string
  isNpc: boolean
  isAvailable: boolean
}

const route = useRoute()
const router = useRouter()
const gameStore = useGameStore()
const roomId = route.params.roomId as string

const roles = ref<RoleItem[]>([])
const selectedId = ref<string | null>(null)
const submitting = ref(false)

const gridClass = computed(() => {
  const count = roles.value.length
  if (count <= 5) return 'grid-1-row'
  if (count <= 10) return 'grid-2-rows'
  return 'grid-3-rows'
})

onMounted(async () => {
  try {
    const { data } = await getRoles(roomId)
    roles.value = data
  } catch (e) {
    console.error('加载角色失败', e)
  }
})

function handleSelect(role: RoleItem) {
  selectedId.value = role.id
}

async function handleConfirm() {
  if (!selectedId.value) return
  submitting.value = true
  try {
    await selectRole(roomId, selectedId.value)
    gameStore.setMyRole(selectedId.value)
    await startGame(roomId)
    gameStore.setGameStatus('PLAYING')
    router.push(`/game/${roomId}/play`)
  } catch (e) {
    console.error('开始游戏失败', e)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.role-wall-container {
  min-height: 100vh;
  background: #1a1a2e;
  color: #e0e0e0;
  padding: 32px;
  display: flex;
  flex-direction: column;
}

.rw-header {
  text-align: center;
  margin-bottom: 32px;
}

.rw-header h2 {
  font-size: 28px;
  color: #fff;
  margin: 0 0 8px;
}

.rw-header p {
  color: #a0a0b0;
  margin: 0;
}

.role-grid {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  gap: 20px;
}

.grid-1-row {
  align-content: center;
}

.grid-2-rows,
.grid-3-rows {
  align-content: center;
}

.role-card {
  width: 140px;
  padding: 20px 16px;
  background: #16213e;
  border-radius: 12px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
  border: 2px solid transparent;
  position: relative;
}

.role-card:hover:not(.unavailable):not(.npc) {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(233, 69, 96, 0.2);
}

.role-card.selected {
  border-color: #e94560;
  box-shadow: 0 0 16px rgba(233, 69, 96, 0.4);
}

.role-card.unavailable,
.role-card.npc {
  cursor: default;
  opacity: 0.5;
}

.role-avatar {
  margin-bottom: 12px;
}

.role-name {
  font-size: 14px;
  color: #fff;
}

.role-tag {
  position: absolute;
  top: 8px;
  right: 8px;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  background: #0f3460;
  color: #a0a0b0;
}

.role-tag.taken {
  background: #e94560;
  color: #fff;
}

.rw-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 24px 0 0;
}
</style>
