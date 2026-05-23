<template>
  <div class="admin-page">
    <div class="admin-page-header">
      <div class="header-info">
        <div class="header-icon"><el-icon><Cpu /></el-icon></div>
        <div>
          <h2>{{ $t('nav.llm') }}</h2>
          <div class="header-desc">{{ $t('llm.subtitle') }}</div>
        </div>
      </div>
    </div>
    <el-card shadow="hover" class="admin-card">
    <el-tabs v-model="activeTab">
      <!-- Provider 管理 -->
      <el-tab-pane :label="$t('llm.providers')" name="providers">
        <div class="admin-table-toolbar">
          <el-input v-model="providerSearch" :placeholder="$t('mcp.searchServer')" style="width: 300px" clearable>
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button type="primary" @click="openProviderDialog()"><el-icon><Plus /></el-icon> {{ $t('llm.newProvider') }}</el-button>
        </div>
        <!-- Mobile Card List -->
        <div class="mobile-card-list" v-if="isMobile">
          <el-card v-for="item in filteredProviders" :key="item.id" shadow="hover">
            <div class="mobile-card-item">
              <div class="mobile-card-header">
                <span class="mobile-card-title">{{ item.displayName || item.name }}</span>
                <el-tag :type="item.enabled ? 'success' : 'info'" size="small">{{ item.enabled ? t('common.enable') : t('common.disable') }}</el-tag>
              </div>
              <div class="mobile-card-meta">
                <span>{{ item.providerType }}</span>
              </div>
              <div v-if="item.apiBase" class="mobile-card-desc">{{ item.apiBase }}</div>
              <div class="mobile-card-actions">
                <el-button size="small" @click="openProviderDialog(item)">{{ $t('common.edit') }}</el-button>
                <el-button size="small" type="danger" @click="confirmDeleteProvider(item)">{{ $t('common.delete') }}</el-button>
              </div>
            </div>
          </el-card>
          <div v-if="filteredProviders.length === 0" style="text-align:center;padding:40px;color:var(--cc-text-muted)">{{ $t('common.noData') }}</div>
        </div>
        <el-table :data="filteredProviders" :class="{ 'mobile-hide': isMobile }" v-loading="loading" stripe>
          <el-table-column prop="name" :label="$t('common.name')" width="140" />
          <el-table-column prop="displayName" :label="$t('llm.displayName')" width="140" />
          <el-table-column prop="apiBase" label="API Base" show-overflow-tooltip />
          <el-table-column prop="providerType" :label="$t('common.type')" width="160" />
          <el-table-column prop="enabled" :label="$t('common.status')" width="80">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? t('common.enable') : t('common.disable') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('common.actions')" fixed="right" width="160">
            <template #default="{ row }">
              <el-button link type="primary" @click="openProviderDialog(row)">{{ $t('common.edit') }}</el-button>
              <el-popconfirm :title="$t('common.deleteConfirm')" @confirm="handleDeleteProvider(row.id)">
                <template #reference><el-button link type="danger">{{ $t('common.delete') }}</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 模型管理 -->
      <el-tab-pane :label="$t('llm.models')" name="models">
        <div class="admin-table-toolbar">
          <el-input v-model="modelSearch" :placeholder="$t('llm.models')" style="width: 300px" clearable>
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button type="primary" @click="openModelDialog()"><el-icon><Plus /></el-icon> {{ $t('llm.newModel') }}</el-button>
        </div>
        <!-- Mobile Card List -->
        <div class="mobile-card-list" v-if="isMobile">
          <el-card v-for="item in filteredModels" :key="item.id" shadow="hover">
            <div class="mobile-card-item">
              <div class="mobile-card-header">
                <span class="mobile-card-title">{{ item.displayName || item.modelName }}</span>
                <el-tag :type="item.enabled ? 'success' : 'info'" size="small">{{ item.enabled ? t('common.enable') : t('common.disable') }}</el-tag>
              </div>
              <div class="mobile-card-meta">
                <span>{{ getProviderName(item.providerId) }}</span>
                <span style="margin-left: 12px">{{ item.modelType }}</span>
                <span v-if="item.contextWindow" style="margin-left: 12px">{{ $t('llm.contextWindow') }}: {{ item.contextWindow }}</span>
              </div>
              <div class="mobile-card-desc">{{ item.modelName }}</div>
              <div class="mobile-card-actions">
                <el-button size="small" @click="openModelDialog(item)">{{ $t('common.edit') }}</el-button>
                <el-button size="small" type="danger" @click="confirmDeleteModel(item)">{{ $t('common.delete') }}</el-button>
              </div>
            </div>
          </el-card>
          <div v-if="filteredModels.length === 0" style="text-align:center;padding:40px;color:var(--cc-text-muted)">{{ $t('common.noData') }}</div>
        </div>
        <el-table :data="filteredModels" :class="{ 'mobile-hide': isMobile }" v-loading="loading" stripe>
          <el-table-column prop="modelName" :label="$t('llm.modelName')" min-width="160" />
          <el-table-column prop="displayName" :label="$t('llm.displayName')" width="160" />
          <el-table-column prop="providerId" label="Provider" width="140">
            <template #default="{ row }">{{ getProviderName(row.providerId) }}</template>
          </el-table-column>
          <el-table-column prop="modelType" :label="$t('common.type')" width="80" />
          <el-table-column prop="contextWindow" :label="$t('llm.contextWindow')" width="100" />
          <el-table-column prop="enabled" :label="$t('common.status')" width="80">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? t('common.enable') : t('common.disable') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('common.actions')" fixed="right" width="160">
            <template #default="{ row }">
              <el-button link type="primary" @click="openModelDialog(row)">{{ $t('common.edit') }}</el-button>
              <el-popconfirm :title="$t('common.deleteConfirm')" @confirm="handleDeleteModel(row.id)">
                <template #reference><el-button link type="danger">{{ $t('common.delete') }}</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 凭据管理 -->
      <el-tab-pane :label="$t('llm.credentials')" name="credentials">
        <div class="admin-table-toolbar">
          <el-input v-model="credSearch" :placeholder="$t('llm.credentials')" style="width: 300px" clearable>
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button type="primary" @click="openCredDialog()"><el-icon><Plus /></el-icon> {{ $t('llm.newCredential') }}</el-button>
        </div>
        <!-- Mobile Card List -->
        <div class="mobile-card-list" v-if="isMobile">
          <el-card v-for="item in filteredCredentials" :key="item.id" shadow="hover">
            <div class="mobile-card-item">
              <div class="mobile-card-header">
                <span class="mobile-card-title">{{ item.name }}</span>
                <div class="mobile-card-tags">
                  <el-tag size="small">{{ getProviderName(item.providerId) }}</el-tag>
                  <el-tag :type="item.enabled ? 'success' : 'info'" size="small">{{ item.enabled ? t('common.enable') : t('common.disable') }}</el-tag>
                </div>
              </div>
              <div class="mobile-card-meta">
                <span>{{ $t('llm.priority') }}: {{ item.priority }}</span>
                <span style="margin-left: 12px">{{ $t('llm.weight') }}: {{ item.weight }}</span>
              </div>
              <div class="mobile-card-desc">{{ item.apiKeyEncrypted }}</div>
              <div class="mobile-card-actions">
                <el-button size="small" @click="openCredDialog(item)">{{ $t('common.edit') }}</el-button>
                <el-button size="small" type="danger" @click="confirmDeleteCred(item)">{{ $t('common.delete') }}</el-button>
              </div>
            </div>
          </el-card>
          <div v-if="filteredCredentials.length === 0" style="text-align:center;padding:40px;color:var(--cc-text-muted)">{{ $t('common.noData') }}</div>
        </div>
        <el-table :data="filteredCredentials" :class="{ 'mobile-hide': isMobile }" v-loading="credLoading" stripe>
          <el-table-column prop="name" :label="$t('common.name')" width="140" />
          <el-table-column label="Provider" width="140">
            <template #default="{ row }">{{ getProviderName(row.providerId) }}</template>
          </el-table-column>
          <el-table-column prop="apiKeyEncrypted" :label="$t('llm.apiKey')" min-width="200" />
          <el-table-column prop="priority" :label="$t('llm.priority')" width="80" />
          <el-table-column prop="weight" :label="$t('llm.weight')" width="80" />
          <el-table-column prop="enabled" :label="$t('common.status')" width="80">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? t('common.enable') : t('common.disable') }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('common.actions')" fixed="right" width="160">
            <template #default="{ row }">
              <el-button link type="primary" @click="openCredDialog(row)">{{ $t('common.edit') }}</el-button>
              <el-popconfirm :title="$t('common.deleteConfirm')" @confirm="handleDeleteCred(row.id)">
                <template #reference><el-button link type="danger">{{ $t('common.delete') }}</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 用量统计 -->
      <el-tab-pane :label="$t('llm.usage')" name="usage">
        <div style="margin-bottom: 16px; display: flex; align-items: center; gap: 12px; flex-wrap: wrap">
          <el-date-picker v-model="usageDateRange" type="daterange" range-separator="-" start-placeholder="$t('monitor.startTime')" end-placeholder="$t('monitor.endTime')" value-format="YYYY-MM-DD" @change="loadUsage" />
          <el-button type="primary" @click="loadUsage">{{ $t('common.refresh') }}</el-button>
        </div>

        <!-- 概览卡片 -->
        <el-row :gutter="16" style="margin-bottom: 20px">
          <el-col :span="6" :xs="12" :sm="12">
            <el-card shadow="hover"><el-statistic :title="$t('llm.requestCount')" :value="usageSummary.totalRequests || 0" /></el-card>
          </el-col>
          <el-col :span="6" :xs="12" :sm="12">
            <el-card shadow="hover"><el-statistic :title="$t('llm.tokensIn')" :value="usageSummary.totalTokensIn || 0" /></el-card>
          </el-col>
          <el-col :span="6" :xs="12" :sm="12">
            <el-card shadow="hover"><el-statistic :title="$t('llm.tokensOut')" :value="usageSummary.totalTokensOut || 0" /></el-card>
          </el-col>
          <el-col :span="6" :xs="12" :sm="12">
            <el-card shadow="hover"><el-statistic :title="$t('llm.cost')" :value="usageSummary.totalCost || 0" :precision="4" /></el-card>
          </el-col>
        </el-row>

        <el-tabs v-model="usageSubTab" type="border-card">
          <!-- 按模型 -->
          <el-tab-pane :label="$t('llm.models')" name="byModel">
            <el-table :data="usageByModel" stripe border v-loading="usageLoading">
              <el-table-column prop="modelId" :label="$t('llm.models')" width="280" />
              <el-table-column :label="$t('llm.modelName')" width="160">
                <template #default="{ row }">{{ getModelName(row.modelId) }}</template>
              </el-table-column>
              <el-table-column prop="requestCount" :label="$t('llm.requestCount')" width="100" />
              <el-table-column prop="tokensIn" :label="$t('llm.tokensIn')" width="140" />
              <el-table-column prop="tokensOut" :label="$t('llm.tokensOut')" width="140" />
              <el-table-column prop="cost" :label="$t('llm.cost')" width="120" />
            </el-table>
          </el-tab-pane>

          <!-- 按用户 -->
          <el-tab-pane :label="$t('nav.user')" name="byUser">
            <el-table :data="usageByUser" stripe border v-loading="usageLoading">
              <el-table-column prop="userId" :label="$t('nav.user')" width="320" />
              <el-table-column prop="requestCount" :label="$t('llm.requestCount')" width="100" />
              <el-table-column prop="tokensIn" :label="$t('llm.tokensIn')" width="140" />
              <el-table-column prop="tokensOut" :label="$t('llm.tokensOut')" width="140" />
              <el-table-column prop="cost" :label="$t('llm.cost')" width="120" />
            </el-table>
          </el-tab-pane>

          <!-- 按日期 -->
          <el-tab-pane :label="$t('monitor.startTime')" name="daily">
            <el-table :data="usageDaily" stripe border v-loading="usageLoading">
              <el-table-column prop="date" :label="$t('monitor.startTime')" width="140" />
              <el-table-column prop="requestCount" :label="$t('llm.requestCount')" width="100" />
              <el-table-column prop="tokensIn" :label="$t('llm.tokensIn')" width="140" />
              <el-table-column prop="tokensOut" :label="$t('llm.tokensOut')" width="140" />
              <el-table-column prop="cost" :label="$t('llm.cost')" width="120" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </el-tab-pane>
    </el-tabs>
    </el-card>

    <!-- Provider Dialog -->
    <el-dialog v-model="providerDialogVisible" :title="isProviderEdit ? t('llm.editProvider') : t('llm.newProvider')" width="560" destroy-on-close>
      <el-form :model="providerForm" ref="providerFormRef" label-width="100px">
        <el-form-item :label="$t('common.name')" required><el-input v-model="providerForm.name" :placeholder="$t('llm.providerNamePlaceholder')" /></el-form-item>
        <el-form-item :label="$t('llm.displayName')"><el-input v-model="providerForm.displayName" :placeholder="$t('llm.providerNamePlaceholder')" /></el-form-item>
        <el-form-item label="API Base" required><el-input v-model="providerForm.apiBase" placeholder="https://api.openai.com/v1" /></el-form-item>
        <el-form-item :label="$t('common.type')" required>
          <el-select v-model="providerForm.providerType" style="width: 100%">
            <el-option :label="$t('llm.providerTypeOpenAI')" value="openai_compatible" />
            <el-option label="Ollama" value="ollama" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('common.enable')"><el-switch v-model="providerForm.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="providerDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveProvider">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- Model Dialog -->
    <el-dialog v-model="modelDialogVisible" :title="isModelEdit ? t('llm.editModel') : t('llm.newModel')" width="680" destroy-on-close>
      <el-form :model="modelForm" ref="modelFormRef" label-width="100px">
        <el-form-item label="Provider" required>
          <el-select v-model="modelForm.providerId" style="width: 100%">
            <el-option v-for="p in providers" :key="p.id" :label="p.displayName || p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('llm.modelName')" required><el-input v-model="modelForm.modelName" :placeholder="$t('llm.modelNamePlaceholder')" /></el-form-item>
        <el-form-item :label="$t('llm.displayName')"><el-input v-model="modelForm.displayName" :placeholder="$t('llm.displayNamePlaceholder')" /></el-form-item>
        <el-form-item :label="$t('common.type')" required>
          <el-select v-model="modelForm.modelType" style="width: 100%">
            <el-option label="Chat" value="chat" />
            <el-option label="Embedding" value="embedding" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('llm.contextWindow')"><el-input-number v-model="modelForm.contextWindow" :min="0" /></el-form-item>
        <el-form-item :label="$t('llm.maxOutput')"><el-input-number v-model="modelForm.maxOutput" :min="0" /></el-form-item>
        <el-form-item :label="$t('llm.inputPrice')"><el-input-number v-model="modelForm.inputPrice" :precision="6" :step="0.0001" /></el-form-item>
        <el-form-item :label="$t('llm.outputPrice')"><el-input-number v-model="modelForm.outputPrice" :precision="6" :step="0.0001" /></el-form-item>
        <el-form-item :label="$t('llm.defaultParams')"><el-input v-model="modelForm.defaultParams" type="textarea" :rows="2" placeholder='{"temperature":0.7}' /></el-form-item>
        <el-form-item :label="$t('common.enable')"><el-switch v-model="modelForm.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="modelDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveModel">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- Credential Dialog -->
    <el-dialog v-model="credDialogVisible" :title="isCredEdit ? t('llm.editCredential') : t('llm.newCredential')" width="560" destroy-on-close>
      <el-form :model="credForm" ref="credFormRef" label-width="100px">
        <el-form-item v-if="!isCredEdit" label="Provider" required>
          <el-select v-model="credForm.providerId" style="width: 100%" :placeholder="$t('llm.providers')">
            <el-option v-for="p in providers" :key="p.id" :label="p.displayName || p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="Provider">
          <el-input :model-value="getProviderName(credForm.providerId)" disabled />
        </el-form-item>
        <el-form-item :label="$t('common.name')" required><el-input v-model="credForm.name" :placeholder="$t('llm.credentialNamePlaceholder')" /></el-form-item>
        <el-form-item :label="$t('llm.apiKey')" required><el-input v-model="credForm.apiKey" type="password" show-password :placeholder="isCredEdit ? t('common.required') : t('llm.apiKeyPlaceholder')" /></el-form-item>
        <el-form-item :label="$t('llm.priority')"><el-input-number v-model="credForm.priority" :min="1" /></el-form-item>
        <el-form-item :label="$t('llm.weight')"><el-input-number v-model="credForm.weight" :min="1" /></el-form-item>
        <el-form-item :label="$t('common.enable')"><el-switch v-model="credForm.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="credDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveCred">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import {
  getLlmProviders, createLlmProvider, updateLlmProvider, deleteLlmProvider,
  getLlmModels, createLlmModel, updateLlmModel, deleteLlmModel,
  getLlmCredentials, createLlmCredential, updateLlmCredential, deleteLlmCredential
} from '@/api/admin'
import '@/assets/admin.css'
import api from '@/api/index'
import { useMobile } from '@/composables/useMobile'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const activeTab = ref('providers')
const loading = ref(false)
const saving = ref(false)
const { isMobile } = useMobile()

