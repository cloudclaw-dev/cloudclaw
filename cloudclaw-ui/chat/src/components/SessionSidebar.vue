<template>
  <el-aside width="280px" class="session-sidebar" :class="{ 'mobile-hidden': isMobile && !showMobileSessions, dark: isDark }">
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
      <el-button type="primary" :icon="Plus" size="small" @click="$emit('newSession')">{{ $t('chat.newSession') }}</el-button>
    </div>
    <!-- Session Search -->
    <div class="session-search">
      <el-input v-model="searchQuery" :placeholder="$t('chat.searchSessions')" size="small" clearable>
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
    </div>
    <!-- Batch Operations Bar -->
    <div v-if="batchMode" class="batch-bar">
      <span class="batch-count">{{ t('chat.selectedCount', { n: selectedSessions.length }) }}</span>
      <div class="batch-actions">
        <el-button size="small" text @click="batchPin">{{ t('chat.pin') }}</el-button>
        <el-button size="small" text type="danger" @click="batchDelete">{{ t('common.delete') }}</el-button>
        <el-button size="small" text @click="exitBatchMode">{{ t('common.cancel') }}</el-button>
      </div>
    </div>
    <div class="session-list">
      <template v-if="!batchMode">
        <div v-for="group in groupedSessions" :key="group.label">
          <div v-if="group.sessions.length > 0" class="session-group-label">{{ group.label }}</div>
          <div v-for="session in group.sessions" :key="session.id"
               class="session-item"
               :class="{ active: currentSessionId === session.id, pinned: pinnedIds.has(session.id) }"
               @click="handleSelectSession(session.id)"
               @contextmenu.prevent="showContextMenu($event, session)">
            <div class="session-info">
              <span v-if="pinnedIds.has(session.id)" class="pin-icon" :title="t('chat.pinned')">&#x1F4CC;</span>
              <span class="session-title">{{ session.title || t('chat.newChat') }}</span>
            </div>
            <el-button :icon="Delete" circle size="small" text class="session-delete" @click.stop="$emit('deleteSession', session.id)" />
          </div>
        </div>
      </template>
      <template v-else>
        <div v-for="session in filteredSessions" :key="session.id"
             class="session-item batch-item"
             :class="{ selected: selectedSessions.includes(session.id) }"
             @click="toggleSelectSession(session.id)">
          <el-checkbox :model-value="selectedSessions.includes(session.id)" @click.stop />
          <span class="session-title">{{ session.title || t('chat.newChat') }}</span>
        </div>
      </template>
      <div v-if="filteredSessions.length === 0" class="no-sessions">
        <p>{{ $t('chat.noSession') }}</p>
      </div>
    </div>
    <!-- Select mode toggle -->
    <div v-if="!batchMode && sessions.length > 0" class="sidebar-footer">
      <el-button size="small" text @click="enterBatchMode">{{ t('chat.batchSelect') }}</el-button>
    </div>
    <!-- Context Menu -->
    <div v-if="contextMenu.visible" class="context-menu" :style="{ top: contextMenu.y + 'px', left: contextMenu.x + 'px' }">
      <div class="context-menu-item" @click="handleRename">{{ t('chat.rename') }}</div>
      <div class="context-menu-item" @click="handleTogglePin">{{ pinnedIds.has(contextMenu.sessionId) ? t('chat.unpin') : t('chat.pin') }}</div>
      <div class="context-menu-item danger" @click="handleContextDelete">{{ t('common.delete') }}</div>
    </div>
  </el-aside>
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
import { ref, computed, watch, onMounted, onUnmounted, inject } from 'vue'
import { Plus, Delete, ChatDotRound, SetUp, Search } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'

interface Agent {
  id: string
  name: string
  description?: string
}

interface Session {
  id: string
  title: string
  agentId: string
  createdAt: string
  updatedAt: string
}

const props = defineProps<{
  agents: Agent[]
  sessions: Session[]
  currentSessionId: string
  selectedAgentId: string
  isMobile: boolean
  showMobileSessions: boolean
}>()

const emit = defineEmits<{
  (e: 'update:selectedAgentId', val: string): void
  (e: 'selectSession', id: string): void
  (e: 'deleteSession', id: string): void
  (e: 'newSession'): void
  (e: 'renameSession', id: string, title: string): void
  (e: 'pinSession', id: string): void
  (e: 'unpinSession', id: string): void
  (e: 'batchDelete', ids: string[]): void
  (e: 'batchPin', ids: string[]): void
}>()

