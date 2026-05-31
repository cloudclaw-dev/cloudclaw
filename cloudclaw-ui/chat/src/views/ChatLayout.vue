<template>
  <div class="chat-layout" :class="{ dark: isDark }">
    <el-container class="main-container">
      <el-aside :width="navCollapsed ? '64px' : '200px'" class="nav-bar" :class="{ collapsed: navCollapsed }">
        <a href="http://cloudclaw.run" target="_blank" rel="noopener" class="nav-bar-header-link" style="text-decoration:none">
          <div class="nav-bar-header">
            <img src="@/assets/logo.png" alt="CC" class="nav-logo" />
            <span v-if="!navCollapsed" class="nav-brand">CloudClaw</span>
          </div>
          <div v-if="!navCollapsed" class="nav-version">v{{ systemVersion }} · {{ systemMode }}</div>
        </a>
        <div class="nav-bar-menu">
          <div class="nav-bar-item" :class="{ active: $route.path === '/' || $route.path === '' }" @click="$router.push('/')" :title="navCollapsed ? t('nav.chat') : ''">
            <el-icon :size="20"><ChatDotRound /></el-icon>
            <span class="nav-bar-label" :class="{ 'label-hidden': navCollapsed }">{{ $t('nav.chat') }}</span>
          </div>
          <div class="nav-bar-item" :class="{ active: $route.path === '/memory' }" @click="$router.push({ path: '/memory', query: { agentId: selectedAgentId } })" :title="navCollapsed ? t('nav.memory') : ''">
            <el-icon :size="20"><Memo /></el-icon>
            <span class="nav-bar-label" :class="{ 'label-hidden': navCollapsed }">{{ $t('nav.memory') }}</span>
          </div>
          <template v-if="isAdmin">
            <div class="nav-bar-divider" />
            <div v-for="item in adminMenuItems" :key="item.path" class="nav-bar-item" :class="{ active: $route.path === item.path }" @click="$router.push(item.path)" :title="navCollapsed ? item.title : ''">
              <el-icon :size="20"><component :is="item.icon" /></el-icon>
              <span class="nav-bar-label" :class="{ 'label-hidden': navCollapsed }">{{ item.title }}</span>
            </div>
          </template>
        </div>
        <div class="nav-bar-bottom">
          <div class="nav-bar-divider" />
          <div class="nav-bar-item" @click="toggleLocale">
            <span class="locale-icon">{{ locale === 'zh' ? 'EN' : '中' }}</span>
            <span class="nav-bar-label" :class="{ 'label-hidden': navCollapsed }">{{ locale === 'zh' ? 'English' : '中文' }}</span>
          </div>
          <div class="nav-bar-item" @click="toggleDark" :title="isDark ? 'Light' : 'Dark'">
            <el-icon :size="18"><component :is="isDark ? Sunny : Moon" /></el-icon>
            <span class="nav-bar-label" :class="{ 'label-hidden': navCollapsed }">{{ isDark ? $t('login.light') : $t('login.dark') }}</span>
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

      <el-container class="content-container">
        <template v-if="!showAdminPage">
          <div v-if="isMobile && showMobileSessions" class="mobile-session-overlay" @click="showMobileSessions = false" />
          <SessionSidebar
            :agents="agents"
            :sessions="sessions"
            :currentSessionId="currentSessionId"
            :selectedAgentId="selectedAgentId"
            :isMobile="isMobile"
            :showMobileSessions="showMobileSessions"
            @update:selectedAgentId="handleAgentChange"
            @selectSession="handleSelectSession"
            @deleteSession="deleteSession"
            @newSession="showNewSessionDialog = true"
            @renameSession="renameSession"
            @pinSession="pinSession"
            @unpinSession="unpinSession"
            @batchDelete="batchDeleteSessions"
            @batchPin="batchPinSessions"
          />
          <el-container class="chat-container">
            <el-header class="chat-header" height="56px">
              <div class="chat-header-info">
                <el-button v-if="isMobile" :icon="Operation" circle size="small" text class="mobile-session-btn" @click="showMobileSessions = !showMobileSessions" />
                <el-icon v-if="!isMobile" :size="18" class="chat-header-icon-text"><ChatLineSquare /></el-icon>
                <span class="header-title">{{ currentSessionTitle || 'CloudClaw Chat' }}</span>
              </div>
              <div>
                <el-tag v-if="activeAgentName" type="warning" size="small" effect="plain" style="margin-right: 8px">{{ activeAgentName }}</el-tag>
                <el-tag v-if="currentAgent" type="info" size="small" effect="plain">{{ currentAgent.name }}</el-tag>
              </div>
            </el-header>
            <el-main class="messages-area" ref="messagesAreaRef">
              <div class="messages-inner">
                <transition name="fade" mode="out-in">
                  <div v-if="!currentSessionId" key="welcome" class="welcome-wrapper">
                    <WelcomeSection @newSession="showNewSessionDialog = true" @startWithPrompt="startWithPrompt" />
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
                            <div v-if="seg.type === 'tool_call'" class="tool-call-card">
                              <div class="tool-call-header"><el-icon class="tool-icon"><SetUp /></el-icon><span class="tool-name">{{ seg.toolName }}</span></div>
                              <pre v-if="seg.content" class="tool-args">{{ formatToolArgs(seg.content) }}</pre>
                            </div>
                            <div v-else-if="seg.type === 'tool_result'" class="tool-result-card">
                              <div class="tool-result-header"><el-icon class="tool-icon"><Finished /></el-icon><span>{{ seg.toolName }} result</span></div>
                              <pre class="tool-result-content">{{ seg.content }}</pre>
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
              </div>
            </el-main>
            <ChatInput
              v-model="inputMessage"
              :isStreaming="isStreaming"
              :contextStats="contextStats"
              @send="sendMessage"
              @stop="stopGeneration"
            />
          </el-container>
        </template>
        <el-main v-else class="admin-page-area"><router-view /></el-main>
      </el-container>
    </el-container>

    <el-dialog v-model="showNewSessionDialog" :title="$t('chat.newSession')" width="400px" :close-on-click-modal="true">
      <el-form label-position="top">
        <el-form-item label="Agent">
          <el-select v-model="newSessionAgentId" :placeholder="$t('chat.selectAgent')" style="width: 100%">
            <el-option v-for="agent in agents" :key="agent.id" :label="agent.name" :value="agent.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('chat.title')">
          <el-input v-model="newSessionTitle" :placeholder="$t('chat.sessionTitlePlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showNewSessionDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :disabled="!newSessionAgentId" @click="createNewSession">{{ $t('chat.newSession') }}</el-button>
      </template>
    </el-dialog>

    <div class="mobile-tab-bar">
      <div class="tab-item" :class="{ active: mobileTab === 'chat' && !showAdminPage }" @click="handleMobileTab('chat')">
        <el-icon :size="22"><ChatDotRound /></el-icon>
        <span>{{ $t('nav.chat') }}</span>
      </div>
      <div class="tab-item" :class="{ active: mobileTab === 'memory' || showMobileMore }" @click="handleMobileMore">
        <el-icon :size="22"><More /></el-icon>
        <span>{{ $t('common.more') }}</span>
      </div>
      <div v-if="isAdmin" class="tab-item" :class="{ active: showMobileAdmin || showAdminPage }" @click="handleMobileTab('admin')">
        <el-icon :size="22"><Setting /></el-icon>
        <span>{{ $t('common.manage') }}</span>
      </div>
    </div>

    <div v-if="showMobileMore" class="mobile-overlay" @click="showMobileMore = false" />
    <div v-if="showMobileMore" class="mobile-menu more-menu">
      <div class="mobile-menu-item" @click="showMobileMore = false; mobileTab = 'memory'; router.push({ path: '/memory', query: { agentId: selectedAgentId } })">
        <el-icon><Memo /></el-icon><span>{{ $t('nav.memory') }}</span>
      </div>
      <div class="mobile-menu-item" @click="toggleLocale(); showMobileMore = false">
        <span class="locale-icon">{{ locale === 'zh' ? 'EN' : '中' }}</span><span>{{ locale === 'zh' ? 'English' : '中文' }}</span>
      </div>
      <div class="mobile-menu-item" @click="toggleDark(); showMobileMore = false">
        <el-icon><component :is="isDark ? Sunny : Moon" /></el-icon><span>{{ isDark ? t('login.light') : t('login.dark') }}</span>
      </div>
      <div class="mobile-menu-item" @click="handleLogout(); showMobileMore = false">
        <el-icon><SwitchButton /></el-icon><span>{{ $t('nav.logout') }}</span>
      </div>
    </div>

    <div v-if="showMobileAdmin" class="mobile-overlay" @click="showMobileAdmin = false" />
    <div v-if="showMobileAdmin" class="mobile-menu admin-menu">
      <div v-for="item in adminMenuItems" :key="item.path" class="mobile-menu-item" @click="handleMobileAdminNav(item.path)">
        <el-icon><component :is="item.icon" /></el-icon><span>{{ item.title }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch, provide } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ChatDotRound, ChatLineSquare, Memo, Sunny, Moon, SwitchButton, Fold,
  Monitor, SetUp, Finished, Operation, More, Setting, Odometer, User,
  Connection, Reading, Cpu, Loading, Check, Clock, Right, Grid, Close
} from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import python from 'highlight.js/lib/languages/python'
import java from 'highlight.js/lib/languages/java'
import bash from 'highlight.js/lib/languages/bash'
import sql from 'highlight.js/lib/languages/sql'
import json from 'highlight.js/lib/languages/json'
import yaml from 'highlight.js/lib/languages/yaml'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import html from 'highlight.js/lib/languages/xml'
import plaintext from 'highlight.js/lib/languages/plaintext'
import 'highlight.js/styles/github.css'
import 'highlight.js/styles/github-dark.css'
import api from '@/api/index'
import { sessionApi, messageApi, agentApi, sendChatMessage, sendMessageAsync, pollMessages } from '@/api/chat'
import { useI18n } from 'vue-i18n'
import SessionSidebar from '@/components/SessionSidebar.vue'
import MessageBubble from '@/components/MessageBubble.vue'
import ChatInput from '@/components/ChatInput.vue'
import WelcomeSection from '@/components/WelcomeSection.vue'
import WorkflowPanel from '@/components/WorkflowPanel.vue'
import SkeletonScreen from '@/components/SkeletonScreen.vue'

hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('js', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('ts', typescript)
hljs.registerLanguage('python', python)
hljs.registerLanguage('py', python)
hljs.registerLanguage('java', java)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('shell', bash)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('json', json)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('yml', yaml)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', html)
hljs.registerLanguage('css', css)
hljs.registerLanguage('plaintext', plaintext)
hljs.registerLanguage('text', plaintext)

// ===== Types =====
interface Agent { id: string; name: string; description?: string; systemPrompt?: string }
interface Session { id: string; title: string; agentId: string; createdAt: string; updatedAt: string }
interface MessageSegment { type: 'text' | 'tool_call' | 'tool_result' | 'workflow_status'; content: string; toolName?: string }
interface Message { role: 'user' | 'assistant'; content: string; segments?: MessageSegment[]; createdAt?: string; agentName?: string }
interface WorkflowStepStatus { name: string; status: 'pending' | 'running' | 'done' }
interface WorkflowState { mode: string; steps: WorkflowStepStatus[]; activeNode: string; supervisorAction: string; mergeStatus: string; reason: string }

// ===== State =====
const router = useRouter()
const isDark = ref(false)
const navCollapsed = ref(false)
const agents = ref<Agent[]>([])
const sessions = ref<Session[]>([])
const systemVersion = ref('1.0.4')
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

const showNewSessionDialog = ref(false)
const newSessionAgentId = ref('')
const newSessionTitle = ref('')
const selectedAgentId = ref('')

const mobileTab = ref<'chat' | 'memory' | 'admin'>('chat')
const showMobileSessions = ref(true)
const showMobileMore = ref(false)
const showMobileAdmin = ref(false)
const isMobile = ref(false)
const showUserMenu = ref(false)

const { t, locale } = useI18n()

// Provide isDark for child components via inject
provide('isDark', isDark)
provide('isMobile', isMobile)

// ===== Markdown Renderer =====
const md = new MarkdownIt({
  html: false, linkify: true, typographer: true,
  highlight(str: string, lang: string) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        const highlighted = hljs.highlight(str, { language: lang, ignoreIllegals: true }).value
        return `<pre class="hljs" data-lang="${lang}"><code data-code="${lang}">${highlighted}</code></pre>`
      } catch (_) { /* fallback */ }
    }
    return '<pre class="hljs"><code>' + md.utils.escapeHtml(str) + '</code></pre>'
  }
})

