import http from './axios'

export function createRoom() {
  return http.post('/rooms')
}

export function getRoom(roomId: string) {
  return http.get(`/rooms/${roomId}`)
}

export function selectScript(roomId: string, scriptId: string) {
  return http.put(`/rooms/${roomId}/script`, { scriptId })
}

export function getRoles(roomId: string) {
  return http.get(`/rooms/${roomId}/roles`)
}

export function selectRole(roomId: string, roleId: string) {
  return http.put(`/rooms/${roomId}/role`, { roleId })
}

export function startGame(roomId: string) {
  return http.post(`/rooms/${roomId}/start`)
}

export function leaveRoom(roomId: string) {
  return http.post(`/rooms/${roomId}/leave`)
}
