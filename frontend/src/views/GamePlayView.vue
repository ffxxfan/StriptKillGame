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
      <div style="width: 80px"></div>
    </header>

    <!-- Chat Area -->
    <div class="chat-area" ref="chatAreaRef">
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getRoom, leaveRoom } from '../api/room'
import { useGameStore } from '../stores/game'
import { useWebSocket } from '../composables/useWebSocket'

const route = useRoute()
const router = useRouter()
const gameStore = useGameStore()
const { connect, send, disconnect } = useWebSocket()

const roomId = route.params.roomId as string
const roomDetail = ref<any>(null)
const inputText = ref('')
const chatAreaRef = ref<HTMLElement | null>(null)
const systemMessages = ref<string[]>([])

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

  connect(roomId, (msg: any) => {
    if (msg.type === 'SYSTEM') {
      systemMessages.value.push(msg.content)
      if (msg.content === '游戏结束！') {
        gameStore.setGameStatus('FINISHED')
      }
    } else {
      gameStore.addMessage(msg)
    }
    nextTick(() => scrollToBottom())
  })
})

onUnmounted(() => {
  disconnect()
})

watch(() => gameStore.messages.length, () => {
  nextTick(() => scrollToBottom())
})

function scrollToBottom() {
  if (chatAreaRef.value) {
    chatAreaRef.value.scrollTop = chatAreaRef.value.scrollHeight
  }
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
</style>
