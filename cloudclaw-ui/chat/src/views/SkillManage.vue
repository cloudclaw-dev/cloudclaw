<template>
  <div class="admin-page">
    <!-- Page Header -->
    <div class="admin-page-header">
      <div class="header-info">
        <div class="header-icon"><el-icon><Reading /></el-icon></div>
        <div>
          <h2>{{ $t('nav.skill') }}</h2>
          <div class="header-desc">{{ $t('skill.subtitle') }}</div>
        </div>
      </div>
      <div class="admin-page-header-actions">
        <el-upload :show-file-list="false" :before-upload="handleUpload" accept=".zip">
          <el-button type="primary"><el-icon><Upload /></el-icon> {{ $t('skill.uploadSkill') }}</el-button>
        </el-upload>
      </div>
    </div>

    <!-- Table Card -->
    <el-card shadow="hover" class="admin-card">
      <div class="admin-table-toolbar">
        <el-input v-model="search" :placeholder="$t('common.search')" style="width: 300px" clearable @keyup.enter="loadData" @clear="loadData">
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
      <div v-for="item in filteredData" :key="item.id" class="item-card" @click="openEditDialog(item)">
        <div class="item-card-top">
          <div class="item-card-icon"><el-icon :size="24"><Reading /></el-icon></div>
          <div class="item-card-main">
            <div class="item-card-name">{{ item.name }}</div>
            <div class="item-card-desc" v-if="item.description">{{ item.description }}</div>
          </div>
        </div>
        <div class="item-card-meta">
          <el-tag :type="item.enabled ? 'success' : 'info'" size="small">{{ item.enabled ? t('common.enable') : t('common.disable') }}</el-tag>
          <span class="item-card-meta-right">{{ item._fileCount ?? 0 }} files &middot; {{ item.updatedAt || '-' }}</span>
        </div>
        <div class="item-card-footer">
          <el-switch v-model="item.enabled" @click.stop @change="toggleEnabled(item)" size="small" />
          <div class="item-card-actions">
            <el-button link type="primary" size="small" @click.stop="openFileBrowser(item)">{{ $t('skill.files') }}</el-button>
            <el-button link type="primary" size="small" @click.stop="openEditDialog(item)">{{ $t('common.edit') }}</el-button>
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
          <div v-if="item.description" class="mobile-card-desc">{{ item.description }}</div>
          <div class="mobile-card-actions">
            <el-button size="small" @click="viewDetail(item)">{{ $t('common.detail') }}</el-button>
            <el-button size="small" type="danger" @click="deleteSkill(item)">{{ $t('common.delete') }}</el-button>
          </div>
        </div>
      </el-card>
      <div v-if="filteredData.length === 0" style="text-align:center;padding:40px;color:var(--cc-text-muted)">{{ $t('common.noData') }}</div>
    </div>

          <el-table v-if="viewMode === 'list' || isMobile" :data="filteredData" :class="{ 'mobile-hide': isMobile }" v-loading="loading" stripe>
        <el-table-column prop="name" :label="$t('common.name')" width="180" />
        <el-table-column prop="description" :label="$t('common.description')" show-overflow-tooltip />
        <el-table-column prop="enabled" :label="$t('common.status')" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? t('common.enable') : t('common.disable') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('skill.files')" width="80" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openFileBrowser(row)">{{ row._fileCount ?? '-' }}</el-button>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" :label="$t('sandbox.lastActivity')" width="170" />
        <el-table-column :label="$t('common.actions')" fixed="right" width="200">
          <template #default="{ row }">
            <el-button link type="primary" @click="openFileBrowser(row)">{{ $t('skill.files') }}</el-button>
            <el-button link type="primary" @click="openEditDialog(row)">{{ $t('common.edit') }}</el-button>
            <el-switch v-model="row.enabled" size="small" @change="toggleEnabled(row)" style="margin-left: 8px" />
            <el-popconfirm :title="$t('common.deleteConfirm')" @confirm="handleDelete(row.id)">
              <template #reference><el-button link type="danger" style="margin-left: 8px">{{ $t('common.delete') }}</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Edit metadata dialog -->
    <el-dialog v-model="editDialogVisible" :title="$t('common.edit')" width="500" destroy-on-close>
      <el-form :model="editForm" label-width="80px">
        <el-form-item :label="$t('common.name')">
          <el-input v-model="editForm.name" />
        </el-form-item>
        <el-form-item :label="$t('common.description')">
          <el-input v-model="editForm.description" type="textarea" :rows="3" :placeholder="$t('common.description')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleEditSave">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- File browser dialog -->
    <el-dialog v-model="fileDialogVisible" :title="t('skill.manageFiles') + ': ' + currentSkill?.name" width="900" destroy-on-close>
      <div style="margin-bottom: 12px; display: flex; justify-content: space-between">
        <span style="color: #999; font-size: 13px">
          {{ currentSkill?.name }}/ 
        </span>
        <el-button size="small" @click="addFileDialogVisible = true">
          <el-icon><Plus /></el-icon> {{ $t('common.create') }}
        </el-button>
      </div>
      <el-table :data="skillFiles" v-loading="filesLoading" stripe border size="small">
        <el-table-column prop="filePath" :label="$t('skill.filePath')" width="260">
          <template #default="{ row }">
            <span :style="{ color: row.filePath === 'SKILL.md' ? '#409eff' : '#333', fontWeight: row.filePath === 'SKILL.md' ? 'bold' : 'normal' }">
              {{ row.filePath }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="fileType" :label="$t('common.type')" width="80" />
        <el-table-column :label="$t('common.actions')" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="openFileEditor(row)">{{ $t('common.edit') }}</el-button>
            <el-popconfirm v-if="row.filePath !== 'SKILL.md'" :title="$t('common.deleteConfirm')" @confirm="handleDeleteFile(row.filePath)">
              <template #reference><el-button link type="danger">{{ $t('common.delete') }}</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <!-- Inline file editor -->
      <el-dialog v-model="fileEditorVisible" :title="editingFile?.filePath" width="800" append-to-body destroy-on-close>
        <el-input v-model="fileContent" type="textarea" :rows="25" style="font-family: monospace" />
        <template #footer>
          <el-button @click="fileEditorVisible = false">{{ $t('common.cancel') }}</el-button>
          <el-button type="primary" :loading="savingFile" @click="handleSaveFile">{{ $t('common.save') }}</el-button>
        </template>
      </el-dialog>

      <!-- Add file dialog -->
      <el-dialog v-model="addFileDialogVisible" :title="$t('common.create')" width="500" append-to-body destroy-on-close>
        <el-form :model="newFile" label-width="80px">
          <el-form-item :label="$t('skill.filePath')">
            <el-input v-model="newFile.path" :placeholder="$t('skill.newFilePlaceholder')" />
          </el-form-item>
          <el-form-item :label="$t('memory.content')">
            <el-input v-model="newFile.content" type="textarea" :rows="10" style="font-family: monospace" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="addFileDialogVisible = false">{{ $t('common.cancel') }}</el-button>
          <el-button type="primary" @click="handleAddFile">{{ $t('chat.newSession') }}</el-button>
        </template>
      </el-dialog>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Reading, Plus, Search, Upload, Grid, List } from '@element-plus/icons-vue'