const renderMarkdown = (content: string): string => content ? md.render(content) : ''
const formatToolArgs = (args: string): string => { try { return JSON.stringify(JSON.parse(args), null, 2) } catch { return args } }

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

const showAdminPage = computed(() => { const p = router.currentRoute.value.path; return p !== '/' && p !== '' })

const adminMenuItems = computed(() => [
  { path: '/dashboard', title: t('dashboard.title'), icon: Odometer },
  { path: '/agents', title: t('nav.agent'), icon: SetUp },
  { path: '/mcp-servers', title: t('nav.mcp'), icon: Connection },
  { path: '/skills', title: t('nav.skill'), icon: Reading },
  { path: '/llm', title: t('nav.llm'), icon: Cpu },
  { path: '/users', title: t('nav.user'), icon: User },
  { path: '/sandboxes', title: t('nav.sandbox'), icon: Grid },
  { path: '/monitor', title: t('nav.monitor'), icon: Monitor }
])

const currentSessionTitle = computed(() => {
  if (!currentSessionId.value) return 'CloudClaw Chat'
  return sessions.value.find(s => s.id === currentSessionId.value)?.title || 'Untitled Chat'
})

const currentAgent = computed(() => {
  const session = sessions.value.find(s => s.id === currentSessionId.value)
  return session ? agents.value.find(a => a.id === session.agentId) || null : null
})

