<template>
  <div class="chat-layout" :class="{ dark: isDark }">
    <el-container class="main-container">
      <!-- Left Nav Bar -->
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
          <el-aside width="280px" class="session-sidebar" :class="{ 'mobile-hidden': isMobile && !showMobileSessions }">
            <div class="sidebar-page-header">
              <div class="header-icon chat-header-icon"><el-icon><ChatDotRound /></el-icon></div>
              <div>
                <h2>{{ $t('nav.chat') }}</h2>
                <div class="header-desc">{{ $t('nav.chat') }}</div>
              </div>
            </div>
            <div class="sidebar-toolbar">
              <el-select v-model="selectedAgentId" :placeholder="$t('chat.selectAgent')" class="agent-select" size="small">
                <template #prefix><el-icon><SetUp /></el-icon></template>
                <el-option v-for="agent in agents" :key="agent.id" :label="agent.name" :value="agent.id" />
              </el-select>
              <el-button type="primary" :icon="Plus" size="small" @click="showNewSessionDialog = true">{{ $t('chat.newSession') }}</el-button>
            </div>
            <div class="session-list">
              <div v-for="session in sessions" :key="session.id" class="session-item" :class="{ active: currentSessionId === session.id }" @click="isMobile ? handleMobileSelectSession(session.id) : selectSession(session.id)">
                <div class="session-info"><span class="session-title">{{ session.title || 'New Chat' }}</span></div>
                <el-button :icon="Delete" circle size="small" text class="session-delete" @click.stop="deleteSession(session.id)" />
              </div>
              <div v-if="sessions.length === 0" class="no-sessions"><p>{{ $t('chat.noSession') }}</p></div>
            </div>
          </el-aside>
          <el-container class="chat-container">
            <el-header class="chat-header" height="56px">
              <div class="chat-header-info">
                <el-button v-if="isMobile" :icon="Operation" circle size="small" text class="mobile-session-btn" @click="showMobileSessions = !showMobileSessions" />
                <el-icon v-if="!isMobile" :size="18" class="chat-header-icon-text"><ChatLineSquare /></el-icon>
                <span class="header-title">{{ currentSessionTitle || 'CloudClaw Chat' }}</span>
              </div>
              <el-tag v-if="activeAgentName" type="warning" size="small" effect="plain" style="margin-right: 8px">{{ activeAgentName }}</el-tag>
              <el-tag v-if="currentAgent" type="info" size="small" effect="plain">{{ currentAgent.name }}</el-tag>
            </el-header>
            <el-main class="messages-area" ref="messagesAreaRef">
              <div class="messages-inner">
                <div v-if="!currentSessionId" class="welcome-section">
                  <div class="welcome-icon"><el-icon :size="48"><Promotion /></el-icon></div>
                  <h2>Welcome to CloudClaw</h2>
                  <p>{{ $t('chat.newChat') }}</p>
                  <el-button type="primary" size="large" @click="showNewSessionDialog = true"><el-icon><Plus /></el-icon> {{ $t('chat.newSession') }}</el-button>
                </div>
                <template v-if="currentSessionId">
                  <div v-for="(msg, index) in messages" :key="index" class="message-row" :class="msg.role">
                    <template v-if="msg.role === 'user'">
                      <div class="message-content user-msg-content">
                        <div class="message-meta">You · {{ formatTime(msg.createdAt) }}</div>
                        <div class="message-text">{{ msg.content }}</div>
                      </div>
                      <div class="message-avatar">
                        <el-avatar :size="32" :icon="UserFilled" />
                      </div>
                    </template>
                    <template v-else>
                      <div class="message-avatar">
                        <el-avatar :size="32" class="assistant-avatar"><el-icon><Monitor /></el-icon></el-avatar>
                      </div>
                      <div class="message-content">
                        <div class="message-meta">Assistant<span v-if="msg.agentName" class="agent-label"> · {{ msg.agentName }}</span> · {{ formatTime(msg.createdAt) }}</div>
                        <template v-if="msg.segments && msg.segments.length > 0">
                          <template v-for="(seg, si) in msg.segments" :key="si">
                            <div v-if="seg.type === 'tool_call'" class="tool-call-card">
                              <div class="tool-call-header"><el-icon class="tool-icon"><SetUp /></el-icon><span class="tool-name">{{ seg.toolName }}</span></div>
                              <pre v-if="seg.content" class="tool-args">{{ formatToolArgs(seg.content) }}</pre>
                            </div>
                            <div v-else-if="seg.type === 'tool_result'" class="tool-result-card">
                              <div class="tool-result-header"><el-icon class="tool-icon"><Finished /></el-icon><span>{{ seg.toolName }} result</span></div>
                              <pre class="tool-result-content">{{ seg.content }}</pre>
                            </div>
                            <div v-else class="message-text markdown-body" v-html="renderMarkdown(seg.content)" />
                          </template>
                        </template>
                        <div v-else class="message-text markdown-body" v-html="renderMarkdown(msg.content)" />
                        <div class="message-meta">{{ formatTime(msg.createdAt) }}</div>
                      </div>
                    </template>
                  </div>
                  <div v-if="isStreaming && (streamingContent || streamingSegments.length > 0 || workflowState)" class="message-row assistant">
                    <div class="message-avatar"><el-avatar :size="32" class="assistant-avatar"><el-icon><Monitor /></el-icon></el-avatar></div>
                    <div class="message-content">
                      <div class="message-meta">Assistant<span v-if="activeAgentName" class="agent-label"> · {{ activeAgentName }}</span></div>
                      <!-- Workflow V3 Status Panel -->
                      <div v-if="workflowState" class="workflow-panel">
                        <div class="workflow-panel-header">
                          <el-icon class="workflow-panel-icon"><SetUp /></el-icon>
                          <span class="workflow-panel-title">
                            <template v-if="workflowState.mode === 'pipeline'">{{ t('chat.workflowPipeline').replace(': ', '') }}</template>
                            <template v-else-if="workflowState.mode === 'parallel'">{{ t('chat.workflowParallel').replace(': ', '') }}</template>
                            <template v-else-if="workflowState.mode === 'router'">{{ t('chat.workflowRouter').replace(': ', '').replace(':','') }}</template>
                            <template v-else-if="workflowState.mode === 'supervisor'">{{ t('chat.workflowSupervisor').replace(': ', '') }}</template>
                            <template v-else-if="workflowState.mode === 'handoff'">{{ t('chat.workflowHandoff').replace(': ', '').replace(':','') }}</template>
                          </span>
                          <span class="workflow-panel-mode-tag">{{ workflowState.mode }}</span>
                        </div>
                        <div class="workflow-panel-body">
                          <!-- Pipeline -->
                          <template v-if="workflowState.mode === 'pipeline'">
                            <div class="workflow-steps-row">
                              <template v-for="(step, si) in workflowState.steps" :key="si">
                                <div v-if="si > 0" class="workflow-step-arrow"><el-icon><Right /></el-icon></div>
                                <div class="workflow-step-item" :class="'step-' + step.status">
                                  <span v-if="step.status === 'running'" class="step-icon"><el-icon class="is-loading"><Loading /></el-icon></span>
                                  <span v-else-if="step.status === 'done'" class="step-icon"><el-icon style="color: var(--cc-success)"><Check /></el-icon></span>
                                  <span v-else class="step-icon"><el-icon><Clock /></el-icon></span>
                                  <span class="step-label">{{ step.name }}</span>
                                </div>
                              </template>
                            </div>
                          </template>
                          <!-- Parallel -->
                          <template v-else-if="workflowState.mode === 'parallel'">
                            <div class="workflow-parallel-grid">
                              <div v-for="(step, si) in workflowState.steps" :key="si" class="workflow-parallel-node" :class="'step-' + step.status">
                                <span v-if="step.status === 'running'" class="step-icon"><el-icon class="is-loading"><Loading /></el-icon></span>
                                <span v-else-if="step.status === 'done'" class="step-icon"><el-icon style="color: var(--cc-success)"><Check /></el-icon></span>
                                <span v-else class="step-icon"><el-icon><Clock /></el-icon></span>
                                <span class="step-label">{{ step.name }}</span>
                              </div>
                            </div>
                            <div v-if="workflowState.mergeStatus === 'merging'" class="workflow-merge-row">
                              <el-icon class="is-loading" style="color: var(--cc-warning)"><Loading /></el-icon>
                              <span class="merge-label">{{ t('chat.workflowParallelMerging') }}</span>
                            </div>
                          </template>
                          <!-- Router -->
                          <template v-else-if="workflowState.mode === 'router'">
                            <div class="workflow-router-info">
                              <el-tag type="primary" effect="plain" size="small" class="router-target-tag">
                                <el-icon style="margin-right:4px"><Right /></el-icon>{{ workflowState.activeNode }}
                              </el-tag>
                              <span v-if="workflowState.reason" class="workflow-router-reason">{{ workflowState.reason }}</span>
                            </div>
                          </template>
                          <!-- Supervisor -->
                          <template v-else-if="workflowState.mode === 'supervisor'">
                            <div v-if="workflowState.steps.length > 0" class="workflow-supervisor-steps">
                              <div v-for="(step, si) in workflowState.steps" :key="si" class="workflow-supervisor-step" :class="'step-' + step.status">
                                <span v-if="step.status === 'running'" class="step-icon"><el-icon class="is-loading"><Loading /></el-icon></span>
                                <span v-else-if="step.status === 'done'" class="step-icon"><el-icon style="color: var(--cc-success)"><Check /></el-icon></span>
                                <span v-else class="step-icon"><el-icon><Clock /></el-icon></span>
                                <span class="step-label">{{ step.name }}</span>
                              </div>
                            </div>
                            <div v-if="workflowState.activeNode" class="workflow-supervisor-delegate">
                              <el-tag type="warning" effect="plain" size="small">
                                <el-icon style="margin-right:4px"><Promotion /></el-icon>{{ workflowState.activeNode }}
                              </el-tag>
                            </div>
                            <div v-if="workflowState.supervisorAction" class="workflow-supervisor-action">
                              <el-icon style="margin-right:4px"><Monitor /></el-icon>{{ workflowState.supervisorAction }}
                            </div>
                          </template>
                          <!-- Handoff -->
                          <template v-else-if="workflowState.mode === 'handoff'">
                            <div class="workflow-handoff-info">
                              <el-tag type="success" effect="plain" size="small">
                                <el-icon style="margin-right:4px"><Promotion /></el-icon>{{ workflowState.activeNode }}
                              </el-tag>
                            </div>
                          </template>
                        </div>
                      </div>
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
                        <div v-else class="message-text markdown-body streaming" v-html="renderMarkdown(seg.content)" />
                      </template>
                      <span class="cursor-blink">|</span>
                    </div>
                  </div>
                  <div v-if="isStreaming && !streamingContent && streamingSegments.length === 0" class="message-row assistant">
                    <div class="message-avatar"><el-avatar :size="32" class="assistant-avatar"><el-icon><Monitor /></el-icon></el-avatar></div>
                    <div class="message-content">
                      <div class="message-meta">Assistant<span v-if="activeAgentName" class="agent-label"> · {{ activeAgentName }}</span></div>
                      <div class="message-text"><div class="typing-indicator"><span></span><span></span><span></span></div></div>
                    </div>
                  </div>
                </template>
              </div>
            </el-main>
            <el-footer class="input-area" height="auto">
              <div class="input-wrapper">
                <el-input v-model="inputMessage" type="textarea" :autosize="{ minRows: 1, maxRows: 6 }" :placeholder="$t('chat.inputPlaceholder')" :disabled="isStreaming" @keydown="handleInputKeydown" />
                <el-button v-if="!isStreaming" type="primary" :icon="Promotion" circle class="send-button" :disabled="!inputMessage.trim()" @click="sendMessage" />
                <el-button v-else type="danger" :icon="VideoPause" circle class="send-button" @click="stopGeneration" />
              </div>
              <div v-if="contextStats" class="context-bar">
                <div class="context-bar-track">
                  <div class="context-bar-fill" :class="{ 'context-ok': contextStats.usagePercent < 50, 'context-warn': contextStats.usagePercent >= 50 && contextStats.usagePercent < 80, 'context-danger': contextStats.usagePercent >= 80 }" :style="{ width: contextStats.usagePercent + '%' }" />
                </div>
                <span class="context-bar-label">Context: {{ formatTokens(contextStats.totalTokens) }} / {{ formatTokens(contextStats.maxTokens) }} ({{ contextStats.usagePercent }}%)</span>
              </div>
            </el-footer>
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

    <!-- Mobile Tab Bar (hidden on PC) -->
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

    <!-- Mobile More Menu -->
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

    <!-- Mobile Admin Menu -->
    <div v-if="showMobileAdmin" class="mobile-overlay" @click="showMobileAdmin = false" />
    <div v-if="showMobileAdmin" class="mobile-menu admin-menu">
      <div v-for="item in adminMenuItems" :key="item.path" class="mobile-menu-item" @click="handleMobileAdminNav(item.path)">
        <el-icon><component :is="item.icon" /></el-icon><span>{{ item.title }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  Delete,
  ChatDotRound,
  ChatLineSquare,
  Memo,
  Sunny,
  Moon,
  SwitchButton,
  Fold,
  Promotion,
  Monitor,
  UserFilled,
  SetUp,
  Finished,
  VideoPause,
  ArrowLeft,
  Operation,
  More,
  Setting,
  Odometer,
  User,
  Connection,
  Reading,
  Cpu,
  Loading,
  Check,
  Clock,
  Right,
  Grid
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
import 'highlight.js/styles/github.css'
import 'highlight.js/styles/github-dark.css'
import api from '@/api/index'
import {
  sessionApi,
  messageApi,
  agentApi,
  sendChatMessage,
  sendMessageAsync,
  pollMessages
} from '@/api/chat'
import { useI18n } from 'vue-i18n'

// ===== Types =====
interface Agent {
  id: string
  name: string
  description?: string
  systemPrompt?: string
}

interface Session {
  id: string
  title: string
  agentId: string
  createdAt: string
  updatedAt: string
}

interface MessageSegment {
  type: 'text' | 'tool_call' | 'tool_result' | 'workflow_status'
  content: string
  toolName?: string
}

interface Message {
  role: 'user' | 'assistant'
  content: string
  segments?: MessageSegment[]
  createdAt?: string
}

// ===== State =====
const router = useRouter()
const isDark = ref(false)
const navCollapsed = ref(false)
const sidebarCollapsed = ref(false)
const agents = ref<Agent[]>([])
const sessions = ref<Session[]>([])
const systemVersion = ref('1.0.1')
const systemMode = ref('standalone')
const currentSessionId = ref('')
const messages = ref<Message[]>([])
const inputMessage = ref('')
const isStreaming = ref(false)
const contextStats = ref<any>(null)
// Agent Transfer v2: track current active agent
const activeAgentName = ref('')
const streamingContent = ref('')
const streamingSegments = ref<MessageSegment[]>([])
const messagesAreaRef = ref<HTMLElement | null>(null)

// Workflow V3: track workflow status during streaming
interface WorkflowStepStatus {
  name: string
  status: 'pending' | 'running' | 'done'
}
interface WorkflowState {
  mode: string
  steps: WorkflowStepStatus[]
  activeNode: string
  supervisorAction: string
  mergeStatus: string
  reason: string
}
const workflowState = ref<WorkflowState | null>(null)

// New session dialog
const showNewSessionDialog = ref(false)

// Refresh agents when dialog opens
watch(showNewSessionDialog, (val) => {
  if (val) loadAgents()
})
const newSessionAgentId = ref('')
const newSessionTitle = ref('')

// Agent selector
const selectedAgentId = ref('')

// Mobile state
const mobileTab = ref<'chat' | 'memory' | 'admin'>('chat')
const showMobileSessions = ref(true)
const showMobileMore = ref(false)
const showMobileAdmin = ref(false)
const isMobile = ref(false)

const { t, locale } = useI18n()
const toggleLocale = () => {
  locale.value = locale.value === 'zh' ? 'en' : 'zh'
  localStorage.setItem('cloudclaw-locale', locale.value)
}

// Close user dropdown on outside click
const handleClickOutside = (e: MouseEvent) => {
    const target = e.target as HTMLElement
    if (!target.closest('.nav-bar-user')) showUserMenu.value = false
  }
onMounted(() => document.addEventListener('click', handleClickOutside))
onUnmounted(() => document.removeEventListener('click', handleClickOutside))

const checkMobile = () => {
  isMobile.value = window.innerWidth < 768
}

// Abort controller for cancelling SSE
let abortController: AbortController | null = null
let titleRefreshTimer: ReturnType<typeof setInterval> | null = null

// Poll sessions until all have titles (auto-generated titles are async)
const startTitleRefresh = () => {
  if (titleRefreshTimer) return
  titleRefreshTimer = setInterval(async () => {
    const hasUntitled = sessions.value.some((s: any) => !s.title)
    if (hasUntitled) {
      await loadSessions()
    } else {
      // All sessions have titles, stop polling
      if (titleRefreshTimer) {
        clearInterval(titleRefreshTimer)
        titleRefreshTimer = null
      }
    }
  }, 3000)
}

// ===== Markdown Renderer =====
const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  highlight(str: string, lang: string) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return '<pre class="hljs"><code>' +
          hljs.highlight(str, { language: lang, ignoreIllegals: true }).value +
          '</code></pre>'
      } catch (_) { /* fallback */ }
    }
    return '<pre class="hljs"><code>' + md.utils.escapeHtml(str) + '</code></pre>'
  }
})