// Search
const providerSearch = ref('')
const modelSearch = ref('')
const credSearch = ref('')

// Usage stats
const usageSubTab = ref('byModel')
const usageLoading = ref(false)
const usageDateRange = ref<[string, string] | null>(null)
const usageSummary = ref<any>({})
const usageByModel = ref<any[]>([])
const usageByUser = ref<any[]>([])
const usageDaily = ref<any[]>([])

// Providers
const providers = ref<any[]>([])
const providerDialogVisible = ref(false)
const isProviderEdit = ref(false)
const editProviderId = ref('')
const defaultProviderForm = { name: '', displayName: '', apiBase: '', providerType: 'openai_compatible', enabled: true }
const providerForm = reactive({ ...defaultProviderForm })

// Models
const models = ref<any[]>([])
const modelDialogVisible = ref(false)
const isModelEdit = ref(false)
const editModelId = ref('')
const defaultModelForm = { providerId: '', modelName: '', displayName: '', modelType: 'chat', contextWindow: 128000, maxOutput: 4096, inputPrice: 0, outputPrice: 0, defaultParams: '{"temperature":0.7}', enabled: true }
const modelForm = reactive({ ...defaultModelForm })

// Credentials
const credentials = ref<any[]>([])
const credLoading = ref(false)
const credDialogVisible = ref(false)
const isCredEdit = ref(false)
const editCredId = ref('')
const defaultCredForm = { providerId: '', name: '', apiKey: '', priority: 1, weight: 100, enabled: true }
const credForm = reactive({ ...defaultCredForm })