import { getSkills, uploadSkillZip, updateSkill, deleteSkill, getSkillFiles, getSkillFile, saveSkillFile, deleteSkillFile } from '@/api/admin'
import '@/assets/admin.css'
import { useMobile } from '@/composables/useMobile'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const tableData = ref<any[]>([])
const loading = ref(false)
const { isMobile } = useMobile()
const search = ref('')
const viewMode = ref<'card' | 'list'>('card')

// Edit dialog
const editDialogVisible = ref(false)
const editForm = ref({ name: '', description: '' })
const editingSkillId = ref('')
const saving = ref(false)

// File browser
const fileDialogVisible = ref(false)
const currentSkill = ref<any>(null)
const skillFiles = ref<any[]>([])
const filesLoading = ref(false)
const fileEditorVisible = ref(false)
const editingFile = ref<any>(null)
const fileContent = ref('')
const savingFile = ref(false)
const addFileDialogVisible = ref(false)
const newFile = ref({ path: '', content: '' })

const filteredData = computed(() => {
  if (!search.value) return tableData.value
  const kw = search.value.toLowerCase()
  return tableData.value.filter(r =>
    r.name?.toLowerCase().includes(kw) || r.description?.toLowerCase().includes(kw)
  )
})

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await getSkills()
    const skills = Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : []
    // Load file counts
    for (const skill of skills) {
      try {
        const filesRes: any = await getSkillFiles(skill.id)
        const files = filesRes?.data ?? filesRes ?? []
        skill._fileCount = Array.isArray(files) ? files.length : 0
      } catch {
        skill._fileCount = 0
      }
    }
    tableData.value = skills
  } catch {} finally { loading.value = false }
}