const formatToolArgs = (args: string): string => {
  try {
    const parsed = JSON.parse(args)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return args
  }
}

const renderMarkdown = (content: string): string => {
  if (!content) return ''
  return md.render(content)
}

// ===== Computed =====
const isAdmin = computed(() => localStorage.getItem('user_role') === 'ADMIN')
const userName = computed(() => localStorage.getItem('user_name') || 'Admin')

const showUserMenu = ref(false)
const localeOptions = [
  { label: '中文', value: 'zh' },
  { label: 'EN', value: 'en' }
]

const onLocaleChange = () => {
  localStorage.setItem('cloudclaw-locale', locale.value)
}

const handleLocaleChange = (lang: string) => {
  locale.value = lang
  localStorage.setItem('cloudclaw-locale', lang)
}

const showAdminPage = computed(() => {
  const path = router.currentRoute.value.path
  return path !== '/' && path !== ''
})

const adminMenuItems = computed(() => [
  { path: '/dashboard', title: t('dashboard.title'), shortTitle: t('dashboard.title'), icon: Odometer },
  { path: '/agents', title: t('nav.agent'), shortTitle: 'Agent', icon: SetUp },
  { path: '/mcp-servers', title: t('nav.mcp'), shortTitle: 'MCP', icon: Connection },
  { path: '/skills', title: t('nav.skill'), shortTitle: t('agent.skills'), icon: Reading },
  { path: '/llm', title: t('nav.llm'), shortTitle: 'LLM', icon: Cpu },
  { path: '/users', title: t('nav.user'), shortTitle: t('nav.user'), icon: User },
  { path: '/sandboxes', title: t('nav.sandbox'), shortTitle: t('nav.sandbox'), icon: Grid },
  { path: '/monitor', title: t('nav.monitor'), shortTitle: t('nav.monitor'), icon: Monitor }
])