// Filtered data
const filteredProviders = computed(() => {
  if (!providerSearch.value) return providers.value
  const kw = providerSearch.value.toLowerCase()
  return providers.value.filter(r =>
    r.name?.toLowerCase().includes(kw) || r.displayName?.toLowerCase().includes(kw) || r.apiBase?.toLowerCase().includes(kw) || r.providerType?.toLowerCase().includes(kw)
  )
})

const filteredModels = computed(() => {
  if (!modelSearch.value) return models.value
  const kw = modelSearch.value.toLowerCase()
  return models.value.filter(r =>
    r.modelName?.toLowerCase().includes(kw) || r.displayName?.toLowerCase().includes(kw) || getProviderName(r.providerId).toLowerCase().includes(kw)
  )
})

const filteredCredentials = computed(() => {
  if (!credSearch.value) return credentials.value
  const kw = credSearch.value.toLowerCase()
  return credentials.value.filter(r =>
    r.name?.toLowerCase().includes(kw) || r.apiKeyEncrypted?.toLowerCase().includes(kw) || getProviderName(r.providerId).toLowerCase().includes(kw)
  )
})

const getProviderName = (pid: string) => {
  const p = providers.value.find(p => p.id === pid)
  return p ? (p.displayName || p.name) : pid
}

const getModelName = (mid: string) => {
  const m = models.value.find((m: any) => m.id === mid)
  return m ? (m.displayName || m.modelName) : mid
}

