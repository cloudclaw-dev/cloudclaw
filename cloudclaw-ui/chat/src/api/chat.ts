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
  register: (data: { username: string; email: string; password: string }) => api.post('/v1/auth/register', data),
  refresh: (refreshToken: string) => api.post('/v1/auth/refresh', { refreshToken })
}

export const sessionApi = {
  list: (page = 1, size = 20, agentId?: string) => api.get('/v1/sessions', { params: { page, size, ...(agentId ? { agentId } : {}) } }),
  create: (data: { agentId: string; title?: string }) => api.post('/v1/sessions', data),
  get: (id: string) => api.get(`/v1/sessions/${id}`),
  delete: (id: string) => api.delete(`/v1/sessions/${id}`),
  rename: (id: string, title: string) => api.patch(`/v1/sessions/${id}`, { title })
}

export const messageApi = {
  history: (sessionId: string, page = 1, size = 50) => api.get(`/v1/sessions/${sessionId}/messages`, { params: { page, size } })
}

export const agentApi = {
  list: () => api.get('/v1/agents'),
  featured: () => api.get('/v1/agents/featured'),
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

// ===== WebSocket Chat Implementation =====

interface WebSocketChatConfig {
  /** WebSocket endpoint, defaults to current host's /ws/chat */
  url?: string
  onConnected?: (userId: string) => void
  onClosed?: (event: CloseEvent) => void
  onError?: (error: Event) => void
}

/** Client-to-server message types */
interface WsChatMessage {
  action: 'chat'
  sessionId: string
  message: string
  requestId?: string
}

interface WsCancelMessage {
  action: 'cancel'
  sessionId: string
}

interface WsPingMessage {
  action: 'ping'
}

/** Server-to-client control messages */
interface WsConnectedMessage {
  type: 'connected'
  userId: string
}

interface WsErrorMessage {
  type: 'error'
  errorCode: number
  errorDetail: string
  done?: boolean
}

/**
 * WebSocket chat client with heartbeat, auto-reconnect, and cancel support.
 */
export class WebSocketChatClient {
  private ws: WebSocket | null = null
  private token: string
  private url: string
  private manualClose = false
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null
  private reconnectDelay = 1000
  private readonly maxReconnectDelay = 30000
  private readonly heartbeatInterval = 30000

  private onConnected?: (userId: string) => void
  private onClosed?: (event: CloseEvent) => void
  private onError?: (error: Event) => void

  constructor(token: string, config: WebSocketChatConfig = {}) {
    this.token = token
    const baseUrl = config.url || this.getDefaultWsUrl()
    this.url = `${baseUrl}?token=${encodeURIComponent(token)}`

    if (config.onConnected) this.onConnected = config.onConnected
    if (config.onClosed) this.onClosed = config.onClosed
    if (config.onError) this.onError = config.onError
  }

  private getDefaultWsUrl(): string {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = window.location.host
    return `${protocol}//${host}/ws/chat`
  }

  connect(): void {
    if (this.ws && (this.ws.readyState === WebSocket.CONNECTING || this.ws.readyState === WebSocket.OPEN)) {
      return
    }
    try {
      this.ws = new WebSocket(this.url)
      this.setupEventHandlers()
    } catch (error) {
      console.error('[WS] Failed to create WebSocket:', error)
      this.scheduleReconnect()
    }
  }

  private setupEventHandlers(): void {
    if (!this.ws) return

    this.ws.onopen = () => {
      this.reconnectDelay = 1000
      this.startHeartbeat()
      if (this.reconnectTimer) {
        clearTimeout(this.reconnectTimer)
        this.reconnectTimer = null
      }
    }

    this.ws.onmessage = (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data)
        this.handleMessage(data)
      } catch (error) {
        console.error('[WS] Failed to parse message:', error)
      }
    }

    this.ws.onclose = (event: CloseEvent) => {
      this.cleanup()
      if (!this.manualClose) {
        this.scheduleReconnect()
      }
      if (this.onClosed) {
        this.onClosed(event)
      }
    }

    this.ws.onerror = (error: Event) => {
      if (this.onError) {
        this.onError(error)
      }
    }
  }

  private handleMessage(data: any): void {
    if (data.type === 'connected') {
      const msg = data as WsConnectedMessage
      if (this.onConnected) {
        this.onConnected(msg.userId)
      }
      return
    }
    if (data.type === 'error') {
      return
    }
    if (data.type === 'pong') {
      return
    }
  }

  private startHeartbeat(): void {
    this.stopHeartbeat()
    this.heartbeatTimer = setInterval(() => {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.send({ action: 'ping' })
      }
    }, this.heartbeatInterval)
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer)
      this.heartbeatTimer = null
    }
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimer) return
    const delay = this.reconnectDelay
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      this.connect()
    }, delay)
    this.reconnectDelay = Math.min(this.reconnectDelay * 2, this.maxReconnectDelay)
  }

  send(message: object): boolean {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(message))
      return true
    }
    return false
  }

  sendChat(sessionId: string, message: string, requestId?: string): boolean {
    return this.send({ action: 'chat', sessionId, message, requestId })
  }

  cancelChat(sessionId: string): boolean {
    return this.send({ action: 'cancel', sessionId })
  }

  close(): void {
    this.manualClose = true
    this.cleanup()
    if (this.ws) {
      this.ws.close(1000, 'Client closing')
      this.ws = null
    }
  }

  private cleanup(): void {
    this.stopHeartbeat()
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }

  get readyState(): number {
    return this.ws ? this.ws.readyState : WebSocket.CLOSED
  }

  get isConnected(): boolean {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN
  }
}

