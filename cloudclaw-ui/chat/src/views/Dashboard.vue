<template>
  <div class="admin-page">
    <!-- Page Header -->
    <div class="admin-page-header">
      <div class="header-info">
        <div class="header-icon"><el-icon><Odometer /></el-icon></div>
        <div>
          <h2>{{ $t('dashboard.title') }}</h2>
          <div class="header-desc">{{ $t('dashboard.welcomeDesc') }}</div>
        </div>
      </div>
    </div>

    <!-- Stats Cards -->
    <el-row :gutter="16" class="admin-page-content">
      <el-col :span="6" :xs="12">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon stat-icon-blue">
            <el-icon :size="28"><User /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.userCount }}</div>
            <div class="stat-title">{{ $t('user.title') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6" :xs="12">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon stat-icon-green">
            <el-icon :size="28"><ChatDotRound /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.sessionCount }}</div>
            <div class="stat-title">{{ $t('dashboard.sessions') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6" :xs="12">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon stat-icon-orange">
            <el-icon :size="28"><Coin /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.tokenUsage }}</div>
            <div class="stat-title">{{ $t('llm.tokenStats') }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6" :xs="12">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon stat-icon-purple">
            <el-icon :size="28"><Monitor /></el-icon>
          </div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.agentCount }}</div>
            <div class="stat-title">{{ $t('dashboard.agents') }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Charts Row -->
    <el-row :gutter="16" style="margin-top: 16px" class="admin-page-content">
      <el-col :span="12" :xs="24">
        <el-card shadow="hover" class="admin-card">
          <template #header><span>{{ $t('llm.tokenStats') }}</span></template>
          <div id="usage-chart" style="width: 100%; height: 320px"></div>
        </el-card>
      </el-col>
      <el-col :span="12" :xs="24">
        <el-card shadow="hover" class="admin-card">
          <template #header><span>{{ $t('dashboard.recentSessions') }}</span></template>
          <div id="session-chart" style="width: 100%; height: 320px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Sprint 3.5: Recent Chats & Agent Usage -->
    <el-row :gutter="16" style="margin-top: 16px" class="admin-page-content">
      <el-col :span="12" :xs="24">
        <el-card shadow="hover" class="admin-card">
          <template #header><span>{{ $t('dashboard.recentChats') }}</span></template>
          <div class="recent-chats-list">
            <div v-if="recentChats.length === 0" class="no-data-hint">{{ $t('dashboard.noRecentChats') }}</div>
            <div v-for="chat in recentChats" :key="chat.id" class="recent-chat-item">
              <div class="chat-item-info">
                <div class="chat-item-title">{{ chat.title || $t('chat.newChat') }}</div>
                <div class="chat-item-meta">
                  <span class="chat-item-agent">{{ chat.agentName || '-' }}</span>
                  <span class="chat-item-time">{{ formatTime(chat.lastActive || chat.createdAt) }}</span>
                </div>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12" :xs="24">
        <el-card shadow="hover" class="admin-card">
          <template #header><span>{{ $t('dashboard.agentUsage') }}</span></template>
          <div class="agent-usage-chart">
            <div v-if="agentUsage.length === 0" class="no-data-hint">{{ $t('common.noData') }}</div>
            <div v-for="(item, i) in agentUsage" :key="i" class="usage-bar-row">
              <span class="usage-bar-label">{{ item.name }}</span>
              <div class="usage-bar-track">
                <div class="usage-bar-fill" :style="{ width: item.percent + '%' }" />
              </div>
              <span class="usage-bar-value">{{ item.count }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { User, ChatDotRound, Coin, Monitor, Odometer } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getUsageStats, getSessionStats, getAgents } from '@/api/admin'
import api from '@/api/index'
import '@/assets/admin.css'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const stats = ref<any>({ userCount: 0, sessionCount: 0, tokenUsage: 0, agentCount: 0 })
const recentChats = ref<any[]>([])
const agentUsage = ref<any[]>([])
const chartInstances: any[] = []

onMounted(async () => {
  try {
    const [usage, session]: any[] = await Promise.all([getUsageStats(), getSessionStats()])
    const usageData = usage?.data ?? usage
    const sessionData = session?.data ?? session
    stats.value = {
      userCount: usageData.userCount ?? 0,
      sessionCount: usageData.sessionCount ?? 0,
      tokenUsage: usageData.totalTokens ?? 0,
      agentCount: usageData.agentCount ?? 0
    }
    await nextTick()
    setTimeout(() => {
      renderUsageChart(usageData)
      renderSessionChart(sessionData)
    }, 300)

    // Load recent chats
    loadRecentChats()
    // Load agent usage
    loadAgentUsage()
  } catch (e) {
    console.error('Dashboard load error', e)
  }
})

onUnmounted(() => { chartInstances.forEach(c => c.dispose()) })

const loadRecentChats = async () => {
  try {
    const res: any = await api.get('/admin/stats/recent-sessions', { params: { page: 1, size: 10 } })
    const data = res?.data ?? res
    const list = Array.isArray(data) ? data : (data?.list ?? data?.items ?? [])
    recentChats.value = list.slice(0, 10)
  } catch (e) {
    recentChats.value = []
  }
}

const loadAgentUsage = async () => {
  try {
    const res: any = await getAgents()
    const data = res?.data?.data ?? res?.data ?? res
    const agents = (data?.list ?? data?.items ?? data ?? []) as any[]
    if (!Array.isArray(agents)) { agentUsage.value = []; return }
    const total = agents.reduce((s: number, a: any) => s + (a.sessionCount || 0), 0) || 1
    agentUsage.value = agents
      .map((a: any) => ({ name: a.name || 'Agent', count: a.sessionCount || 0, percent: Math.round(((a.sessionCount || 0) / total) * 100) }))
      .sort((a: any, b: any) => b.count - a.count)
      .slice(0, 8)
  } catch (e) {
    agentUsage.value = []
  }
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

function renderUsageChart(data: any) {
  const el = document.getElementById('usage-chart')
  if (!el) return
  const existing = echarts.getInstanceByDom(el)
  if (existing) { existing.dispose() }
  const chart = echarts.init(el)
  chartInstances.push(chart)
  const d = data?.daily ?? []
  if (d.length === 0) {
    chart.setOption({ title: { text: t('common.noData'), left: 'center', top: 'center', textStyle: { color: '#999', fontSize: 14 } } })
    return
  }
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['Tokens In', 'Tokens Out'], top: 0 },
    grid: { left: '3%', right: '4%', top: 40, bottom: 60, containLabel: true },
    xAxis: { type: 'category', data: d.map((i: any) => i.date), axisLabel: { rotate: 45, fontSize: 10, interval: 3 } },
    yAxis: { type: 'value' },
    series: [
      { name: 'Tokens In', data: d.map((i: any) => i.tokensIn), type: 'line', smooth: true, areaStyle: { opacity: 0.15 }, color: '#ff9500' },
      { name: 'Tokens Out', data: d.map((i: any) => i.tokensOut), type: 'line', smooth: true, areaStyle: { opacity: 0.15 }, color: '#3370ff' }
    ]
  })
}

