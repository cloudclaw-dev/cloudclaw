import api from './index'

// ===== Type Definitions =====

export interface ChatChunk {
  content: string
  toolCall: boolean
  done: boolean
  type?: string
  targetAgent?: string
  contextStats?: ContextStats
  errorCode?: number
  errorI18nKey?: string
  errorDetail?: string
}

export interface ContextStats {
  totalTokens: number
  historyMessages: number
  toolCallCount: number
  maxTokens: number
  usagePercent: number
  systemTokens: number
  historyTokens: number
  memoryTokens: number
  userMessageTokens: number
  toolResultTokens: number
}

export interface AsyncChatResult {
  userMessageId: string
  assistantMessageId: string
  status: string
}

export interface PollResult {
  messages: MessageVo[]
  hasMore: boolean
}

export interface MessageVo {
  id: string
  role: string
  content: string
  status: string
  createdAt: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  userId: string
  username: string
  role: string
}

export interface CreateUserRequest {
  username: string
  password: string
  email?: string
  role: string
}

export interface CreateAgentRequest {
  name: string
  description?: string
  systemPrompt: string
  modelId: string
  temperature?: number
  maxTokens?: number
}

export interface CreateLlmProviderRequest {
  name: string
  displayName?: string
  apiBase: string
  providerType: string
}

export interface CreateLlmModelRequest {
  id: string
  providerId: string
  modelName: string
  displayName?: string
  contextWindow?: number
  maxOutput?: number
}

export interface CreateLlmCredentialRequest {
  providerId: string
  name: string
  apiKey: string
}


export const authApi = {
  login: (data: { username: string; password: string }) => api.post('/v1/auth/login', data),
  refresh: (refreshToken: string) => api.post('/v1/auth/refresh', { refreshToken })
}

export const sessionApi = {
  list: (page = 1, size = 20, agentId?: string) => api.get('/v1/sessions', { params: { page, size, ...(agentId ? { agentId } : {}) } }),
  create: (data: { agentId: string; title?: string }) => api.post('/v1/sessions', data),
  get: (id: string) => api.get(`/v1/sessions/${id}`),
  delete: (id: string) => api.delete(`/v1/sessions/${id}`)
}

export const messageApi = {
  history: (sessionId: string, page = 1, size = 50) => api.get(`/v1/sessions/${sessionId}/messages`, { params: { page, size } })
}

export const agentApi = {
  list: () => api.get('/v1/agents'),
  get: (id: string) => api.get(`/v1/agents/${id}`)
}

export const memoryApi = {
  // Profile
  listProfile: () => api.get('/v1/memory/profile'),
  replaceProfile: (data: { profile?: string }) => api.put('/v1/memory/profile', data),
  addProfile: (content: string) => api.post('/v1/memory/profile', { content }),
  updateProfile: (id: string, content: string) => api.put(`/v1/memory/profile/${id}`, { content }),
  deleteProfile: (id: string) => api.delete(`/v1/memory/profile/${id}`),
  // Session
  listSessions: (sessionId?: string) => api.get('/v1/memory/sessions', { params: { sessionId } }),
  deleteSession: (id: string) => api.delete(`/v1/memory/sessions/${id}`),
}

export const userApi = {
  me: () => api.get('/v1/me')
}

