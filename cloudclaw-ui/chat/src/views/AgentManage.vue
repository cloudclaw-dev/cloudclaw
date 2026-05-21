<template>
  <div class="admin-page">
    <!-- Page Header -->
    <div class="admin-page-header">
      <div class="header-info">
        <div class="header-icon"><el-icon><SetUp /></el-icon></div>
        <div>
          <h2>{{ $t('nav.agent') }}</h2>
          <div class="header-desc">{{ $t('agent.subtitle') }}</div>
        </div>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon> {{ $t('agent.newAgent') }}</el-button>
      </div>
    </div>

    <!-- Table Card -->
    <el-card shadow="hover" class="admin-card">
      <div class="admin-table-toolbar">
        <el-input v-model="search" :placeholder="$t('agent.searchAgent')" style="width: 300px" clearable>
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>
    <!-- Mobile Card List -->
    <div class="mobile-card-list" v-if="isMobile">
      <el-card v-for="item in filteredData" :key="item.id" shadow="hover">
        <div class="mobile-card-item">
          <div class="mobile-card-header">
            <span class="mobile-card-title">{{ item.name }}</span>
            <el-tag :type="item.enabled ? 'success' : 'info'" size="small">{{ item.enabled ? t('common.enabled') : t('common.disabled') }}</el-tag>
          </div>
          <div class="mobile-card-meta">
            <span>{{ $t('common.model') }}: {{ item.modelId || '-' }}</span>
          </div>
          <div v-if="item.description" class="mobile-card-desc">{{ item.description }}</div>
          <div class="mobile-card-actions">
            <el-button size="small" @click="openDialog(item)">{{ $t('common.edit') }}</el-button>
            <el-button size="small" type="danger" @click="deleteAgent(item)">{{ $t('common.delete') }}</el-button>
          </div>
        </div>
      </el-card>
      <div v-if="filteredData.length === 0" style="text-align:center;padding:40px;color:var(--cc-text-muted)">{{ $t('common.noData') }}</div>
    </div>

          <el-table :data="filteredData" :class="{ 'mobile-hide': isMobile }" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="220" />
        <el-table-column prop="name" :label="$t('common.name')" width="160" />
        <el-table-column prop="modelId" :label="$t('common.model')" width="140" />
        <el-table-column prop="enabled" :label="$t('common.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? t('common.enable') : t('common.disable') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" :label="$t('common.description')" show-overflow-tooltip />
        <el-table-column :label="$t('common.actions')" fixed="right" width="240">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="handleToggle(row)" style="margin-right: 8px" />
            <el-button link type="primary" @click="openDialog(row)">{{ $t('common.edit') }}</el-button>
            <el-popconfirm :title="$t('common.deleteConfirm')" @confirm="handleDelete(row.id)">
              <template #reference><el-button link type="danger">{{ $t('common.delete') }}</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? t('agent.editAgent') : t('agent.newAgent')" width="680" destroy-on-close>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
        <el-form-item :label="$t('common.name')" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item :label="$t('common.model')" prop="modelId">
          <el-select v-model="form.modelId" :placeholder="$t('agent.selectModel')" filterable style="width: 100%">
            <el-option v-for="m in llmModels" :key="m.id" :label="m.displayName || m.modelName" :value="m.id">
              <span>{{ m.displayName || m.modelName }}</span>
              <span style="color: #999; font-size: 12px; margin-left: 8px">({{ m.providerName || m.providerId }})</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('common.description')"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="System Prompt" prop="systemPrompt">
          <el-input v-model="form.systemPrompt" type="textarea" :rows="8" :placeholder="$t('agent.systemPromptPlaceholder')" />
        </el-form-item>
        <el-form-item label="MCP Servers">
          <el-select v-model="form.mcpServerIds" multiple :placeholder="$t('agent.bindMcp')" style="width: 100%">
            <el-option v-for="s in mcpServers" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('agent.skills')">
          <el-select v-model="form.skillIds" multiple :placeholder="$t('agent.bindSkills')" style="width: 100%">
            <el-option v-for="s in skills" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <!-- Advanced Settings (collapsed) -->
        <el-divider>
          <el-button text @click="showAdvanced = !showAdvanced" style="font-size: 13px; color: #909399">
            {{ showAdvanced ? t('common.collapseAdvanced') : t('common.expandAdvanced') }}
            <el-icon style="margin-left: 4px"><component :is="showAdvanced ? 'ArrowUp' : 'ArrowDown'" /></el-icon>
          </el-button>
        </el-divider>
        <template v-if="showAdvanced">
          <el-form-item :label="$t('agent.maxToolCalls')">
            <el-input-number v-model="form.maxToolCalls" :min="1" :max="200" :step="1" />
            <span style="margin-left: 8px; font-size: 12px; color: #909399">{{ $t('agent.maxToolCallsDesc') }}</span>
          </el-form-item>
          <el-form-item :label="$t('agent.compressionThreshold')">
            <el-input-number v-model="form.compressionThreshold" :min="5" :max="100" :step="1" />
            <span style="margin-left: 8px; font-size: 12px; color: #909399">{{ $t('agent.compressionThresholdDesc') }}</span>
          </el-form-item>
          <el-form-item :label="$t('agent.keepRounds')">
            <el-input-number v-model="form.compressionKeepRounds" :min="2" :max="50" :step="1" />
            <span style="margin-left: 8px; font-size: 12px; color: #909399">{{ $t('agent.keepRoundsDesc') }}</span>
          </el-form-item>
          <el-form-item :label="$t('agent.contextThreshold')">
            <el-input-number v-model="form.contextUsageThreshold" :min="0.1" :max="0.99" :step="0.05" :precision="2" />
            <span style="margin-left: 8px; font-size: 12px; color: #909399">{{ $t('agent.contextThresholdDesc') }}</span>
          </el-form-item>
          <el-divider content-position="left">{{ $t('agent.sandboxConfig') }}</el-divider>
          <el-form-item :label="$t('agent.enableSandbox')">
            <el-switch v-model="form.sandboxEnabled" />
            <span style="margin-left: 8px; font-size: 12px; color: #909399">{{ $t('agent.enableSandboxDesc') }}</span>
          </el-form-item>
          <el-form-item :label="$t('sandbox.providers')" v-if="form.sandboxEnabled">
            <el-select v-model="form.sandboxProviderId" style="width: 100%" :placeholder="$t('agent.selectSandboxProvider')" clearable>
              <el-option v-for="p in sandboxProviders" :key="p.id" :label="p.name + ' (' + p.type + ')'" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('agent.sandboxMode')" v-if="form.sandboxEnabled">
            <el-radio-group v-model="form.sandboxMode">
              <el-radio value="STATELESS">{{ $t('agent.statelessMode') }}</el-radio>
              <el-radio value="SESSION" :disabled="!isSessionModeAllowed">{{ $t('agent.sessionMode') }}{{ !isSessionModeAllowed ? t('agent.sessionModeNote') : '' }}</el-radio>
            </el-radio-group>
            <div v-if="!isSessionModeAllowed && form.sandboxMode === 'SESSION'" style="color: #e6a23c; font-size: 12px; margin-top: 4px;">
              {{ $t('agent.sessionModeWarning') }}
            </div>
          </el-form-item>
          <el-form-item :label="$t('agent.execTimeout')" v-if="form.sandboxEnabled">
            <el-input-number v-model="form.sandboxTimeout" :min="5" :max="300" :step="5" />
          </el-form-item>
        </template>
        <el-form-item :label="$t('common.status')">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { SetUp, Plus, Search, ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import { getAgents, createAgent, updateAgent, deleteAgent, getMcpServers, getSkills, getLlmModels, getSandboxProviders } from '@/api/admin'
import '@/assets/admin.css'
import { useMobile } from '@/composables/useMobile'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const tableData = ref<any[]>([])
const mcpServers = ref<any[]>([])
const skills = ref<any[]>([])
const llmModels = ref<any[]>([])
const sandboxProviders = ref<any[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editId = ref('')
const { isMobile } = useMobile()
const search = ref('')
const formRef = ref<FormInstance>()

const defaultForm = { name: '', modelId: '', description: '', systemPrompt: '', mcpServerIds: [] as string[], skillIds: [] as string[], enabled: true, maxToolCalls: 50, compressionThreshold: 20, compressionKeepRounds: 6, contextUsageThreshold: 0.75, sandboxEnabled: false, sandboxBackend: 'LOCAL', sandboxProviderId: '', sandboxMode: 'STATELESS', sandboxTimeout: 30 }
const form = reactive({ ...defaultForm })
const showAdvanced = ref(false)
const rules = {
  name: [{ required: true, message: t('common.required'), trigger: 'blur' }],
  modelId: [{ required: true, message: t('common.required'), trigger: 'change' }],
  systemPrompt: [{ required: true, message: t('common.required'), trigger: 'blur' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const [agentsRes, mcpRes, skillRes, modelRes, providerRes]: any[] = await Promise.all([getAgents(), getMcpServers(), getSkills(), getLlmModels(), getSandboxProviders()])
    tableData.value = Array.isArray(agentsRes?.data) ? agentsRes.data : Array.isArray(agentsRes?.data?.list) ? agentsRes.data.list : Array.isArray(agentsRes) ? agentsRes : []
    mcpServers.value = Array.isArray(mcpRes?.data) ? mcpRes.data : Array.isArray(mcpRes?.data?.list) ? mcpRes.data.list : Array.isArray(mcpRes) ? mcpRes : []
    skills.value = Array.isArray(skillRes?.data) ? skillRes.data : Array.isArray(skillRes?.data?.list) ? skillRes.data.list : Array.isArray(skillRes) ? skillRes : []
    llmModels.value = Array.isArray(modelRes?.data) ? modelRes.data : Array.isArray(modelRes?.data?.list) ? modelRes.data.list : Array.isArray(modelRes) ? modelRes : []
    sandboxProviders.value = Array.isArray(providerRes?.data) ? providerRes.data : []
  } catch {} finally { loading.value = false }
}

const filteredData = computed(() => {
  if (!search.value) return tableData.value
  const kw = search.value.toLowerCase()
  return tableData.value.filter(r =>
    r.name?.toLowerCase().includes(kw) || r.description?.toLowerCase().includes(kw)
  )
})

/** Check if SESSION mode is allowed based on provider/backend */
const isSessionModeAllowed = computed(() => {
  // If a provider is selected, check its type
  if (form.sandboxProviderId) {
    const provider = sandboxProviders.value.find((p: any) => p.id === form.sandboxProviderId)
    return provider && provider.type === 'E2B'
  }
  // Otherwise check backend string
  return form.sandboxBackend === 'E2B'
})

/** Auto-fix: if SESSION mode is selected but not allowed, reset to STATELESS */
watch(isSessionModeAllowed, (allowed) => {
  if (!allowed && form.sandboxMode === 'SESSION') {
    form.sandboxMode = 'STATELESS'
  }
})

const openDialog = (row?: any) => {
  isEdit.value = !!row
  if (row) {
    editId.value = row.id
    Object.assign(form, { name: row.name, modelId: row.modelId, description: row.description || '', systemPrompt: row.systemPrompt || '', mcpServerIds: row.mcpServerIds || [], skillIds: row.skillIds || [], enabled: row.enabled !== false, maxToolCalls: row.maxToolCalls || 50, compressionThreshold: row.compressionThreshold || 20, compressionKeepRounds: row.compressionKeepRounds || 6, contextUsageThreshold: row.contextUsageThreshold || 0.75, sandboxEnabled: row.sandboxEnabled || false, sandboxBackend: row.sandboxBackend || 'LOCAL', sandboxProviderId: row.sandboxProviderId || '', sandboxMode: row.sandboxMode || 'STATELESS', sandboxTimeout: row.sandboxTimeout || 30 })
  } else {
    editId.value = ''
    Object.assign(form, defaultForm)
  }
  dialogVisible.value = true
}

const handleSave = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (isEdit.value) { await updateAgent(editId.value, form) } else { await createAgent(form) }
    ElMessage.success(isEdit.value ? t('common.updateSuccess') : t('common.createSuccess'))
    dialogVisible.value = false
    loadData()
  } catch {} finally { saving.value = false }
}

const handleDelete = async (id: string) => {
  await deleteAgent(id)
  ElMessage.success(t('common.deleteSuccess'))
  loadData()
}

const handleToggle = async (row: any) => {
  try {
    await updateAgent(row.id, { enabled: row.enabled })
    ElMessage.success(row.enabled ? t('common.enabled') : t('agent.stopped'))
  } catch {
    row.enabled = !row.enabled
  }
}

onMounted(loadData)
</script>

<style scoped>
</style>