function renderSessionChart(data: any) {
  const el = document.getElementById('session-chart')
  if (!el) return
  const existing = echarts.getInstanceByDom(el)
  if (existing) { existing.dispose() }
  const chart = echarts.init(el)
  chartInstances.push(chart)
  const d = data?.daily ?? []
  if (d.length === 0) {
    chart.setOption({ title: { text: t('common.noData'), left: 'center', top: 'center', textStyle: { color: '#999', fontSize: 14 } } })
    return
  }
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', top: 20, bottom: 60, containLabel: true },
    xAxis: { type: 'category', data: d.map((i: any) => i.date), axisLabel: { rotate: 45, fontSize: 10, interval: 3 } },
    yAxis: { type: 'value' },
    series: [{ data: d.map((i: any) => i.count), type: 'bar', color: '#3370ff', itemStyle: { borderRadius: [4, 4, 0, 0] } }]
  })
}
</script>

<style scoped>
.stat-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 16px;
}
.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}
.stat-icon-blue { background: linear-gradient(135deg, #3370ff, #5b8def); }
.stat-icon-green { background: linear-gradient(135deg, #34c759, #5ad87d); }
.stat-icon-orange { background: linear-gradient(135deg, #ff9500, #ffb340); }
.stat-icon-purple { background: linear-gradient(135deg, #af52de, #c77deb); }
.stat-info .stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--cc-text-primary, #1f2329);
  line-height: 1;
}
.stat-info .stat-title {
  font-size: 13px;
  color: var(--cc-text-muted, #8f959e);
  margin-top: 4px;
}

/* Recent Chats */
.recent-chats-list {
  max-height: 320px;
  overflow-y: auto;
}
.no-data-hint {
  text-align: center;
  padding: 40px 20px;
  color: var(--cc-text-muted, #8f959e);
  font-size: 13px;
}
.recent-chat-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  border-radius: 8px;
  transition: background 0.15s;
  cursor: default;
}
.recent-chat-item:hover {
  background: var(--cc-bg-tertiary, #eef0f4);
}
.chat-item-info {
  flex: 1;
  min-width: 0;
}
.chat-item-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--cc-text-primary, #1f2329);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chat-item-meta {
  display: flex;
  gap: 12px;
  margin-top: 2px;
  font-size: 11px;
  color: var(--cc-text-muted, #8f959e);
}
.chat-item-agent {
  color: var(--cc-accent, #3370ff);
}

/* Agent Usage Chart */
.agent-usage-chart {
  padding: 8px 0;
}
.usage-bar-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 0;
}
.usage-bar-label {
  font-size: 13px;
  color: var(--cc-text-primary, #1f2329);
  width: 120px;
  flex-shrink: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.usage-bar-track {
  flex: 1;
  height: 20px;
  background: var(--cc-bg-tertiary, #eef0f4);
  border-radius: 4px;
  overflow: hidden;
}
.usage-bar-fill {
  height: 100%;
  border-radius: 4px;
  background: linear-gradient(90deg, #3370ff, #5b8def);
  transition: width 0.6s ease;
  min-width: 4px;
}
.usage-bar-value {
  font-size: 12px;
  color: var(--cc-text-muted, #8f959e);
  width: 40px;
  text-align: right;
}

@media (max-width: 767px) {
  .usage-bar-label {
    width: 80px;
    font-size: 12px;
  }
}

/* ===== Dark Mode ===== */
:global(.dark) .recent-chat-item:hover {
  background: rgba(255,255,255,0.06);
}
:global(.dark) .chat-item-title {
  color: #e5eaf3;
}
:global(.dark) .usage-bar-label {
  color: #e5eaf3;
}
:global(.dark) .usage-bar-track {
  background: #262627;
}
:global(.dark) .no-data-hint {
  color: #636569;
}
</style>
