import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface ChatMessage {
  messageId: string
  senderRoleId: string
  senderRoleName: string
  senderAvatar: string
  isAi: boolean
  content: string
  timestamp: string
}

// Track in-flight streaming messages by streamId
interface StreamState {
  messageIndex: number  // index in messages array
  chunks: string[]      // ordered chunks
}

export const useGameStore = defineStore('game', () => {
  const roomId = ref<string | null>(null)
  const scriptId = ref<string | null>(null)
  const myRoleId = ref<string | null>(null)
  const messages = ref<ChatMessage[]>([])
  const gameStatus = ref<'WAITING' | 'PLAYING' | 'FINISHED'>('WAITING')

  // Active streams
  const activeStreams = new Map<string, StreamState>()

  function setRoom(id: string) {
    roomId.value = id
  }

  function setScript(id: string) {
    scriptId.value = id
  }

  function setMyRole(id: string) {
    myRoleId.value = id
  }

  function addMessage(msg: ChatMessage) {
    messages.value.push(msg)
  }

  /**
   * Append a streaming chunk. Creates a placeholder message on first chunk,
   * then updates its content as more chunks arrive (ordered by seq).
   */
  function appendStreamChunk(streamId: string, roleId: string, roleName: string, chunk: string, seq: number) {
    let state = activeStreams.get(streamId)

    if (!state) {
      // First chunk — create placeholder message
      const placeholder: ChatMessage = {
        messageId: `stream-${streamId}`,
        senderRoleId: roleId,
        senderRoleName: roleName || '',
        senderAvatar: '',
        isAi: true,
        content: '',
        timestamp: new Date().toISOString()
      }
      messages.value.push(placeholder)
      state = {
        messageIndex: messages.value.length - 1,
        chunks: []
      }
      activeStreams.set(streamId, state)
    }

    // Store chunk at correct position (seq-based ordering)
    state.chunks[seq] = chunk

    // Rebuild content from ordered chunks
    const msg = messages.value[state.messageIndex]
    if (msg) {
      msg.content = state.chunks.filter(c => c !== undefined).join('')
    }
  }

  /**
   * Mark a stream as complete. Clean up tracking state.
   * The full message stored via REST will NOT be re-added (storeAiMessage doesn't broadcast).
   */
  function finalizeStream(streamId: string) {
    activeStreams.delete(streamId)
  }

  function setGameStatus(status: 'WAITING' | 'PLAYING' | 'FINISHED') {
    gameStatus.value = status
  }

  function clearGame() {
    roomId.value = null
    scriptId.value = null
    myRoleId.value = null
    messages.value = []
    gameStatus.value = 'WAITING'
    activeStreams.clear()
  }

  return {
    roomId, scriptId, myRoleId, messages, gameStatus,
    setRoom, setScript, setMyRole, addMessage,
    appendStreamChunk, finalizeStream,
    setGameStatus, clearGame
  }
})
