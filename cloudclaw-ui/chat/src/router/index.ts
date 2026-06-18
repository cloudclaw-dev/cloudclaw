import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory('/'),
  routes: [
    { path: '/login', name: 'Login', component: () => import('@/views/Login.vue') },
    { path: '/register', name: 'Register', component: () => import('@/views/Register.vue') },
    {
      path: '/',
      component: () => import('@/views/ChatLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', name: 'ChatHome', component: { template: '<span/>' }, meta: { title: 'nav.chat' } },
        { path: 'agents', name: 'AgentGallery', component: () => import('@/views/AgentGallery.vue'), meta: { title: 'nav.agentGallery' } },
        { path: 'memory', name: 'Memory', component: () => import('@/views/MemoryPanel.vue'), meta: { title: 'Memory', requiresAuth: true } },
        { path: 'profile', name: 'Profile', component: () => import('@/views/Profile.vue'), meta: { title: 'nav.profile', requiresAuth: true } },
      ]
    },
    {
      path: '/admin',
      component: () => import('@/views/AdminLayout.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
      children: [
        { path: '', redirect: '/admin/dashboard' },
        { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: 'dashboard.title' } },
        { path: 'agents', name: 'Agents', component: () => import('@/views/AgentManage.vue'), meta: { title: 'nav.agent' } },
        { path: 'mcp', name: 'McpServers', component: () => import('@/views/McpServerManage.vue'), meta: { title: 'MCP Server' } },
        { path: 'skills', name: 'Skills', component: () => import('@/views/SkillManage.vue'), meta: { title: 'nav.skill' } },
        { path: 'llm', name: 'Llm', component: () => import('@/views/LlmManage.vue'), meta: { title: 'nav.llm' } },
        { path: 'users', name: 'Users', component: () => import('@/views/UserManage.vue'), meta: { title: 'nav.user' } },
        { path: 'sandboxes', name: 'Sandboxes', component: () => import('@/views/SandboxManage.vue'), meta: { title: 'Sandbox' } },
        { path: 'monitor', name: 'Monitor', component: () => import('@/views/SystemMonitor.vue'), meta: { title: 'nav.monitor' } },
        { path: 'channel', name: 'ChannelConfig', component: () => import('@/views/ChannelConfig.vue'), meta: { title: 'nav.channel' } },
      ]
    }
  ]
})

router.beforeEach(async (to, _from, next) => {
  const token = localStorage.getItem('access_token')
  if (to.meta.requiresAuth && !token) {
    next('/login')
    return
  }
  if (to.path === '/login') {
    next()
    return
  }
  if (token && to.meta.requiresAuth) {
    try {
      const res = await fetch('/api/v1/agents', {
        headers: { 'Authorization': `Bearer ${token}` }
      })
      if (res.status === 401) {
        const refreshToken = localStorage.getItem('refresh_token')
        if (refreshToken) {
          try {
            const refreshRes = await fetch('/api/v1/auth/refresh', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ refreshToken })
            })
            if (refreshRes.ok) {
              const data = await refreshRes.json()
              const newToken = data.data?.accessToken || data.accessToken
              if (newToken) {
                localStorage.setItem('access_token', newToken)
                if (data.data?.refreshToken || data.refreshToken) {
                  localStorage.setItem('refresh_token', data.data?.refreshToken || data.refreshToken)
                }
                next()
                return
              }
            }
          } catch {}
        }
        localStorage.removeItem('access_token')
        localStorage.removeItem('refresh_token')
        next('/login')
        return
      }
    } catch {
      // Network error, let page load
    }
  }
  if (to.meta.requiresAdmin) {
    const token = localStorage.getItem('access_token')
    let role: string | null = null
    if (token) {
      try {
        const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
        const jsonPayload = decodeURIComponent(atob(base64).split('').map((ch: string) => '%' + ('00' + ch.charCodeAt(0).toString(16)).slice(-2)).join(''))
        role = JSON.parse(jsonPayload).role || null
      } catch { /* invalid token */ }
    }
    if (role !== 'admin') {
      next('/')
      return
    }
  }
  next()
})

export default router
