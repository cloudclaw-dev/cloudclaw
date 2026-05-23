<template>
  <div class="admin-page">
    <!-- Page Header -->
    <div class="admin-page-header">
      <div class="header-info">
        <div class="header-icon"><el-icon><Memo /></el-icon></div>
        <div>
          <h2>{{ $t('memory.title') }}</h2>
          <div class="header-desc">{{ $t('memory.title') }}</div>
        </div>
      </div>
    </div>

    <div class="memory-content">
      <!-- Stats Overview -->
      <div class="stats-bar">
        <div class="stat-item">
          <div class="stat-value">{{ profileItems.length }}</div>
          <div class="stat-label">{{ $t('memory.profile') }}</div>
        </div>
        <div class="stat-divider" />
        <div class="stat-item">
          <div class="stat-value">{{ profileTokens }} / 1000</div>
          <div class="stat-label">Profile Tokens</div>
        </div>
        <div class="stat-divider" />
        <div class="stat-item">
          <div class="stat-value">{{ tasks.length }}</div>
          <div class="stat-label">{{ $t('memory.sessions') }}</div>
        </div>
      </div>

      <!-- User Profile (collapsible) -->
      <el-card shadow="hover">
        <template #header>
          <div class="card-header clickable" @click="showProfile = !showProfile">
            <el-icon><User /></el-icon>
            <span>{{ $t('memory.profile') }}</span>
            <el-tag size="small" type="info" v-if="profileItems.length > 0 && !showProfile">{{ profileItems.length }}</el-tag>
            <el-icon class="collapse-icon" :class="{ rotated: showProfile }"><ArrowRight /></el-icon>
          </div>
        </template>
        <div v-show="showProfile">
          <!-- Profile items as editable list -->
          <div class="profile-list">
            <div v-for="item in profileItems" :key="item.id" class="profile-list-item">
              <div v-if="editingProfileId !== item.id" class="profile-item-content">
                {{ item.content }}
              </div>
              <el-input
                v-else
                v-model="editingProfileContent"
                type="textarea"
                :autosize="{ minRows: 1, maxRows: 4 }"
              />
              <div class="profile-item-actions">
                <template v-if="editingProfileId !== item.id">
                  <el-button type="primary" text size="small" @click="startEditProfile(item)">
                    <el-icon><Edit /></el-icon>
                  </el-button>
                  <el-button type="danger" text size="small" @click="deleteProfileItem(item.id)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </template>
                <template v-else>
                  <el-button type="success" text size="small" @click="saveEditProfile">✓</el-button>
                  <el-button text size="small" @click="cancelEditProfile">✗</el-button>
                </template>
              </div>
            </div>
          </div>
          <!-- Add new profile item -->
          <div class="add-profile-area">
            <el-input
              v-model="newProfileContent"
              :placeholder="$t('memory.addProfile')"
              size="small"
              @keyup.enter="addProfileItem"
            >
              <template #append>
                <el-button @click="addProfileItem" :loading="savingProfile">
                  <el-icon><Plus /></el-icon>
                </el-button>
              </template>
            </el-input>
          </div>
          <div class="profile-actions">
            <span class="token-counter" :class="{ warning: profileTokens > 1000 }">{{ profileTokens }} / 1000 tokens</span>
          </div>
        </div>
      </el-card>

      <!-- Session Context (bottom) -->
      <el-card shadow="hover">
        <template #header>
          <div class="card-header">
            <el-icon><List /></el-icon>
            <span>{{ $t('memory.sessions') }}</span>
            <el-select
              v-model="filterSessionId"
              :placeholder="$t('memory.sessionFilter')"
              size="small"
              style="width: 200px; margin-left: auto"
            >
              <el-option
                v-for="session in sessions"
                :key="session.id"
                :label="session.title || t('memory.untitled')"
                :value="session.id"
              />
            </el-select>
          </div>
        </template>

        <div v-if="tasks.length > 0" class="task-list">
          <div v-for="task in tasks" :key="task.id" class="task-item">
            <div class="task-top">
              <div class="task-content">{{ task.content }}</div>
              <el-button type="danger" text size="small" @click="deleteTask(task.id)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <div v-if="task.sessionId" class="task-meta">
              {{ getSessionTitle(task.sessionId) }} · {{ formatTime(task.createdAt) }}
            </div>
          </div>
        </div>

        <el-empty v-else :description="t('memory.noSessionMemory')" :image-size="60" />
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Memo, ArrowRight, User, List, Delete, Edit, Plus
} from '@element-plus/icons-vue'
import { memoryApi, sessionApi } from '@/api/chat'
import '@/assets/admin.css'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const route = useRoute()

