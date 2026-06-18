<template>
  <div class="memory-page">
    <!-- Title + search (no header bar) -->
    <div class="memory-title-bar">
      <h2 class="memory-title">{{ t('memory.title') }}</h2>
      <el-input v-model="searchQuery" :placeholder="t('memory.searchPlaceholder')" clearable size="small" class="memory-search" prefix-icon="Search" />
    </div>

    <!-- Single column scrollable content -->
    <div class="memory-scroll">

      <!-- ===== Profile Section ===== -->
      <div class="memory-section">
        <div class="section-label">
          <span>🧠 个人记忆</span>
          <span class="section-count">{{ filteredProfileItems.length }}</span>
        </div>

        <!-- Add input (matches ChatInput style) -->
        <div class="memory-add">
          <el-input v-model="newProfileContent" :placeholder="t('memory.addProfile')" @keyup.enter="addProfileItem" size="default">
            <template #prefix><el-icon><Plus /></el-icon></template>
          </el-input>
          <el-button type="primary" @click="addProfileItem" :loading="savingProfile" :disabled="!newProfileContent.trim()" circle class="add-btn">
            <el-icon><Promotion /></el-icon>
          </el-button>
        </div>

        <!-- Profile items -->
        <div v-if="filteredProfileItems.length > 0" class="memory-items">
          <div v-for="item in filteredProfileItems" :key="item.id" class="memory-item">
            <div v-if="editingProfileId !== item.id" class="item-bubble">
              <div class="item-text">{{ item.content }}</div>
              <div class="item-meta">
                <span class="item-time">{{ formatTime(item.createdAt) }}</span>
                <span class="item-actions">
                  <span class="action-btn" @click="startEditProfile(item)">编辑</span>
                  <span class="action-btn danger" @click="deleteProfileItem(item.id)">删除</span>
                </span>
              </div>
            </div>
            <div v-else class="item-bubble editing">
              <el-input v-model="editingProfileContent" type="textarea" :autosize="{ minRows: 1, maxRows: 4 }" />
              <div class="item-edit-actions">
                <el-button type="primary" size="small" round @click="saveEditProfile">{{ t('common.save') || '保存' }}</el-button>
                <el-button size="small" round @click="cancelEditProfile">{{ t('common.cancel') || '取消' }}</el-button>
              </div>
            </div>
          </div>
        </div>

        <div v-else class="memory-empty">
          <div class="empty-text">{{ searchQuery ? '没有匹配的记忆' : '还没有个人记忆' }}</div>
          <div class="empty-hint">{{ searchQuery ? '试试其他关键词' : '对话中的个人偏好会自动记录在这里' }}</div>
        </div>
      </div>

      <!-- Divider -->
      <div class="memory-divider"></div>

      <!-- ===== Session Memory Section ===== -->
      <div class="memory-section">
        <div class="section-label">
          <span>💬 会话记忆</span>
          <span class="section-count">{{ filteredTasks.length }}</span>
        </div>

        <!-- Session filter -->
        <div v-if="sessions.length > 1" class="session-filter">
          <el-select v-model="filterSessionId" placeholder="全部会话" size="small" clearable>
            <el-option v-for="session in sessions" :key="session.id" :label="session.title || t('memory.untitled')" :value="session.id" />
          </el-select>
        </div>

        <!-- Grouped by session -->
        <div v-if="filteredGroupedTasks.length > 0" class="memory-items">
          <template v-for="group in filteredGroupedTasks" :key="group.sessionId">
            <div class="session-subtitle">{{ group.title }}</div>
            <div v-for="task in group.items" :key="task.id" class="memory-item">
              <div class="item-bubble">
                <div class="item-text">{{ task.content }}</div>
                <div class="item-meta">
                  <span class="item-time">{{ formatTime(task.createdAt) }}</span>
                  <span class="item-actions">
                    <span class="action-btn danger" @click="deleteTask(task.id)">删除</span>
                  </span>
                </div>
              </div>
            </div>
          </template>
        </div>

        <div v-else class="memory-empty">
          <div class="empty-text">{{ t('memory.noSessionMemory') || '暂无会话记忆' }}</div>
          <div class="empty-hint">开始对话后，AI 会自动记住重要信息</div>
        </div>
      </div>

    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Promotion, Plus, Edit, Delete } from '@element-plus/icons-vue'
