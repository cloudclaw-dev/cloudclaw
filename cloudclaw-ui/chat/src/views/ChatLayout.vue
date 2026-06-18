<template>
  <div class="chat-layout" :class="{ dark: isDark }">
    <el-container class="main-container">
      <!-- Left Nav Bar -->
      <el-aside :width="navCollapsed ? '64px' : '260px'" class="nav-bar" :class="{ collapsed: navCollapsed }">
        <a href="http://cloudclaw.run" target="_blank" rel="noopener" class="nav-bar-header-link" style="text-decoration:none">
          <div class="nav-bar-header">
            <img src="@/assets/logo.png" alt="CC" class="nav-logo" />
            <span v-if="!navCollapsed" class="nav-brand">CloudClaw</span>
          </div>
          <div v-if="!navCollapsed" class="nav-version">v{{ systemVersion }} · {{ systemMode }}</div>
        </a>

        <!-- Action buttons -->
        <div class="nav-bar-menu">
          <div class="nav-bar-item new-chat-btn" @click="showNewSessionDialog = true" :title="navCollapsed ? t('chat.newSession') : ''">
            <el-icon :size="20"><EditPen /></el-icon>
            <span class="nav-bar-label" :class="{ 'label-hidden': navCollapsed }">{{ navCollapsed ? '' : $t('chat.newSession') }}</span>
          </div>

        </div>

        <!-- Session list (inline in nav) -->
        <div v-if="!navCollapsed" class="nav-session-list">
          <SessionList
            :sessions="sessions"
            :agents="agents"
            :currentSessionId="currentSessionId"
            @selectSession="handleSelectSession"
            @deleteSession="deleteSession"
            @renameSession="renameSession"
            @pinSession="pinSession"
            @unpinSession="unpinSession"
          />
        </div>
        <div v-else class="nav-session-list-collapsed">
          <div v-for="s in sessions.slice(0, 8)" :key="s.id"
               class="nav-bar-item collapsed-session"
               :class="{ active: currentSessionId === s.id }"
               @click="handleSelectSession(s.id)"
               :title="s.title || t('chat.newChat')">
            <el-icon :size="16"><ChatDotRound /></el-icon>
          </div>
        </div>

        <!-- Bottom section -->
        <div class="nav-bar-bottom">
          <div class="nav-bar-divider" />
          <div class="nav-bar-item" :class="{ active: $route.path === '/memory' }" @click="$router.push('/memory')" :title="navCollapsed ? t('nav.memory') : ''">
            <el-icon :size="20"><Memo /></el-icon>
            <span class="nav-bar-label" :class="{ 'label-hidden': navCollapsed }">{{ $t('nav.memory') }}</span>
          </div>
          <div v-if="isAdmin" class="nav-bar-item" :class="{ active: $route.path.startsWith('/admin') }" @click="$router.push('/admin')" :title="navCollapsed ? t('nav.systemAdmin') : ''">
            <el-icon :size="20"><Setting /></el-icon>
            <span class="nav-bar-label" :class="{ 'label-hidden': navCollapsed }">{{ $t('nav.systemAdmin') }}</span>
          </div>
          <div class="nav-bar-divider" />
          <div class="nav-bar-item" @click="toggleLocale">
            <span class="locale-icon">{{ locale === 'zh' ? 'EN' : '中' }}</span>
            <span class="nav-bar-label" :class="{ 'label-hidden': navCollapsed }">{{ locale === 'zh' ? 'English' : '中文' }}</span>
          </div>
          <div class="nav-bar-item" @click="toggleDark" :title="isDark ? 'Light' : 'Dark'">
            <el-icon :size="18"><component :is="isDark ? Sunny : Moon" /></el-icon>
            <span class="nav-bar-label" :class="{ 'label-hidden': navCollapsed }">{{ isDark ? $t('login.light') : $t('login.dark') }}</span>
          </div>
          <div class="nav-bar-item" :class="{ active: $route.path === '/profile' }" @click="$router.push('/profile')" :title="t('nav.profile')">
            <el-icon :size="18"><UserFilled /></el-icon>
            <span class="nav-bar-label" :class="{ 'label-hidden': navCollapsed }">{{ $t('nav.profile') }}</span>
          </div>
          <div class="nav-bar-item" @click="handleLogout" :title="$t('nav.logout')">
            <el-icon :size="18"><SwitchButton /></el-icon>
            <span class="nav-bar-label" :class="{ 'label-hidden': navCollapsed }">{{ $t('nav.logout') }}</span>
          </div>
          <div class="nav-bar-item collapse-toggle" @click="navCollapsed = !navCollapsed" :title="navCollapsed ? t('nav.expandMenu') : t('nav.collapseMenu')">
            <el-icon :size="18" :class="{ 'rotate-icon': !navCollapsed }"><Fold /></el-icon>
            <span class="nav-bar-label" :class="{ 'label-hidden': navCollapsed }">{{ navCollapsed ? t('nav.expandMenu') : t('nav.collapseMenu') }}</span>
          </div>
        </div>
      </el-aside>

      <!-- Content Area -->
      <el-container class="content-container">
        <el-container class="chat-container" @click="isMobile && showMobileSessions ? showMobileSessions = false : null">
          <el-header v-if="!isSubPage" class="chat-header" height="56px">
            <div class="chat-header-info">
              <el-button v-if="isMobile" :icon="Operation" circle size="small" text class="mobile-session-btn" @click.stop="showMobileSessions = !showMobileSessions" />
              <el-icon v-if="!isMobile" :size="18" class="chat-header-icon-text"><ChatLineSquare /></el-icon>
              <span class="header-title">{{ currentSessionTitle || 'CloudClaw Chat' }}</span>
            </div>
            <el-tag v-if="activeAgentName" type="warning" size="small" effect="plain">{{ activeAgentName }}</el-tag>
            <div class="ws-status-indicator" :title="wsConnected ? 'WebSocket 已连接' : wsUseSSE ? 'SSE 模式' : 'WebSocket 连接中...'">
              <span class="ws-status-dot" :class="wsConnected ? 'connected' : wsUseSSE ? 'sse' : 'connecting'"></span>
              <span class="ws-status-text">{{ wsConnected ? 'WS' : wsUseSSE ? 'SSE' : '...' }}</span>
            </div>
          </el-header>
          <el-main class="messages-area" ref="messagesAreaRef">
            <div class="messages-inner">
              <template v-if="isSubPage"><router-view /></template>
              <template v-else>
              <transition name="fade" mode="out-in">
                <div v-if="!currentSessionId" key="welcome" class="welcome-wrapper">
                  <WelcomeSection :agents="agents" :sessions="sessions" @newSession="showNewSessionDialog = true" @selectAgent="startChatWithAgent" @startWithPrompt="startWithPrompt" />
                </div>
                <div v-else key="messages" class="messages-transition">
                  <SkeletonScreen v-if="loadingMessages" type="message" :count="3" />
                  <template v-else>
                    <transition-group name="fade">
                      <MessageBubble
                        v-for="(msg, index) in messages"
                        :key="'msg-' + index"
                        :msg="msg"
                        :index="index"
                        :md="md"
                        @regenerate="handleRegenerate"
                        @edit="handleEdit"
                      />
                    </transition-group>
                    <div v-if="isStreaming && (streamingContent || streamingSegments.length > 0 || workflowState)" class="message-row assistant streaming-fade">
                      <div class="message-avatar"><el-avatar :size="32" class="assistant-avatar"><el-icon><Monitor /></el-icon></el-avatar></div>
                      <div class="message-content">
                        <div class="message-meta">Assistant<span v-if="activeAgentName" class="agent-label"> · {{ activeAgentName }}</span></div>
                        <WorkflowPanel :workflowState="workflowState" />
                        <template v-for="(seg, si) in streamingSegments" :key="si">
                          <div v-if="seg.type === 'tool_call'" class="tool-card" :class="{ 'tool-done': seg.status === 'done', 'tool-pending': seg.status === 'pending' }">
                            <div class="tool-card-header">
                              <span class="tool-card-icon">{{ getToolDisplay(seg.toolName || '').icon }}</span>
                              <span class="tool-card-label">{{ getToolDisplay(seg.toolName || '').label }}</span>
                              <span v-if="seg.status === 'pending'" class="tool-status pending">⏳</span>
                              <span v-else class="tool-status done">✅</span>
                            </div>
                            <div v-if="seg.content" class="tool-card-desc">{{ formatToolCallHuman(seg.toolName || '', seg.content) }}</div>
                            <template v-if="seg.status === 'done' && seg.resultContent">
                              <div class="tool-card-result-toggle" @click="toggleToolResult(si)">
                                {{ formatToolResultSummary(seg.toolName || '', seg.resultContent) }}
                                <span class="tool-toggle-icon">{{ expandedTools[si] ? '▲' : '▼' }}</span>
                              </div>
                              <div v-if="expandedTools[si]" class="tool-card-result-detail">
                                <pre>{{ formatToolArgs(seg.resultContent) }}</pre>
                              </div>
                            </template>
                          </div>
                          <div v-else-if="seg.type === 'tool_result'" class="tool-card tool-done">
                            <div class="tool-card-header">
                              <span class="tool-card-icon">{{ getToolDisplay(seg.toolName || '').icon }}</span>
                              <span class="tool-card-label">{{ getToolDisplay(seg.toolName || '').label }}</span>
                              <span class="tool-status done">✅</span>
                            </div>
                            <div class="tool-card-result-toggle" @click="toggleToolResult(si)">
                              {{ formatToolResultSummary(seg.toolName || '', seg.content) }}
                              <span class="tool-toggle-icon">{{ expandedTools[si] ? '▲' : '▼' }}</span>
                            </div>
                            <div v-if="expandedTools[si]" class="tool-card-result-detail">
                              <pre>{{ formatToolArgs(seg.content) }}</pre>
                            </div>
                          </div>
                          <div v-else-if="seg.type === 'workflow_status'" class="workflow-event-text">{{ seg.content }}</div>
                          <div v-else class="message-text markdown-body streaming-fade" v-html="renderMarkdown(seg.content)" />
                        </template>
                      </div>
                    </div>
                    <div v-if="isStreaming && !streamingContent && streamingSegments.length === 0" class="message-row assistant streaming-fade">
                      <div class="message-avatar"><el-avatar :size="32" class="assistant-avatar"><el-icon><Monitor /></el-icon></el-avatar></div>
                      <div class="message-content">
                        <div class="message-meta">Assistant<span v-if="activeAgentName" class="agent-label"> · {{ activeAgentName }}</span></div>
                        <div class="message-text"><div class="typing-indicator"><span></span><span></span><span></span></div></div>
                      </div>
                    </div>
                  </template>
                  <div v-if="reconnectStatus" class="reconnect-banner" :class="reconnectStatus.type">
                    <el-icon v-if="reconnectStatus.type === 'reconnecting'" class="is-loading"><Loading /></el-icon>
                    <el-icon v-else-if="reconnectStatus.type === 'error'"><Close /></el-icon>
                    <span>{{ reconnectStatus.message }}</span>
                  </div>
                </div>
              </transition>
            </template>
            </div>
          </el-main>
          <ChatInput
            v-if="!isSubPage"
            v-model="inputMessage"
            :isStreaming="isStreaming"
            :contextStats="contextStats"
            @send="sendMessage"
            @stop="stopGeneration"
          />
        </el-container>
      </el-container>
    </el-container>

    <!-- Mobile session overlay -->
    <div v-if="isMobile && showMobileSessions" class="mobile-overlay" @click="showMobileSessions = false" />
    <div v-if="isMobile && showMobileSessions" class="mobile-session-panel">
      <div class="mobile-session-header">
        <span>{{ $t('nav.chat') }}</span>
        <el-button text @click="showNewSessionDialog = true"><el-icon><Plus /></el-icon></el-button>
      </div>
      <SessionList
        :sessions="sessions"
        :agents="agents"
        :currentSessionId="currentSessionId"
        @selectSession="handleSelectSession($event); showMobileSessions = false"
        @deleteSession="deleteSession"
        @renameSession="renameSession"
        @pinSession="pinSession"
        @unpinSession="unpinSession"
      />
    </div>

    <!-- New Chat Dialog -->
    <NewChatDialog v-model="showNewSessionDialog" :agents="agents" @selectAgent="startChatWithAgent" />



    <!-- Mobile tab bar -->
    <div class="mobile-tab-bar">
      <div class="tab-item" :class="{ active: mobileTab === 'chat' }" @click="handleMobileTab('chat')">
        <el-icon :size="22"><ChatDotRound /></el-icon>
        <span>{{ $t('nav.chat') }}</span>
      </div>

      <div v-if="isAdmin" class="tab-item" :class="{ active: $route.path.startsWith('/admin') }" @click="$router.push('/admin')">
        <el-icon :size="22"><Setting /></el-icon>
        <span>{{ $t('nav.systemAdmin') }}</span>
      </div>
    </div>

    <!-- Mobile overlay backdrop -->
    <div v-if="showMobileMore" class="mobile-overlay" @click="showMobileMore = false" />
  </div>
