<template>
  <div class="admin-page">
    <!-- Page Header -->
    <div class="admin-page-header">
      <div class="header-info">
        <div class="header-icon"><el-icon><Monitor /></el-icon></div>
        <div>
          <h2>{{ $t('nav.monitor') }}</h2>
          <div class="header-desc">{{ $t('monitor.subtitle') }}</div>
        </div>
      </div>
    </div>

    <el-tabs v-model="activeSection" class="system-monitor-tabs">
      <!-- 系统日志 -->
      <el-tab-pane :label="$t('monitor.logs')" name="logs">
        <el-card shadow="hover" class="admin-card">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span>{{ $t('monitor.logs') }}</span>
              <div>
                <el-select v-model="logLevel" style="width: 120px; margin-right: 8px" @change="loadLogs">
                  <el-option :label="$t('common.all')" value="" /><el-option label="ERROR" value="error" /><el-option label="WARN" value="warn" /><el-option label="INFO" value="info" />
                </el-select>
                <el-button @click="loadLogs"><el-icon><Refresh /></el-icon> {{ $t('common.refresh') }}</el-button>
              </div>
            </div>
          </template>
          <el-table :data="logs" v-loading="logLoading" max-height="500" stripe size="small">
            <el-table-column prop="time" :label="$t('common.time')" width="180" />
            <el-table-column prop="level" :label="$t('monitor.level')" width="80">
              <template #default="{ row }">
                <el-tag :type="row.level === 'error' ? 'danger' : row.level === 'warn' ? 'warning' : 'info'" size="small">{{ row.level?.toUpperCase() }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="message" :label="$t('memory.content')" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 关键指标 -->
      <el-tab-pane :label="$t('monitor.keyMetrics')" name="usage">
        <!-- 统计卡片 -->
        <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:16px;margin-bottom:20px">
          <el-card v-for="s in statCards" :key="s.label" shadow="hover" style="text-align:center">
            <div style="font-size:24px;font-weight:700;color:var(--el-color-primary)">{{ s.value }}</div>
            <div style="font-size:13px;color:#909399;margin-top:4px">{{ s.label }}</div>
          </el-card>
        </div>
        <!-- 趋势图 -->
        <el-card shadow="hover" class="admin-card">
          <template #header><span>{{ $t('monitor.keyMetrics') }}</span></template>
          <div ref="chartRef" style="height: 500px"></div>
        </el-card>
      </el-tab-pane>

      <!-- Prompt 日志 -->
      <el-tab-pane :label="$t('monitor.promptLogs')" name="prompts">
        <el-card shadow="hover" class="admin-card">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px">
              <span>{{ $t('monitor.promptLogs') }}</span>
              <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap">
                <el-input v-model="promptKeyword" :placeholder="$t('monitor.keyword')" clearable size="small" style="width: 140px" />
                <el-select v-model="promptRole" :placeholder="$t('user.role')" clearable size="small" style="width: 100px">
                  <el-option :label="$t('common.all')" value="" />
                  <el-option label="system" value="system" />
                  <el-option label="user" value="user" />
                </el-select>
                <el-date-picker v-model="promptTimeRange" type="datetimerange" :range-separator="$t('monitor.query')" :start-placeholder="$t('monitor.startTime')" :end-placeholder="$t('monitor.endTime')" size="small" style="width: 340px" value-format="YYYY-MM-DDTHH:mm:ss" />
                <el-button size="small" @click="loadPromptLogs"><el-icon><Search /></el-icon> {{ $t('monitor.query') }}</el-button>
              </div>
            </div>
          </template>
          <el-table :data="promptLogs" v-loading="promptLoading" max-height="500" stripe size="small" @row-click="showPromptDetail">
            <el-table-column prop="createdAt" :label="$t('common.time')" width="180" />
            <el-table-column prop="role" :label="$t('user.role')" width="90">
              <template #default="{ row }">
                <el-tag :type="row.role === 'system' ? 'warning' : row.role === 'user' ? '' : row.role === 'assistant' ? 'success' : 'info'" size="small">{{ row.role }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="modelId" :label="$t('common.model')" width="100" show-overflow-tooltip />
            <el-table-column prop="tokenCount" label="Tokens" width="80" />
            <el-table-column prop="durationMs" :label="$t('monitor.uptime')" width="90">
              <template #default="{ row }">{{ row.durationMs != null ? row.durationMs : '-' }}</template>
            </el-table-column>
            <el-table-column prop="content" :label="$t('memory.content')" show-overflow-tooltip />
          </el-table>
          <div style="display: flex; justify-content: flex-end; margin-top: 12px">
            <el-pagination v-model:current-page="promptPage" :page-size="20" :total="promptTotal" layout="total, prev, pager, next" @current-change="loadPromptLogs" />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- Prompt Detail Dialog -->
    <el-dialog v-model="promptDetailVisible" :title="$t('monitor.promptDetail')" width="700px">
      <div v-if="promptDetailData" style="font-size: 13px">
        <p><strong>{{ $t('common.time') }}:</strong> {{ promptDetailData.createdAt }}</p>
        <p><strong>{{ $t('user.role') }}:</strong> {{ promptDetailData.role }} | <strong>{{ $t('common.model') }}:</strong> {{ promptDetailData.modelId }} | <strong>Tokens:</strong> {{ promptDetailData.tokenCount }} | <strong>{{ $t('monitor.uptime') }}:</strong> {{ promptDetailData.durationMs ?? '-' }}ms</p>
        <el-divider />
        <pre style="white-space: pre-wrap; word-break: break-word; max-height: 500px; overflow-y: auto; background: #f5f7fa; padding: 12px; border-radius: 4px">{{ promptDetailData.content }}</pre>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { Monitor, Refresh, Search } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getLogs, getUsageStats } from '@/api/admin'
import { adminApi } from '@/api/chat'
import '@/assets/admin.css'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const activeSection = ref('logs')
const logs = ref<any[]>([])
const logLoading = ref(false)
const logLevel = ref('')
const chartRef = ref<HTMLDivElement>()
const statsData = ref<any>({})

const statCards = computed(() => [
  { label: t('nav.user'), value: statsData.value.userCount ?? '-' },
  { label: t('nav.session'), value: statsData.value.sessionCount ?? '-' },
  { label: t('monitor.totalMessages'), value: statsData.value.messageCount ?? '-' },
  { label: t('nav.agent'), value: statsData.value.agentCount ?? '-' },
  { label: t('llm.tokensIn'), value: statsData.value.totalTokensIn ?? '-' },
  { label: t('llm.tokensOut'), value: statsData.value.totalTokensOut ?? '-' },
  { label: t('llm.requestCount'), value: statsData.value.totalRequests ?? '-' },
])

const loadLogs = async () => {
  logLoading.value = true
  try {
    const res: any = await getLogs({ level: logLevel.value, limit: 200 })
    logs.value = Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : []
  } catch {} finally { logLoading.value = false }
}

const initChart = async () => {
  if (!chartRef.value) return
  try {
    const data: any = await getUsageStats()
    const resp = data.data ?? data
    statsData.value = resp
    const daily = resp.daily ?? []
    setTimeout(() => {
      if (!chartRef.value) { console.warn('chartRef still null'); return }
      const chart = echarts.init(chartRef.value)
      chart.setOption({
        tooltip: { trigger: 'axis' },
        legend: { data: [t('llm.tokenStats'), t('llm.usageCount')] },
        xAxis: { type: 'category', data: daily.map((i: any) => i.date) },
        yAxis: [
          { type: 'value', name: 'Tokens' },
          { type: 'value', name: t('llm.usageCount') }
        ],
        series: [
          { name: t('llm.tokenStats'), type: 'line', smooth: true, data: daily.map((i: any) => i.tokens), areaStyle: { opacity: 0.2 } },
          { name: t('llm.usageCount'), type: 'bar', yAxisIndex: 1, data: daily.map((i: any) => i.requests) }
        ]
      })
    })
  } catch (e) { console.error('initChart error:', e) }
}

// Load data when switching to usage tab
watch(activeSection, (val) => {
  if (val === 'usage') {
    // Use setTimeout to ensure DOM is rendered after tab switch
    setTimeout(() => initChart(), 100)
  }
})

// ===== Prompt Logs =====
const promptLogs = ref<any[]>([])
const promptLoading = ref(false)
const promptKeyword = ref('')
const promptRole = ref('')
const promptTimeRange = ref<string[]>([])
const promptPage = ref(1)
const promptTotal = ref(0)
const promptDetailVisible = ref(false)
const promptDetailData = ref<any>(null)

const loadPromptLogs = async () => {
  promptLoading.value = true
  try {
    const params: any = { page: promptPage.value, size: 20 }
    if (promptKeyword.value) params.keyword = promptKeyword.value
    if (promptRole.value) params.role = promptRole.value
    if (promptTimeRange.value && promptTimeRange.value.length === 2) {
      params.startTime = promptTimeRange.value[0]
      params.endTime = promptTimeRange.value[1]
    }
    const res: any = await adminApi.promptLogs(params)
    const d = res.data || res
    promptLogs.value = d.content || d.list || d.items || []
    promptTotal.value = d.totalElements || d.total || 0
  } catch {} finally { promptLoading.value = false }
}

const showPromptDetail = (row: any) => {
  promptDetailData.value = row
  promptDetailVisible.value = true
}

onMounted(() => { loadLogs(); loadPromptLogs() })
</script>