import { memoryApi, sessionApi } from '@/api/chat'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

interface ProfileItem { id: string; userId: string; content: string; createdAt: string }
interface SessionContextItem { id: string; userId: string; sessionId: string; content: string; createdAt: string }
interface Session { id: string; title: string }
interface SessionGroup { sessionId: string; title: string; items: SessionContextItem[] }

const profileItems = ref<ProfileItem[]>([])
const savingProfile = ref(false)
const newProfileContent = ref('')
const editingProfileId = ref<string | null>(null)
const editingProfileContent = ref('')
const tasks = ref<SessionContextItem[]>([])
const filterSessionId = ref('')
const sessions = ref<Session[]>([])
const searchQuery = ref('')

const filteredProfileItems = computed(() => {
  if (!searchQuery.value) return profileItems.value
  const q = searchQuery.value.toLowerCase()
  return profileItems.value.filter(item => item.content.toLowerCase().includes(q))
})

const filteredTasks = computed(() => {
  let result = tasks.value
  if (filterSessionId.value) result = result.filter(t => t.sessionId === filterSessionId.value)
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    result = result.filter(task => task.content.toLowerCase().includes(q))
  }
  return result.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
})

const filteredGroupedTasks = computed<SessionGroup[]>(() => {
  const map = new Map<string, SessionContextItem[]>()
  for (const task of filteredTasks.value) {
    const sid = task.sessionId || 'unknown'
    if (!map.has(sid)) map.set(sid, [])
    map.get(sid)!.push(task)
  }
  const groups: SessionGroup[] = []
  for (const [sid, items] of map) {
    groups.push({ sessionId: sid, title: getSessionTitle(sid), items })
  }
  return groups
})

const loadProfileItems = async () => {
  try { const res: any = await memoryApi.listProfile(); profileItems.value = res.data || [] } catch { /* */ }
}
const loadTasks = async () => {
  try { const res: any = await memoryApi.listSessions(); tasks.value = res.data || [] } catch { tasks.value = [] }
}
const loadSessions = async () => {
  try {
    const res: any = await sessionApi.list(1, 100)
    const d = res.data || res
    sessions.value = (d.list || d.items || d || []).map((s: any) => ({ id: s.id, title: s.title || t('memory.untitled') }))
  } catch { sessions.value = [] }
}

const addProfileItem = async () => {
  if (!newProfileContent.value.trim()) return
  savingProfile.value = true
  try { await memoryApi.addProfile(newProfileContent.value.trim()); newProfileContent.value = ''; ElMessage.success(t('common.createSuccess')); await loadProfileItems() }
  catch { /* */ } finally { savingProfile.value = false }
}
const deleteProfileItem = async (id: string) => {
  try { await memoryApi.deleteProfile(id); ElMessage.success(t('common.deleteSuccess')); await loadProfileItems() } catch { /* */ }
}
const startEditProfile = (item: ProfileItem) => { editingProfileId.value = item.id; editingProfileContent.value = item.content }
const cancelEditProfile = () => { editingProfileId.value = null; editingProfileContent.value = '' }
const saveEditProfile = async () => {
  if (!editingProfileContent.value.trim() || !editingProfileId.value) return
  try { await memoryApi.updateProfile(editingProfileId.value, editingProfileContent.value.trim()); editingProfileId.value = null; editingProfileContent.value = ''; ElMessage.success(t('common.updateSuccess')); await loadProfileItems() } catch { /* */ }
}
const deleteTask = async (id: string) => {
  try { await memoryApi.deleteSession(id); ElMessage.success(t('common.deleteSuccess')); await loadTasks() } catch { /* */ }
}
const getSessionTitle = (sessionId: string): string => sessions.value.find(s => s.id === sessionId)?.title || t('memory.noSession')

const formatTime = (dateStr: string): string => {
  if (!dateStr) return ''
  try {
    const d = new Date(dateStr); const now = new Date(); const diffMin = Math.floor((now.getTime() - d.getTime()) / 60000)
    if (diffMin < 1) return '刚刚'; if (diffMin < 60) return `${diffMin}分钟前`
    const diffH = Math.floor(diffMin / 60); if (diffH < 24) return `${diffH}小时前`
    const diffD = Math.floor(diffH / 24); if (diffD < 30) return `${diffD}天前`
    return d.toLocaleDateString()
  } catch { return dateStr }
}

