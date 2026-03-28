import http from './axios'

export function getRandomScripts() {
  return http.get('/scripts/random')
}
