import { Client } from '@stomp/stompjs'
import { useAuthStore } from '../stores/auth'
import { ref } from 'vue'

let stompClient: Client | null = null
const connected = ref(false)

export function useWebSocket() {
  const authStore = useAuthStore()

  function connect(roomId: string, onMessage: (msg: any) => void) {
    if (stompClient?.active) {
      stompClient.deactivate()
    }

    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const host = window.location.host

    stompClient = new Client({
      brokerURL: `${protocol}://${host}/ws`,
      connectHeaders: {
        Authorization: `Bearer ${authStore.accessToken}`
      },
      onConnect: () => {
        connected.value = true
        stompClient!.subscribe(`/topic/room.${roomId}`, (frame) => {
          const body = JSON.parse(frame.body)
          onMessage(body)
        })
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
