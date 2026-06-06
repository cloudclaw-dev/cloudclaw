<template>
  <div class="session-list-container">
    <!-- Search -->
    <div class="session-search">
      <el-input v-model="searchQuery" :placeholder="t('chat.searchSessions')" size="small" clearable>
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
    </div>

    <!-- Scrollable session list -->
    <div class="session-list-scroll">
      <!-- Pinned -->
      <template v-if="pinnedSessions.length > 0">
        <div class="section-label">📌 {{ t('chat.pinned') }}</div>
        <div v-for="s in pinnedSessions" :key="s.id"
             class="session-item"
             :class="{ active: currentSessionId === s.id }"
             @click="$emit('selectSession', s.id)"
             @contextmenu.prevent="showContextMenu($event, s)">
          <el-icon class="session-icon" :size="14"><ChatDotRound /></el-icon><span class="session-title">{{ s.title || t('chat.newChat') }}</span>
          <span v-if="getAgentTag(s)" class="session-agent-tag" :style="{ background: getAgentColor(s) }">{{ getAgentTag(s) }}</span>
          <el-button :icon="Delete" circle size="small" text class="session-delete" @click.stop="$emit('deleteSession', s.id)" />
        </div>
      </template>

      <!-- Today -->
      <template v-if="todaySessions.length > 0">
        <div class="section-label">{{ t('chat.today') }}</div>
        <div v-for="s in todaySessions" :key="s.id"
             class="session-item"
             :class="{ active: currentSessionId === s.id }"
             @click="$emit('selectSession', s.id)"
             @contextmenu.prevent="showContextMenu($event, s)">
          <el-icon class="session-icon" :size="14"><ChatDotRound /></el-icon><span class="session-title">{{ s.title || t('chat.newChat') }}</span>
          <span v-if="getAgentTag(s)" class="session-agent-tag" :style="{ background: getAgentColor(s) }">{{ getAgentTag(s) }}</span>
          <el-button :icon="Delete" circle size="small" text class="session-delete" @click.stop="$emit('deleteSession', s.id)" />
        </div>
      </template>

      <!-- Week -->
      <template v-if="weekSessions.length > 0">
        <div class="section-label">{{ t('chat.recent7Days') }}</div>
        <div v-for="s in weekSessions" :key="s.id"
             class="session-item"
             :class="{ active: currentSessionId === s.id }"
             @click="$emit('selectSession', s.id)"
             @contextmenu.prevent="showContextMenu($event, s)">
          <el-icon class="session-icon" :size="14"><ChatDotRound /></el-icon><span class="session-title">{{ s.title || t('chat.newChat') }}</span>
          <span v-if="getAgentTag(s)" class="session-agent-tag" :style="{ background: getAgentColor(s) }">{{ getAgentTag(s) }}</span>
          <el-button :icon="Delete" circle size="small" text class="session-delete" @click.stop="$emit('deleteSession', s.id)" />
        </div>
      </template>

      <!-- Older -->
      <template v-if="olderSessions.length > 0">
        <div class="section-label">{{ t('chat.older') }}</div>
        <div v-for="s in olderSessions" :key="s.id"
             class="session-item"
             :class="{ active: currentSessionId === s.id }"
             @click="$emit('selectSession', s.id)"
             @contextmenu.prevent="showContextMenu($event, s)">
          <el-icon class="session-icon" :size="14"><ChatDotRound /></el-icon><span class="session-title">{{ s.title || t('chat.newChat') }}</span>
          <span v-if="getAgentTag(s)" class="session-agent-tag" :style="{ background: getAgentColor(s) }">{{ getAgentTag(s) }}</span>
          <el-button :icon="Delete" circle size="small" text class="session-delete" @click.stop="$emit('deleteSession', s.id)" />
        </div>
      </template>

      <div v-if="filteredSessions.length === 0" class="no-sessions">
        <p>{{ t('chat.noSession') }}</p>
      </div>
    </div>

    <!-- Context Menu -->
    <div v-if="contextMenu.visible" class="context-menu" :style="{ top: contextMenu.y + 'px', left: contextMenu.x + 'px' }">
      <div class="context-menu-item" @click="handleRename">{{ t('chat.rename') }}</div>
      <div class="context-menu-item" @click="handleTogglePin">{{ pinnedIds.has(contextMenu.sessionId) ? t('chat.unpin') : t('chat.pin') }}</div>
      <div class="context-menu-item danger" @click="handleContextDelete">{{ t('common.delete') }}</div>
    </div>
  </div>

  <!-- Rename Dialog -->
  <el-dialog v-model="showRenameDialog" :title="t('chat.rename')" width="400px" :close-on-click-modal="true">
    <el-input v-model="renameTitle" :placeholder="t('chat.sessionTitlePlaceholder')" />
    <template #footer>
      <el-button @click="showRenameDialog = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" @click="confirmRename">{{ t('common.confirm') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { Delete, Search, ChatDotRound } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'

interface Session {
  id: string
  title: string
  agentId?: string
  createdAt?: string
  updatedAt?: string
  pinned?: boolean
}

interface Agent {
  id: string
  name: string
  emoji?: string
}

const AGENT_COLORS = ['#3370ff', '#34c724', '#f7ba1e', '#f54a45', '#8b5cf6', '#0fc6c2', '#f472b6', '#a3825e']

const props = defineProps<{
  sessions: Session[]
  agents: Agent[]
  currentSessionId: string | null
}>()

const emit = defineEmits<{
  (e: 'selectSession', id: string): void
  (e: 'deleteSession', id: string): void
  (e: 'renameSession', id: string, title: string): void
  (e: 'pinSession', id: string): void
  (e: 'unpinSession', id: string): void
}>()

const { t } = useI18n()
const searchQuery = ref('')
const showRenameDialog = ref(false)
const renameTitle = ref('')
const renameSessionId = ref('')
const pinnedIds = ref<Set<string>>(new Set(JSON.parse(localStorage.getItem('cc_pinned_sessions') || '[]')))

// Context menu
const contextMenu = ref<{ visible: boolean; x: number; y: number; sessionId: string }>({
  visible: false, x: 0, y: 0, sessionId: ''
})

// Agent color map
const agentColorMap = computed(() => {
  const map: Record<string, string> = {}
  props.agents.forEach((agent, idx) => {
    map[agent.id] = AGENT_COLORS[idx % AGENT_COLORS.length]
  })
  return map
})

const getAgentColor = (session: Session) => {
  if (!session.agentId) return 'transparent'
  return agentColorMap.value[session.agentId] || '#909399'
}

const getAgentTag = (session: Session) => {
  if (!session.agentId) return ''
  const agent = props.agents.find(a => a.id === session.agentId)
  if (!agent) return ''
  // If there's only one agent, no need for tags
  if (props.agents.length <= 1) return ''
  return agent.name
}

// Filtering
const filteredSessions = computed(() => {
  if (!searchQuery.value) return props.sessions
  const q = searchQuery.value.toLowerCase()
  return props.sessions.filter(s => {
    const title = (s.title || '').toLowerCase()
    const agentName = props.agents.find(a => a.id === s.agentId)?.name?.toLowerCase() || ''
    return title.includes(q) || agentName.includes(q)
  })
})

// Time grouping
const isToday = (dateStr?: string) => {
  if (!dateStr) return false
  const d = new Date(dateStr)
  const now = new Date()
  return d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth() && d.getDate() === now.getDate()
}

const isThisWeek = (dateStr?: string) => {
  if (!dateStr) return false
  const d = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  return diff < 7 * 24 * 60 * 60 * 1000
}

const pinnedSessions = computed(() => filteredSessions.value.filter(s => pinnedIds.value.has(s.id)))
const unpinnedSessions = computed(() => filteredSessions.value.filter(s => !pinnedIds.value.has(s.id)))
const todaySessions = computed(() => unpinnedSessions.value.filter(s => isToday(s.updatedAt || s.createdAt)))
const weekSessions = computed(() => unpinnedSessions.value.filter(s => !isToday(s.updatedAt || s.createdAt) && isThisWeek(s.updatedAt || s.createdAt)))
const olderSessions = computed(() => unpinnedSessions.value.filter(s => !isToday(s.updatedAt || s.createdAt) && !isThisWeek(s.updatedAt || s.createdAt)))

// Context menu actions
const showContextMenu = (e: MouseEvent, session: Session) => {
  contextMenu.value = { visible: true, x: e.clientX, y: e.clientY, sessionId: session.id }
}

const handleClickOutside = () => { contextMenu.value.visible = false }

const handleRename = () => {
  const session = props.sessions.find(s => s.id === contextMenu.value.sessionId)
  if (session) {
    renameTitle.value = session.title || ''
    renameSessionId.value = session.id
    showRenameDialog.value = true
  }
  contextMenu.value.visible = false
}

const confirmRename = () => {
  if (renameSessionId.value && renameTitle.value.trim()) {
    emit('renameSession', renameSessionId.value, renameTitle.value.trim())
  }
  showRenameDialog.value = false
}

const handleTogglePin = () => {
  const id = contextMenu.value.sessionId
  if (pinnedIds.value.has(id)) {
    pinnedIds.value.delete(id)
    emit('unpinSession', id)
  } else {
    pinnedIds.value.add(id)
    emit('pinSession', id)
  }
  localStorage.setItem('cc_pinned_sessions', JSON.stringify([...pinnedIds.value]))
  contextMenu.value.visible = false
}

const handleContextDelete = () => {
  emit('deleteSession', contextMenu.value.sessionId)
  contextMenu.value.visible = false
}

onMounted(() => document.addEventListener('click', handleClickOutside))
onUnmounted(() => document.removeEventListener('click', handleClickOutside))
</script>

<style scoped>
.session-list-container {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}
.session-search { padding: 0 8px 4px; }
.session-list-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 0 4px;
  scrollbar-width: thin;
}
.section-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--cc-text-muted, #8f959e);
  padding: 6px 4px 2px;
  letter-spacing: 0.3px;
}
.session-item {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
  position: relative;
}
.session-item:hover { background: var(--cc-bg-tertiary, rgba(0,0,0,0.04)); }
.session-item.active { background: var(--cc-accent-light, rgba(51,112,255,0.08)); }
:global(.dark) .session-item:hover { background: rgba(255,255,255,0.06); }
:global(.dark) .session-item.active { background: rgba(51,112,255,0.15); }

.session-icon {
  flex-shrink: 0;
  color: var(--cc-text-muted, #8f959e);
  margin-right: 2px;
}
.session-title {
  flex: 1;
  font-size: 12px;
  color: var(--cc-text-primary, #1f2329);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-agent-tag {
  font-size: 9px;
  color: #fff;
  padding: 1px 6px;
  border-radius: 4px;
  flex-shrink: 0;
  line-height: 1.4;
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-delete {
  opacity: 0;
  transition: opacity 0.15s;
  flex-shrink: 0;
}
.session-item:hover .session-delete { opacity: 1; }

.no-sessions {
  text-align: center;
  padding: 30px 0;
  font-size: 13px;
  color: var(--cc-text-muted, #8f959e);
}

/* Context Menu */
.context-menu {
  position: fixed;
  background: var(--el-bg-color, #fff);
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  border-radius: 8px;
  padding: 4px;
  z-index: 3000;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
  min-width: 120px;
}
.context-menu-item {
  padding: 6px 12px;
  font-size: 13px;
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.15s;
}
.context-menu-item:hover { background: var(--cc-bg-tertiary, rgba(0,0,0,0.04)); }
.context-menu-item.danger { color: var(--el-color-danger, #f56c6c); }
</style>
