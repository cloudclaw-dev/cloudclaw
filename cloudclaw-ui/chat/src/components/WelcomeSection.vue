<template>
  <div class="welcome-section" :class="{ dark: isDark }">
    <div class="welcome-icon"><el-icon :size="48"><Promotion /></el-icon></div>
    <h2>{{ t('dashboard.welcomeTitle') }}</h2>
    <p>选择一个助手开始对话</p>

    <!-- Agent cards (top 4 by usage) -->
    <div class="agent-cards" v-if="topAgents.length > 0">
      <div v-for="item in topAgents" :key="item.agent.id" class="agent-card" @click="$emit('selectAgent', item.agent)">
        <div class="agent-avatar">{{ getAgentEmoji(item.agent) }}</div>
        <div class="agent-info">
          <div class="agent-name">{{ item.agent.name }}</div>
          <div class="agent-desc">{{ item.agent.description || t('chat.newChat') }}</div>
        </div>
        <span v-if="item.count > 0" class="agent-count">{{ item.count }}次</span>
        <el-icon class="agent-arrow"><ArrowRight /></el-icon>
      </div>
    </div>

    <el-button type="primary" size="large" @click="$emit('newSession')">
      <el-icon><Plus /></el-icon> {{ t('chat.newSession') }}
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, inject, watch } from 'vue'
import { Plus, Promotion, ArrowRight } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'

interface Agent { id: string; name: string; description?: string }
interface Session { id: string; agentId?: string }

const props = defineProps<{
  agents: Agent[]
  sessions: Session[]
}>()

defineEmits<{
  (e: 'newSession'): void
  (e: 'selectAgent', agent: Agent): void
  (e: 'startWithPrompt', text: string): void
}>()

const { t } = useI18n()
const isDark = inject('isDark', ref(false))

// Count sessions per agent, sort by usage desc, take top 4
const topAgents = computed(() => {
  const countMap = new Map<string, number>()
  for (const s of props.sessions) {
    const aid = s.agentId
    if (aid) {
      countMap.set(aid, (countMap.get(aid) || 0) + 1)
    }
  }
  return [...props.agents]
    .map(a => ({ agent: a, count: countMap.get(a.id) || 0 }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 4)
})

const getAgentEmoji = (agent: Agent) => {
  const name = (agent.name || '').toLowerCase()
  if (/翻译|translat/.test(name)) return '🌐'
  if (/代码|code|dev|开发/.test(name)) return '💻'
  if (/客服|support|售后/.test(name)) return '🎧'
  if (/旅游|travel|trip/.test(name)) return '🗺️'
  if (/数学|math/.test(name)) return '🔢'
  if (/历史|history/.test(name)) return '📜'
  if (/科学|science/.test(name)) return '🔬'
  if (/安全|security/.test(name)) return '🔒'
  if (/性能|perf/.test(name)) return '⚡'
  if (/乐观|optimist/.test(name)) return '😊'
  if (/悲观|pessimist/.test(name)) return '🤔'
  if (/现实|realist/.test(name)) return '🎯'
  if (/高德|amap|地图/.test(name)) return '📍'
  if (/助手|assistant|default/.test(name)) return '🤖'
  return '🤖'
}
</script>

<style scoped>
.welcome-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 40px 20px;
  min-height: calc(100vh - 56px - 80px);
  max-width: 520px;
  margin: 0 auto;
  color: var(--cc-text-secondary, #646a73);
}
.welcome-icon {
  margin-bottom: 16px;
  color: var(--cc-accent, #3370ff);
}
.welcome-section h2 {
  font-size: 22px;
  font-weight: 600;
  color: var(--cc-text-primary, #1f2329);
  margin-bottom: 8px;
}
.welcome-section p { font-size: 14px; margin-bottom: 24px; }

.agent-cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  margin: 0 auto 24px;
}
.agent-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 10px;
  background: var(--cc-bg-primary, #fff);
  cursor: pointer;
  text-align: left;
  transition: all 0.2s;
}
.agent-card:hover {
  background: var(--cc-bg-tertiary, #eef0f4);
}
.welcome-section.dark .agent-card { background: #1d1e1f; }
.welcome-section.dark .agent-card:hover { background: #262627; }

.agent-avatar {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: var(--cc-bg-tertiary, #eef0f4);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}
.welcome-section.dark .agent-avatar { background: #2a2b2d; }

.agent-info { flex: 1; min-width: 0; }
.agent-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--cc-text-primary, #1f2329);
  margin-bottom: 2px;
}
.agent-desc {
  font-size: 12px;
  color: var(--cc-text-muted, #8f959e);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.agent-count {
  font-size: 11px;
  color: var(--cc-accent, #3370ff);
  background: var(--cc-accent-light, #e8f0ff);
  padding: 2px 8px;
  border-radius: 8px;
  flex-shrink: 0;
}
html.dark .agent-count { background: rgba(51,112,255,0.15); }
.agent-arrow {
  color: var(--cc-text-muted, #8f959e);
  flex-shrink: 0;
}

@media (max-width: 767px) {
  .agent-cards { gap: 6px; }
  .agent-card { padding: 12px 14px; }
}
</style>