const currentSessionTitle = computed(() => {
  if (!currentSessionId.value) return 'CloudClaw Chat'
  const session = sessions.value.find(s => s.id === currentSessionId.value)
  return session?.title || 'Untitled Chat'
})

const currentAgent = computed(() => {
  const session = sessions.value.find(s => s.id === currentSessionId.value)
  if (!session) return null
  return agents.value.find(a => a.id === session.agentId) || null
})

// ===== Theme =====
const toggleDark = () => {
  isDark.value = !isDark.value
  if (isDark.value) {
    document.documentElement.classList.add('dark')
  } else {
    document.documentElement.classList.remove('dark')
  }
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
  } catch (e) {
    // Silently fail - agents might not be configured yet
  }
}

const loadSessions = async () => {
  try {
    const res: any = await sessionApi.list(1, 50, selectedAgentId.value || undefined)
    sessions.value = res.data?.list || res.data?.items || res.data || res.items || []
  } catch (e) {
    // Silently fail
  }
}

const loadMessages = async (sessionId: string) => {
  try {
    const res: any = await messageApi.history(sessionId)
    const items = res.data?.list || res.data?.items || res.data || res.items || []
    messages.value = items.map((m: any) => ({
      role: m.role,
      content: m.content,
      createdAt: m.createdAt,
      agentName: m.agentName || '',
      segments: m.segments || undefined
    }))
    scrollToBottom()
  } catch (e) {
    messages.value = []
  }
}

// ===== Session Actions =====
const selectSession = (sessionId: string) => {
  if (isStreaming.value) return
  // Clear previous session state immediately to prevent content leaking
  messages.value = []
  streamingContent.value = ''
  streamingSegments.value = []
  workflowState.value = null
  activeAgentName.value = ''
  currentSessionId.value = sessionId
  loadMessages(sessionId)
}

const createNewSession = async () => {
  if (!newSessionAgentId.value) return

  try {
    const res: any = await sessionApi.create({
      agentId: newSessionAgentId.value,
      title: newSessionTitle.value || undefined
    })
    const newSession = res.data || res
    showNewSessionDialog.value = false
    newSessionTitle.value = ''

    await loadSessions()

    if (newSession?.id) {
      selectSession(newSession.id)
    }
    ElMessage.success(t('chat.sessionCreated'))
  } catch (e) {
    // Error handled by interceptor
  }
}

