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
        <div class="view-toggle">
          <el-button-group>
            <el-button :type="viewMode === 'card' ? 'primary' : 'default'" @click="viewMode = 'card'"><el-icon><Grid /></el-icon></el-button>
            <el-button :type="viewMode === 'list' ? 'primary' : 'default'" @click="viewMode = 'list'"><el-icon><List /></el-icon></el-button>
          </el-button-group>
        </div>
      </div>

    <!-- Card View -->
    <div v-if="viewMode === 'card' && !isMobile" class="item-card-grid">
      <div v-for="item in filteredData" :key="item.id" class="item-card" @click="openDialog(item)">
        <div class="item-card-top">
          <div class="item-card-icon"><el-icon :size="24"><Connection /></el-icon></div>
          <div class="item-card-main">
            <div class="item-card-name">{{ item.name }}</div>
            <div class="item-card-desc" v-if="item.description">{{ item.description }}</div>
          </div>
        </div>
        <div class="item-card-meta">
          <el-tag size="small">{{ item.transport }}</el-tag>
          <span class="item-card-meta-right">{{ item.url }}</span>
        </div>
        <div class="item-card-footer">
          <el-switch v-model="item.enabled" @click.stop @change="handleToggle(item)" size="small" />
          <div class="item-card-actions">
            <el-button link type="success" size="small" @click.stop="handleTest(item)" :loading="item._testing">{{ $t('mcp.testConnection') }}</el-button>
            <el-button link type="primary" size="small" @click.stop="openDialog(item)">{{ $t('common.edit') }}</el-button>
            <el-popconfirm :title="$t('common.deleteConfirm')" @confirm="handleDelete(item.id)">
              <template #reference><el-button link type="danger" size="small" @click.stop>{{ $t('common.delete') }}</el-button></template>
            </el-popconfirm>
          </div>
        </div>
      </div>
      <div v-if="filteredData.length === 0" class="item-card-empty">{{ $t('common.noData') }}</div>
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

          <el-table v-if="viewMode === 'list' || isMobile" :data="filteredData" :class="{ 'mobile-hide': isMobile }" v-loading="loading" stripe>
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
import { Connection, Plus, Search, Grid, List } from '@element-plus/icons-vue'
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
const viewMode = ref<'card' | 'list'>('card')
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
  try {
    await updateMcpServer(row.id, { enabled: row.enabled })
    ElMessage.success(row.enabled ? t('common.enabled') : t('agent.stopped'))
  } catch { row.enabled = !row.enabled }
}

const handleDelete = async (id: string) => {
  await deleteMcpServer(id)
  ElMessage.success(t('common.deleteSuccess'))
  loadData()
}

onMounted(loadData)
</script>

<style scoped>

/* View Toggle */
.view-toggle { margin-left: auto; }
.admin-table-toolbar { display: flex; align-items: center; gap: 12px; }

/* Card Grid */
.item-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
  padding: 0 0 20px;
}
.item-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px;
  border-radius: 12px;
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  background: var(--el-bg-color, #fff);
  cursor: pointer;
  transition: all 0.2s;
}
.item-card:hover {
  border-color: var(--el-color-primary, #409eff);
  box-shadow: 0 4px 16px rgba(0,0,0,0.08);
  transform: translateY(-2px);
}
:global(.dark) .item-card {
  background: #1d1e1f;
  border-color: #363637;
}
:global(.dark) .item-card:hover {
  border-color: #3370ff;
  background: #1a2a44;
}
.item-card-top {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.item-card-icon {
  width: 40px; height: 40px; border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  background: rgba(64,158,255,0.08);
  color: var(--el-color-primary, #409eff);
  flex-shrink: 0;
}
:global(.dark) .item-card-icon {
  background: rgba(51,112,255,0.15);
}
.item-card-main { flex: 1; min-width: 0; }
.item-card-name {
  font-size: 15px; font-weight: 600;
  color: var(--el-text-color-primary, #303133);
  margin-bottom: 4px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.item-card-desc {
  font-size: 12px; color: var(--el-text-color-secondary, #909399);
  line-height: 1.4;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.item-card-meta {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
}
.item-card-meta-right { margin-left: auto; font-size: 12px; color: var(--el-text-color-secondary, #909399); }
.item-card-footer {
  display: flex; align-items: center; justify-content: space-between;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter, #e4e7ed);
}
:global(.dark) .item-card-footer { border-top-color: #363637; }
.item-card-actions { display: flex; gap: 4px; }
.item-card-empty {
  grid-column: 1 / -1; text-align: center; padding: 60px 0;
  color: var(--el-text-color-secondary, #909399);
}

@media (max-width: 767px) {
  .item-card-grid { grid-template-columns: 1fr; }
}
</style>
