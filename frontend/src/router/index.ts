import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('../views/LoginView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/',
      name: 'Home',
      component: () => import('../views/HomeView.vue'),
      meta: { requiresAuth: true },
      alias: '/home',
    },
    {
      path: '/profile',
      name: 'Profile',
      component: () => import('../views/ProfileView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/game/:roomId/scripts',
      name: 'ScriptWall',
      component: () => import('../views/ScriptWallView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/game/:roomId/roles',
      name: 'RoleWall',
      component: () => import('../views/RoleWallView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/game/:roomId/play',
      name: 'GamePlay',
      component: () => import('../views/GamePlayView.vue'),
      meta: { requiresAuth: true },
    },
  ],
})

/**
 * 路由守卫：
 * - 需要认证的页面：未登录跳 /login
 * - 登录页：已登录跳 /
 */
router.beforeEach((to, _from, next) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    next('/login')
  } else if (to.path === '/login' && authStore.isLoggedIn) {
    next('/')
  } else {
    next()
  }
})

export default router
