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

    <!-- Charts -->
    <el-row :gutter="16" style="margin-top: 16px" class="admin-page-content">
      <el-col :span="12" :xs="24">
        <el-card shadow="hover" class="admin-card">
          <template #header><span>{{ $t('dashboard.recentSessions') }}</span></template>
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { User, ChatDotRound, Coin, Monitor, Odometer } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getUsageStats, getSessionStats } from '@/api/admin'
import '@/assets/admin.css'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const stats = ref<any>({ userCount: 0, sessionCount: 0, tokenUsage: 0, agentCount: 0 })

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
    // Use DOM id instead of ref to avoid binding issues
    setTimeout(() => {
      renderUsageChart(usageData)
      renderSessionChart(sessionData)
    }, 300)
  } catch (e) {
    console.error('Dashboard load error', e)
  }
})

function renderUsageChart(data: any) {
  const el = document.getElementById('usage-chart')
  if (!el) { console.warn('usage-chart element not found'); return }
  const chart = echarts.init(el)
  const d = data?.daily ?? []
  if (d.length === 0) {
    chart.setOption({ title: { text: t('common.noData'), left: 'center', top: 'center', textStyle: { color: '#999', fontSize: 14 } } })
    return
  }
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['Tokens In', 'Tokens Out'] },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: d.map((i: any) => i.date), axisLabel: { rotate: 30 } },
    yAxis: { type: 'value' },
    series: [
      { name: 'Tokens In', data: d.map((i: any) => i.tokensIn), type: 'line', smooth: true, areaStyle: { opacity: 0.15 }, color: '#ff9500' },
      { name: 'Tokens Out', data: d.map((i: any) => i.tokensOut), type: 'line', smooth: true, areaStyle: { opacity: 0.15 }, color: '#3370ff' }
    ]
  })
}

function renderSessionChart(data: any) {
  const el = document.getElementById('session-chart')
  if (!el) { console.warn('session-chart element not found'); return }
  const chart = echarts.init(el)
  const d = data?.daily ?? []
  if (d.length === 0) {
    chart.setOption({ title: { text: t('common.noData'), left: 'center', top: 'center', textStyle: { color: '#999', fontSize: 14 } } })
    return
  }
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: d.map((i: any) => i.date), axisLabel: { rotate: 30 } },
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
</style>