// Mobile delete confirm helpers
const confirmDeleteProvider = (item: any) => {
  ElMessageBox.confirm(t('common.deleteConfirm'), t('common.confirm'), { type: 'warning' }).then(() => handleDeleteProvider(item.id)).catch(() => {})
}
const confirmDeleteModel = (item: any) => {
  ElMessageBox.confirm(t('common.deleteConfirm'), t('common.confirm'), { type: 'warning' }).then(() => handleDeleteModel(item.id)).catch(() => {})
}
const confirmDeleteCred = (item: any) => {
  ElMessageBox.confirm(t('common.deleteConfirm'), t('common.confirm'), { type: 'warning' }).then(() => handleDeleteCred(item.id)).catch(() => {})
}

// Usage loading
const loadUsage = async () => {
  usageLoading.value = true
  try {
    let sd: string | undefined
    let ed: string | undefined
    if (usageDateRange.value && usageDateRange.value.length === 2) {
      sd = usageDateRange.value[0]
      ed = usageDateRange.value[1]
    }
    const params = new URLSearchParams()
    if (sd) params.append('startDate', sd)
    if (ed) params.append('endDate', ed)
    const qs = params.toString()
    const base = '/admin/llm/usage'
    const [sumRes, modelRes, userRes, dailyRes]: any[] = await Promise.all([
      api.get(base + (qs ? '?' + qs : '')),
      api.get(base + '/by-model' + (qs ? '?' + qs : '')),
      api.get(base + '/by-user' + (qs ? '?' + qs : '')),
      api.get(base + '/daily' + (qs ? '?' + qs : ''))
    ])
    usageSummary.value = sumRes?.data || {}
    usageByModel.value = modelRes?.data || []
    usageByUser.value = userRes?.data || []
    usageDaily.value = dailyRes?.data || []
  } catch {} finally { usageLoading.value = false }
}