// ===== Types =====
interface ProfileItem {
  id: string
  userId: string
  content: string
  createdAt: string
}
interface SessionContextItem {
  id: string
  userId: string
  sessionId: string
  content: string
  createdAt: string
}
interface Session {
  id: string
  title: string
}

// ===== State =====
const showProfile = ref(true)
const profileItems = ref<ProfileItem[]>([])
const savingProfile = ref(false)
const newProfileContent = ref('')
const editingProfileId = ref<string | null>(null)
const editingProfileContent = ref('')

const tasks = ref<SessionContextItem[]>([])
const loadingTasks = ref(false)
const filterSessionId = ref('')
const sessions = ref<Session[]>([])

// ===== Data Loading =====
const loadProfileItems = async () => {
  try {
    const res: any = await memoryApi.listProfile()
    profileItems.value = res.data || []
    if (profileItems.value.length > 0) showProfile.value = false
  } catch (e) { /* ignore */ }
}

const loadTasks = async () => {
  loadingTasks.value = true
  try {
    if (filterSessionId.value) {
      const res: any = await memoryApi.listSessions(filterSessionId.value)
      tasks.value = res.data || []
    } else {
      const res: any = await memoryApi.listSessions()
      tasks.value = res.data || []
    }
  } catch (e) {
    tasks.value = []
  } finally {
    loadingTasks.value = false
  }
}

const loadSessions = async () => {
  try {
    const res: any = await sessionApi.list(1, 100)
    const d = res.data || res
    sessions.value = (d.list || d.items || d || []).map((s: any) => ({
      id: s.id,
      title: s.title || t('memory.untitled')
    }))
  } catch (e) {
    sessions.value = []
  }
}

// ===== Profile item actions =====
const addProfileItem = async () => {
  if (!newProfileContent.value.trim()) return
  savingProfile.value = true
  try {
    await memoryApi.addProfile(newProfileContent.value.trim())
    newProfileContent.value = ''
    ElMessage.success(t('common.createSuccess'))
    await loadProfileItems()
  } catch (e) { /* handled */ }
  finally { savingProfile.value = false }
}

const deleteProfileItem = async (id: string) => {
  try {
    await memoryApi.deleteProfile(id)
    ElMessage.success(t('common.deleteSuccess'))
    await loadProfileItems()
  } catch (e) { /* handled */ }
}

const startEditProfile = (item: ProfileItem) => {
  editingProfileId.value = item.id
  editingProfileContent.value = item.content
}

const cancelEditProfile = () => {
  editingProfileId.value = null
  editingProfileContent.value = ''
}

const saveEditProfile = async () => {
  if (!editingProfileContent.value.trim() || !editingProfileId.value) return
  try {
    await memoryApi.updateProfile(editingProfileId.value, editingProfileContent.value.trim())
    editingProfileId.value = null
    editingProfileContent.value = ''
    ElMessage.success(t('common.updateSuccess'))
    await loadProfileItems()
  } catch (e) { /* handled */ }
}

const deleteTask = async (id: string) => {
  try {
    await memoryApi.deleteSession(id)
    ElMessage.success(t('common.deleteSuccess'))
    await loadTasks()
  } catch (e) { /* handled */ }
}

const getSessionTitle = (sessionId: string): string => {
  return sessions.value.find(s => s.id === sessionId)?.title || t('memory.noSession')
}

