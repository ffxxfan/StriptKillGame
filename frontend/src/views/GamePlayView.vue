<template>
  <div class="game-play-container">
    <!-- Top Bar -->
    <header class="gp-header">
      <el-button class="exit-btn" text @click="handleLeave">
        <el-icon :size="20"><ArrowLeft /></el-icon>
        退出
      </el-button>
      <div class="room-info">
        <span v-if="roomDetail">{{ roomDetail.scriptTitle || '剧本杀' }}</span>
        <el-tag size="small" type="info" v-if="roomDetail">
          第 {{ (roomDetail.currentStage || 0) + 1 }} 幕
        </el-tag>
      </div>
      <el-button class="script-btn" text @click="openScriptDrawer">
        <el-icon :size="18"><Document /></el-icon>
        我的剧本
        <el-badge v-if="hasNewStage" is-dot class="stage-dot" />
      </el-button>
    </header>

    <!-- Chat Area -->
    <div class="chat-area" ref="chatAreaRef" @scroll="onChatScroll">
      <div
        v-for="msg in gameStore.messages"
        :key="msg.messageId"
        class="chat-message"
        :class="{ 'is-self': msg.senderRoleId === gameStore.myRoleId }"
      >
        <!-- Left: others -->
        <template v-if="msg.senderRoleId !== gameStore.myRoleId">
          <el-avatar :size="36" :src="msg.senderAvatar" class="msg-avatar">
            {{ msg.senderRoleName?.charAt(0) }}
          </el-avatar>
          <div class="msg-body">
            <div class="msg-name">{{ msg.senderRoleName }}</div>
            <div class="msg-bubble left">{{ msg.content }}</div>
          </div>
        </template>
        <!-- Right: self -->
        <template v-else>
          <div class="msg-body">
            <div class="msg-name self">{{ msg.senderRoleName }}</div>
            <div class="msg-bubble right">{{ msg.content }}</div>
          </div>
          <el-avatar :size="36" :src="msg.senderAvatar" class="msg-avatar">
            {{ msg.senderRoleName?.charAt(0) }}
          </el-avatar>
        </template>
      </div>

      <!-- System messages -->
      <div
        v-for="(sysMsg, idx) in systemMessages"
        :key="'sys-' + idx"
        class="system-message"
      >
        {{ sysMsg }}
      </div>
    </div>

    <!-- Input Area -->
    <div class="input-area">
      <el-input
        v-model="inputText"
        placeholder="输入消息..."
        @keyup.enter="handleSend"
        :disabled="gameStore.gameStatus !== 'PLAYING'"
      />
      <el-button
        type="primary"
        @click="handleSend"
        :disabled="!inputText.trim() || gameStore.gameStatus !== 'PLAYING'"
      >
        发送
      </el-button>
    </div>

    <!-- Script Drawer -->
    <el-drawer
      v-model="scriptDrawerVisible"
      title="我的剧本"
      direction="rtl"
      size="380px"
      :with-header="true"
      class="script-drawer"
    >
      <div v-if="stageContents.length === 0" class="script-empty">
        暂无剧本内容，等待游戏开始...
      </div>
      <el-tabs v-else v-model="activeStageTab" class="script-tabs">
        <el-tab-pane
          v-for="stage in stageContents"
          :key="stage.stageNumber"
          :label="stage.stageTitle || `第${stage.stageNumber}幕`"
          :name="String(stage.stageNumber)"
        >
          <div class="stage-content">
            <div v-if="stage.content" class="stage-text" v-html="formatContent(stage.content)"></div>
            <div v-else class="stage-no-content">该幕暂无你的专属内容</div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Document } from '@element-plus/icons-vue'
import { getRoom, leaveRoom } from '../api/room'
import { getMyScriptContent } from '../api/script'
import { useGameStore } from '../stores/game'
import { useWebSocket } from '../composables/useWebSocket'

interface StageContent {
  stageNumber: number
  stageTitle: string
  content: string | null
  audioUrl: string | null
  totalStages: number
  isLastStage: boolean
}

const route = useRoute()
const router = useRouter()
const gameStore = useGameStore()
const { connect, send, disconnect } = useWebSocket()

const roomId = route.params.roomId as string
const roomDetail = ref<any>(null)
const inputText = ref('')
const chatAreaRef = ref<HTMLElement | null>(null)
const systemMessages = ref<string[]>([])
const userScrolledUp = ref(false)

// Script drawer state
const scriptDrawerVisible = ref(false)
const stageContents = ref<StageContent[]>([])
const activeStageTab = ref('0')
const hasNewStage = ref(false)