// Upload ZIP
const handleUpload = async (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  try {
    await uploadSkillZip(formData)
    ElMessage.success(t('skill.uploadSuccess'))
    loadData()
  } catch {
    ElMessage.error(t('skill.uploadFailed'))
  }
  return false // prevent default upload
}

// Edit metadata
const openEditDialog = (row: any) => {
  editingSkillId.value = row.id
  editForm.value = { name: row.name, description: row.description || '' }
  editDialogVisible.value = true
}

const handleEditSave = async () => {
  saving.value = true
  try {
    await updateSkill(editingSkillId.value, editForm.value)
    ElMessage.success(t('common.updateSuccess'))
    editDialogVisible.value = false
    loadData()
  } catch { ElMessage.error(t('common.failed')) }
  finally { saving.value = false }
}

// Toggle enabled
const toggleEnabled = async (row: any) => {
  try {
    await updateSkill(row.id, { enabled: row.enabled })
  } catch {
    row.enabled = !row.enabled
    ElMessage.error(t('common.failed'))
  }
}

// Delete skill
const handleDelete = async (id: string) => {
  await deleteSkill(id)
  ElMessage.success(t('common.deleteSuccess'))
  loadData()
}

// File browser
const openFileBrowser = async (row: any) => {
  currentSkill.value = row
  fileDialogVisible.value = true
  await loadFiles(row.id)
}

const loadFiles = async (skillId: string) => {
  filesLoading.value = true
  try {
    const res: any = await getSkillFiles(skillId)
    skillFiles.value = Array.isArray(res?.data) ? res.data : Array.isArray(res) ? res : []
  } catch { skillFiles.value = [] }
  finally { filesLoading.value = false }
}

const openFileEditor = async (file: any) => {
  editingFile.value = file
  fileContent.value = ''
  fileEditorVisible.value = true
  try {
    const res: any = await getSkillFile(currentSkill.value.id, file.filePath)
    fileContent.value = res?.data?.content ?? res?.data ?? file.content ?? ''
  } catch {
    fileContent.value = file.content || ''
  }
}

const handleSaveFile = async () => {
  savingFile.value = true
  try {
    await saveSkillFile(currentSkill.value.id, {
      path: editingFile.value.filePath,
      content: fileContent.value
    })
    ElMessage.success(t('common.updateSuccess'))
    fileEditorVisible.value = false
    loadFiles(currentSkill.value.id)
  } catch { ElMessage.error(t('skill.fileSaveFailed')) }
  finally { savingFile.value = false }
}

const handleAddFile = async () => {
  try {
    await saveSkillFile(currentSkill.value.id, newFile.value)
    ElMessage.success(t('common.createSuccess'))
    addFileDialogVisible.value = false
    newFile.value = { path: '', content: '' }
    loadFiles(currentSkill.value.id)
  } catch { ElMessage.error(t('common.failed')) }
}

const handleDeleteFile = async (filePath: string) => {
  try {
    await deleteSkillFile(currentSkill.value.id, filePath)
    ElMessage.success(t('skill.fileDeleted'))
    loadFiles(currentSkill.value.id)
  } catch { ElMessage.error(t('common.deleteFailed')) }
}

onMounted(loadData)
</script>

