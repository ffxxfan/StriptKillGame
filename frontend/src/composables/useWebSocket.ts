import { Client } from '@stomp/stompjs'
import { useAuthStore } from '../stores/auth'
import { ref } from 'vue'

let stompClient: Client | null = null
const connected = ref(false)

export interface WebSocketCallbacks {
  onMessage: (msg: any) => void
  onStageUpdate?: (signal: { currentStage: number; stageTitle: string }) => void
  onStreamChunk?: (chunk: any) => void
  onPrivateMessage?: (data: any) => void
}

export function useWebSocket() {
  const authStore = useAuthStore()

  function connect(roomId: string, callbacks: WebSocketCallbacks | ((msg: any) => void)) {
    if (stompClient?.active) {
      stompClient.deactivate()
    }

    // Support both legacy (single callback) and new (callbacks object) signatures
    const cbs: WebSocketCallbacks = typeof callbacks === 'function'
      ? { onMessage: callbacks }
      : callbacks

    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const host = window.location.host

    stompClient = new Client({
      brokerURL: `${protocol}://${host}/ws`,
      connectHeaders: {
        Authorization: `Bearer ${authStore.accessToken}`
      },
      onConnect: () => {
        connected.value = true

        // Main room topic (chat messages, system messages, STAGE_UPDATE signals)
        stompClient!.subscribe(`/topic/room.${roomId}`, (frame) => {
          const body = JSON.parse(frame.body)
          if (body.type === 'STAGE_UPDATE' && cbs.onStageUpdate) {
            cbs.onStageUpdate(body)
          } else {
            cbs.onMessage(body)
          }
        })

        // Streaming topic (AI response chunks)
        if (cbs.onStreamChunk) {
          stompClient!.subscribe(`/topic/room.${roomId}.stream`, (frame) => {
            const body = JSON.parse(frame.body)
            cbs.onStreamChunk!(body)
          })
        }

        // Private channel (clues, vote confirmations)
        if (cbs.onPrivateMessage) {
          stompClient!.subscribe(`/user/queue/room.${roomId}.private`, (frame) => {
            const body = JSON.parse(frame.body)
            cbs.onPrivateMessage!(body)
          })
        }
      },
      onDisconnect: () => {
        connected.value = false
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers['message'])
        connected.value = false
      }
    })

    stompClient.activate()
  }

  function send(roomId: string, content: string) {
    if (!stompClient?.active) return
    stompClient.publish({
      destination: `/app/chat.${roomId}`,
      body: JSON.stringify({ content })
    })
  }

  function disconnect() {
    if (stompClient?.active) {
      stompClient.deactivate()
    }
    connected.value = false
  }

  return { connect, send, disconnect, connected }
}
