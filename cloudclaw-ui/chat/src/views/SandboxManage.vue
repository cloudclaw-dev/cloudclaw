<template>
  <div class="admin-page">
    <div class="admin-page-header">
      <div class="header-info">
        <div class="header-icon"><el-icon><Monitor /></el-icon></div>
        <div>
          <h2>{{ $t('nav.sandbox') }}</h2>
          <div class="header-desc">{{ $t('sandbox.subtitle') }}</div>
        </div>
      </div>
    </div>

    <!-- Provider Section -->
    <el-card shadow="hover" class="admin-card">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">{{ $t('sandbox.providers') }}</span>
          <el-button type="primary" size="small" @click="openProviderDialog()"><el-icon><Plus /></el-icon> {{ $t('memory.addProfile') }}</el-button>
        </div>
      </template>
      <el-table :data="providers" v-loading="providerLoading" stripe>
        <el-table-column prop="name" :label="$t('common.name')" width="160" />
        <el-table-column prop="type" :label="$t('common.type')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.type === 'LOCAL' ? 'info' : row.type === 'DOCKER' ? 'success' : 'warning'" size="small">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.status')" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">{{ row.enabled ? t('common.enable') : t('common.disable') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('agent.sandboxConfig')" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.type === 'DOCKER'">{{ $t('sandbox.dockerImages') }}: {{ row.dockerImages || 'default' }}, {{ $t('sandbox.memoryLimit') }}: {{ row.dockerMemory || '512m' }}</span>
            <span v-else-if="row.type === 'E2B'">Template: {{ row.e2bTemplateId || '-' }}, API: {{ row.e2bApiKey ? '***' + row.e2bApiKey.slice(-4) : $t('common.disabled') }}</span>
            <span v-else>{{ $t('sandbox.local') }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="defaultTimeout" :label="$t('sandbox.timeout')" width="80" />
        <el-table-column :label="$t('common.actions')" fixed="right" width="160">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="toggleProvider(row)" style="margin-right:8px" />
            <el-button link type="primary" @click="openProviderDialog(row)">{{ $t('common.edit') }}</el-button>
            <el-popconfirm :title="$t('common.deleteConfirm')" @confirm="deleteProvider(row.id)">
              <template #reference><el-button link type="danger">{{ $t('common.delete') }}</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Session Section -->
    <el-card shadow="hover" class="admin-card" style="margin-top:16px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:600">{{ $t('sandbox.sessions') }}</span>
          <div>
            <el-button type="danger" size="small" :disabled="orphanCount === 0" @click="cleanOrphans">
              {{ $t('sandbox.cleanOrphans') }} ({{ orphanCount }})
            </el-button>
            <el-button size="small" @click="loadSessions"><el-icon><Refresh /></el-icon> {{ $t('common.refresh') }}</el-button>
          </div>
        </div>
      </template>
      <div style="margin-bottom:12px">
        <el-radio-group v-model="sessionFilter" @change="loadSessions" size="small">
          <el-radio-button value="">{{ $t('common.all') }}</el-radio-button>
          <el-radio-button value="ACTIVE">{{ $t('sandbox.active') }}</el-radio-button>
          <el-radio-button value="ORPHANED">{{ $t('sandbox.orphaned') }}</el-radio-button>
          <el-radio-button value="CLOSED">{{ $t('sandbox.closed') }}</el-radio-button>
        </el-radio-group>
      </div>
      <el-table :data="sessions" v-loading="sessionLoading" stripe>
        <el-table-column label="ID" width="100">{{ (row: any) => shortId(row.id) }}</el-table-column>
        <el-table-column :label="$t('dashboard.sessionTitle')" width="100">{{ (row: any) => shortId(row.sessionId) }}</el-table-column>
        <el-table-column label="Agent" width="100">{{ (row: any) => shortId(row.agentId) }}</el-table-column>
        <el-table-column prop="backend" :label="$t('sandbox.backend')" width="80" />
        <el-table-column prop="status" :label="$t('common.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="workDir" :label="$t('sandbox.workDir')" show-overflow-tooltip />
        <el-table-column :label="$t('memory.createdAt')" width="170">{{ (row: any) => formatTime(row.createdAt) }}</el-table-column>
        <el-table-column :label="$t('common.actions')" fixed="right" width="100">
          <template #default="{ row }">
            <el-button v-if="row.status === 'ACTIVE'" link type="danger" @click="forceClose(row)">{{ $t('sandbox.forceClose') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Provider Dialog -->
    <el-dialog v-model="providerDialogVisible" :title="isEditProvider ? t('sandbox.editProvider') : t('sandbox.newProvider')" width="600" destroy-on-close>
      <el-form :model="providerForm" label-width="120px">
        <el-form-item :label="$t('common.name')"><el-input v-model="providerForm.name" /></el-form-item>
        <el-form-item :label="$t('common.type')">
          <el-select v-model="providerForm.type" style="width:100%">
            <el-option value="LOCAL" label="{{ $t('sandbox.local') }}" />
            <el-option value="DOCKER" :label="$t('sandbox.docker')" />
            <el-option value="E2B" :label="$t('sandbox.e2b')" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('sandbox.timeout')"><el-input-number v-model="providerForm.defaultTimeout" :min="5" :max="300" /></el-form-item>
        <el-form-item :label="$t('sandbox.timeout')"><el-input-number v-model="providerForm.maxTimeout" :min="10" :max="600" /></el-form-item>

        <!-- Docker specific -->
        <template v-if="providerForm.type === 'DOCKER'">
          <el-divider content-position="left">{{ $t('agent.sandboxConfig') }}</el-divider>
          <el-form-item :label="$t('sandbox.dockerImages')">
            <el-input v-model="providerForm.dockerImages" type="textarea" :rows="3" placeholder='{"python":"python:3.11-slim","javascript":"node:20-slim"}' />
          </el-form-item>
          <el-form-item :label="$t('sandbox.memoryLimit')"><el-input v-model="providerForm.dockerMemory" placeholder="512m" /></el-form-item>
          <el-form-item :label="$t('sandbox.cpuLimit')"><el-input-number v-model="providerForm.dockerCpus" :min="1" :max="16" /></el-form-item>
          <el-form-item :label="$t('sandbox.providers')"><el-switch v-model="providerForm.dockerNetworkEnabled" /></el-form-item>
        </template>

        <!-- E2B specific -->
        <template v-if="providerForm.type === 'E2B'">
          <el-divider content-position="left">{{ $t('agent.sandboxConfig') }}</el-divider>
          <el-form-item :label="$t('llm.apiKey')"><el-input v-model="providerForm.e2bApiKey" show-password /></el-form-item>
          <el-form-item label="Template ID"><el-input v-model="providerForm.e2bTemplateId" /></el-form-item>
          <el-form-item label="API URL"><el-input v-model="providerForm.e2bApiUrl" placeholder="https://api.e2b.dev" /></el-form-item>
        </template>

        <el-form-item :label="$t('common.status')"><el-switch v-model="providerForm.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="providerDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="saveProvider">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Monitor, Plus, Refresh } from '@element-plus/icons-vue'
import { getSandboxes, forceCloseSandbox, cleanOrphanSandboxes, getSandboxProviders, createSandboxProvider, updateSandboxProvider, deleteSandboxProvider } from '@/api/admin'
import '@/assets/admin.css'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const providers = ref<any[]>([])
const sessions = ref<any[]>([])
const providerLoading = ref(false)
const sessionLoading = ref(false)
const saving = ref(false)
const sessionFilter = ref('')
const providerDialogVisible = ref(false)
const isEditProvider = ref(false)
const editProviderId = ref('')

const defaultProviderForm = { name: '', type: 'LOCAL', enabled: true, defaultTimeout: 30, maxTimeout: 300, dockerImages: '', dockerMemory: '512m', dockerCpus: 1, dockerNetworkEnabled: false, e2bApiKey: '', e2bTemplateId: '', e2bApiUrl: '' }
const providerForm = reactive({ ...defaultProviderForm })

const orphanCount = computed(() => sessions.value.filter(r => r.status === 'ORPHANED').length)

const loadProviders = async () => {
  providerLoading.value = true
  try {
    const res: any = await getSandboxProviders()
    providers.value = Array.isArray(res?.data) ? res.data : []
  } catch {} finally { providerLoading.value = false }
}

const loadSessions = async () => {
  sessionLoading.value = true
  try {
    const params: any = {}
    if (sessionFilter.value) params.status = sessionFilter.value
    const res: any = await getSandboxes(params)
    sessions.value = Array.isArray(res?.data) ? res.data : []
  } catch {} finally { sessionLoading.value = false }
}

const openProviderDialog = (row?: any) => {
  isEditProvider.value = !!row
  if (row) {
    editProviderId.value = row.id
    Object.assign(providerForm, { name: row.name, type: row.type, enabled: row.enabled, defaultTimeout: row.defaultTimeout || 30, maxTimeout: row.maxTimeout || 300, dockerImages: row.dockerImages || '', dockerMemory: row.dockerMemory || '512m', dockerCpus: row.dockerCpus || 1, dockerNetworkEnabled: row.dockerNetworkEnabled || false, e2bApiKey: row.e2bApiKey || '', e2bTemplateId: row.e2bTemplateId || '', e2bApiUrl: row.e2bApiUrl || '' })
  } else {
    editProviderId.value = ''
    Object.assign(providerForm, defaultProviderForm)
  }
  providerDialogVisible.value = true
}

const saveProvider = async () => {
  saving.value = true
  try {
    if (isEditProvider.value) { await updateSandboxProvider(editProviderId.value, providerForm) } else { await createSandboxProvider(providerForm) }
    ElMessage.success(isEditProvider.value ? t('common.updateSuccess') : t('common.createSuccess'))
    providerDialogVisible.value = false
    loadProviders()
  } catch {} finally { saving.value = false }
}

const toggleProvider = async (row: any) => {
  try { await updateSandboxProvider(row.id, { enabled: row.enabled }) } catch { row.enabled = !row.enabled }
}

const deleteProvider = async (id: string) => {
  await deleteSandboxProvider(id)
  ElMessage.success(t('common.deleteSuccess'))
  loadProviders()
}

const forceClose = async (row: any) => {
  await forceCloseSandbox(row.id)
  ElMessage.success(t('sandbox.closed'))
  loadSessions()
}

const cleanOrphans = async () => {
  const res: any = await cleanOrphanSandboxes()
  ElMessage.success(`${res?.data || 0}`)
  loadSessions()
}

const statusType = (status: string) => status === 'ACTIVE' ? 'success' : status === 'ORPHANED' ? 'warning' : 'info'
const shortId = (id: string) => id ? id.substring(0, 8) + '...' : '-'
const formatTime = (t: string) => t ? t.replace('T', ' ').substring(0, 19) : '-'

onMounted(() => { loadProviders(); loadSessions() })
</script>

<style scoped>
</style>