const { t, locale } = useI18n()
const isDark = inject('isDark', ref(false))

// Search
const searchQuery = ref('')
const selectedAgentId = computed({
  get: () => props.selectedAgentId,
  set: (val) => emit('update:selectedAgentId', val)
})

// Batch mode
const batchMode = ref(false)
const selectedSessions = ref<string[]>([])

// Context menu
const contextMenu = ref<{ visible: boolean; x: number; y: number; sessionId: string }>({ visible: false, x: 0, y: 0, sessionId: '' })
const showRenameDialog = ref(false)
const renameTitle = ref('')
const renameSessionId = ref('')

// Pinned sessions
const pinnedIds = ref<Set<string>>(new Set(
  JSON.parse(localStorage.getItem('cloudclaw-pinned-sessions') || '[]')
))

const savePinnedIds = () => {
  localStorage.setItem('cloudclaw-pinned-sessions', JSON.stringify([...pinnedIds.value]))
}

// Filtered sessions
const filteredSessions = computed(() => {
  let result = props.sessions
  if (searchQuery.value.trim()) {
    const q = searchQuery.value.trim().toLowerCase()
    result = result.filter(s => (s.title || '').toLowerCase().includes(q))
  }
  return result
})

// Group sessions by time
const groupedSessions = computed(() => {
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const yesterday = new Date(today.getTime() - 86400000)
  const weekAgo = new Date(today.getTime() - 7 * 86400000)

  const pinned: Session[] = []
  const todayList: Session[] = []
  const yesterdayList: Session[] = []
  const weekList: Session[] = []
  const olderList: Session[] = []

  for (const s of filteredSessions.value) {
    if (pinnedIds.value.has(s.id)) {
      pinned.push(s)
      continue
    }
    const d = new Date(s.updatedAt || s.createdAt)
    if (d >= today) todayList.push(s)
    else if (d >= yesterday) yesterdayList.push(s)
    else if (d >= weekAgo) weekList.push(s)
    else olderList.push(s)
  }

  return [
    { label: t('chat.pinnedSessions'), sessions: pinned },
    { label: t('chat.today'), sessions: todayList },
    { label: t('chat.yesterday'), sessions: yesterdayList },
    { label: t('chat.last7Days'), sessions: weekList },
    { label: t('chat.earlier'), sessions: olderList },
  ]
})

const handleSelectSession = (id: string) => {
  if (props.isMobile) {
    emit('selectSession', id)
  } else {
    emit('selectSession', id)
  }
}

// Context menu
const showContextMenu = (e: MouseEvent, session: Session) => {
  contextMenu.value = { visible: true, x: e.clientX, y: e.clientY, sessionId: session.id }
}

const hideContextMenu = () => {
  contextMenu.value.visible = false
}

const handleRename = () => {
  const session = props.sessions.find(s => s.id === contextMenu.value.sessionId)
  if (session) {
    renameSessionId.value = session.id
    renameTitle.value = session.title || ''
    showRenameDialog.value = true
  }
  hideContextMenu()
}

const confirmRename = () => {
  emit('renameSession', renameSessionId.value, renameTitle.value)
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
  savePinnedIds()
  hideContextMenu()
}

const handleContextDelete = () => {
  emit('deleteSession', contextMenu.value.sessionId)
  hideContextMenu()
}

// Batch operations
const enterBatchMode = () => {
  batchMode.value = true
  selectedSessions.value = []
}

const exitBatchMode = () => {
  batchMode.value = false
  selectedSessions.value = []
}

const toggleSelectSession = (id: string) => {
  const idx = selectedSessions.value.indexOf(id)
  if (idx >= 0) selectedSessions.value.splice(idx, 1)
  else selectedSessions.value.push(id)
}

const batchDelete = () => {
  if (selectedSessions.value.length > 0) {
    emit('batchDelete', [...selectedSessions.value])
    exitBatchMode()
  }
}

const batchPin = () => {
  if (selectedSessions.value.length > 0) {
    for (const id of selectedSessions.value) {
      pinnedIds.value.add(id)
    }
    savePinnedIds()
    emit('batchPin', [...selectedSessions.value])
    exitBatchMode()
  }
}

