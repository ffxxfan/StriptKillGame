import http from './axios'

/**
 * 认证相关 API 封装
 */

/** 登录 */
export function login(username: string, password: string) {
  return http.post('/auth/login', { username, password })
}

/** 注册 */
export function register(username: string, password: string, nickname?: string) {
  return http.post('/auth/register', { username, password, nickname })
}

/** 登出 */
export function logout() {
  return http.post('/auth/logout')
}

/** 刷新 Token */
export function refreshToken(token: string) {
  return http.post('/auth/refresh', { refreshToken: token })
}

/** 获取当前用户信息 */
export function getUserInfo() {
  return http.get('/auth/info')
}

/** 修改密码 */
export function changePassword(oldPassword: string, newPassword: string) {
  return http.put('/auth/password', { oldPassword, newPassword })
}