</template>


<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch, provide } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound, ChatLineSquare, Memo, Sunny, Moon, SwitchButton, Fold,
  Monitor, SetUp, Operation, Setting, Odometer, User,
  Connection, Reading, Cpu, Loading, Grid, Close, EditPen, Plus, UserFilled
} from '@element-plus/icons-vue'
import { renderMarkdown, md } from '@/utils/markdown'
import api from '@/api/index'
import { sessionApi, messageApi, agentApi, sendChatMessage, sendChatMessageAuto, WebSocketChatClient } from '@/api/chat'
import { useI18n } from 'vue-i18n'
import { useTheme } from '@/utils/theme'
import SessionList from '@/components/SessionList.vue'
import NewChatDialog from '@/components/NewChatDialog.vue'
import MessageBubble from '@/components/MessageBubble.vue'
import ChatInput from '@/components/ChatInput.vue'
import WelcomeSection from '@/components/WelcomeSection.vue'
import { getToolDisplay, formatToolCallHuman, formatToolResultSummary } from '@/utils/toolDisplay'
import WorkflowPanel from '@/components/WorkflowPanel.vue'
import SkeletonScreen from '@/components/SkeletonScreen.vue'


// ===== Types =====
interface Agent { id: string; name: string; description?: string; systemPrompt?: string }
interface Session { id: string; title: string; agentId: string; createdAt: string; updatedAt: string }
interface MessageSegment {
  type: 'text' | 'tool_call' | 'tool_result' | 'workflow_status'
  content: string
  toolName?: string
  status?: 'pending' | 'done' | 'error'
  resultContent?: string
}
interface Message { role: 'user' | 'assistant'; content: string; segments?: MessageSegment[]; createdAt?: string; agentName?: string; memoryRefs?: { type: string; itemId: string; content: string }[] }
interface WorkflowStepStatus { name: string; status: 'pending' | 'running' | 'done' }
interface WorkflowState { mode: string; steps: WorkflowStepStatus[]; activeNode: string; supervisorAction: string; mergeStatus: string; reason: string }

