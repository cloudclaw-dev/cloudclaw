<template>
  <div class="admin-page">
    <!-- Page Header -->
    <div class="admin-page-header">
      <div class="header-info">
        <div class="header-icon"><el-icon><Connection /></el-icon></div>
        <div>
          <h2>{{ $t('mcp.title') }}</h2>
          <div class="header-desc">{{ $t('mcp.subtitle') }}</div>
        </div>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon> {{ $t('mcp.newServer') }}</el-button>
      </div>
    </div>

    <!-- Table Card -->
    <el-card shadow="hover" class="admin-card">
      <div class="admin-table-toolbar">
        <el-input v-model="search" :placeholder="$t('common.search')" style="width: 300px" clearable>
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
            <span>{{ item.transport }}</span>
          </div>
          <div v-if="item.url" class="mobile-card-desc">{{ item.url }}</div>
          <div class="mobile-card-actions">
            <el-button size="small" @click="testConnection(item)">{{ $t('mcp.testConnection') }}</el-button>
            <el-button size="small" @click="openDialog(item)">{{ $t('common.edit') }}</el-button>
            <el-button size="small" type="danger" @click="deleteServer(item)">{{ $t('common.delete') }}</el-button>
          </div>
        </div>
      </el-card>
      <div v-if="filteredData.length === 0" style="text-align:center;padding:40px;color:var(--cc-text-muted)">{{ $t('common.noData') }}</div>
    </div>

          <el-table :data="filteredData" :class="{ 'mobile-hide': isMobile }" v-loading="loading" stripe>
        <el-table-column prop="name" :label="$t('common.name')" width="160" />
        <el-table-column prop="description" :label="$t('common.description')" show-overflow-tooltip />
        <el-table-column prop="transport" :label="$t('mcp.transport')" width="130" />
        <el-table-column prop="url" label="URL" show-overflow-tooltip />
        <el-table-column :label="$t('common.status')" width="100">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="handleToggle(row)" />
          </template>
        </el-table-column>
        <el-table-column :label="$t('common.actions')" fixed="right" width="200">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">{{ $t('common.edit') }}</el-button>
            <el-button link type="success" @click="handleTest(row)" :loading="row._testing">{{ $t('mcp.testConnection') }}</el-button>
            <el-popconfirm :title="$t('common.deleteConfirm')" @confirm="handleDelete(row.id)">
              <template #reference><el-button link type="danger">{{ $t('common.delete') }}</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? t('mcp.editServer') : t('mcp.newServer')" width="560" destroy-on-close>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item :label="$t('common.name')" prop="name"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="URL" prop="url"><el-input v-model="form.url" :placeholder="$t('mcp.urlPlaceholder')" /></el-form-item>
        <el-form-item :label="$t('mcp.transport')" prop="transport">
          <el-select v-model="form.transport"><el-option label="SSE" value="sse" /><el-option label="Stdio" value="stdio" /><el-option label="Streamable HTTP" value="streamable_http" /></el-select>
        </el-form-item>
        <el-form-item :label="$t('common.description')"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item :label="$t('mcp.headers')"><el-input v-model="form.headers" type="textarea" :rows="2" :placeholder="$t('mcp.envVarsPlaceholder')" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { Connection, Plus, Search } from '@element-plus/icons-vue'
import { getMcpServers, createMcpServer, updateMcpServer, deleteMcpServer, testMcpServer } from '@/api/admin'
import '@/assets/admin.css'
import { useMobile } from '@/composables/useMobile'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const tableData = ref<any[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editId = ref('')
const { isMobile } = useMobile()
const search = ref('')
const formRef = ref<FormInstance>()

const defaultForm = { name: '', url: '', transport: 'sse', description: '', headers: '' }
const form = reactive({ ...defaultForm })
const rules = {
  name: [{ required: true, message: t('common.required'), trigger: 'blur' }],
  url: [{ required: true, message: t('common.required'), trigger: 'blur' }],
  transport: [{ required: true, message: t('common.required'), trigger: 'change' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await getMcpServers()
    tableData.value = (Array.isArray(res?.data) ? res.data : Array.isArray(res?.data?.list) ? res.data.list : Array.isArray(res) ? res : []).map((i: any) => ({ ...i, _testing: false }))
  } catch {} finally { loading.value = false }
}

const filteredData = computed(() => {
  if (!search.value) return tableData.value
  const kw = search.value.toLowerCase()
  return tableData.value.filter(r =>
    r.name?.toLowerCase().includes(kw) || r.description?.toLowerCase().includes(kw) || r.url?.toLowerCase().includes(kw)
  )
})

const openDialog = (row?: any) => {
  isEdit.value = !!row
  if (row) {
    editId.value = row.id
    Object.assign(form, { name: row.name, url: row.url, transport: row.transport || 'sse', description: row.description || '', headers: row.headers ? JSON.stringify(row.headers) : '' })
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
    const payload: any = { ...form }
    if (payload.headers) { try { payload.headers = JSON.parse(payload.headers) } catch { delete payload.headers } } else { delete payload.headers }
    if (isEdit.value) { await updateMcpServer(editId.value, payload) } else { await createMcpServer(payload) }
    ElMessage.success(isEdit.value ? t('common.updateSuccess') : t('common.createSuccess'))
    dialogVisible.value = false
    loadData()
  } catch {} finally { saving.value = false }
}

const handleTest = async (row: any) => {
  row._testing = true
  try {
    const res: any = await testMcpServer(row.id)
    ElMessage.success(res.message || res.data?.message || t('mcp.testSuccess'))
  } catch {} finally { row._testing = false }
}

const handleToggle = async (row: any) => {
  await updateMcpServer(row.id, { enabled: row.enabled })
  ElMessage.success(row.enabled ? t('common.enabled') : t('agent.stopped'))
}

const handleDelete = async (id: string) => {
  await deleteMcpServer(id)
  ElMessage.success(t('common.deleteSuccess'))
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
</style>