// Click outside to close context menu
const handleClickOutside = () => {
  if (contextMenu.value.visible) hideContextMenu()
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<style scoped>
.session-sidebar {
  width: 280px !important;
  background: var(--cc-bg-primary, #fff);
  border-right: 1px solid var(--cc-border, #e8eaed);
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
  color: var(--cc-text-primary, #1f2329);
}
.sidebar-page-header .header-desc {
  font-size: 13px;
  color: var(--cc-text-muted, #8f959e);
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
  background: var(--cc-bg-tertiary, #eef0f4) !important;
  color: var(--cc-accent, #3370ff);
}
.sidebar-toolbar {
  display: flex;
  gap: 8px;
  padding: 0 16px 12px;
}
.sidebar-toolbar .agent-select { flex: 1; }
.session-search {
  padding: 0 16px 8px;
}
.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.session-list::-webkit-scrollbar { width: 4px; }
.session-list::-webkit-scrollbar-thumb { background: var(--cc-border, #e8eaed); border-radius: 2px; }
.session-group-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--cc-text-muted, #8f959e);
  padding: 8px 12px 4px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
  margin-bottom: 2px;
}
.session-item:hover { background: var(--cc-bg-tertiary, #eef0f4); }
.session-item.active { background: var(--cc-accent-light, #e8f0ff); color: var(--cc-accent, #3370ff); }
.session-info {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 4px;
}
.pin-icon { font-size: 12px; flex-shrink: 0; }
.session-title {
  font-size: 13px;
  color: var(--cc-text-primary, #1f2329);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.session-item.active .session-title { color: var(--cc-accent, #3370ff); font-weight: 500; }
.session-delete { opacity: 0; transition: opacity 0.15s; }
.session-item:hover .session-delete { opacity: 1; }
.no-sessions {
  text-align: center;
  padding: 40px 20px;
  color: var(--cc-text-muted, #8f959e);
  font-size: 13px;
}

/* Batch mode */
.batch-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 16px;
  background: var(--cc-accent-light, #e8f0ff);
  border-bottom: 1px solid var(--cc-border, #e8eaed);
  font-size: 12px;
}
.batch-count { color: var(--cc-accent, #3370ff); font-weight: 500; }
.batch-actions { display: flex; gap: 4px; }
.batch-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.batch-item.selected { background: var(--cc-accent-light, #e8f0ff); }
.sidebar-footer {
  padding: 8px 16px;
  border-top: 1px solid var(--cc-border, #e8eaed);
  text-align: center;
}

/* Context Menu */
.context-menu {
  position: fixed;
  background: var(--cc-bg-primary, #fff);
  border: 1px solid var(--cc-border, #e8eaed);
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.12);
  z-index: 999;
  padding: 4px 0;
  min-width: 140px;
}
.context-menu-item {
  padding: 8px 16px;
  font-size: 13px;
  cursor: pointer;
  color: var(--cc-text-primary, #1f2329);
  transition: background 0.1s;
}
.context-menu-item:hover { background: var(--cc-bg-tertiary, #eef0f4); }
.context-menu-item.danger { color: var(--cc-danger, #ff3b30); }

/* ===== Dark Mode ===== */
.dark .session-item:hover { background: rgba(255,255,255,0.06); }
.dark .session-item.active { background: rgba(51,112,255,0.15); }
.dark .session-item.active .session-title { color: #79bbff; }
.dark .session-title { color: #e5eaf3; }
.dark .session-group-label { color: #636569; }
.dark .batch-bar { background: rgba(51,112,255,0.12); border-color: #363637; }
.dark .batch-item.selected { background: rgba(51,112,255,0.15); }
.dark .sidebar-footer { border-color: #363637; }
.dark .context-menu { background: #1d1e1f; border-color: #363637; box-shadow: 0 4px 16px rgba(0,0,0,0.4); }
.dark .context-menu-item { color: #e5eaf3; }
.dark .context-menu-item:hover { background: rgba(255,255,255,0.06); }
.dark .sidebar-page-header h2 { color: #e5eaf3; }
.dark .sidebar-page-header .header-desc { color: #636569; }
</style>