// Load data
const loadProviders = async () => {
  loading.value = true
  try {
    const res: any = await getLlmProviders()
    providers.value = res?.data?.data || res?.data || []
  } catch {} finally { loading.value = false }
}

const loadModels = async () => {
  try {
    const res: any = await getLlmModels()
    models.value = res?.data?.data || res?.data || []
  } catch {}
}

const loadCredentials = async () => {
  credLoading.value = true
  try {
    const res: any = await getLlmCredentials()
    credentials.value = res?.data?.data || res?.data || []
  } catch {} finally { credLoading.value = false }
}

// Provider CRUD
const openProviderDialog = (row?: any) => {
  isProviderEdit.value = !!row
  if (row) {
    editProviderId.value = row.id
    Object.assign(providerForm, { name: row.name, displayName: row.displayName || '', apiBase: row.apiBase, providerType: row.providerType, enabled: row.enabled })
  } else {
    editProviderId.value = ''
    Object.assign(providerForm, defaultProviderForm)
  }
  providerDialogVisible.value = true
}

const handleSaveProvider = async () => {
  saving.value = true
  try {
    if (isProviderEdit.value) { await updateLlmProvider(editProviderId.value, providerForm) } else { await createLlmProvider(providerForm) }
    ElMessage.success(t('common.updateSuccess'))
    providerDialogVisible.value = false
    loadProviders()
  } catch {} finally { saving.value = false }
}

