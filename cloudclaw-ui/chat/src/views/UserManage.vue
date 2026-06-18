<template>
  <div class="admin-page">
    <div class="admin-page-header">
      <div class="header-info">
        <div class="header-icon"><el-icon><User /></el-icon></div>
        <div>
          <h2>{{ $t('nav.user') }}</h2>
          <div class="header-desc">{{ $t('user.subtitle') }}</div>
        </div>
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon> {{ $t('user.newUser') }}</el-button>
      </div>
    </div>
    <div class="admin-card">
      <div class="admin-table-toolbar">
        <el-input v-model="search" :placeholder="$t('user.searchUser')" style="width: 300px" clearable @clear="loadData" @keyup.enter="loadData">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>
    <!-- Mobile Card List -->
    <div class="mobile-card-list" v-if="isMobile">
      <el-card v-for="item in tableData" :key="item.id" shadow="hover">
        <div class="mobile-card-item">
          <div class="mobile-card-header">
            <span class="mobile-card-title">{{ item.username }}</span>
            <div class="mobile-card-tags">
              <el-tag :type="item.role === 'ADMIN' ? 'warning' : ''" size="small">{{ item.role }}</el-tag>
              <el-tag :type="item.enabled ? 'success' : 'info'" size="small">{{ item.enabled ? t('common.enable') : t('common.disable') }}</el-tag>
            </div>
          </div>
          <div class="mobile-card-meta">
            <span>{{ item.email || '-' }}</span>
          </div>
          <div class="mobile-card-actions">
            <el-button size="small" @click="openDialog(item)">{{ $t('common.edit') }}</el-button>
            <el-button size="small" type="danger" @click="confirmDeleteUser(item)">{{ $t('common.delete') }}</el-button>
          </div>
        </div>
      </el-card>
      <div v-if="tableData.length === 0" style="text-align:center;padding:40px;color:var(--cc-text-muted)">{{ $t('common.noData') }}</div>
    </div>

          <el-table :data="tableData" :class="{ 'mobile-hide': isMobile }" v-loading="loading" stripe>
        <el-table-column prop="username" :label="$t('user.username')" width="150" />
        <el-table-column prop="email" :label="$t('user.email')" min-width="200" />
        <el-table-column prop="role" :label="$t('user.role')" width="120">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'" size="small">{{ row.role }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" :label="$t('common.status')" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled !== false ? 'success' : 'warning'" size="small">{{ row.enabled !== false ? t('common.enable') : t('common.disable') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="$t('memory.createdAt')" width="180" />
        <el-table-column :label="$t('common.actions')" fixed="right" width="150">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">{{ $t('common.edit') }}</el-button>
            <el-popconfirm :title="$t('common.deleteConfirm')" @confirm="handleDelete(row.id)">
              <template #reference><el-button link type="danger" size="small">{{ $t('common.delete') }}</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? t('user.editUser') : t('user.newUser')" width="500" destroy-on-close>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item :label="$t('user.username')" prop="username"><el-input v-model="form.username" /></el-form-item>
        <el-form-item :label="$t('user.email')" prop="email"><el-input v-model="form.email" /></el-form-item>
        <el-form-item :label="$t('user.password')" :prop="isEdit ? '' : 'password'"><el-input v-model="form.password" type="password" show-password :placeholder="isEdit ? t('common.required') : t('user.passwordPlaceholder')" /></el-form-item>
        <el-form-item :label="$t('user.role')" prop="role">
          <el-select v-model="form.role"><el-option label="ADMIN" value="ADMIN" /><el-option label="USER" value="USER" /></el-select>
        </el-form-item>
        <el-form-item :label="$t('common.status')"><el-switch v-model="form.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { User, Plus, Search } from '@element-plus/icons-vue'
import { getUsers, createUser, updateUser, deleteUser } from '@/api/admin'
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

const defaultForm = { username: '', email: '', password: '', role: 'USER', enabled: true }
const form = reactive({ ...defaultForm })
const rules = {
  username: [{ required: true, message: t('common.required'), trigger: 'blur' }],
  email: [{ required: true, message: t('common.required'), trigger: 'blur' }],
  password: [{ required: true, message: t('common.required'), trigger: 'blur' }],
  role: [{ required: true, message: t('common.required'), trigger: 'change' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await getUsers({ search: search.value })
    tableData.value = Array.isArray(res?.data) ? res.data : Array.isArray(res?.data?.list) ? res.data.list : Array.isArray(res) ? res : []
  } catch {} finally { loading.value = false }
}

const openDialog = (row?: any) => {
  isEdit.value = !!row
  if (row) {
    editId.value = row.id
    Object.assign(form, { username: row.username, email: row.email, password: '', role: row.role, enabled: row.enabled !== false })
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
    if (isEdit.value && !payload.password) delete payload.password
    if (isEdit.value) { await updateUser(editId.value, payload) } else { await createUser(payload) }
    ElMessage.success(isEdit.value ? t('common.updateSuccess') : t('common.createSuccess'))
    dialogVisible.value = false
    loadData()
  } catch {} finally { saving.value = false }
}

const confirmDeleteUser = (item: any) => {
  ElMessageBox.confirm(t('common.deleteConfirm'), t('common.confirm'), { type: 'warning' }).then(() => handleDelete(item.id)).catch(() => {})
}

const handleDelete = async (id: string) => {
  await deleteUser(id)
  ElMessage.success(t('common.deleteSuccess'))
  loadData()
}

onMounted(loadData)
</script>