// ===== Locale & Theme =====
const toggleLocale = () => { locale.value = locale.value === 'zh' ? 'en' : 'zh'; localStorage.setItem('cloudclaw-locale', locale.value) }
const toggleDark = () => {
  isDark.value = !isDark.value
  document.documentElement.classList.toggle('dark', isDark.value)
  localStorage.setItem('theme', isDark.value ? 'dark' : 'light')
}

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
    const res: any = await sessionApi.list(1, 50, selectedAgentId.value || undefined)
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
  loadMessages(id)
}

const handleAgentChange = async (id: string) => {
  selectedAgentId.value = id
  currentSessionId.value = ''; messages.value = []
  streamingContent.value = ''; streamingSegments.value = []; workflowState.value = null; activeAgentName.value = ''; isStreaming.value = false
  if (abortController) { abortController.abort(); abortController = null }
  newSessionAgentId.value = id
  await loadSessions()
}

const createNewSession = async () => {
  if (!newSessionAgentId.value) return
  try {
    const res: any = await sessionApi.create({ agentId: newSessionAgentId.value, title: newSessionTitle.value || undefined })
    const newSession = res.data || res
    showNewSessionDialog.value = false; newSessionTitle.value = ''
    await loadSessions()
    if (newSession?.id) handleSelectSession(newSession.id)
    ElMessage.success(t('chat.sessionCreated'))
  } catch (e) { /* handled by interceptor */ }
}

const deleteSession = async (sessionId: string) => {
  try {
    await ElMessageBox.confirm('Are you sure you want to delete this conversation?', 'Delete Conversation', { confirmButtonText: 'Delete', cancelButtonText: 'Cancel', type: 'warning' })
    await sessionApi.delete(sessionId)
    sessions.value = sessions.value.filter(s => s.id !== sessionId)
    if (currentSessionId.value === sessionId) { currentSessionId.value = ''; messages.value = [] }
    ElMessage.success(t('chat.sessionDeleted'))
  } catch (e) { /* cancelled or error */ }
}

const renameSession = async (id: string, title: string) => {
  try {
    await sessionApi.create({ agentId: selectedAgentId.value, title })
    const session = sessions.value.find(s => s.id === id)
    if (session) session.title = title
  } catch (e) { /* handled */ }
}

const pinSession = (_id: string) => { /* pin handled in SessionSidebar localStorage */ }
const unpinSession = (_id: string) => { /* unpin handled in SessionSidebar localStorage */ }
const batchDeleteSessions = async (ids: string[]) => {
  try {
    for (const id of ids) await sessionApi.delete(id)
    sessions.value = sessions.value.filter(s => !ids.includes(s.id))
    if (ids.includes(currentSessionId.value)) { currentSessionId.value = ''; messages.value = [] }
    ElMessage.success(t('chat.sessionDeleted'))
  } catch (e) { /* handled */ }
}
const batchPinSessions = (_ids: string[]) => { /* handled in SessionSidebar */ }

