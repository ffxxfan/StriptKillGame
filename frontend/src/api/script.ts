import http from './axios'

export function getRandomScripts() {
  return http.get('/scripts/random')
}

export function getMyScriptContent() {
  return http.get('/scripts/my-content')
}