const deleteSession = async (sessionId: string) => {
  try {
    await ElMessageBox.confirm(
      'Are you sure you want to delete this conversation?',
      'Delete Conversation',
      { confirmButtonText: 'Delete', cancelButtonText: 'Cancel', type: 'warning' }
    )

    await sessionApi.delete(sessionId)
    sessions.value = sessions.value.filter(s => s.id !== sessionId)

    if (currentSessionId.value === sessionId) {
      currentSessionId.value = ''
      messages.value = []
    }
    ElMessage.success(t('chat.sessionDeleted'))
  } catch (e) {
    // Cancelled or error
  }
}

// ===== Chat =====
const sendMessage = async () => {
  const msg = inputMessage.value.trim()
  if (!msg || isStreaming.value) return

  // Auto-create session if none selected
  if (!currentSessionId.value) {
    if (!selectedAgentId.value) {
      ElMessage.warning(t('chat.selectAgentFirst'))
      return
    }
    try {
      const res: any = await sessionApi.create({
        agentId: selectedAgentId.value
      })
      const newSession = res.data || res
      if (newSession?.id) {
        currentSessionId.value = newSession.id
        sessions.value.unshift({
          id: newSession.id,
          title: '',
          agentId: selectedAgentId.value,
          createdAt: newSession.createdAt || new Date().toISOString(),
          updatedAt: newSession.updatedAt || new Date().toISOString()
        })
        startTitleRefresh()
      }
    } catch (e) {
      ElMessage.error(t('common.failed'))
      return
    }
  }

  if (!currentSessionId.value) return

  // Add user message
  messages.value.push({ role: 'user', content: msg, createdAt: new Date().toISOString() })
  inputMessage.value = ''
  isStreaming.value = true
  streamingContent.value = ''
  streamingSegments.value = []
  workflowState.value = null
  abortController = new AbortController()

  scrollToBottom()

  // Check if using async poll mode
  const chatMode = localStorage.getItem('chatMode') || 'stream'

  if (chatMode === 'poll') {
    try {
      const requestId = 'req_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8)
      const res = await sendMessageAsync(currentSessionId.value, msg, requestId)
      const data = res?.data || res
      const assistantMsgId = data?.assistantMessageId

      // Poll for the assistant response
      let pollInterval: ReturnType<typeof setInterval> | null = null
      let lastContent = ''
      let stableCount = 0

      const stopPolling = () => {
        if (pollInterval) { clearInterval(pollInterval); pollInterval = null }
        isStreaming.value = false
      }

      pollInterval = setInterval(async () => {
        try {
          const pollRes = await pollMessages(currentSessionId.value, data?.userMessageId)
          const msgs = pollRes?.data?.messages || []
          // Find the assistant message
          const assistant = msgs.find((m: any) => m.id === assistantMsgId)
          if (assistant) {
            if (assistant.status === 'pending' || assistant.status === 'processing') {
              streamingContent.value = t('chat.thinking')
              scrollToBottom()
            } else if (assistant.status === 'completed' || assistant.status === 'failed') {
              stopPolling()
              if (assistant.content) {
                messages.value.push({
                  role: 'assistant',
                  content: assistant.content,
                  createdAt: assistant.createdAt
                })
              }
              if (assistant.status === 'failed') {
                ElMessage.error(t('chat.responseFailed'))
              }
              streamingContent.value = ''
              scrollToBottom()
              startTitleRefresh()
            } else {
              // streaming or unknown - show partial content
              if (assistant.content && assistant.content !== lastContent) {
                lastContent = assistant.content
                stableCount = 0
                streamingContent.value = assistant.content
                scrollToBottom()
              } else {
                stableCount++
              }
              // Timeout after 60 stable polls (~30s)
              if (stableCount > 60) {
                stopPolling()
                ElMessage.warning(t('chat.responseTimeout'))
              }
            }
          }
        } catch (e) {
          // Poll error, keep trying
        }
      }, 500)
    } catch (error) {
      isStreaming.value = false
      streamingContent.value = ''
      ElMessage.error(t('chat.responseFailed'))
    }
    return
  }

  // Default: SSE stream mode
  try {
    await sendChatMessage(
      currentSessionId.value,
      msg,
      // onChunk
      (chunk: any) => {
        const type = chunk.type || 'text'
        const text = chunk.content || ''

        // Workflow V3 events - handled before agent_switched
        if (type === 'pipeline_step') {
          // Workflow V3: Pipeline step event
          if (!workflowState.value) {
            workflowState.value = { mode: 'pipeline', steps: [], activeNode: '', supervisorAction: '', mergeStatus: '', reason: '' }
          }
          const nodeName = chunk.node || ''
          const existing = workflowState.value.steps.find(s => s.name === nodeName)
          if (!existing) {
            workflowState.value.steps.push({ name: nodeName, status: 'running' })
          } else {
            existing.status = 'running'
          }
          // Mark previous steps as done
          const currentIdx = workflowState.value.steps.findIndex(s => s.name === nodeName)
          for (let i = 0; i < currentIdx; i++) {
            workflowState.value.steps[i].status = 'done'
          }
        } else if (type === 'parallel_start') {
          // Workflow V3: Parallel starts
          const nodes: string[] = chunk.nodes || []
          workflowState.value = {
            mode: 'parallel',
            steps: nodes.map(n => ({ name: n, status: 'pending' as const })),
            activeNode: '',
            supervisorAction: '',
            mergeStatus: '',
            reason: ''
          }
        } else if (type === 'parallel_progress') {
          // Parallel node is now running
          if (workflowState.value) {
            const node = workflowState.value.steps.find(s => s.name === chunk.node)
            if (node) node.status = 'running'
          }
        } else if (type === 'parallel_complete') {
          // Parallel node completed
          if (workflowState.value) {
            const node = workflowState.value.steps.find(s => s.name === chunk.node)
            if (node) node.status = 'done'
          }
        } else if (type === 'parallel_merge') {
          // Parallel merging
          if (workflowState.value) {
            workflowState.value.mergeStatus = 'merging'
          }
        } else if (type === 'router_select') {
          // Workflow V3: Router selected a node
          const target = chunk.targetAgent || ''
          const reason = chunk.reason || ''
          workflowState.value = {
            mode: 'router',
            steps: [],
            activeNode: target,
            supervisorAction: '',
            mergeStatus: '',
            reason: reason
          }
          activeAgentName.value = target
        } else if (type === 'supervisor_plan') {
          // Workflow V3: Supervisor planning
          const planSteps: string[] = chunk.plan || []
          workflowState.value = {
            mode: 'supervisor',
            steps: planSteps.map(s => ({ name: s, status: 'pending' as const })),
            activeNode: '',
            supervisorAction: t('chat.workflowSupervisorReviewing'),
            mergeStatus: '',
            reason: ''
          }
        } else if (type === 'supervisor_delegate') {
          // Supervisor delegating to a sub-agent
          const target = chunk.targetAgent || ''
          if (!workflowState.value) {
            workflowState.value = { mode: 'supervisor', steps: [], activeNode: '', supervisorAction: '', mergeStatus: '', reason: '' }
          }
          workflowState.value.supervisorAction = t('chat.workflowSupervisorDelegating', { name: target })
          workflowState.value.activeNode = target
        } else if (type === 'supervisor_result') {
          // Supervisor received result from sub-agent
          if (workflowState.value) {
            const node = workflowState.value.steps.find(s => s.name === chunk.from)
            if (node) node.status = 'done'
            workflowState.value.supervisorAction = t('chat.workflowSupervisorReviewing')
          }
        } else if (type === 'handoff') {
          // Workflow V3: Handoff event
          const target = chunk.targetAgent || ''
          workflowState.value = {
            mode: 'handoff',
            steps: [],
            activeNode: target,
            supervisorAction: '',
            mergeStatus: '',
            reason: ''
          }
          activeAgentName.value = target
        } else if (type === 'agent_switched') {
          // Agent Transfer v2: show transfer notification
          const displayName = chunk.displayName || chunk.targetAgent || ''
          activeAgentName.value = displayName
          streamingSegments.value.push({
            type: 'text' as const,
            content: '↩ ' + t('chat.agentSwitched', { name: displayName }),
          })
          // Update workflowState if in handoff mode
          if (workflowState.value && workflowState.value.mode === 'handoff') {
            workflowState.value.activeNode = displayName
          }
        } else if (type === 'tool_call') {
          // Add tool call segment
          streamingSegments.value.push({
            type: 'tool_call',
            content: text,
            toolName: chunk.toolName || 'tool'
          })
        } else if (type === 'tool_result') {
          streamingSegments.value.push({
            type: 'tool_result',
            content: text,
            toolName: chunk.toolName || 'tool'
          })
        } else if (text) {
          // Text: append to last text segment or create new
          const last = streamingSegments.value[streamingSegments.value.length - 1]
          if (last && last.type === 'text') {
            last.content += text
          } else {
            streamingSegments.value.push({ type: 'text', content: text })
          }
          streamingContent.value += text
        }
        scrollToBottom()
      },
      // onDone
      (stats?: any) => {
        if (streamingContent.value || streamingSegments.value.length > 0) {
          messages.value.push({
            role: 'assistant',
            content: streamingContent.value,
            segments: [...streamingSegments.value]
          })
        }
        streamingContent.value = ''
        streamingSegments.value = []
        workflowState.value = null
        isStreaming.value = false
        if (stats) contextStats.value = stats
        scrollToBottom()

        // Agent Transfer v2: active agent is tracked by activeAgentName ref

        // Start polling for auto-generated title
        startTitleRefresh()
      },
      // onError
      (error: any) => {
        isStreaming.value = false
        streamingContent.value = ''
        streamingSegments.value = []
        workflowState.value = null
        ElMessage.error(t('common.failed'))
      },
      abortController?.signal
    )
  } catch (error) {
    isStreaming.value = false
    streamingContent.value = ''
    streamingSegments.value = []
    workflowState.value = null
  }
}

const handleInputKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

const formatTokens = (tokens: number): string => {
  if (tokens >= 1000) return (tokens / 1000).toFixed(1) + 'K'
  return tokens.toString()
}

const formatTime = (dateStr?: string): string => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  if (isNaN(d.getTime())) return ''
  const mo = (d.getMonth() + 1).toString().padStart(2, '0')
  const day = d.getDate().toString().padStart(2, '0')
  const h = d.getHours().toString().padStart(2, '0')
  const m = d.getMinutes().toString().padStart(2, '0')
  return mo + '-' + day + ' ' + h + ':' + m
}

const stopGeneration = () => {
  if (abortController) {
    abortController.abort()
    abortController = null
  }
  if (streamingContent.value || streamingSegments.value.length > 0) {
    messages.value.push({
      role: 'assistant',
      content: streamingContent.value,
      segments: [...streamingSegments.value]
    })
  }
  streamingContent.value = ''
  streamingSegments.value = []
  workflowState.value = null
  isStreaming.value = false
}

// ===== UI Helpers =====
const scrollToBottom = () => {
  nextTick(() => {
    const area = messagesAreaRef.value
    if (area) {
      // el-main wraps content in a div
      const scrollEl = area.$el || area
      scrollEl.scrollTop = scrollEl.scrollHeight
    }
  })
}

const handleLogout = () => {
  localStorage.removeItem('access_token')
  localStorage.removeItem('refresh_token')
  router.push('/login')
}

// ===== Lifecycle =====
onUnmounted(() => {
  if (titleRefreshTimer) {
    clearInterval(titleRefreshTimer)
    titleRefreshTimer = null
  }
})

onMounted(async () => {
  // Apply saved theme
  const savedTheme = localStorage.getItem('theme')
  if (savedTheme === 'dark') {
    isDark.value = true
    document.documentElement.classList.add('dark')
  }

  // Mobile detection
  checkMobile()
  window.addEventListener('resize', checkMobile)

  await loadAgents()
  await loadSessions()

  // Load system info
  try {
    const infoRes: any = await api.get('/admin/stats/info')
    const info = infoRes?.data?.data || infoRes?.data || infoRes
    if (info?.version) systemVersion.value = info.version
    if (info?.mode) systemMode.value = info.mode
  } catch (e) { /* ignore */ }

  // Refresh agents when tab becomes visible
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) loadAgents()
  })
})

// Watch agent selector to filter sessions
watch(selectedAgentId, async (newAgentId) => {
  // Clear current session when switching agent
  currentSessionId.value = ''
  messages.value = []
  // Reset all streaming state to prevent content leaking
  streamingContent.value = ''
  streamingSegments.value = []
  workflowState.value = null
  activeAgentName.value = ''
  isStreaming.value = false
  // Abort any active SSE stream
  if (abortController) {
    abortController.abort()
    abortController = null
  }
  newSessionAgentId.value = newAgentId
  await loadSessions()
})

// Auto-scroll when messages change (covers load, new msg, route switch)
watch(messages, () => {
  nextTick(() => scrollToBottom())
}, { deep: true })

// Mobile tab navigation
const handleMobileTab = (tab) => {
  showMobileMore.value = false
  showMobileAdmin.value = false
  if (tab === 'chat') {
    mobileTab.value = 'chat'
    showMobileSessions.value = true
    router.push('/')
  } else if (tab === 'admin') {
    showMobileAdmin.value = !showMobileAdmin.value
  }
}

const handleMobileAdminNav = (path) => {
  showMobileAdmin.value = false
  mobileTab.value = 'admin'
  router.push(path)
}

const handleMobileMore = () => {
  showMobileAdmin.value = false
  showMobileMore.value = !showMobileMore.value
}

const handleMobileSelectSession = (sessionId) => {
  selectSession(sessionId)
  showMobileSessions.value = false
}

// Watch for sidebar collapse
watch(sidebarCollapsed, (val) => {
  const aside = document.querySelector('.sidebar') as HTMLElement
  if (aside) {
    aside.style.display = val ? 'none' : ''
  }
})
</script>

