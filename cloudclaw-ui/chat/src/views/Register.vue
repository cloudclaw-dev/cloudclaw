<template>
  <div class="login-container" :class="{ dark: isDark }">
    <div class="login-card">
      <div class="login-header">
        <a href="http://cloudclaw.run" target="_blank" rel="noopener" style="text-decoration:none;color:inherit"><h1 class="login-title">CloudClaw</h1></a>
        <p class="login-subtitle">{{ t('login.subtitle') }}</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @submit.prevent="handleRegister"
      >
        <el-form-item :label="t('login.username')" prop="username">
          <el-input v-model="form.username" :placeholder="t('login.enterUsername')" prefix-icon="User" clearable />
        </el-form-item>

        <el-form-item :label="t('login.email')" prop="email">
          <el-input v-model="form.email" :placeholder="t('login.enterEmail')" prefix-icon="Message" clearable />
        </el-form-item>

        <el-form-item :label="t('login.password')" prop="password">
          <el-input v-model="form.password" type="password" :placeholder="t('login.enterPassword')" prefix-icon="Lock" show-password />
        </el-form-item>

        <el-form-item :label="t('login.confirmPassword')" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" :placeholder="t('login.enterConfirmPassword')" prefix-icon="Lock" show-password @keyup.enter="handleRegister" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" class="login-button" :loading="loading" @click="handleRegister">
            {{ loading ? t('login.signingUp') : t('login.signUp') }}
          </el-button>
        </el-form-item>
      </el-form>

      <el-divider>{{ t('login.orDivider') }}</el-divider>

      <el-button class="feishu-btn" size="large" @click="feishuLogin">
        <span style="font-size:16px">🔵</span> {{ t('login.feishuLogin') }}
      </el-button>

      <div class="login-footer">
        <span>{{ t('login.hasAccount') }} </span>
        <el-link type="primary" @click="router.push('/login')">{{ t('login.goToLogin') }}</el-link>
        <span style="flex:1"></span>
        <el-switch v-model="isDark" :active-text="t('login.dark')" :inactive-text="t('login.light')" @change="toggleTheme" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useTheme } from '@/utils/theme'
import type { FormInstance, FormRules } from 'element-plus'
import { authApi } from '@/api/chat'

const router = useRouter()
const { t } = useI18n()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: ''
})

const validateConfirm = (_rule: any, value: string, callback: any) => {
  if (value !== form.password) {
    callback(new Error(t('login.passwordMismatch')))
  } else {
    callback()
  }
}

const rules: FormRules = {
  username: [
    { required: true, message: t('login.usernameRequired'), trigger: 'blur' },
    { min: 2, max: 50, message: t('login.usernameLength'), trigger: 'blur' }
  ],
  email: [
    { required: true, message: 'Email is required', trigger: 'blur' },
    { type: 'email', message: 'Invalid email format', trigger: 'blur' }
  ],
  password: [
    { required: true, message: t('login.passwordRequired'), trigger: 'blur' },
    { min: 6, max: 100, message: t('login.passwordLength'), trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: t('login.enterConfirmPassword'), trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

const { isDark, toggleDark } = useTheme()
const toggleTheme = (val: boolean) => { isDark.value = val }

const handleRegister = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const res: any = await authApi.register({
        username: form.username,
        email: form.email,
        password: form.password
      })
      if (res.code !== 0 && res.code !== 200) {
        ElMessage.error(res.message || t('login.registerFailed'))
        return
      }
      const data = res.data
      if (!data?.accessToken) {
        ElMessage.error(t('login.noToken'))
        return
      }
      localStorage.setItem('access_token', data.accessToken)
      if (data.refreshToken) localStorage.setItem('refresh_token', data.refreshToken)
      ElMessage.success(t('login.registerSuccess'))
      router.push('/')
    } catch (e: any) {
      ElMessage.error(e?.response?.data?.message || t('login.registerFailed'))
    } finally {
      loading.value = false
    }
  })
}

const feishuLogin = () => {
  window.location.href = '/api/v1/auth/feishu/authorize'
}

onMounted(() => {
  // Check for error params from feishu callback
  const params = new URLSearchParams(window.location.search)
  const error = params.get('error')
  if (error) {
    ElMessage.error(params.get('message') || 'OAuth failed')
  }
})
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}
.login-container.dark { background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%); }
.login-card {
  background: #fff;
  border-radius: 12px;
  padding: 40px;
  width: 100%;
  max-width: 420px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.15);
}
.dark .login-card { background: #1e1e2e; color: #e0e0e0; }
.login-header { text-align: center; margin-bottom: 30px; }
.login-title { font-size: 28px; font-weight: 700; color: #333; margin: 0; }
.dark .login-title { color: #e0e0e0; }
.login-subtitle { color: #888; margin: 8px 0 0; }
.login-button { width: 100%; }
.feishu-btn { width: 100%; margin-top: 8px; }
.login-footer {
  display: flex;
  align-items: center;
  margin-top: 20px;
  gap: 8px;
  font-size: 13px;
  color: #888;
}
</style>
