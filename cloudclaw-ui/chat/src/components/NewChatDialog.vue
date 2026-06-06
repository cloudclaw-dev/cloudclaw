<template>
  <el-dialog
    v-model="visible"
    :title="t('chat.newChat')"
    width="640px"
    :close-on-click-modal="true"
    class="new-chat-dialog"
  >
    <div class="dialog-search">
      <el-input v-model="search" :placeholder="t('agent.searchAgent')" clearable size="large">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
    </div>

    <div class="agent-card-grid">
      <div
        v-for="agent in filteredAgents"
        :key="agent.id"
        class="agent-card"
        @click="selectAgent(agent)"
      >
        <div class="agent-card-icon">
          <span class="agent-emoji">{{ agent.emoji || '🤖' }}</span>
        </div>
        <div class="agent-card-main">
          <div class="agent-card-name">{{ agent.name }}</div>
          <div class="agent-card-desc" v-if="agent.description">{{ agent.description }}</div>
        </div>
        <div class="agent-card-meta">
          <el-tag v-if="agent.workflowMode" :type="workflowModeTagType(agent.workflowMode)" size="small">
            {{ agent.workflowMode }}
          </el-tag>
        </div>
        <el-button type="primary" size="small" class="agent-card-action">
          {{ t('chat.startChat') }}
        </el-button>
      </div>

      <div v-if="filteredAgents.length === 0" class="agent-card-empty">
        {{ t('common.noData') }}
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
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

const props = defineProps<{
  modelValue: boolean
  agents: Agent[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'selectAgent', agent: Agent): void
}>()

const { t } = useI18n()
const search = ref('')

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const filteredAgents = computed(() => {
  const list = props.agents.filter(a => a.enabled !== false)
  if (!search.value) return list
  const q = search.value.toLowerCase()
  return list.filter(a =>
    a.name.toLowerCase().includes(q) ||
    (a.description || '').toLowerCase().includes(q)
  )
})

const workflowModeTagType = (mode: string) => {
  const map: Record<string, string> = {
    transfer: 'primary',
    supervisor: 'warning',
    pipeline: 'success',
    router: 'danger',
    handoff: 'info'
  }
  return map[mode] || ''
}

const selectAgent = (agent: Agent) => {
  emit('selectAgent', agent)
  visible.value = false
}
</script>

<style scoped>
.dialog-search { margin-bottom: 16px; }

.agent-card-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  max-height: 60vh;
  overflow-y: auto;
}

.agent-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  border-radius: 12px;
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  background: var(--el-bg-color, #fff);
  cursor: pointer;
  transition: all 0.2s;
}
.agent-card:hover {
  border-color: var(--el-color-primary, #409eff);
  box-shadow: 0 4px 16px rgba(0,0,0,0.08);
  transform: translateY(-1px);
}
:global(.dark) .agent-card {
  background: #1d1e1f;
  border-color: #363637;
}
:global(.dark) .agent-card:hover {
  border-color: #3370ff;
  background: #1a2a44;
}

.agent-card-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(64,158,255,0.08);
}
:global(.dark) .agent-card-icon { background: rgba(51,112,255,0.15); }
.agent-emoji { font-size: 20px; }

.agent-card-main { flex: 1; min-width: 0; }
.agent-card-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 2px;
}
.agent-card-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.agent-card-meta { display: flex; align-items: center; gap: 6px; }
.agent-card-action { align-self: flex-start; }

.agent-card-empty {
  grid-column: 1 / -1;
  text-align: center;
  padding: 40px 0;
  color: var(--el-text-color-secondary);
}

@media (max-width: 500px) {
  .agent-card-grid { grid-template-columns: 1fr; }
}
</style>