<style scoped>
/* ===== CSS Variables ===== */
.chat-layout {
  position: relative;
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

/* ===== Layout ===== */
.chat-layout {
  position: relative;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  background: var(--cc-bg-secondary);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
}
.main-container { height: 100%; }

/* ===== Nav Bar (Left) ===== */
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
.nav-bar-header {
  padding: 14px 0 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  white-space: nowrap;
  overflow: hidden;
}
.nav-brand {
  font-size: 16px;
  font-weight: 700;
  color: var(--cc-text-primary);
}
.nav-version {
  font-size: 10px;
  color: var(--cc-text-tertiary, #999);
  text-align: center;
  margin-top: -4px;
  margin-bottom: 4px;
  letter-spacing: 0.5px;
}
.nav-bar.collapsed .nav-bar-header { justify-content: center; }
.nav-logo { width: 32px; height: 32px; border-radius: 8px; flex-shrink: 0; }
.nav-bar-menu {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 4px 8px;
  overflow-y: auto;
}
.nav-bar.collapsed .nav-bar-menu { align-items: center; padding: 4px 6px; }
.nav-bar-item {
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  cursor: pointer;
  color: #5a5e66;
  font-size: 13px;
  transition: all 0.15s;
  user-select: none;
  white-space: nowrap;
  overflow: hidden;
}
.nav-bar.collapsed .nav-bar-item {
  width: 52px;
  height: 46px;
  justify-content: center;
  flex-direction: column;
  gap: 2px;
  padding: 0;
}
.nav-bar-item:hover { background: rgba(0,0,0,0.06); color: #303133; }
.nav-bar-item.active { background: var(--cc-accent); color: #fff; }
.chat-layout.dark .nav-bar-item { color: #9ca0a8; }
.chat-layout.dark .nav-bar-item:hover { background: rgba(255,255,255,0.08); color: #ccc; }
.nav-bar-label {
  font-size: 13px;
  line-height: 1;
  transition: opacity 0.2s, width 0.2s;
  overflow: hidden;
}
.nav-bar.collapsed .nav-bar-label { font-size: 10px; }
.nav-bar.collapsed .nav-bar-label.label-hidden { display: none; }
.nav-bar-divider { height: 1px; background: rgba(0,0,0,0.08); margin: 6px 0; }
.nav-bar.collapsed .nav-bar-divider { width: 32px; margin: 4px auto; }
.chat-layout.dark .nav-bar-divider { background: rgba(255,255,255,0.08); }
..nav-bar-user:hover { background: var(--el-fill-color-light, #f5f7fa); }
.dark .nav-bar-user:hover { background: rgba(255,255,255,0.06); }
.....nav-bar.collapsed ..nav-bar.collapsed ...dark ...user-dropdown-item:hover { background: var(--el-fill-color-light, #f5f7fa); }
.dark .user-dropdown-item:hover { background: rgba(255,255,255,0.06); }
....dark ..locale-icon {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  border-radius: 4px;
  background: var(--el-color-primary-light-5, #b3d8ff);
  color: var(--el-color-primary, #409eff);
  flex-shrink: 0;
}
.dark .locale-icon { background: rgba(64,158,255,0.2); }
.nav-bar-bottom {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 4px 8px 12px;
}
.locale-icon {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  border-radius: 4px;
  background: var(--el-color-primary-light-5, #b3d8ff);
  color: var(--el-color-primary, #409eff);
  flex-shrink: 0;
}
.dark .locale-icon { background: rgba(64,158,255,0.2); }
.collapse-toggle .rotate-icon { transform: rotate(180deg); transition: transform 0.25s; }

/* ===== Content Container ===== */

.dark 
:deep(.el-dropdown-menu__item.active) { color: var(--el-color-primary, #409eff); font-weight: 600; }
.content-container {
  flex: 1;
  overflow: hidden;
  background: var(--cc-bg-secondary);
}

/* ===== Session Sidebar ===== */
.session-sidebar {
  width: 280px !important;
  background: var(--cc-bg-primary);
  border-right: 1px solid var(--cc-border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.sidebar-page-header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 20px 16px;
}
.sidebar-page-header h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--cc-text-primary);
}
.sidebar-page-header .header-desc {
  font-size: 13px;
  color: var(--cc-text-muted);
  margin-top: 2px;
}
.header-icon {
  width: 36px; height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}
.chat-header-icon {
  background: var(--cc-bg-tertiary) !important;
  color: var(--cc-accent);
}
.sidebar-toolbar {
  display: flex;
  gap: 8px;
  padding: 0 16px 12px;
  border-bottom: 1px solid var(--cc-border);
}
.sidebar-toolbar .agent-select { flex: 1; }
.session-sidebar-header { display: none; }
.agent-selector { display: none; }
.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.session-list::-webkit-scrollbar { width: 4px; }
.session-list::-webkit-scrollbar-thumb { background: var(--cc-border); border-radius: 2px; }
.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: var(--cc-radius-sm);
  cursor: pointer;
  transition: all 0.15s;
  margin-bottom: 2px;
}
.session-item:hover { background: var(--cc-bg-tertiary); }
.session-item.active { background: var(--cc-accent-light); color: var(--cc-accent); }
.session-info { flex: 1; min-width: 0; }
.session-title {
  font-size: 13px;
  color: var(--cc-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}
.session-item.active .session-title { color: var(--cc-accent); font-weight: 500; }
.session-delete { opacity: 0; transition: opacity 0.15s; }
.session-item:hover .session-delete { opacity: 1; }
.no-sessions {
  text-align: center;
  padding: 40px 20px;
  color: var(--cc-text-muted);
  font-size: 13px;
}

/* ===== Chat Container ===== */
.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--cc-bg-secondary);
}
.chat-header {
  height: 56px !important;
  background: var(--cc-bg-primary);
  border-bottom: 1px solid var(--cc-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}
.chat-header-info {
  display: flex;
  align-items: center;
  gap: 8px;
}
.chat-header-icon-text {
  color: var(--cc-accent);
}
.header-title { font-size: 15px; font-weight: 600; color: var(--cc-text-primary); }

/* ===== Messages Area ===== */
.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 0 !important;
  background: var(--cc-bg-secondary);
}
.messages-area::-webkit-scrollbar { width: 6px; }
.messages-area::-webkit-scrollbar-thumb { background: var(--cc-border); border-radius: 3px; }
.messages-inner {
  max-width: 820px;
  margin: 0 auto;
  padding: 20px 24px;
}

/* Welcome Section */
.welcome-section {
  text-align: center;
  padding: 60px 20px 40px;
  color: var(--cc-text-secondary);
}
.welcome-icon {
  width: 72px; height: 72px;
  border-radius: 18px;
  background: linear-gradient(135deg, #3370ff, #5b8def);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
  color: #fff;
}
.welcome-section h2 {
  font-size: 22px;
  font-weight: 600;
  color: var(--cc-text-primary);
  margin-bottom: 8px;
}
.welcome-section p { font-size: 14px; margin-bottom: 24px; }

/* Message Bubbles */
.message-row {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  align-items: flex-start;
}
.message-row.user {
  flex-direction: row;
  justify-content: flex-end;
}
.message-avatar { flex-shrink: 0; margin-top: 2px; }
.assistant-avatar { background: var(--cc-accent) !important; }
.message-content {
  flex: 1;
  min-width: 0;
  max-width: 720px;
}
.message-row.user .message-content {
  flex: none;
  max-width: 520px;
  width: fit-content;
  background: #e8f0ff;
  padding: 10px 14px;
  border-radius: 12px 12px 4px 12px;
}
.chat-layout.dark .message-row.user .message-content {
  background: #1a2a44;
}
.message-row.user .message-content .message-meta {
  color: rgba(0,0,0,0.45);
}
.chat-layout.dark .message-row.user .message-content .message-meta {
  color: rgba(255,255,255,0.5);
}
.message-row.user .message-meta {
  color: rgba(0,0,0,0.35);
  text-align: right;
}
.chat-layout.dark .message-row.user .message-meta {
  color: rgba(255,255,255,0.35);
}
.message-meta {
  font-size: 11px;
  color: var(--cc-text-muted);
  margin-top: 4px;
}
.message-text {
  font-size: 14px;
  line-height: 1.7;
  color: var(--cc-text-primary);
  word-break: break-word;
}

/* Markdown Body */
.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3) {
  margin: 16px 0 8px;
  font-weight: 600;
}
.markdown-body :deep(p) { margin: 6px 0; }
.markdown-body :deep(pre) {
  background: var(--cc-bg-tertiary);
  border-radius: var(--cc-radius-sm);
  padding: 12px 16px;
  overflow-x: auto;
  margin: 8px 0;
  font-size: 13px;
  line-height: 1.5;
}
.markdown-body :deep(code) {
  background: var(--cc-bg-tertiary);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
}
.markdown-body :deep(pre code) { background: none; padding: 0; }
.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 8px 0;
}
.markdown-body :deep(th), .markdown-body :deep(td) {
  border: 1px solid var(--cc-border);
  padding: 8px 12px;
  text-align: left;
}
.markdown-body :deep(th) { background: var(--cc-bg-tertiary); font-weight: 600; }

/* Tool Call Cards */
.tool-call-card {
  background: #fff8e6;
  border: 1px solid #ffe7a0;
  border-radius: var(--cc-radius-sm);
  margin: 6px 0;
  overflow: hidden;
}
.dark .tool-call-card { background: #2a2518; border-color: #4a3f28; }
.tool-call-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  background: rgba(255,150,0,0.08);
}
.tool-icon { color: var(--cc-accent); }
.tool-name { color: var(--cc-text-primary); }
.tool-args {
  margin: 0;
  padding: 8px 12px;
  font-size: 12px;
  line-height: 1.4;
  max-height: 200px;
  overflow-y: auto;
  color: var(--cc-text-secondary);
}
.tool-result-card {
  background: #e8f5e9;
  border: 1px solid #a5d6a7;
  border-radius: var(--cc-radius-sm);
  margin: 6px 0;
  overflow: hidden;
}
.dark .tool-result-card { background: #1a2a1c; border-color: #2a4a2c; }
.tool-result-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  background: rgba(52,199,89,0.08);
}
.tool-result-content {
  margin: 0;
  padding: 8px 12px;
  font-size: 12px;
  line-height: 1.4;
  max-height: 200px;
  overflow-y: auto;
  color: var(--cc-text-secondary);
}

/* Streaming */
.cursor-blink {
  animation: blink 1s infinite;
  color: var(--cc-accent);
  font-weight: bold;
}
@keyframes blink { 0%,50% { opacity: 1; } 51%,100% { opacity: 0; } }
.typing-indicator {
  display: inline-flex;
  gap: 4px;
  padding: 4px 0;
}
.typing-indicator span {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: var(--cc-text-muted);
  animation: typing 1.4s infinite;
}
.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }
@keyframes typing { 0%,60%,100% { opacity: 0.3; transform: translateY(0); } 30% { opacity: 1; transform: translateY(-4px); } }
.streaming-controls { text-align: center; padding: 8px 0; }

/* ===== Workflow V3 Status Panel ===== */
.workflow-panel {
  margin-bottom: 10px;
  background: var(--cc-bg-tertiary, #eef0f4);
  border-radius: var(--cc-radius, 10px);
  border: 1px solid var(--cc-border);
  overflow: hidden;
  font-size: 13px;
}
.chat-layout.dark .workflow-panel {
  background: #262627;
}
.workflow-panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: rgba(51,112,255,0.06);
  border-bottom: 1px solid var(--cc-border);
}
.chat-layout.dark .workflow-panel-header {
  background: rgba(51,112,255,0.12);
}
.workflow-panel-icon {
  color: var(--cc-accent);
  font-size: 16px;
}
.workflow-panel-title {
  font-weight: 600;
  color: var(--cc-accent);
  white-space: nowrap;
}
.workflow-panel-mode-tag {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 10px;
  background: var(--cc-accent-light, #e8f0ff);
  color: var(--cc-accent);
  font-weight: 500;
  text-transform: uppercase;
  margin-left: auto;
}
.chat-layout.dark .workflow-panel-mode-tag {
  background: #1a2a44;
}
.workflow-panel-body {
  padding: 8px 12px;
}
.workflow-steps-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}
.workflow-step-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 12px;
}
.workflow-step-item.step-pending {
  color: var(--cc-text-muted);
  background: transparent;
}
.workflow-step-item.step-running {
  color: var(--cc-accent);
  background: var(--cc-accent-light, #e8f0ff);
}
.chat-layout.dark .workflow-step-item.step-running {
  background: #1a2a44;
}
.workflow-step-item.step-done {
  color: var(--cc-success);
  background: #e8f5e9;
}
.chat-layout.dark .workflow-step-item.step-done {
  background: #1a2a1c;
}
.workflow-step-arrow {
  color: var(--cc-text-muted);
  font-size: 12px;
  display: flex;
  align-items: center;
}
.step-icon {
  display: inline-flex;
  align-items: center;
  font-size: 14px;
}
.step-label {
  font-weight: 500;
}
.workflow-parallel-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.workflow-parallel-node {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 6px;
  font-size: 12px;
  border: 1px solid var(--cc-border);
  background: var(--cc-bg-primary);
}
.workflow-parallel-node.step-pending {
  color: var(--cc-text-muted);
  opacity: 0.7;
}
.workflow-parallel-node.step-running {
  color: var(--cc-accent);
  border-color: var(--cc-accent);
}
.workflow-parallel-node.step-done {
  color: var(--cc-success);
  border-color: var(--cc-success);
}
.workflow-merge-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  padding: 4px 0;
  font-size: 12px;
  color: var(--cc-warning);
}
.merge-label {
  font-weight: 500;
}
.workflow-router-info {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.router-target-tag {
  font-weight: 600;
}
.workflow-router-reason {
  color: var(--cc-text-secondary);
  font-size: 12px;
  font-style: italic;
}
.workflow-supervisor-steps {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 4px;
}
.workflow-supervisor-step {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 12px;
}
.workflow-supervisor-step.step-pending {
  color: var(--cc-text-muted);
}
.workflow-supervisor-step.step-running {
  color: var(--cc-accent);
  background: var(--cc-accent-light, #e8f0ff);
}
.chat-layout.dark .workflow-supervisor-step.step-running {
  background: #1a2a44;
}
.workflow-supervisor-step.step-done {
  color: var(--cc-success);
  background: #e8f5e9;
}
.chat-layout.dark .workflow-supervisor-step.step-done {
  background: #1a2a1c;
}
.workflow-supervisor-delegate {
  margin: 4px 0;
}
.workflow-supervisor-action {
  display: flex;
  align-items: center;
  color: var(--cc-text-secondary);
  font-size: 12px;
  margin-top: 2px;
}
.workflow-handoff-info {
  display: flex;
  align-items: center;
  gap: 6px;
}
.workflow-event-text {
  font-size: 12px;
  color: var(--cc-text-muted);
  padding: 2px 0;
  font-style: italic;
}

/* ===== Input Area ===== */
.input-area {
  padding: 0 !important;
  background: var(--cc-bg-secondary);
  border-top: 1px solid var(--cc-border);
}
.input-wrapper {
  max-width: 820px;
  margin: 0 auto;
  padding: 12px 24px;
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
.input-wrapper .el-textarea {
  flex: 1;
}
.input-wrapper .el-textarea :deep(.el-textarea__inner) {
  border-radius: var(--cc-radius);
  border: 1px solid var(--cc-border);
  background: var(--cc-bg-primary);
  padding: 10px 14px;
  font-size: 14px;
  resize: none;
  box-shadow: var(--cc-shadow);
}
.input-wrapper .el-textarea :deep(.el-textarea__inner):focus {
  border-color: var(--cc-accent);
  box-shadow: 0 0 0 2px rgba(51,112,255,0.15);
}
.send-button {
  width: 38px !important;
  height: 38px !important;
  border-radius: var(--cc-radius) !important;
}

/* Context Bar */
.context-bar {
  max-width: 820px;
  margin: 0 auto;
  padding: 0 24px 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.context-bar-track {
  flex: 1;
  height: 3px;
  background: var(--cc-bg-tertiary);
  border-radius: 2px;
  overflow: hidden;
}
.context-bar-fill { height: 100%; border-radius: 2px; transition: width 0.3s; }
.context-ok { background: var(--cc-success); }
.context-warn { background: var(--cc-warning); }
.context-danger { background: var(--cc-danger); }
.context-bar-label { font-size: 11px; color: var(--cc-text-muted); white-space: nowrap; }
.agent-label {
  color: var(--cc-accent);
  font-weight: 500;
}

/* ===== Admin Page Area ===== */
.admin-page-area {
  background: var(--cc-bg-secondary);
  padding: 0 !important;
  overflow-y: auto;
}
.admin-page-area::-webkit-scrollbar { width: 6px; }
.admin-page-area::-webkit-scrollbar-thumb { background: var(--cc-border); border-radius: 3px; }

/* ===== Mobile Components (hidden on PC) ===== */
.mobile-tab-bar {
  display: none;
}
.mobile-session-btn {
  display: none;
}
.mobile-overlay {
  display: none;
}
.mobile-menu {
  display: none;
}

/* ===== Responsive ===== */
@media (max-width: 767px) {
  /* Hide PC nav bar */
  .nav-bar {
    display: none !important;
  }

  /* Session sidebar: left slide-in drawer */
  .mobile-session-overlay {
    position: fixed;
    inset: 0;
    background: rgba(0,0,0,0.4);
    z-index: 199;
  }
  .session-sidebar {
    position: fixed !important;
    top: 0; left: 0; bottom: 0;
    width: 280px !important;
    z-index: 200;
    border-right: 1px solid var(--cc-border) !important;
    transition: transform 0.25s ease;
  }
  .session-sidebar.mobile-hidden {
    transform: translateX(-100%);
    display: flex !important;
  }

  /* Content fills full width */
.content-container {
    width: 100% !important;
  }

  /* Chat header */
  .chat-header {
    padding: 0 12px !important;
    height: 48px !important;
  }
  .mobile-session-btn {
    display: flex !important;
  }
  .header-title {
    font-size: 14px;
  }

  /* Messages */
  .messages-inner {
    padding: 12px 16px;
    max-width: 100%;
  }
  .message-row {
    gap: 8px;
  }
  .message-avatar :deep(.el-avatar) {
    width: 28px !important;
    height: 28px !important;
  }
  .message-content {
    max-width: calc(100vw - 60px);
  }
  .message-row.user .message-content {
    max-width: 80%;
  }

  /* Input area */
  .input-wrapper {
    padding: 8px 12px;
  }
  .input-area {
    padding-bottom: 0 !important;
  }
  /* Chat container bottom padding for tab bar */
  .chat-container {
    padding-bottom: 56px;
  }
  .context-bar {
    padding: 0 12px 6px;
  }

  /* Welcome section */
  .welcome-section {
    padding: 40px 16px 20px;
  }
  .welcome-icon {
    width: 56px; height: 56px;
  }
  .welcome-section h2 {
    font-size: 18px;
  }

  /* Bottom Tab Bar */
  .mobile-tab-bar {
    display: flex !important;
    position: fixed;
    bottom: 0; left: 0; right: 0;
    height: 56px;
    background: var(--cc-bg-primary);
    border-top: 1px solid var(--cc-border);
    z-index: 300;
    align-items: center;
    justify-content: space-around;
    padding-bottom: env(safe-area-inset-bottom, 0px);
    box-shadow: 0 -1px 6px rgba(0,0,0,0.06);
  }
  .chat-layout.dark .mobile-tab-bar {
    box-shadow: 0 -1px 6px rgba(0,0,0,0.3);
  }
  .tab-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
    cursor: pointer;
    color: var(--cc-text-muted);
    font-size: 10px;
    padding: 4px 12px;
    transition: color 0.15s;
    user-select: none;
  }
  .tab-item.active {
    color: var(--cc-accent);
  }
  .tab-item span {
    line-height: 1;
  }

  /* Make room for tab bar */
  .main-container {
    height: calc(100vh - 56px) !important;
  }

  /* Mobile overlay */
  .mobile-overlay {
    display: block !important;
    position: fixed;
    top: 0; left: 0; right: 0; bottom: 0;
    background: rgba(0,0,0,0.4);
    z-index: 250;
  }

  /* Mobile menu */
  .mobile-menu {
    display: block !important;
    position: fixed;
    bottom: 56px;
    left: 8px;
    right: 8px;
    background: var(--cc-bg-primary);
    border-radius: 12px;
    box-shadow: 0 4px 20px rgba(0,0,0,0.15);
    z-index: 260;
    padding: 8px 0;
  }
  .more-menu {
    left: 50%;
    transform: translateX(-50%);
    right: auto;
    min-width: 160px;
  }
  .admin-menu {
    right: 8px;
    left: auto;
    min-width: 200px;
  }
  .mobile-menu-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 12px 16px;
    cursor: pointer;
    font-size: 14px;
    color: var(--cc-text-primary);
    transition: background 0.15s;
  }
  .mobile-menu-item:hover {
    background: var(--cc-bg-tertiary);
  }
  .mobile-menu-item .el-icon {
    color: var(--cc-text-secondary);
  }

  /* Admin pages on mobile */
  .admin-page-area {
    padding: 0 !important;
  }

  /* Markdown tables scroll on mobile */
  .markdown-body :deep(table) {
    display: block;
    overflow-x: auto;
  }
  .markdown-body :deep(pre) {
    font-size: 12px;
    padding: 8px 12px;
  }
}

</style>

<style>
/* Global reset for full-viewport layout */
html, body {
  margin: 0;
  padding: 0;
  height: 100%;
  overflow: hidden;
}
#app {
  height: 100%;
  overflow: hidden;
}
</style>