// ===== Admin API =====
export const adminApi = {
  // Users
  getUsers: (params?: Record<string, unknown>) => api.get('/admin/users', { params }),
  createUser: (data: CreateUserRequest) => api.post('/admin/users', data),
  updateUser: (id: string, data: any) => api.put(`/admin/users/${id}`, data),
  deleteUser: (id: string) => api.delete(`/admin/users/${id}`),

  // Agents
  getAgents: (params?: any) => api.get('/admin/agents', { params }),
  getAgent: (id: string) => api.get(`/admin/agents/${id}`),
  createAgent: (data: any) => api.post('/admin/agents', data),
  updateAgent: (id: string, data: any) => api.put(`/admin/agents/${id}`, data),
  deleteAgent: (id: string) => api.delete(`/admin/agents/${id}`),

  // MCP Servers
  getMcpServers: (params?: any) => api.get('/admin/mcp-servers', { params }),
  createMcpServer: (data: any) => api.post('/admin/mcp-servers', data),
  updateMcpServer: (id: string, data: any) => api.put(`/admin/mcp-servers/${id}`, data),
  deleteMcpServer: (id: string) => api.delete(`/admin/mcp-servers/${id}`),
  testMcpServer: (id: string) => api.post(`/admin/mcp-servers/${id}/test`),

  // Skills
  getSkills: (params?: any) => api.get('/admin/skills', { params }),
  uploadSkillZip: (formData: FormData) => api.post('/admin/skills/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } }),
  updateSkill: (id: string, data: any) => api.put(`/admin/skills/${id}`, data),
  deleteSkill: (id: string) => api.delete(`/admin/skills/${id}`),
  getSkillFiles: (skillId: string) => api.get(`/admin/skills/${skillId}/files`),
  getSkillFile: (skillId: string, path: string) => api.get(`/admin/skills/${skillId}/files`, { params: { path } }),
  saveSkillFile: (skillId: string, data: { path: string; content: string }) => api.put(`/admin/skills/${skillId}/files`, data),
  deleteSkillFile: (skillId: string, path: string) => api.delete(`/admin/skills/${skillId}/files`, { params: { path } }),

  // LLM
  getLlmProviders: () => api.get('/admin/llm/providers'),
  createLlmProvider: (data: any) => api.post('/admin/llm/providers', data),
  updateLlmProvider: (id: string, data: any) => api.put(`/admin/llm/providers/${id}`, data),
  deleteLlmProvider: (id: string) => api.delete(`/admin/llm/providers/${id}`),
  getLlmModels: () => api.get('/admin/llm/models'),
  createLlmModel: (data: any) => api.post('/admin/llm/models', data),
  updateLlmModel: (id: string, data: any) => api.put(`/admin/llm/models/${id}`, data),
  deleteLlmModel: (id: string) => api.delete(`/admin/llm/models/${id}`),
  getLlmCredentials: (providerId?: string) => api.get('/admin/llm/credentials', { params: providerId ? { providerId } : {} }),
  createLlmCredential: (data: any) => api.post('/admin/llm/credentials', data),
  updateLlmCredential: (id: string, data: any) => api.put(`/admin/llm/credentials/${id}`, data),
  deleteLlmCredential: (id: string) => api.delete(`/admin/llm/credentials/${id}`),

  // Stats
  getUsageStats: (params?: any) => api.get('/admin/stats/usage', { params }),
  getSessionStats: (params?: any) => api.get('/admin/stats/sessions', { params }),
  getLogs: (params?: any) => api.get('/admin/logs', { params }),

  // Prompt Logs
  promptLogs: (params?: any) => api.get('/admin/prompt-logs', { params }),

  // Sandbox
  getSandboxes: (params?: any) => api.get('/admin/sandboxes/sessions', { params }),
  forceCloseSandbox: (id: string) => api.delete(`/admin/sandboxes/sessions/${id}`),
  cleanOrphanSandboxes: () => api.delete('/admin/sandboxes/sessions/orphans'),

  // Sandbox Providers
  getSandboxProviders: () => api.get('/admin/sandboxes/providers'),
  createSandboxProvider: (data: any) => api.post('/admin/sandboxes/providers', data),
  updateSandboxProvider: (id: string, data: any) => api.put(`/admin/sandboxes/providers/${id}`, data),
  deleteSandboxProvider: (id: string) => api.delete(`/admin/sandboxes/providers/${id}`)
}

export async function sendChatMessage(
  sessionId: string,
  message: string,
  onChunk: (chunk: any) => void,
  onDone: (contextStats?: any) => void,
  onError: (error: any) => void,
  signal?: AbortSignal
) {
  // TODO: [Security] JWT stored in localStorage is vulnerable to XSS attacks.
  // Consider migrating to httpOnly cookies for production deployments.
  const token = localStorage.getItem('access_token')
  try {
    const response = await fetch(`/api/v1/sessions/${sessionId}/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({ message }),
      signal
    })

    if (!response.ok) {
      if (response.status === 401) {
        localStorage.removeItem('access_token')
        localStorage.removeItem('refresh_token')
        window.location.href = '/login'
      }
      throw new Error(`HTTP ${response.status}`)
    }

    const reader = response.body?.getReader()
    if (!reader) throw new Error('No reader')

    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('data:')) {
          const data = line.slice(5).trim()
          if (data) {
            try {
              const chunk = JSON.parse(data)
              if (chunk.done) {
                onDone(chunk.contextStats || undefined)
              } else {
                onChunk(chunk)
              }
            } catch (e) {
              // skip non-JSON lines
            }
          }
        }
      }
    }
    onDone()
  } catch (error) {
    onError(error)
  }
}

// ===== Async Chat API (Poll Mode) =====

export async function sendMessageAsync(
  sessionId: string,
  message: string,
  requestId?: string
): Promise<any> {
  const res = await api.post(`/v1/sessions/${sessionId}/send`, { message, requestId })
  return res.data
}

export async function pollMessages(
  sessionId: string,
  afterMessageId?: string
): Promise<any> {
  const res = await api.get(`/v1/sessions/${sessionId}/messages/poll`, {
    params: { after: afterMessageId || undefined }
  })
  return res.data
}