// ===== State =====
const router = useRouter()
const route = useRoute()
const isSubPage = computed(() => ['/memory', '/agents', '/profile'].includes(route.path))
const navCollapsed = ref(false)
const agents = ref<Agent[]>([])
const sessions = ref<Session[]>([])
const systemVersion = ref('')
const systemMode = ref('standalone')
const currentSessionId = ref('')
const messages = ref<Message[]>([])
const inputMessage = ref('')
const isStreaming = ref(false)
const loadingMessages = ref(false)
const contextStats = ref<any>(null)
const activeAgentName = ref('')
const streamingContent = ref('')
const streamingSegments = ref<MessageSegment[]>([])
const messagesAreaRef = ref<HTMLElement | null>(null)
const workflowState = ref<WorkflowState | null>(null)
const reconnectStatus = ref<{ type: 'reconnecting' | 'error'; message: string } | null>(null)

// WebSocket 客户端
const wsClient = ref<WebSocketChatClient | null>(null)
const wsEnabled = ref(true) // 是否启用 WebSocket（可配置）
const wsConnected = ref(false) // WebSocket 连接状态（用于 UI 展示）
const wsUseSSE = ref(false) // 是否回退到了 SSE 模式

const showNewSessionDialog = ref(false)
const newSessionAgentId = ref('')
const selectedAgentId = ref('')

const mobileTab = ref<'chat' | 'memory' | 'admin'>('chat')
const showMobileSessions = ref(false)
const showMobileMore = ref(false)
const showMobileAdmin = ref(false)
const isMobile = ref(false)
const showUserMenu = ref(false)

const { t, locale } = useI18n()
const { isDark, toggleDark } = useTheme()

// Provide isDark for child components via inject
provide('isDark', isDark)
provide('isMobile', isMobile)

// Markdown renderer imported from @/utils/markdown
const formatToolArgs = (args: string): string => { try { return JSON.stringify(JSON.parse(args), null, 2) } catch { return args } }
const expandedTools = ref<Record<number, boolean>>({})
const toggleToolResult = (idx: number) => { expandedTools.value[idx] = !expandedTools.value[idx] }

// ===== Helpers =====
function parseJwtRole(token: string): string | null {
  try {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const jsonPayload = decodeURIComponent(atob(base64).split('').map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)).join(''))
    return JSON.parse(jsonPayload).role || null
  } catch { return null }
}

const isAdmin = computed(() => {
  const token = localStorage.getItem('access_token')
  return token ? parseJwtRole(token) === 'admin' : false
})


const currentSessionTitle = computed(() => {
  if (!currentSessionId.value) return 'CloudClaw Chat'
  return sessions.value.find(s => s.id === currentSessionId.value)?.title || 'Untitled Chat'
})


// ===== Locale & Theme =====
const toggleLocale = () => { locale.value = locale.value === 'zh' ? 'en' : 'zh'; localStorage.setItem('cloudclaw-locale', locale.value) }

// ===== Data Loading =====
const loadAgents = async () => {
  try {
    const res: any = await agentApi.list()
    agents.value = res.data || res || []
    if (agents.value.length > 0 && !selectedAgentId.value) {
      selectedAgentId.value = agents.value[0].id
      newSessionAgentId.value = agents.value[0].id
    }
  } catch (e) { /* silently fail */ }
}

const loadSessions = async () => {
  try {
    const res: any = await sessionApi.list(1, 50, undefined)
    sessions.value = res.data?.list || res.data?.items || res.data || res.items || []
  } catch (e) { /* silently fail */ }
}

const loadMessages = async (sessionId: string) => {
  loadingMessages.value = true
  try {
    const res: any = await messageApi.history(sessionId)
    const items = res.data?.list || res.data?.items || res.data || res.items || []
    messages.value = items.map((m: any) => ({ role: m.role, content: m.content, createdAt: m.createdAt, agentName: m.agentName || '', segments: m.segments || undefined }))
    scrollToBottom()
  } catch (e) { messages.value = [] } finally { loadingMessages.value = false }
}

// ===== Title Refresh =====
let abortController: AbortController | null = null
let titleRefreshTimer: ReturnType<typeof setInterval> | null = null
const startTitleRefresh = () => {
  if (titleRefreshTimer) return
  titleRefreshTimer = setInterval(async () => {
    if (sessions.value.some((s: any) => !s.title)) { await loadSessions() }
    else if (titleRefreshTimer) { clearInterval(titleRefreshTimer); titleRefreshTimer = null }
  }, 3000)
}

// ===== Session Actions =====
const handleSelectSession = (id: string) => {
  if (isStreaming.value) return
  messages.value = []; streamingContent.value = ''; streamingSegments.value = []; workflowState.value = null; activeAgentName.value = ''
  currentSessionId.value = id
  if (isMobile.value) showMobileSessions.value = false
  if (route.path !== '/') router.push('/')
  loadMessages(id)
}


const startChatWithAgent = async (agent: any) => {
  try {
    const res: any = await sessionApi.create({ agentId: agent.id, title: '' })
    const newSession = res.data || res
    showNewSessionDialog.value = false
    if (newSession?.id) {
      await loadSessions()
      handleSelectSession(newSession.id)
    }
  } catch (e) {
    console.error('Failed to create session:', e)
    ElMessage.error(t('chat.createSessionFailed') || 'Failed to create session')
  }
}