// ===== Token estimation =====
const profileTokens = computed(() => {
  if (!profileItems.value.length) return 0
  return profileItems.value.reduce((sum, item) => {
    const text = item.content
    const chinese = (text.match(/[\u4e00-\u9fff\u3000-\u303f\uff00-\uffef]/g) || []).length
    const englishWords = (text.match(/[a-zA-Z0-9]+/g) || []).length
    return sum + Math.ceil(chinese * 1.5 + englishWords * 0.75)
  }, 0)
})

const formatTime = (dateStr: string): string => {
  if (!dateStr) return ''
  try {
    const d = new Date(dateStr)
    const now = new Date()
    const diffMs = now.getTime() - d.getTime()
    const diffMin = Math.floor(diffMs / 60000)
    if (diffMin < 1) return t('memory.justNow')
    if (diffMin < 60) return t('memory.minutesAgo')
    const diffH = Math.floor(diffMin / 60)
    if (diffH < 24) return t('memory.hoursAgo')
    const diffD = Math.floor(diffH / 24)
    if (diffD < 30) return t('memory.daysAgo')
    return d.toLocaleDateString()
  } catch { return dateStr }
}

// ===== Lifecycle =====
watch(filterSessionId, () => { loadTasks() })

onMounted(async () => {
  await Promise.all([loadProfileItems(), loadSessions(), loadTasks()])
})
</script>

<style scoped>
.memory-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.card-header.clickable { cursor: pointer; user-select: none; }
.collapse-icon { margin-left: auto; transition: transform 0.2s; }
.collapse-icon.rotated { transform: rotate(90deg); }

/* Profile list */
.profile-list { display: flex; flex-direction: column; gap: 8px; margin-bottom: 12px; }
.profile-list-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 6px;
  border: 1px solid #ebeef5;
}
.profile-list-item:hover { border-color: #c0c4cc; }
.profile-item-content { flex: 1; font-size: 14px; color: #303133; line-height: 1.6; }
.profile-item-actions { display: flex; align-items: center; gap: 2px; flex-shrink: 0; }
.add-profile-area { margin-bottom: 8px; }
.profile-actions { margin-top: 8px; display: flex; justify-content: flex-end; align-items: center; }
.token-counter { font-size: 12px; color: #67c23a; }
.token-counter.warning { color: #f56c6c; font-weight: 600; }

/* Stats bar */
.stats-bar {
  display: flex;
  align-items: center;
  gap: 0;
  background: #fff;
  border-radius: 8px;
  padding: 16px 24px;
  border: 1px solid #ebeef5;
}
.stat-item { flex: 1; text-align: center; }
.stat-value { font-size: 24px; font-weight: 700; color: #409eff; }
.stat-label { font-size: 12px; color: #909399; margin-top: 2px; }
.stat-divider { width: 1px; height: 32px; background: #e4e7ed; }

/* Task list */
.task-list { display: flex; flex-direction: column; gap: 10px; }
.task-item { padding: 12px; background: #f5f7fa; border-radius: 8px; border: 1px solid #ebeef5; }
.task-top { display: flex; align-items: flex-start; gap: 8px; }
.task-content { flex: 1; font-size: 14px; color: #303133; line-height: 1.5; white-space: pre-line; }
.task-meta { font-size: 12px; color: #909399; margin-top: 6px; }

/* Dark mode */
:root.dark .card-header { color: #e5eaf3; }
:root.dark .stats-bar { background: #1d1e1f; border-color: #363637; }
:root.dark .stat-value { color: #79bbff; }
:root.dark .stat-divider { background: #363637; }
:root.dark .task-item { background: #262737; border-color: #363637; }
:root.dark .task-content { color: #e5eaf3; }
:root.dark .profile-list-item { background: #262737; border-color: #363637; }
:root.dark .profile-item-content { color: #e5eaf3; }

@media (max-width: 768px) {
  .stats-bar { padding: 12px 16px; }
}
</style>
