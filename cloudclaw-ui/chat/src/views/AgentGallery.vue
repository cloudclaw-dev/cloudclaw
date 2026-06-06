<template>
  <div class="agent-gallery-page">
    <div class="gallery-header">
      <div class="gallery-title-row">
        <h2>🤖 {{ t('nav.agentGallery') }}</h2>
      </div>
      <p class="gallery-desc">{{ t('agentGallery.desc') }}</p>
      <el-input v-model="search" :placeholder="t('agent.searchAgent')" clearable style="max-width: 400px" size="large">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
    </div>

    <div class="agent-card-grid">
      <div
        v-for="agent in filteredAgents"
        :key="agent.id"
        class="gallery-card"
        @click="startChat(agent)"
      >
        <div class="gallery-card-emoji">{{ agent.emoji || '🤖' }}</div>
        <div class="gallery-card-name">{{ agent.name }}</div>
        <div class="gallery-card-desc" v-if="agent.description">{{ agent.description }}</div>
        <div class="gallery-card-tags">
          <el-tag v-if="agent.workflowMode" :type="workflowModeTagType(agent.workflowMode)" size="small">
            {{ agent.workflowMode }}
          </el-tag>
          <el-tag v-else type="info" size="small">single</el-tag>
        </div>
        <el-button type="primary" size="small" class="gallery-card-action">
          {{ t('chat.startChat') }}
        </el-button>
      </div>

      <div v-if="filteredAgents.length === 0" class="gallery-empty">
        {{ t('common.noData') }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, inject, Ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'

interface Agent {
  id: string
  name: string
  description?: string
  emoji?: string
  workflowMode?: string
  enabled?: boolean
}

const router = useRouter()
const { t } = useI18n()
const search = ref('')
const agents = inject<Ref<Agent[]>>('agents', ref([]))

const filteredAgents = computed(() => {
  const list = agents.value.filter(a => a.enabled !== false)
  if (!search.value) return list
  const q = search.value.toLowerCase()
  return list.filter(a =>
    a.name.toLowerCase().includes(q) ||
    (a.description || '').toLowerCase().includes(q)
  )
})

const workflowModeTagType = (mode: string) => {
  const map: Record<string, string> = {
    transfer: 'primary', supervisor: 'warning', pipeline: 'success', router: 'danger', handoff: 'info'
  }
  return map[mode] || ''
}

const startChat = async (agent: Agent) => {
  try {
    const token = localStorage.getItem('access_token')
    const res = await fetch('/api/v1/sessions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      body: JSON.stringify({ agentId: agent.id, title: '' })
    })
    if (res.ok) {
      const data = await res.json()
      const sessionId = data.data?.id || data.data
      if (sessionId) {
        router.push({ path: '/', query: { session: sessionId } })
      }
    }
  } catch (e) {
    console.error('Failed to create session:', e)
  }
}
</script>

<style scoped>
.agent-gallery-page {
  padding: 40px 32px;
  max-width: 900px;
  margin: 0 auto;
}

.gallery-header { margin-bottom: 32px; }
.gallery-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.gallery-title-row h2 {
  font-size: 22px;
  font-weight: 600;
  color: var(--cc-text-primary, #1f2329);
  margin: 0;
}
.gallery-desc {
  font-size: 14px;
  color: var(--cc-text-secondary, #646a73);
  margin: 8px 0 16px;
}

.agent-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 16px;
}

.gallery-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 20px;
  border-radius: 12px;
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  background: var(--el-bg-color, #fff);
  cursor: pointer;
  transition: all 0.2s;
}
.gallery-card:hover {
  border-color: var(--el-color-primary, #409eff);
  box-shadow: 0 4px 16px rgba(0,0,0,0.08);
  transform: translateY(-2px);
}
:global(.dark) .gallery-card { background: #1d1e1f; border-color: #363637; }
:global(.dark) .gallery-card:hover { border-color: #3370ff; background: #1a2a44; }

.gallery-card-emoji { font-size: 32px; }
.gallery-card-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.gallery-card-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.gallery-card-tags { display: flex; gap: 6px; }
.gallery-card-action { align-self: flex-start; margin-top: 4px; }
.gallery-empty {
  grid-column: 1 / -1;
  text-align: center;
  padding: 60px 0;
  color: var(--el-text-color-secondary);
}
</style>