onMounted(async () => {
  try {
    const { data } = await getRoom(roomId)
    roomDetail.value = data
    gameStore.setGameStatus(data.status)
  } catch (e) {
    console.error('加载房间信息失败', e)
    router.push('/home')
    return
  }

  // Load script content if game is already playing
  if (roomDetail.value?.status === 'PLAYING') {
    await fetchScriptContent()
  }

  connect(roomId, {
    onMessage: (msg: any) => {
      if (msg.type === 'SYSTEM') {
        systemMessages.value.push(msg.content)
        if (msg.content === '游戏结束！') {
          gameStore.setGameStatus('FINISHED')
        }
      } else {
        gameStore.addMessage(msg)
      }
      nextTick(() => scrollToBottom())
    },
    onStageUpdate: async (signal) => {
      // Update room detail display
      if (roomDetail.value) {
        roomDetail.value.currentStage = signal.currentStage
      }
      // Refresh script content
      await fetchScriptContent()
      // Show new-stage indicator
      hasNewStage.value = true
      // Auto-switch to the new tab
      activeStageTab.value = String(signal.currentStage)
    },
    onStreamChunk: (chunk: any) => {
      if (chunk.type === 'STREAM_CHUNK') {
        gameStore.appendStreamChunk(chunk.streamId, chunk.roleId, chunk.roleName, chunk.chunk, chunk.seq)
      } else if (chunk.type === 'STREAM_END') {
        gameStore.finalizeStream(chunk.streamId)
      }
      nextTick(() => scrollToBottom())
    }
  })
})

onUnmounted(() => {
  disconnect()
})

watch(() => gameStore.messages.length, () => {
  nextTick(() => scrollToBottom())
})

async function fetchScriptContent() {
  try {
    const { data } = await getMyScriptContent()
    stageContents.value = data
  } catch (e) {
    console.error('获取剧本内容失败', e)
  }
}

function openScriptDrawer() {
  scriptDrawerVisible.value = true
  hasNewStage.value = false
}

function formatContent(content: string): string {
  // Convert newlines to <br> for display
  return content.replace(/\n/g, '<br>')
}

/** Only auto-scroll if user is near the bottom (within threshold). */
function scrollToBottom() {
  if (chatAreaRef.value && !userScrolledUp.value) {
    chatAreaRef.value.scrollTop = chatAreaRef.value.scrollHeight
  }
}

function onChatScroll() {
  if (!chatAreaRef.value) return
  const el = chatAreaRef.value
  const threshold = 150
  userScrolledUp.value = el.scrollTop + el.clientHeight < el.scrollHeight - threshold
}

function handleSend() {
  const text = inputText.value.trim()
  if (!text) return
  send(roomId, text)
  inputText.value = ''
}

async function handleLeave() {
  try {
    await leaveRoom(roomId)
  } catch (e) {
    console.error('离开房间失败', e)
  }
  gameStore.clearGame()
  disconnect()
  router.push('/home')
}
</script>

<style scoped>
.game-play-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #1a1a2e;
  color: #e0e0e0;
}

.gp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: #16213e;
  border-bottom: 1px solid #0f3460;
}

.exit-btn {
  color: #e94560 !important;
}

.script-btn {
  color: #e0c97f !important;
  position: relative;
}

.stage-dot {
  position: absolute;
  top: 2px;
  right: -2px;
}

.room-info {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  color: #fff;
}

.chat-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chat-message {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.chat-message.is-self {
  flex-direction: row;
  justify-content: flex-end;
}

.msg-avatar {
  flex-shrink: 0;
}

.msg-body {
  max-width: 60%;
}

.msg-name {
  font-size: 12px;
  color: #a0a0b0;
  margin-bottom: 4px;
}

.msg-name.self {
  text-align: right;
}

.msg-bubble {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
}

.msg-bubble.left {
  background: #16213e;
  color: #e0e0e0;
  border-top-left-radius: 2px;
}

.msg-bubble.right {
  background: #e94560;
  color: #fff;
  border-top-right-radius: 2px;
}

.system-message {
  text-align: center;
  font-size: 12px;
  color: #666;
  padding: 4px 12px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 12px;
  align-self: center;
}

.input-area {
  display: flex;
  gap: 10px;
  padding: 16px 20px;
  background: #16213e;
  border-top: 1px solid #0f3460;
}

.input-area .el-input {
  flex: 1;
}

/* Script Drawer */
.script-empty {
  text-align: center;
  color: #888;
  padding: 40px 20px;
  font-size: 14px;
}

.script-tabs {
  height: 100%;
}

.stage-content {
  padding: 4px 0;
}

.stage-text {
  font-size: 14px;
  line-height: 1.8;
  color: #e0e0e0;
}

.stage-no-content {
  text-align: center;
  color: #666;
  padding: 20px;
  font-size: 13px;
}
</style>

<style>
/* Drawer theme override (unscoped to reach el-drawer internals) */
.script-drawer .el-drawer {
  background: #1a1a2e !important;
  color: #e0e0e0;
}
.script-drawer .el-drawer__header {
  color: #e0c97f;
  border-bottom: 1px solid #0f3460;
  margin-bottom: 0;
  padding-bottom: 16px;
}
.script-drawer .el-tabs__item {
  color: #a0a0b0;
}
.script-drawer .el-tabs__item.is-active {
  color: #e0c97f;
}
.script-drawer .el-tabs__active-bar {
  background-color: #e0c97f;
}
</style>
