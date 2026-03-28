import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * 用户信息接口
 */
export interface UserInfo {
  id: string
  username: string
  nickname: string
  avatarUrl: string | null
  createdAt: string
}

/**
 * 认证 Store：管理 Token 和用户信息
 *
 * Token 持久化到 localStorage，页面刷新后自动恢复。
 */
export const useAuthStore = defineStore('auth', () => {
  // ======================== State ========================

  const accessToken = ref<string | null>(localStorage.getItem('accessToken'))
  const refreshToken = ref<string | null>(localStorage.getItem('refreshToken'))
  const userInfo = ref<UserInfo | null>(null)

  // ======================== Getters ========================

  /** 是否已登录（有 Access Token） */
  const isLoggedIn = computed(() => !!accessToken.value)

  // ======================== Actions ========================

  /**
   * 设置 Token 对（登录/刷新成功后调用）
   */
  function setTokens(access: string, refresh: string) {
    accessToken.value = access
    refreshToken.value = refresh
    localStorage.setItem('accessToken', access)
    localStorage.setItem('refreshToken', refresh)
  }

  /**
   * 设置用户信息
   */
  function setUserInfo(info: UserInfo) {
    userInfo.value = info
  }

  /**
   * 清除所有认证状态（登出时调用）
   */
  function clearAuth() {
    accessToken.value = null
    refreshToken.value = null
    userInfo.value = null
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
  }

  return {
    accessToken,
    refreshToken,
    userInfo,
    isLoggedIn,
    setTokens,
    setUserInfo,
    clearAuth,
  }
})