/**
 * Send a chat message via an existing WebSocket long-lived connection.
 * The caller is responsible for managing the connection lifecycle.
 */
export function sendChatViaExistingWs(
  client: WebSocketChatClient,
  sessionId: string,
  message: string,
  onChunk: (chunk: ChatChunk) => void,
  onDone: (contextStats?: any) => void,
  onError: (error: any) => void,
  signal?: AbortSignal
): boolean {
  if (!client.isConnected) {
    return false
  }

  // Register a temporary message handler for this chat session
  const origHandler = client['handleMessage'].bind(client)
  client['handleMessage'] = (data: any) => {
    if (data.type === 'connected' || data.type === 'pong') return
    if (data.type === 'error') {
      onError(new Error(data.errorDetail || 'Server error'))
      return
    }
    const chunk = data as ChatChunk
    if (chunk.done) {
      onDone(chunk.contextStats || undefined)
      // Restore original handler after chat completes
      client['handleMessage'] = origHandler
    } else {
      onChunk(chunk)
    }
  }

  if (signal) {
    signal.addEventListener('abort', () => {
      client.cancelChat(sessionId)
      client['handleMessage'] = origHandler
      onError(new Error('Aborted'))
    })
  }

  return client.sendChat(sessionId, message)
}

/**
 * Unified chat send function with automatic WS/SSE selection.
 * If an existing WS client is provided, reuses it (long-lived connection).
 * Otherwise falls back to SSE.
 */
export async function sendChatMessageAuto(
  sessionId: string,
  message: string,
  onChunk: (chunk: ChatChunk) => void,
  onDone: (contextStats?: any) => void,
  onError: (error: any) => void,
  signal?: AbortSignal,
  wsClient?: WebSocketChatClient | null
): Promise<(() => void) | null> {
  // Try existing WebSocket connection first
  if (wsClient && wsClient.isConnected) {
    const sent = sendChatViaExistingWs(wsClient, sessionId, message, onChunk, onDone, onError, signal)
    if (sent) return null // connection lifecycle managed by caller
  }

  // Fallback to SSE
  await sendChatMessage(sessionId, message, onChunk, onDone, onError, signal)
  return null
}
