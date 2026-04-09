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
  isPrivate?: boolean   // true for private messages (clues, votes)
  privateLabel?: string // e.g., "仅你可见"
}

// Buffered stream waiting to be displayed
interface BufferedStream {
  roleId: string
  roleName: string
  chunks: Map<number, string> // seq → chunk
  ended: boolean
}

// Active stream currently being displayed
interface ActiveStreamState {
  streamId: string
  messageIndex: number  // index in messages array
  chunks: string[]      // ordered chunks applied so far
}

export const useGameStore = defineStore('game', () => {
  const roomId = ref<string | null>(null)
  const scriptId = ref<string | null>(null)
  const myRoleId = ref<string | null>(null)
  const messages = ref<ChatMessage[]>([])
  const gameStatus = ref<'WAITING' | 'PLAYING' | 'FINISHED'>('WAITING')

  // Stream queue: ensures one stream displays at a time
  let activeStream: ActiveStreamState | null = null
  const waitingQueue: string[] = []  // streamIds in arrival order
  const bufferedStreams = new Map<string, BufferedStream>()

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

  function addPrivateMessage(msg: ChatMessage) {
    msg.isPrivate = true
    msg.privateLabel = msg.privateLabel || '仅你可见'
    messages.value.push(msg)
  }

  /**
   * Handle a streaming chunk. If this stream is active, apply immediately.
   * Otherwise buffer it — it will be flushed when prior streams finish.
   */
  function appendStreamChunk(streamId: string, roleId: string, roleName: string, chunk: string, seq: number) {
    // Case 1: this IS the active stream — apply directly
    if (activeStream && activeStream.streamId === streamId) {
      applyChunk(activeStream, chunk, seq)
      return
    }

    // Case 2: no active stream — activate this one
    if (!activeStream) {
      activeStream = createActiveStream(streamId, roleId, roleName)
      applyChunk(activeStream, chunk, seq)
      return
    }

    // Case 3: another stream is active — buffer this chunk
    let buf = bufferedStreams.get(streamId)
    if (!buf) {
      buf = { roleId, roleName, chunks: new Map(), ended: false }
      bufferedStreams.set(streamId, buf)
      waitingQueue.push(streamId)
    }
    buf.chunks.set(seq, chunk)
  }

  /**
   * Mark a stream as complete. If it's the active stream, activate next in queue.
   */
  function finalizeStream(streamId: string) {
    if (activeStream && activeStream.streamId === streamId) {
      // Current stream done — activate next
      activeStream = null
      activateNextStream()
      return
    }

    // Not yet active — mark as ended in buffer
    const buf = bufferedStreams.get(streamId)
    if (buf) {
      buf.ended = true
    }
  }

  // --- Internal helpers ---

  function createActiveStream(streamId: string, roleId: string, roleName: string): ActiveStreamState {
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
    return {
      streamId,
      messageIndex: messages.value.length - 1,
      chunks: []
    }
  }

  function applyChunk(state: ActiveStreamState, chunk: string, seq: number) {
    state.chunks[seq] = chunk
    const msg = messages.value[state.messageIndex]
    if (msg) {
      msg.content = state.chunks.filter(c => c !== undefined).join('')
    }
  }

  /**
   * Activate the next buffered stream: create its message, flush all
   * buffered chunks at once, and if it already ended, move on.
   */
  function activateNextStream() {
    while (waitingQueue.length > 0) {
      const nextId = waitingQueue.shift()!
      const buf = bufferedStreams.get(nextId)
      if (!buf) continue

      bufferedStreams.delete(nextId)

      // Create message and flush all buffered chunks
      activeStream = createActiveStream(nextId, buf.roleId, buf.roleName)
      const sortedSeqs = Array.from(buf.chunks.keys()).sort((a, b) => a - b)
      for (const seq of sortedSeqs) {
        applyChunk(activeStream, buf.chunks.get(seq)!, seq)
      }

      if (buf.ended) {
        // This stream already finished — continue to next
        activeStream = null
        continue
      }

      // Stream still receiving chunks — it's now the active one
      return
    }
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
    activeStream = null
    waitingQueue.length = 0
    bufferedStreams.clear()
  }

  return {
    roomId, scriptId, myRoleId, messages, gameStatus,
    setRoom, setScript, setMyRole, addMessage, addPrivateMessage,
    appendStreamChunk, finalizeStream,
    setGameStatus, clearGame
  }
})
