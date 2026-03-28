import axios from 'axios'
import { useAuthStore } from '../stores/auth'
import router from '../router'

/**
 * Axios 实例：统一配置 baseURL、拦截器
 *
 * 请求拦截器：自动附加 Authorization Header
 * 响应拦截器：401 时自动尝试 Refresh Token，失败则跳转登录页
 */
const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

/** 标记是否正在刷新 Token，防止并发刷新 */
let isRefreshing = false
/** 等待 Token 刷新完成的请求队列 */
let pendingRequests: Array<(token: string) => void> = []

// ======================== 请求拦截器 ========================
http.interceptors.request.use(
  (config) => {
    const authStore = useAuthStore()
    if (authStore.accessToken) {
      config.headers.Authorization = `Bearer ${authStore.accessToken}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ======================== 响应拦截器 ========================
http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config
    const authStore = useAuthStore()

    // 非 401 错误或已重试过，直接拒绝
    if (error.response?.status !== 401 || originalRequest._retry) {
      return Promise.reject(error)
    }

    // 无 Refresh Token，跳转登录
    if (!authStore.refreshToken) {
      authStore.clearAuth()
      router.push('/login')
      return Promise.reject(error)
    }

    // 正在刷新中，将请求加入等待队列
    if (isRefreshing) {
      return new Promise((resolve) => {
        pendingRequests.push((token: string) => {
          originalRequest.headers.Authorization = `Bearer ${token}`
          resolve(http(originalRequest))
        })
      })
    }

    // 开始刷新 Token
    originalRequest._retry = true
    isRefreshing = true

    try {
      const response = await axios.post('/api/auth/refresh', {
        refreshToken: authStore.refreshToken,
      })
      const { accessToken, refreshToken } = response.data
      authStore.setTokens(accessToken, refreshToken)

      // 执行等待队列中的请求
      pendingRequests.forEach((cb) => cb(accessToken))
      pendingRequests = []

      // 重试原始请求
      originalRequest.headers.Authorization = `Bearer ${accessToken}`
      return http(originalRequest)
    } catch (refreshError) {
      // Refresh Token 也失效，清除状态并跳转登录
      authStore.clearAuth()
      router.push('/login')
      return Promise.reject(refreshError)
    } finally {
      isRefreshing = false
    }
  }
)

export default http
