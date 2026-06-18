<template>
  <div class="profile-page">
    <el-card class="profile-card">
      <template #header>
        <div class="card-header">
          <span>👤 {{ t('profile.title') }}</span>
        </div>
      </template>

      <el-form :model="profile" label-width="120px" size="large">
        <el-form-item :label="t('profile.displayName')">
          <el-input v-model="profile.displayName" />
        </el-form-item>
        <el-form-item :label="t('profile.avatarUrl')">
          <el-input v-model="profile.avatarUrl" />
        </el-form-item>
        <el-form-item :label="t('profile.email')">
          <el-input v-model="profile.email" />
        </el-form-item>
        <el-form-item :label="t('profile.phone')">
          <el-input v-model="profile.phone" />
        </el-form-item>
        <el-form-item :label="t('profile.role')">
          <el-tag>{{ profile.role }}</el-tag>
        </el-form-item>
        <el-form-item :label="t('profile.createdAt')">
          <span>{{ profile.createdAt }}</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="saveProfile" :loading="saving">{{ t('common.save') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="profile-card" style="margin-top:20px">
      <template #header>
        <span>🔗 {{ t('profile.bindings') }}</span>
      </template>

      <div v-for="channel in channels" :key="channel.type" class="binding-row">
        <div class="binding-info">
          <span class="binding-name">{{ channel.label }}</span>
          <el-tag v-if="getBinding(channel.type)" type="success">{{ t('profile.bound') }}</el-tag>
          <el-tag v-else type="info">{{ t('profile.notBound') }}</el-tag>
          <span v-if="getBinding(channel.type)" class="binding-detail">
            ({{ parseBindingName(getBinding(channel.type)) }})
          </span>
        </div>
        <div>
          <el-button v-if="getBinding(channel.type)" size="small" type="danger" @click="unbind(channel.type)">
            {{ t('profile.unbind') }}
          </el-button>
          <el-button v-else-if="channel.type === 'feishu'" size="small" type="primary" @click="bindFeishu">
            {{ t('profile.bindFeishu') }}
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card class="profile-card" style="margin-top:20px">
      <template #header>
        <span>🔒 {{ t('profile.changePassword') }}</span>
      </template>

      <el-form :model="passwordForm" label-width="120px" size="large">
        <el-form-item :label="t('profile.oldPassword')">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item :label="t('profile.newPassword')">
          <el-input v-model="passwordForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="changePassword" :loading="changingPassword">
            {{ t('profile.changePassword') }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api'

const { t } = useI18n()

const profile = reactive({
  displayName: '',
  avatarUrl: '',
  email: '',
  phone: '',
  role: '',
  createdAt: ''
})

const bindings = ref<any[]>([])
const saving = ref(false)
const changingPassword = ref(false)

const passwordForm = reactive({
  oldPassword: '',
  newPassword: ''
})

const channels = [
  { type: 'feishu', label: '飞书' },
  { type: 'dingtalk', label: '钉钉' },
  { type: 'wecom', label: '企业微信' }
]

const getBinding = (type: string) => bindings.value.find(b => b.channelType === type)

const parseBindingName = (binding: any) => {
  try {
    const data = typeof binding.channelData === 'string' ? JSON.parse(binding.channelData) : binding.channelData
    return data?.name || binding.channelUserId
  } catch { return binding.channelUserId }
}

const loadProfile = async () => {
  const res: any = await api.get('/v1/me')
  const data = res.data?.data || res.data
  if (data) {
    profile.displayName = data.displayName || ''
    profile.avatarUrl = data.avatarUrl || ''
    profile.email = data.email || ''
    profile.phone = data.phone || ''
    profile.role = data.role || ''
    profile.createdAt = data.createdAt || ''
    bindings.value = data.bindings || []
  }
}

const saveProfile = async () => {
  saving.value = true
  try {
    await api.put('/v1/me/profile', {
      displayName: profile.displayName,
      avatarUrl: profile.avatarUrl,
      email: profile.email,
      phone: profile.phone
    })
    ElMessage.success(t('profile.saveSuccess'))
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || 'Failed')
  } finally {
    saving.value = false
  }
}

const unbind = async (channelType: string) => {
  try {
    await ElMessageBox.confirm(t('profile.unbindConfirm'), { type: 'warning' })
    await api.delete(`/v1/me/bindings/${channelType}`)
    ElMessage.success(t('profile.unbindSuccess'))
    loadProfile()
  } catch {}
}

const bindFeishu = () => {
  const token = localStorage.getItem('access_token')
  if (token) {
    window.location.href = `/api/v1/auth/feishu/bind?token=${token}`
  }
}

const changePassword = async () => {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    ElMessage.warning('Please fill in all fields')
    return
  }
  changingPassword.value = true
  try {
    await api.put('/v1/me/password', {
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    ElMessage.success(t('profile.passwordChanged'))
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || 'Failed')
  } finally {
    changingPassword.value = false
  }
}

onMounted(() => {
  loadProfile()
  // Check for callback params
  const params = new URLSearchParams(window.location.search)
  if (params.get('bound') === 'feishu') {
    ElMessage.success('飞书绑定成功')
    window.history.replaceState({}, '', '/profile')
  }
  if (params.get('error')) {
    ElMessage.error(params.get('message') || '绑定失败')
  }
})
</script>

<style scoped>
.profile-page { max-width: 700px; margin: 0 auto; padding: 20px; }
.profile-card { border-radius: 8px; }
.card-header { font-size: 16px; font-weight: 600; }
.binding-row { display: flex; align-items: center; justify-content: space-between; padding: 12px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.binding-row:last-child { border-bottom: none; }
.binding-info { display: flex; align-items: center; gap: 10px; }
.binding-name { font-weight: 500; min-width: 80px; }
.binding-detail { color: var(--el-text-color-secondary); font-size: 13px; }
</style>