const deleteSession = async (sessionId: string) => {
  try {
    await ElMessageBox.confirm(t('chat.deleteSessionConfirm'), t('chat.deleteSession'), { confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel'), type: 'warning' })
    await sessionApi.delete(sessionId)
    sessions.value = sessions.value.filter(s => s.id !== sessionId)
    if (currentSessionId.value === sessionId) { currentSessionId.value = ''; messages.value = [] }
    ElMessage.success(t('chat.sessionDeleted'))
  } catch (e) { /* cancelled or error */ }
}

const renameSession = async (id: string, title: string) => {
  try {
    await sessionApi.rename(id, title)
    const session = sessions.value.find(s => s.id === id)
    if (session) session.title = title
  } catch (e) { /* handled */ }
}

const pinSession = (_id: string) => { /* pin handled via localStorage in SessionList */ }
const unpinSession = (_id: string) => { /* unpin handled via localStorage in SessionList */ }

const handleChunk = (chunk: any) => {
  const type = chunk.type || 'text'
  const text = chunk.content || ''
  if (type === 'pipeline_step') {
    if (!workflowState.value) workflowState.value = { mode: 'pipeline', steps: [], activeNode: '', supervisorAction: '', mergeStatus: '', reason: '' }
    const nodeName = chunk.node || ''
    const existing = workflowState.value.steps.find(s => s.name === nodeName)
    if (!existing) workflowState.value.steps.push({ name: nodeName, status: 'running' })
    else existing.status = 'running'
    const idx = workflowState.value.steps.findIndex(s => s.name === nodeName)
    for (let i = 0; i < idx; i++) workflowState.value.steps[i].status = 'done'
  } else if (type === 'parallel_start') {
    const nodes: string[] = chunk.nodes || []
    workflowState.value = { mode: 'parallel', steps: nodes.map(n => ({ name: n, status: 'pending' as const })), activeNode: '', supervisorAction: '', mergeStatus: '', reason: '' }
  } else if (type === 'parallel_progress') {
    if (workflowState.value) { const node = workflowState.value.steps.find(s => s.name === chunk.node); if (node) node.status = 'running' }
  } else if (type === 'parallel_complete') {
    if (workflowState.value) { const node = workflowState.value.steps.find(s => s.name === chunk.node); if (node) node.status = 'done' }
  } else if (type === 'parallel_merge') {
    if (workflowState.value) workflowState.value.mergeStatus = 'merging'
  } else if (type === 'router_select') {
    workflowState.value = { mode: 'router', steps: [], activeNode: chunk.targetAgent || '', supervisorAction: '', mergeStatus: '', reason: chunk.reason || '' }
    activeAgentName.value = chunk.targetAgent || ''
  } else if (type === 'supervisor_plan') {
    workflowState.value = { mode: 'supervisor', steps: (chunk.plan || []).map((s: string) => ({ name: s, status: 'pending' as const })), activeNode: '', supervisorAction: t('chat.workflowSupervisorReviewing'), mergeStatus: '', reason: '' }
  } else if (type === 'supervisor_delegate') {
    if (!workflowState.value) workflowState.value = { mode: 'supervisor', steps: [], activeNode: '', supervisorAction: '', mergeStatus: '', reason: '' }
    workflowState.value.supervisorAction = t('chat.workflowSupervisorDelegating', { name: chunk.targetAgent || '' })
    workflowState.value.activeNode = chunk.targetAgent || ''
  } else if (type === 'supervisor_result') {
    if (workflowState.value) { const node = workflowState.value.steps.find(s => s.name === chunk.from); if (node) node.status = 'done'; workflowState.value.supervisorAction = t('chat.workflowSupervisorReviewing') }
  } else if (type === 'handoff') {
    workflowState.value = { mode: 'handoff', steps: [], activeNode: chunk.targetAgent || '', supervisorAction: '', mergeStatus: '', reason: '' }
    activeAgentName.value = chunk.targetAgent || ''
  } else if (type === 'agent_switched') {
    const displayName = chunk.displayName || chunk.targetAgent || ''
    activeAgentName.value = displayName
    streamingSegments.value.push({ type: 'text' as const, content: '↩ ' + t('chat.agentSwitched', { name: displayName }) })
    if (workflowState.value && workflowState.value.mode === 'handoff') workflowState.value.activeNode = displayName
  } else if (type === 'tool_call') {
    streamingSegments.value.push({ type: 'tool_call', content: text, toolName: chunk.toolName || 'tool', status: 'pending' })
  } else if (type === 'tool_result') {
    const toolName = chunk.toolName || 'tool'
    const pendingIdx = streamingSegments.value.findLastIndex(s => s.type === 'tool_call' && s.toolName === toolName && s.status === 'pending')
    if (pendingIdx >= 0) {
      const seg = streamingSegments.value[pendingIdx]
      seg.status = 'done'
      seg.resultContent = text
    } else {
      streamingSegments.value.push({ type: 'tool_result', content: text, toolName, status: 'done' })
    }
  } else if (text) {
    // Mark all pending tool_calls as done (backend ToolCallAdvisor handles results internally)
    for (const seg of streamingSegments.value) {
      if (seg.type === 'tool_call' && seg.status === 'pending') {
        seg.status = 'done'
      }
    }
    const last = streamingSegments.value[streamingSegments.value.length - 1]
    if (last && last.type === 'text') last.content += text
    else streamingSegments.value.push({ type: 'text', content: text })
    streamingContent.value += text
  }
  scrollToBottom()
}

// ===== WebSocket Chat with Fallback =====
const sendWithRetryFn = async (sessionId: string, message: string, retries = 2): Promise<void> => {
  const token = localStorage.getItem('access_token')
  if (!token) {
    ElMessage.error(t('chat.noToken') || 'No access token')
    return
  }

  // WebSocket 清理函数
  let cleanup: (() => void) | null = null

  try {
    // 尝试使用 WebSocket
    const controller = new AbortController()
    abortController = controller

    cleanup = await sendChatMessageAuto(
      sessionId,
      message,
      (chunk) => { handleChunk(chunk) },
      (contextStats) => {
        if (contextStats) contextStats.value = contextStats
        isStreaming.value = false
        if (streamingContent.value || streamingSegments.value.length > 0) {
          messages.value.push({
            role: 'assistant',
            content: streamingContent.value,
            segments: [...streamingSegments.value],
            createdAt: new Date().toISOString()
          })
        }
        streamingContent.value = ''
        streamingSegments.value = []
        workflowState.value = null
        startTitleRefresh()
      },
      (error: any) => {
        if (error?.name === 'AbortError') { return }
        // WebSocket 错误，已由 sendChatMessageAuto 处理回退
        isStreaming.value = false
        streamingContent.value = ''
        streamingSegments.value = []
        workflowState.value = null
        reconnectStatus.value = { type: 'error', message: t('chat.responseFailed') || 'Failed to get response' }
        ElMessage.error(t('chat.responseFailed') || 'Failed to get response')
      },
      controller.signal,
      wsClient.value
    )
  } catch (error) {
    // 发生异常
    isStreaming.value = false
    streamingContent.value = ''
    streamingSegments.value = []
    workflowState.value = null
    reconnectStatus.value = { type: 'error', message: t('chat.responseFailed') || 'Failed to get response' }
    ElMessage.error(t('chat.responseFailed') || 'Failed to get response')
    throw error
  }
}

// ===== Send Message =====
const sendMessage = async () => {
  const msg = inputMessage.value.trim()
  if (!msg || isStreaming.value) return
  if (!currentSessionId.value) {
    if (!selectedAgentId.value) { ElMessage.warning(t('chat.selectAgentFirst')); return }
    try {
      const res: any = await sessionApi.create({ agentId: selectedAgentId.value })
      const newSession = res.data || res
      if (newSession?.id) {
        currentSessionId.value = newSession.id
        sessions.value.unshift({ id: newSession.id, title: '', agentId: selectedAgentId.value, createdAt: newSession.createdAt || new Date().toISOString(), updatedAt: newSession.updatedAt || new Date().toISOString() })
        startTitleRefresh()
      }
    } catch (e) { ElMessage.error(t('common.failed')); return }
  }
  if (!currentSessionId.value) return
  messages.value.push({ role: 'user', content: msg, createdAt: new Date().toISOString() })
  inputMessage.value = ''; isStreaming.value = true; streamingContent.value = ''; streamingSegments.value = []
  workflowState.value = null; abortController = new AbortController(); reconnectStatus.value = null
  scrollToBottom()
  const chatMode = localStorage.getItem('chatMode') || 'stream'
  if (chatMode === 'poll') {
    try {
      const requestId = 'req_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8)
      const res = await sendMessageAsync(currentSessionId.value, msg, requestId)
      const data = res?.data || res
      let pollInterval: ReturnType<typeof setInterval> | null = null; let lastContent = ''; let stableCount = 0
      const stopPolling = () => { if (pollInterval) { clearInterval(pollInterval); pollInterval = null }; isStreaming.value = false }
      pollInterval = setInterval(async () => {
        try {
          const pollRes = await pollMessages(currentSessionId.value, data?.userMessageId)
          const assistant = (pollRes?.data?.messages || []).find((m: any) => m.id === data?.assistantMessageId)
          if (assistant) {
            if (assistant.status === 'pending' || assistant.status === 'processing') { streamingContent.value = t('chat.thinking'); scrollToBottom() }
            else if (assistant.status === 'completed' || assistant.status === 'failed') {
              stopPolling()
              if (assistant.content) messages.value.push({ role: 'assistant', content: assistant.content, createdAt: assistant.createdAt })
              if (assistant.status === 'failed') ElMessage.error(t('chat.responseFailed'))
              streamingContent.value = ''; scrollToBottom(); startTitleRefresh()
            } else {
              if (assistant.content && assistant.content !== lastContent) { lastContent = assistant.content; stableCount = 0; streamingContent.value = assistant.content; scrollToBottom() }
              else { stableCount++ }
              if (stableCount > 60) { stopPolling(); ElMessage.warning(t('chat.responseTimeout')) }
            }
          }
        } catch (e) { /* keep trying */ }
      }, 500)
    } catch (error) { isStreaming.value = false; streamingContent.value = ''; ElMessage.error(t('chat.responseFailed')) }
    return
  }
  try { await sendWithRetryFn(currentSessionId.value, msg) } catch (error) { /* handled in retry */ }
}

// ===== Regenerate & Edit =====
const handleRegenerate = (index: number) => {
  if (isStreaming.value) return
  // Delete the last assistant message and resend the previous user message
  if (messages.value[index]?.role === 'assistant' && index > 0) {
    const userMsg = messages.value[index - 1]
    if (userMsg?.role === 'user') {
      messages.value.splice(index)
      inputMessage.value = userMsg.content
      nextTick(() => sendMessage())
    }
  }
}

const handleEdit = (index: number, content: string) => {
  if (isStreaming.value) return
  // Truncate messages after that index and resend
  messages.value.splice(index)
  inputMessage.value = content
  nextTick(() => sendMessage())
}

// ===== Start With Prompt =====
const startWithPrompt = async (promptText: string) => {
  if (!selectedAgentId.value) { ElMessage.warning(t('chat.selectAgentFirst')); return }
  try {
    const res: any = await sessionApi.create({ agentId: selectedAgentId.value })
    const newSession = res.data || res
    if (newSession?.id) {
      currentSessionId.value = newSession.id
      sessions.value.unshift({ id: newSession.id, title: '', agentId: selectedAgentId.value, createdAt: newSession.createdAt || new Date().toISOString(), updatedAt: newSession.updatedAt || new Date().toISOString() })
      startTitleRefresh()
      inputMessage.value = promptText
      nextTick(() => sendMessage())
    }
  } catch (e) { ElMessage.error(t('common.failed')) }
}

const stopGeneration = () => {
  // 如果使用 WebSocket，发送 cancel 消息
  if (wsEnabled.value && wsClient.value && currentSessionId.value) {
    wsClient.value.cancelChat(currentSessionId.value)
  }

  // 如果使用 AbortController，中止它
  if (abortController) { abortController.abort(); abortController = null }

  // 保存当前流式内容
  if (streamingContent.value || streamingSegments.value.length > 0) {
    messages.value.push({ role: 'assistant', content: streamingContent.value, segments: [...streamingSegments.value] })
  }

  // 清理状态
  streamingContent.value = ''; streamingSegments.value = []; workflowState.value = null; isStreaming.value = false; reconnectStatus.value = null
}

const scrollToBottom = () => {
  nextTick(() => {
    const area = messagesAreaRef.value
    if (area) { const el = area.$el || area; el.scrollTop = el.scrollHeight }
  })
}

const handleLogout = () => { localStorage.removeItem('access_token'); localStorage.removeItem('refresh_token'); router.push('/login') }

// ===== Mobile =====
const checkMobile = () => { isMobile.value = window.innerWidth < 768 }
const handleMobileTab = (tab: string) => {
  showMobileMore.value = false; showMobileAdmin.value = false
  if (tab === 'chat') { mobileTab.value = 'chat'; showMobileSessions.value = true; router.push('/') }
  else if (tab === 'admin') showMobileAdmin.value = !showMobileAdmin.value
}
const handleMobileAdminNav = (path: string) => { showMobileAdmin.value = false; mobileTab.value = 'admin'; router.push(path) }
const handleMobileMore = () => { showMobileAdmin.value = false; showMobileMore.value = !showMobileMore.value }

// ===== Lifecycle =====
const handleClickOutside = (e: MouseEvent) => {
  if (!(e.target as HTMLElement).closest('.nav-bar-user')) showUserMenu.value = false
}

/**
 * 初始化 WebSocket 连接（如果启用）
 * 连接失败时静默回退到 SSE，不显示错误提示。
 * 仅在聊天过程中断开才提示用户。
 */
const initWebSocket = () => {
  if (!wsEnabled.value) return

  const token = localStorage.getItem('access_token')
  if (!token) return

  let hasConnected = false

  try {
    wsClient.value = new WebSocketChatClient(token, {
      onConnected: (_userId) => {
        hasConnected = true
        wsConnected.value = true
        reconnectStatus.value = null
      },
      onClosed: (event) => {
        wsConnected.value = false
        // 仅在已经成功连接过、且正在 streaming 时才提示断开
        if (hasConnected && event.code !== 1000 && isStreaming.value) {
          reconnectStatus.value = { type: 'error', message: t('chat.connectionLost') }
        }
      },
      onError: (_error) => {
        // 初始化阶段静默失败，回退到 SSE
        if (!hasConnected) {
          wsEnabled.value = false
          wsUseSSE.value = true
        }
      }
    })

    wsClient.value.connect()
  } catch (_error) {
    // 静默回退到 SSE
    wsEnabled.value = false
    wsUseSSE.value = true
  }
}

onMounted(async () => {

  checkMobile(); window.addEventListener('resize', checkMobile)
  document.addEventListener('click', handleClickOutside)
  await loadAgents(); await loadSessions()
  try {
    const infoRes: any = await api.get('/admin/stats/info')
    const info = infoRes?.data?.data || infoRes?.data || infoRes
    if (info?.version) systemVersion.value = info.version
    if (info?.mode) systemMode.value = info.mode
  } catch (e) { /* ignore */ }
  document.addEventListener('visibilitychange', () => { if (!document.hidden) loadAgents() })

  // 初始化 WebSocket
  initWebSocket()
})

onUnmounted(() => {
  if (titleRefreshTimer) { clearInterval(titleRefreshTimer); titleRefreshTimer = null }
  window.removeEventListener('resize', checkMobile)
  document.removeEventListener('click', handleClickOutside)

  // 清理 WebSocket 连接
  if (wsClient.value) {
    wsClient.value.close()
    wsClient.value = null
  }
})

watch(showNewSessionDialog, (val) => { if (val) loadAgents() })
watch(selectedAgentId, async (newId) => {
  // Already handled in handleAgentChange; this is a safety fallback
  if (newId) newSessionAgentId.value = newId
})
watch(messages, () => { nextTick(() => scrollToBottom()) }, { deep: true })
</script>

<style scoped>

.chat-layout {
  position: relative;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  background: var(--cc-bg-secondary);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
}
.main-container { height: 100%; }

/* ===== Nav Bar ===== */
.nav-bar {
  height: 100vh;
  background: #f0f1f3;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  flex-shrink: 0;
  border-right: none;
  transition: width 0.25s ease;
}
.chat-layout.dark .nav-bar { background: #1e1f22; }
.nav-bar-header { padding: 10px 0 6px; display: flex; align-items: center; justify-content: center; gap: 10px; white-space: nowrap; overflow: hidden; }
.nav-brand { font-size: 16px; font-weight: 700; color: var(--cc-text-primary); }
.nav-version { font-size: 10px; color: var(--cc-text-tertiary, #999); text-align: center; margin-top: -2px; margin-bottom: 2px; letter-spacing: 0.5px; }
.nav-bar.collapsed .nav-bar-header { justify-content: center; }
.nav-logo { width: 32px; height: 32px; border-radius: 8px; flex-shrink: 0; }
.nav-bar-menu { display: flex; flex-direction: column; gap: 2px; padding: 4px 8px; }
.nav-bar.collapsed .nav-bar-menu { align-items: center; padding: 4px 6px; }
.nav-bar-item {
  height: 40px; border-radius: 8px; display: flex; align-items: center; gap: 10px;
  padding: 0 12px; cursor: pointer; color: #5a5e66; font-size: 13px;
  transition: all 0.15s; user-select: none; white-space: nowrap; overflow: hidden;
}
.nav-bar.collapsed .nav-bar-item { width: 52px; height: 46px; justify-content: center; flex-direction: column; gap: 2px; padding: 0; }
.nav-bar-item:hover { background: rgba(0,0,0,0.06); color: #303133; }
.nav-bar-item.active { background: var(--cc-accent); color: #fff; }
.chat-layout.dark .nav-bar-item { color: #9ca0a8; }
.chat-layout.dark .nav-bar-item:hover { background: rgba(255,255,255,0.08); color: #ccc; }
.nav-bar-label { font-size: 13px; line-height: 1; transition: opacity 0.2s, width 0.2s; overflow: hidden; }
.nav-bar.collapsed .nav-bar-label { font-size: 10px; }
.nav-bar.collapsed .nav-bar-label.label-hidden { display: none; }
.nav-bar-divider { height: 1px; background: rgba(0,0,0,0.08); margin: 6px 0; }
.nav-bar.collapsed .nav-bar-divider { width: 32px; margin: 4px auto; }
.chat-layout.dark .nav-bar-divider { background: rgba(255,255,255,0.08); }
.nav-bar-bottom { display: flex; flex-direction: column; gap: 2px; padding: 4px 8px 12px; }
.locale-icon {
  width: 18px; height: 18px; display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 700; border-radius: 4px;
  background: var(--el-color-primary-light-5, #b3d8ff); color: var(--el-color-primary, #409eff); flex-shrink: 0;
}
.chat-layout.dark .locale-icon { background: rgba(64,158,255,0.2); }
.collapse-toggle .rotate-icon { transform: rotate(180deg); transition: transform 0.25s; }

/* ===== Content Container ===== */
.content-container { flex: 1; overflow: hidden; background: var(--cc-bg-secondary); }

/* ===== Chat Container ===== */
.chat-container { flex: 1; display: flex; flex-direction: column; overflow: hidden; background: var(--cc-bg-secondary); }
.chat-header {
  height: 56px !important; background: var(--cc-bg-primary); border-bottom: 1px solid var(--cc-border);
  display: flex; align-items: center; justify-content: space-between; padding: 0 20px;
}
.chat-header-info { display: flex; align-items: center; gap: 8px; }
.chat-header-icon-text { color: var(--cc-accent); }
.header-title { font-size: 15px; font-weight: 600; color: var(--cc-text-primary); }

/* WebSocket status indicator */
.ws-status-indicator {
  display: flex; align-items: center; gap: 4px;
  padding: 2px 8px; border-radius: 10px;
  font-size: 11px; font-weight: 500;
  background: var(--cc-bg-secondary);
  border: 1px solid var(--cc-border);
  transition: all 0.3s;
}
.ws-status-dot {
  width: 7px; height: 7px; border-radius: 50%;
  display: inline-block; flex-shrink: 0;
}
.ws-status-dot.connected {
  background: #22c55e;
  box-shadow: 0 0 4px rgba(34, 197, 94, 0.5);
}
.ws-status-dot.sse {
  background: #f59e0b;
}
.ws-status-dot.connecting {
  background: #94a3b8;
  animation: ws-pulse 1.4s ease-in-out infinite;
}
.ws-status-text {
  color: var(--cc-text-secondary);
  line-height: 1;
}
@keyframes ws-pulse {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 1; }
}

/* ===== Messages Area ===== */
.messages-area { flex: 1; overflow-y: auto; padding: 0 !important; background: var(--cc-bg-secondary); }
.messages-area::-webkit-scrollbar { width: 6px; }
.messages-area::-webkit-scrollbar-thumb { background: var(--cc-border); border-radius: 3px; }
.messages-inner { max-width: 820px; margin: 0 auto; padding: 20px 24px; }
.welcome-wrapper { width: 100%; display: flex; justify-content: center; align-items: center; min-height: calc(100vh - 120px); }

/* ===== Streaming & Fade Animations ===== */
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
.streaming-fade { animation: fadeIn 0.4s ease-out; }

/* Smooth streaming text appearance */
.streaming-fade .message-text,
.streaming-fade .markdown-body {
  animation: streamReveal 0.3s ease-out;
}
@keyframes streamReveal {
  from { opacity: 0; transform: translateY(2px); }
  to { opacity: 1; transform: translateY(0); }
}

/* Streaming content segments fade in smoothly */
.streaming-fade .tool-call-card,
.streaming-fade .tool-result-card,
.streaming-fade .workflow-event-text {
  animation: fadeIn 0.35s ease-out;
}

/* ===== Reconnect Banner ===== */
.reconnect-banner {
  display: flex; align-items: center; gap: 8px; padding: 10px 16px;
  border-radius: var(--cc-radius-sm); margin: 8px 0; font-size: 13px;
}
.reconnect-banner.reconnecting { background: var(--cc-accent-light); color: var(--cc-accent); }
.reconnect-banner.error { background: #ffebee; color: var(--cc-danger); }
.chat-layout.dark .reconnect-banner.reconnecting { background: #1a2a44; }
.chat-layout.dark .reconnect-banner.error { background: #2a1a1c; }

/* ===== Fade Transition ===== */
.fade-enter-active, .fade-leave-active { transition: opacity 0.25s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

/* ===== Tool Cards ===== */
.tool-call-card { background: #fff8e6; border: 1px solid #ffe7a0; border-radius: var(--cc-radius-sm); margin: 6px 0; overflow: hidden; }
.chat-layout.dark .tool-call-card { background: #2a2518; border-color: #4a3f28; }
.tool-call-header { display: flex; align-items: center; gap: 6px; padding: 6px 12px; font-size: 12px; font-weight: 600; background: rgba(255,150,0,0.08); }
.tool-icon { color: var(--cc-accent); }
.tool-name { color: var(--cc-text-primary); }
.tool-args { margin: 0; padding: 8px 12px; font-size: 12px; line-height: 1.4; max-height: 200px; overflow-y: auto; color: var(--cc-text-secondary); }
.tool-result-card { background: #e8f5e9; border: 1px solid #a5d6a7; border-radius: var(--cc-radius-sm); margin: 6px 0; overflow: hidden; }
.chat-layout.dark .tool-result-card { background: #1a2a1c; border-color: #2a4a2c; }
.tool-result-header { display: flex; align-items: center; gap: 6px; padding: 6px 12px; font-size: 12px; font-weight: 600; background: rgba(52,199,89,0.08); }
.tool-result-content { margin: 0; padding: 8px 12px; font-size: 12px; line-height: 1.4; max-height: 200px; overflow-y: auto; color: var(--cc-text-secondary); }

/* Streaming content styles */
.message-row { display: flex; gap: 12px; margin-bottom: 20px; align-items: flex-start; }
.message-row.user { flex-direction: row; justify-content: flex-end; }
.message-avatar { flex-shrink: 0; margin-top: 2px; }
.assistant-avatar { background: var(--cc-accent) !important; }
.message-content { flex: 1; min-width: 0; max-width: 720px; }
.message-meta { font-size: 11px; color: var(--cc-text-muted); margin-bottom: 4px; }
.message-text { font-size: 14px; line-height: 1.7; color: var(--cc-text-primary); word-break: break-word; }
.agent-label { color: var(--cc-accent); font-weight: 500; }
.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3) { margin: 16px 0 8px; font-weight: 600; }
.markdown-body :deep(p) { margin: 6px 0; }
.markdown-body :deep(pre) { background: var(--cc-bg-tertiary); border-radius: var(--cc-radius-sm); padding: 12px 16px; overflow-x: auto; margin: 8px 0; font-size: 13px; line-height: 1.5; }
.markdown-body :deep(code) { background: var(--cc-bg-tertiary); padding: 2px 6px; border-radius: 4px; font-size: 13px; }
.markdown-body :deep(pre code) { background: none; padding: 0; }
.markdown-body :deep(table) { border-collapse: collapse; width: 100%; margin: 8px 0; }
.markdown-body :deep(th), .markdown-body :deep(td) { border: 1px solid var(--cc-border); padding: 8px 12px; text-align: left; }
.markdown-body :deep(th) { background: var(--cc-bg-tertiary); font-weight: 600; }
.workflow-event-text { font-size: 12px; color: var(--cc-text-muted); padding: 2px 0; font-style: italic; }
.typing-indicator { display: inline-flex; gap: 4px; padding: 4px 0; }
.typing-indicator span { width: 6px; height: 6px; border-radius: 50%; background: var(--cc-text-muted); animation: typing 1.4s infinite; }
.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }
@keyframes typing { 0%,60%,100% { opacity: 0.3; transform: translateY(0); } 30% { opacity: 1; transform: translateY(-4px); } }

/* ===== Admin Page ===== */
.admin-page-area { background: var(--cc-bg-secondary); padding: 0 !important; overflow-y: auto; }

/* ===== Mobile Components (hidden on PC) ===== */
.mobile-tab-bar { display: none; }
.mobile-session-btn { display: none; }
.mobile-overlay { display: none; }
.mobile-menu { display: none; }

/* ===== Responsive ===== */
@media (max-width: 767px) {
  .nav-bar { display: none !important; }
  
  .session-sidebar { position: fixed !important; top: 0; left: 0; bottom: 0; width: 280px !important; z-index: 1001; border-right: 1px solid var(--cc-border) !important; box-shadow: 4px 0 16px rgba(0,0,0,0.2); }
  .session-sidebar.mobile-hidden { display: none !important; }
  
  .content-container { width: 100% !important; }
  aside.session-sidebar { position: fixed !important; flex: none !important; flex-shrink: 0 !important; flex-grow: 0 !important; flex-basis: auto !important; min-width: 0 !important; max-width: none !important; }
  .chat-header { padding: 0 12px !important; height: 48px !important; }
  .mobile-session-btn { display: flex !important; }
  .header-title { font-size: 14px; }
  .messages-inner { padding: 12px 16px; max-width: 100%; }
  .message-content { max-width: calc(100vw - 60px); }
  .chat-container { padding-bottom: 56px; }
  .mobile-tab-bar {
    display: flex !important; position: fixed; bottom: 0; left: 0; right: 0;
    height: 56px; background: var(--cc-bg-primary); border-top: 1px solid var(--cc-border);
    z-index: 300; align-items: center; justify-content: space-around;
    padding-bottom: env(safe-area-inset-bottom, 0px); box-shadow: 0 -1px 6px rgba(0,0,0,0.06);
  }
  .chat-layout.dark .mobile-tab-bar { box-shadow: 0 -1px 6px rgba(0,0,0,0.3); }
  .tab-item {
    display: flex; flex-direction: column; align-items: center; justify-content: center;
    gap: 2px; cursor: pointer; color: var(--cc-text-muted); font-size: 10px;
    padding: 4px 12px; transition: color 0.15s; user-select: none;
  }
  .tab-item.active { color: var(--cc-accent); }
  .tab-item span { line-height: 1; }
  .main-container { height: calc(100vh - 56px) !important; }
  .mobile-overlay { display: block !important; position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.4); z-index: 250; }
  .mobile-menu {
    display: block !important; position: fixed; bottom: 56px; left: 8px; right: 8px;
    background: var(--cc-bg-primary); border-radius: 12px; box-shadow: 0 4px 20px rgba(0,0,0,0.15);
    z-index: 260; padding: 8px 0;
  }
  .more-menu { left: 50%; transform: translateX(-50%); right: auto; min-width: 160px; }
  .admin-menu { right: 8px; left: auto; min-width: 200px; }
  .mobile-menu-item {
    display: flex; align-items: center; gap: 10px; padding: 12px 16px;
    cursor: pointer; font-size: 14px; color: var(--cc-text-primary); transition: background 0.15s;
  }
  .mobile-menu-item:hover { background: var(--cc-bg-tertiary); }
  .mobile-menu-item .el-icon { color: var(--cc-text-secondary); }
  .admin-page-area { padding: 0 !important; }
  .markdown-body :deep(table) { display: block; overflow-x: auto; }
  .markdown-body :deep(pre) { font-size: 12px; padding: 8px 12px; -webkit-overflow-scrolling: touch; }

  /* === Mobile UX Improvements === */
  /* dvh fix for mobile browser address bar */
  .chat-layout { height: 100dvh; }
  .main-container { height: calc(100dvh - 56px) !important; }

  /* Safe area top for notch */
  .chat-header {
    padding-top: env(safe-area-inset-top, 0px) !important;
    height: calc(48px + env(safe-area-inset-top, 0px)) !important;
  }

  /* Message actions always visible on touch */
  .message-actions { opacity: 1 !important; }

  /* Touch target minimum 44px */
  .msg-action-btn { padding: 6px 12px; }
  .send-button { width: 44px !important; height: 44px !important; }

  /* User message bubble width */
  .message-row.user .message-content { max-width: 85% !important; }

  /* Tool cards overflow */
  .tool-card, .tool-call-card, .tool-result-card { max-width: 100%; overflow-x: auto; }
}
</style>

<style>
html, body { margin: 0; padding: 0; height: 100%; overflow: hidden; }
#app { height: 100%; overflow: hidden; }

/* ===== Global Dark Mode Fixes ===== */
.dark .hljs {
  background: #161b22 !important;
  color: #e6edf3;
}
.dark .markdown-body pre {
  background: #161b22;
}
.dark .markdown-body code {
  background: #262627;
  color: #e6edf3;
}
.dark .markdown-body pre code {
  background: none;
  color: #e6edf3;
}
.dark .markdown-body th {
  background: #262627;
  color: #e5eaf3;
}
.dark .markdown-body td {
  color: #e5eaf3;
}
.dark .markdown-body blockquote {
  border-left-color: #363637;
  color: #a3a6ad;
}

/* ===== Mobile sidebar (global, for teleported sidebar) ===== */
@media (max-width: 767px) {
  aside.session-sidebar {
    position: fixed !important;
    top: 0;
    left: 0;
    bottom: 56px;
    width: 280px !important;
    z-index: 1001;
    border-right: 1px solid var(--cc-border) !important;
    box-shadow: 4px 0 16px rgba(0,0,0,0.2);
  }
}

/* New nav action button */
.nav-bar-action-btn {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 16px; border-radius: 10px;
  cursor: pointer; transition: all 0.15s;
  color: var(--el-color-primary, #409eff);
  font-weight: 500; font-size: 14px;
  background: rgba(64,158,255,0.06);
  margin: 4px 8px;
}
.nav-bar-action-btn:hover { background: rgba(64,158,255,0.12); }
:global(.dark) .nav-bar-action-btn { background: rgba(51,112,255,0.12); }
:global(.dark) .nav-bar-action-btn:hover { background: rgba(51,112,255,0.2); }

/* Session list in nav */
.nav-session-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  scrollbar-width: thin;
  padding-top: 0;
}
.nav-session-list-collapsed { flex: 1; min-height: 0; overflow-y: auto; padding: 4px 0; }
.collapsed-session { padding: 8px !important; justify-content: center; }

/* Mobile session panel */
.mobile-session-panel {
  position: fixed; top: 0; left: 0; bottom: 0; width: 300px;
  background: var(--cc-bg-primary, #fff); z-index: 1002;
  border-right: 1px solid var(--el-border-color-lighter, #e4e7ed);
  display: flex; flex-direction: column;
  animation: slideIn 0.2s ease;
}
:global(.dark) .mobile-session-panel { background: #141414; border-right-color: #363637; }
.mobile-session-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px; font-weight: 600; font-size: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
}
@keyframes slideIn { from { transform: translateX(-100%); } to { transform: translateX(0); } }

/* ===== Dark Mode ===== */
.chat-layout.dark .nav-bar { background: #1e1f22; border-right-color: #2d2d2f; }
.chat-layout.dark .nav-brand { color: #e5eaf3; }
.chat-layout.dark .nav-version { color: #6b6b6b; }
.chat-layout.dark .nav-bar-item { color: #b0b0b0; }
.chat-layout.dark .nav-bar-item:hover { background: rgba(255,255,255,0.06); }
.chat-layout.dark .nav-bar-item.active { background: rgba(51,112,255,0.15); color: #5b9aff; }
.chat-layout.dark .nav-session-list { scrollbar-color: #444 transparent; }
.chat-layout.dark .nav-bar-divider { background: rgba(255,255,255,0.08); }
.chat-layout.dark .chat-header { background: #1a1a1a; border-bottom-color: #2d2d2f; }
.chat-layout.dark .header-title { color: #e5eaf3; }
.chat-layout.dark .messages-area { background: #0a0a0a; }
.chat-layout.dark .welcome-wrapper { color: #b0b0b0; }
.chat-layout.dark .input-area { background: #1a1a1a; border-top-color: #2d2d2f; }
.chat-layout.dark .message-row.user .message-content { background: #1d2b45; color: #e5eaf3; }
.chat-layout.dark .message-row.assistant .message-content { color: #cfd3dc; }
.chat-layout.dark .message-meta { color: #8b8b8b; }
.chat-layout.dark .agent-label { color: #8b8b8b; }
.chat-layout.dark .typing-indicator span { background: #666; }
.chat-layout.dark .tool-card { background: #1a1a1a; border-color: #2d2d2f; }
.chat-layout.dark .tool-card-header { color: #cfd3dc; }
.chat-layout.dark .workflow-event-text { color: #8b8b8b; }
.chat-layout.dark .reconnect-banner { background: #1a1a1a; color: #cfd3dc; }
.chat-layout.dark .locale-icon { color: #b0b0b0; }
.chat-layout.dark .nav-session-list-collapsed .collapsed-session { color: #b0b0b0; }
.chat-layout.dark .nav-session-list-collapsed .collapsed-session:hover { background: rgba(255,255,255,0.06); }
.chat-layout.dark .nav-session-list-collapsed .collapsed-session.active { background: rgba(51,112,255,0.15); color: #5b9aff; }
</style>
