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

export const useGameStore = defineStore('game', () => {
  const roomId = ref<string | null>(null)
  const scriptId = ref<string | null>(null)
  const myRoleId = ref<string | null>(null)
  const messages = ref<ChatMessage[]>([])
  const gameStatus = ref<'WAITING' | 'PLAYING' | 'FINISHED'>('WAITING')

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

  function setGameStatus(status: 'WAITING' | 'PLAYING' | 'FINISHED') {
    gameStatus.value = status
  }

  function clearGame() {
    roomId.value = null
    scriptId.value = null
    myRoleId.value = null
    messages.value = []
    gameStatus.value = 'WAITING'
  }

  return {
    roomId, scriptId, myRoleId, messages, gameStatus,
    setRoom, setScript, setMyRole, addMessage, setGameStatus, clearGame
  }
})