// ===== SSE with Reconnection =====
const sendWithRetry = async (sessionId: string, msg: string, attempt = 0): Promise<void> => {
  const maxRetries = 3
  const delays = [1000, 2000, 4000]
  return new Promise((resolve, reject) => {
    sendChatMessage(sessionId, msg, onChunk, onDone, onError, abortController?.signal)
    function onChunk(chunk: any) {
      reconnectStatus.value = null
      handleChunk(chunk)
    }
    function onDone(stats?: any) {
      reconnectStatus.value = null
      if (streamingContent.value || streamingSegments.value.length > 0) {
        messages.value.push({ role: 'assistant', content: streamingContent.value, segments: [...streamingSegments.value] })
      }
      streamingContent.value = ''; streamingSegments.value = []; workflowState.value = null; isStreaming.value = false
      if (stats) contextStats.value = stats
      scrollToBottom(); startTitleRefresh(); resolve()
    }
    function onError(_error: any) {
      if (attempt < maxRetries) {
        reconnectStatus.value = { type: 'reconnecting', message: t('chat.reconnecting') + ` (${attempt + 1}/${maxRetries})...` }
        setTimeout(async () => {
          try { await sendWithRetry(sessionId, msg, attempt + 1); resolve() } catch (e) { reject(e) }
        }, delays[attempt])
      } else {
        reconnectStatus.value = { type: 'error', message: t('chat.connectionLost') || 'Connection lost. Please try again.' }
        isStreaming.value = false; streamingContent.value = ''; streamingSegments.value = []; workflowState.value = null
        setTimeout(() => { reconnectStatus.value = null }, 5000)
        reject(_error)
      }
    }
  })
}

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
    streamingSegments.value.push({ type: 'tool_call', content: text, toolName: chunk.toolName || 'tool' })
  } else if (type === 'tool_result') {
    streamingSegments.value.push({ type: 'tool_result', content: text, toolName: chunk.toolName || 'tool' })
  } else if (text) {
    const last = streamingSegments.value[streamingSegments.value.length - 1]
    if (last && last.type === 'text') last.content += text
    else streamingSegments.value.push({ type: 'text', content: text })
    streamingContent.value += text
  }
  scrollToBottom()
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
  try { await sendWithRetry(currentSessionId.value, msg) } catch (error) { /* handled in retry */ }
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
  if (abortController) { abortController.abort(); abortController = null }
  if (streamingContent.value || streamingSegments.value.length > 0) {
    messages.value.push({ role: 'assistant', content: streamingContent.value, segments: [...streamingSegments.value] })
  }
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
onMounted(async () => {
  const savedTheme = localStorage.getItem('theme')
  if (savedTheme === 'dark') { isDark.value = true; document.documentElement.classList.add('dark') }
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
})

onUnmounted(() => {
  if (titleRefreshTimer) { clearInterval(titleRefreshTimer); titleRefreshTimer = null }
  window.removeEventListener('resize', checkMobile)
  document.removeEventListener('click', handleClickOutside)
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
  --cc-bg-primary: #ffffff;
  --cc-bg-secondary: #f7f8fa;
  --cc-bg-tertiary: #eef0f4;
  --cc-text-primary: #1f2329;
  --cc-text-secondary: #646a73;
  --cc-text-muted: #8f959e;
  --cc-border: #e8eaed;
  --cc-shadow: 0 1px 3px rgba(0,0,0,0.06);
  --cc-radius: 10px;
  --cc-radius-sm: 6px;
  --cc-accent: #3370ff;
  --cc-accent-light: #e8f0ff;
  --cc-accent-hover: #2860e1;
  --cc-success: #34c759;
  --cc-warning: #ff9500;
  --cc-danger: #ff3b30;
}
.chat-layout.dark {
  --cc-bg-primary: #1d1e1f;
  --cc-bg-secondary: #141414;
  --cc-bg-tertiary: #262627;
  --cc-text-primary: #e5eaf3;
  --cc-text-secondary: #a3a6ad;
  --cc-text-muted: #636569;
  --cc-border: #363637;
  --cc-shadow: 0 1px 3px rgba(0,0,0,0.3);
  --cc-accent-light: #1a2a44;
}
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
.nav-bar-header { padding: 14px 0 10px; display: flex; align-items: center; justify-content: center; gap: 10px; white-space: nowrap; overflow: hidden; }
.nav-brand { font-size: 16px; font-weight: 700; color: var(--cc-text-primary); }
.nav-version { font-size: 10px; color: var(--cc-text-tertiary, #999); text-align: center; margin-top: -4px; margin-bottom: 4px; letter-spacing: 0.5px; }
.nav-bar.collapsed .nav-bar-header { justify-content: center; }
.nav-logo { width: 32px; height: 32px; border-radius: 8px; flex-shrink: 0; }
.nav-bar-menu { flex: 1; display: flex; flex-direction: column; gap: 2px; padding: 4px 8px; overflow-y: auto; }
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

/* ===== Messages Area ===== */
.messages-area { flex: 1; overflow-y: auto; padding: 0 !important; background: var(--cc-bg-secondary); }
.messages-area::-webkit-scrollbar { width: 6px; }
.messages-area::-webkit-scrollbar-thumb { background: var(--cc-border); border-radius: 3px; }
.messages-inner { max-width: 820px; margin: 0 auto; padding: 20px 24px; }
.welcome-wrapper { width: 100%; }

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
  .mobile-session-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.4); z-index: 199; }
  .session-sidebar { position: fixed !important; top: 0; left: 0; bottom: 0; width: 280px !important; z-index: 200; border-right: 1px solid var(--cc-border) !important; transition: transform 0.25s ease; }
  .session-sidebar.mobile-hidden { transform: translateX(-100%); display: flex !important; }
  .content-container { width: 100% !important; }
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
  .markdown-body :deep(pre) { font-size: 12px; padding: 8px 12px; }
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
</style>
