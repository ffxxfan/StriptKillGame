<template>
  <div class="script-wall-container">
    <header class="sw-header">
      <h2>剧本墙</h2>
      <p>选择一个剧本开始你的推理之旅</p>
    </header>

    <div class="script-grid">
      <div
        v-for="(item, index) in displayScripts"
        :key="index"
        class="script-card"
        :class="{ placeholder: !item, selected: item && selectedId === item.id }"
        @click="item && handleSelect(item)"
      >
        <template v-if="item">
          <div class="card-cover">
            <img v-if="item.coverImage" :src="item.coverImage" :alt="item.title" />
            <div v-else class="cover-fallback">
              <el-icon :size="40"><Opportunity /></el-icon>
            </div>
          </div>
          <div class="card-info">
            <h3>{{ item.title }}</h3>
            <div class="card-meta">
              <el-tag size="small" :type="difficultyType(item.difficulty)">
                {{ item.difficulty }}
              </el-tag>
              <span class="player-count">{{ item.playerCount }}人</span>
            </div>
          </div>
        </template>
        <template v-else>
          <div class="card-cover cover-fallback">
            <el-icon :size="40" color="#555"><Lock /></el-icon>
          </div>
          <div class="card-info">
            <h3 class="placeholder-text">敬请期待</h3>
          </div>
        </template>
      </div>
    </div>

    <div class="sw-footer">
      <el-button @click="router.push('/home')">返回</el-button>
      <el-button
        type="primary"
        :disabled="!selectedId"
        :loading="submitting"
        @click="handleConfirm"
      >
        确认选择
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Opportunity, Lock } from '@element-plus/icons-vue'
import { getRandomScripts } from '../api/script'
import { getRoom, selectScript } from '../api/room'
import { useGameStore } from '../stores/game'

interface ScriptItem {
  id: string
  title: string
  description: string
  difficulty: string
  playerCount: number
  coverImage: string
}

const route = useRoute()
const router = useRouter()
const gameStore = useGameStore()
const roomId = route.params.roomId as string

const scripts = ref<ScriptItem[]>([])
const selectedId = ref<string | null>(null)
const submitting = ref(false)

const displayScripts = computed(() => {
  const result: (ScriptItem | null)[] = [...scripts.value]
  while (result.length < 8) {
    result.push(null)
  }
  return result
})

onMounted(async () => {
  try {
    // Guard: redirect if room is no longer WAITING
    const { data: room } = await getRoom(roomId)
    if (room.status === 'PLAYING') {
      router.replace(`/game/${roomId}/play`)
      return
    }
    if (room.status === 'FINISHED') {
      router.replace('/home')
      return
    }

    const { data } = await getRandomScripts()
    scripts.value = data
  } catch (e) {
    console.error('加载剧本失败', e)
  }
})

function handleSelect(script: ScriptItem) {
  selectedId.value = script.id
}

function difficultyType(difficulty: string) {
  const map: Record<string, string> = {
    EASY: 'success',
    NORMAL: 'info',
    HARD: 'warning',
    EXPERT: 'danger'
  }
  return (map[difficulty] || 'info') as any
}

async function handleConfirm() {
  if (!selectedId.value) return
  submitting.value = true
  try {
    await selectScript(roomId, selectedId.value)
    gameStore.setScript(selectedId.value)
    router.push(`/game/${roomId}/roles`)
  } catch (e) {
    console.error('选择剧本失败', e)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.script-wall-container {
  min-height: 100vh;
  background: #1a1a2e;
  color: #e0e0e0;
  padding: 32px;
  display: flex;
  flex-direction: column;
}

.sw-header {
  text-align: center;
  margin-bottom: 32px;
}

.sw-header h2 {
  font-size: 28px;
  color: #fff;
  margin: 0 0 8px;
}

.sw-header p {
  color: #a0a0b0;
  margin: 0;
}

.script-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  flex: 1;
}

.script-card {
  background: #16213e;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s;
  border: 2px solid transparent;
}

.script-card:hover:not(.placeholder) {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(233, 69, 96, 0.2);
}

.script-card.selected {
  border-color: #e94560;
  box-shadow: 0 0 16px rgba(233, 69, 96, 0.4);
}

.script-card.placeholder {
  cursor: default;
  opacity: 0.5;
}

.card-cover {
  height: 160px;
  overflow: hidden;
}

.card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-fallback {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #0f3460;
}

.card-info {
  padding: 12px;
}

.card-info h3 {
  font-size: 16px;
  color: #fff;
  margin: 0 0 8px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.player-count {
  font-size: 12px;
  color: #a0a0b0;
}

.placeholder-text {
  color: #555 !important;
}

.sw-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 24px 0 0;
}
</style>
