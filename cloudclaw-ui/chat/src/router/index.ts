import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory('/'),
  routes: [
    { path: '/login', name: 'Login', component: () => import('@/views/Login.vue') },
    {
      path: '/',
      component: () => import('@/views/ChatLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        // Chat is the default view embedded in ChatLayout
        { path: '', name: 'ChatHome', component: { template: '<span/>' }, meta: { title: 'nav.chat' } },
        { path: 'memory', name: 'Memory', component: () => import('@/views/MemoryPanel.vue'), meta: { title: 'Memory', requiresAuth: true } },
        // Admin routes
        { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/Dashboard.vue'), meta: { title: 'dashboard.title', requiresAdmin: true } },
        { path: 'users', name: 'Users', component: () => import('@/views/UserManage.vue'), meta: { title: 'nav.user', requiresAdmin: true } },
        { path: 'agents', name: 'Agents', component: () => import('@/views/AgentManage.vue'), meta: { title: 'nav.agent', requiresAdmin: true } },
        { path: 'mcp-servers', name: 'McpServers', component: () => import('@/views/McpServerManage.vue'), meta: { title: 'MCP Server', requiresAdmin: true } },
        { path: 'skills', name: 'Skills', component: () => import('@/views/SkillManage.vue'), meta: { title: 'nav.skill', requiresAdmin: true } },
        { path: 'llm', name: 'Llm', component: () => import('@/views/LlmManage.vue'), meta: { title: 'nav.llm', requiresAdmin: true } },
        { path: 'sandboxes', name: 'Sandboxes', component: () => import('@/views/SandboxManage.vue'), meta: { title: '\u6C99\u7BB1\u7BA1\u7406', requiresAdmin: true } },
        { path: 'monitor', name: 'Monitor', component: () => import('@/views/SystemMonitor.vue'), meta: { title: 'nav.monitor', requiresAdmin: true } }
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
  // Check if token is still valid by trying a lightweight API call
  if (token && to.meta.requiresAuth) {
    try {
      const res = await fetch('/api/v1/agents', {
        headers: { 'Authorization': `Bearer ${token}` }
      })
      if (res.status === 401) {
        // Try refresh
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
        // Both tokens expired
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
    const role = localStorage.getItem('user_role')
    if (role !== 'ADMIN') {
      next('/')
      return
    }
  }
  next()
})

export default router
