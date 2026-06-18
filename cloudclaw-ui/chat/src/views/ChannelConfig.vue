<template>
  <div class="admin-page">
    <!-- Header -->
    <div class="admin-page-header">
      <div class="header-info">
        <div class="header-icon"><el-icon><Connection /></el-icon></div>
        <div>
          <h2>{{ t('nav.channel') }}</h2>
          <div class="header-desc">{{ t('channel.title') || 'Manage channel configurations' }}</div>
        </div>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          {{ t('common.create') }}
        </el-button>
      </div>
    </div>

    <!-- Channel List -->
    <div class="admin-card admin-page-content" style="margin-bottom: 16px;">
    <el-table :data="channelList" v-loading="loading" style="width: 100%" stripe>
      <el-table-column prop="name" :label="t('common.name')" width="150" />
      <el-table-column prop="channelType" :label="t('common.type')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.channelType === 'feishu' ? 'primary' : 'info'" size="small">
            {{ channelTypeLabel(row.channelType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="appId" label="App ID" width="200" show-overflow-tooltip />
      <el-table-column :label="t('nav.agent')" width="150">
        <template #default="{ row }">
          {{ row.agentName || row.agentId || '-' }}
        </template>
      </el-table-column>
      <el-table-column :label="t('channel.connectionMode')" width="130">
        <template #default="{ row }">
          {{ row.connectionMode === 'long-connection' ? t('channel.longConnection') : t('channel.callback') }}
        </template>
      </el-table-column>
      <el-table-column :label="t('common.status')" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.connectionStatus)" size="small" effect="dot">
            {{ statusLabel(row.connectionStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="enabled" :label="t('common.enabled')" width="80" align="center">
        <template #default="{ row }">
          <el-switch v-model="row.enabled" @change="toggleEnabled(row)" size="small" />
        </template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEditDialog(row)">{{ t('common.edit') }}</el-button>
          <el-button size="small" :type="row.connectionStatus === 'connected' ? 'warning' : 'success'"
                     @click="toggleConnection(row)" :loading="row._toggling">
            {{ row.connectionStatus === 'connected' ? t('channel.disconnect') : t('channel.connect') }}
          </el-button>
          <el-button size="small" @click="testConnection(row)" :loading="row._testing">{{ t('channel.testConnection') }}</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
    </div>

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? t('channel.editChannel') : t('channel.newName')" width="600px">
      <el-form :model="dialogForm" label-width="140px" size="default">
        <el-form-item :label="t('common.name')" required>
          <el-input v-model="dialogForm.name" placeholder="..." />
        </el-form-item>
        <el-form-item :label="t('channel.channelType')" required>
          <el-select v-model="dialogForm.channelType" :disabled="isEdit" style="width:100%">
            <el-option :label="t('channel.feishu')" value="feishu" />
            <el-option :label="t('channel.dingtalk')" value="dingtalk" />
            <el-option :label="t('channel.wecom')" value="wecom" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('channel.purpose')">
          <el-radio-group v-model="dialogForm.purpose">
            <el-radio value="bot":value="'bot'">{{ t('channel.purposeBot') }}</el-radio>
            <el-radio value="login":value="'login'">{{ t('channel.purposeLogin') }}</el-radio>
            <el-radio value="both":value="'both'">{{ t('channel.purposeBoth') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-divider content-position="left">{{ t('channel.credentials') }}</el-divider>
        <el-form-item :label="t('channel.appId')" required>
          <el-input v-model="dialogForm.appId" placeholder="..." />
        </el-form-item>
        <el-form-item :label="t('channel.appSecret')" required>
          <el-input v-model="dialogForm.appSecret" type="password" show-password
                    :placeholder="isEdit ? 'Leave blank to keep current' : 'App Secret'" />
        </el-form-item>
        <el-form-item :label="t('channel.verificationToken')">
          <el-input v-model="dialogForm.verificationToken" placeholder="..." />
        </el-form-item>
        <el-form-item :label="t('channel.encryptKey')">
          <el-input v-model="dialogForm.encryptKey" placeholder="..." />
        </el-form-item>
        <el-divider content-position="left">{{ t('channel.connectionMode') }}</el-divider>
        <el-form-item :label="t('channel.connectionMode')">
          <el-radio-group v-model="dialogForm.connectionMode">
            <el-radio value="long-connection":label="t('channel.longConnection')" />
            <el-radio value="callback":label="t('channel.callback')" />
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="dialogForm.connectionMode === 'callback'" :label="t('channel.callbackUrl')">
          <el-input :model-value="callbackUrl" readonly>
            <template #append>
              <el-button @click="copyCallbackUrl">{{ t('common.confirm') }}</el-button>
            </template>
          </el-input>
        </el-form-item>
        <el-divider content-position="left">{{ t('channel.agentBinding') }}</el-divider>
        <el-form-item :label="t('channel.bindAgent')">
          <el-select v-model="dialogForm.agentId" clearable filterable :placeholder="t('channel.bindAgent')" style="width:100%">
            <el-option v-for="a in agentOptions" :key="a.id" :label="a.name" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('common.enabled')">
          <el-switch v-model="dialogForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Connection } from '@element-plus/icons-vue'
import api from '@/api'
import '@/assets/admin.css'

const { t } = useI18n()
const origin = typeof window !== 'undefined' ? window.location.origin : ''

// ===== State =====
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const channelList = ref<any[]>([])
const agentOptions = ref<any[]>([])

const defaultForm = {
  id: '',
  name: '',
  channelType: 'feishu',
  appId: '',
  appSecret: '',
  verificationToken: '',
  encryptKey: '',
  connectionMode: 'long-connection',
  purpose: 'bot',
  agentId: '',
  enabled: false,
}
const dialogForm = reactive({ ...defaultForm })

const callbackUrl = computed(() => {
  if (!dialogForm.id && !dialogForm.appId) return '(Save first to get callback URL)'
  const id = dialogForm.id || dialogForm.appId
  return `${origin}/api/v1/channel/feishu/event/${id}`
})

// ===== Helpers =====
const channelTypeLabel = (type: string) => {
  const map: Record<string, string> = { feishu: 'Feishu', dingtalk: 'DingTalk', wecom: 'WeCom' }
  return map[type] || type
}
const statusLabel = (status: string) => {
  const map: Record<string, string> = { connected: t('channel.connected'), disconnected: t('channel.disconnected'), error: t('channel.error'), connecting: t('channel.connecting') }
  return map[status] || status
}
const statusTagType = (status: string): any => {
  const map: Record<string, string> = { connected: 'success', disconnected: 'info', error: 'danger', connecting: 'warning' }
  return map[status] || 'info'
}

// ===== Data =====
const loadData = async () => {
  loading.value = true
  try {
    const [chRes, agentRes] = await Promise.all([
      api.get('/admin/channels'),
      api.get('/admin/agents'),
    ])
    channelList.value = (chRes.data?.data || chRes.data || []).map((c: any) => ({ ...c, _toggling: false, _testing: false }))
    const agents = agentRes.data?.data || agentRes.data || []
    agentOptions.value = Array.isArray(agents) ? agents : (agents.content || [])
  } catch (e) {
    console.error('Failed to load channels', e)
  } finally {
    loading.value = false
  }
}

// ===== Dialog =====
const openCreateDialog = () => {
  isEdit.value = false
  Object.assign(dialogForm, { ...defaultForm })
  dialogVisible.value = true
}

const openEditDialog = (row: any) => {
  isEdit.value = true
  Object.assign(dialogForm, {
    id: row.id || '',
    name: row.name || '',
    channelType: row.channelType || 'feishu',
    appId: row.appId || '',
    appSecret: '', // never show existing secret
    verificationToken: row.verificationToken || '',
    encryptKey: row.encryptKey || '',
    connectionMode: row.connectionMode || 'long-connection',
    purpose: row.purpose || 'bot',
    agentId: row.agentId || '',
    enabled: row.enabled || false,
  })
  dialogVisible.value = true
}

const handleSave = async () => {
  if (!dialogForm.name || !dialogForm.appId) {
    ElMessage.warning(t('channel.nameRequired'))
    return
  }
  saving.value = true
  try {
    const payload: any = { ...dialogForm }
    // Don't send empty secret on edit
    if (isEdit.value && !payload.appSecret) delete payload.appSecret
    // Don't send appSecret if it's the masked placeholder
    if (payload.appSecret === '******') delete payload.appSecret

    if (isEdit.value) {
      await api.put(`/admin/channels/${dialogForm.id}`, payload)
    } else {
      delete payload.id
      await api.post('/admin/channels', payload)
    }
    ElMessage.success(isEdit.value ? t('common.updateSuccess') : t('common.createSuccess'))
    dialogVisible.value = false
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || 'Failed to save')
  } finally {
    saving.value = false
  }
}

const handleDelete = async (row: any) => {
  try {
    await ElMessageBox.confirm(t('channel.deleteConfirm', { name: row.name }), t('common.confirm'), { type: 'warning' })
    await api.delete(`/admin/channels/${row.id}`)
    ElMessage.success(t('common.deleteSuccess'))
    loadData()
  } catch {}
}

const toggleEnabled = async (row: any) => {
  try {
    await api.put(`/admin/channels/${row.id}`, { ...row, appSecret: undefined, enabled: row.enabled })
    ElMessage.success(row.enabled ? t('common.enabled') : t('common.disabled'))
    loadData()
  } catch (e: any) {
    ElMessage.error('Failed')
    loadData()
  }
}

const toggleConnection = async (row: any) => {
  row._toggling = true
  try {
    if (row.connectionStatus === 'connected') {
      await api.post(`/admin/channels/${row.id}/disconnect`)
      ElMessage.success(t('channel.disconnected'))
    } else {
      await api.post(`/admin/channels/${row.id}/connect`)
      ElMessage.success(t('channel.connected'))
    }
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || 'Failed')
  } finally {
    row._toggling = false
  }
}

const testConnection = async (row: any) => {
  row._testing = true
  try {
    const res = await api.post(`/admin/channels/${row.id}/test`)
    const msg = res.data?.data || res.data || 'OK'
    ElMessage.success(typeof msg === 'string' ? msg : 'Connection OK')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || 'Test failed')
  } finally {
    row._testing = false
  }
}

const copyCallbackUrl = () => {
  if (callbackUrl.value && !callbackUrl.value.startsWith('(')) {
    navigator.clipboard.writeText(callbackUrl.value)
    ElMessage.success(t('common.success'))
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
/* Mobile: stack radio buttons */
@media (max-width: 767px) {
  :deep(.el-radio-group) { display: flex; flex-direction: column; gap: 8px; }
}
</style>