const handleDeleteProvider = async (id: string) => {
  await deleteLlmProvider(id)
  ElMessage.success(t('common.deleteSuccess'))
  loadProviders()
}

// Model CRUD
const openModelDialog = (row?: any) => {
  isModelEdit.value = !!row
  if (row) {
    editModelId.value = row.id
    Object.assign(modelForm, { providerId: row.providerId, modelName: row.modelName, displayName: row.displayName || '', modelType: row.modelType, contextWindow: row.contextWindow, maxOutput: row.maxOutput, inputPrice: row.inputPrice, outputPrice: row.outputPrice, defaultParams: row.defaultParams || '', enabled: row.enabled })
  } else {
    editModelId.value = ''
    Object.assign(modelForm, defaultModelForm)
  }
  modelDialogVisible.value = true
}

const handleSaveModel = async () => {
  saving.value = true
  try {
    if (isModelEdit.value) { await updateLlmModel(editModelId.value, modelForm) } else { await createLlmModel(modelForm) }
    ElMessage.success(t('common.updateSuccess'))
    modelDialogVisible.value = false
    loadModels()
  } catch {} finally { saving.value = false }
}

const handleDeleteModel = async (id: string) => {
  await deleteLlmModel(id)
  ElMessage.success(t('common.deleteSuccess'))
  loadModels()
}

// Credential CRUD
const openCredDialog = (row?: any) => {
  isCredEdit.value = !!row
  if (row) {
    editCredId.value = row.id
    Object.assign(credForm, { providerId: row.providerId, name: row.name, apiKey: '', priority: row.priority || 1, weight: row.weight || 100, enabled: row.enabled })
  } else {
    editCredId.value = ''
    Object.assign(credForm, defaultCredForm)
  }
  credDialogVisible.value = true
}

const handleSaveCred = async () => {
  saving.value = true
  try {
    const data = { ...credForm }
    if (isCredEdit.value) { await updateLlmCredential(editCredId.value, data) } else { await createLlmCredential(data) }
    ElMessage.success(t('common.updateSuccess'))
    credDialogVisible.value = false
    loadCredentials()
  } catch {} finally { saving.value = false }
}

const handleDeleteCred = async (id: string) => {
  await deleteLlmCredential(id)
  ElMessage.success(t('common.deleteSuccess'))
  loadCredentials()
}

onMounted(async () => {
  await loadProviders()
  loadModels()
  loadCredentials()
  loadUsage()
})
</script>