watch(filterSessionId, () => { loadTasks() })
onMounted(async () => { await Promise.all([loadProfileItems(), loadSessions(), loadTasks()]) })
</script>

<style scoped>
.memory-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  background: var(--cc-bg-secondary);
}

/* ===== Title bar (lightweight, no header) ===== */
.memory-title-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px 0;
  max-width: 820px;
  width: 100%;
  margin: 0 auto;
  box-sizing: border-box;
}
.memory-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  color: var(--cc-text-primary);
}
.memory-search { width: 200px; }

/* ===== Scrollable content ===== */
.memory-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 16px 24px 24px;
}
.memory-scroll::-webkit-scrollbar { width: 6px; }
.memory-scroll::-webkit-scrollbar-thumb { background: var(--cc-border); border-radius: 3px; }

.memory-section {
  max-width: 820px;
  width: 100%;
  margin: 0 auto;
}

/* ===== Section label ===== */
.section-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--cc-text-secondary);
  margin-bottom: 12px;
}
.section-count {
  font-size: 11px;
  background: var(--cc-bg-tertiary);
  color: var(--cc-text-muted);
  padding: 1px 8px;
  border-radius: 8px;
}

/* ===== Add input (matches ChatInput) ===== */
.memory-add {
  display: flex;
  gap: 8px;
  align-items: flex-end;
  margin-bottom: 16px;
}
.memory-add .el-input { flex: 1; }
.memory-add .el-input :deep(.el-input__wrapper) {
  border-radius: 10px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}
html.dark .memory-add .el-input :deep(.el-input__wrapper) {
  background: #1d1e1f;
  border-color: #363637;
}
.add-btn {
  width: 38px !important;
  height: 38px !important;
  border-radius: 10px !important;
  flex-shrink: 0;
}

/* ===== Session filter ===== */
.session-filter { margin-bottom: 12px; }

/* ===== Memory items ===== */
.memory-items {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

/* ===== Bubble style item ===== */
.memory-item {
  padding: 2px 0;
}
.item-bubble {
  padding: 10px 14px;
  border-radius: 10px;
  background: var(--cc-bg-primary);
  transition: background 0.15s;
}
html.dark .item-bubble { background: #1d1e1f; }
.memory-item:hover .item-bubble { background: var(--cc-bg-tertiary); }
html.dark .memory-item:hover .item-bubble { background: #262627; }
.item-bubble.editing { background: var(--cc-bg-tertiary); }

.item-text {
  font-size: 14px;
  line-height: 1.7;
  color: var(--cc-text-primary);
  word-break: break-word;
}
html.dark .item-text { color: #e5eaf3; }

.item-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
}
.item-time {
  font-size: 11px;
  color: var(--cc-text-muted);
}
.item-actions {
  display: flex;
  gap: 12px;
  opacity: 0;
  transition: opacity 0.15s;
}
.memory-item:hover .item-actions { opacity: 1; }
.action-btn {
  font-size: 12px;
  color: var(--cc-accent);
  cursor: pointer;
  user-select: none;
}
.action-btn:hover { text-decoration: underline; }
.action-btn.danger { color: var(--cc-danger); }

.item-edit-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 8px;
}

/* ===== Session subtitle ===== */
.session-subtitle {
  font-size: 12px;
  font-weight: 600;
  color: var(--cc-text-muted);
  padding: 12px 14px 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== Divider ===== */
.memory-divider {
  height: 1px;
  background: var(--cc-border);
  max-width: 820px;
  margin: 20px auto;
}

/* ===== Empty ===== */
.memory-empty {
  text-align: center;
  padding: 32px 20px;
}
.empty-text {
  font-size: 14px;
  font-weight: 500;
  color: var(--cc-text-secondary);
  margin-bottom: 4px;
}
.empty-hint {
  font-size: 13px;
  color: var(--cc-text-muted);
}

/* ===== Responsive ===== */
@media (max-width: 767px) {
  .memory-title-bar { padding: 16px 16px 0; flex-wrap: wrap; gap: 8px; }
  .memory-search { width: 100%; order: 3; }
  .memory-scroll { padding: 12px 16px 16px; }
}
</style>
