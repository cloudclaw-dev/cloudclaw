<template>
  <div class="login-container" :class="{ dark: isDark }">
    <div class="login-card">
      <div class="login-header">
        <a href="http://cloudclaw.run" target="_blank" rel="noopener" style="text-decoration:none;color:inherit"><h1 class="login-title">CloudClaw</h1></a>
        <p class="login-subtitle">AI Agent Platform</p>
      </div>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        label-position="top"
        size="large"
        @submit.prevent="handleLogin"
      >
        <el-form-item :label="t('login.username')" prop="username">
          <el-input
            v-model="loginForm.username"
            :placeholder="t('login.enterUsername')"
            prefix-icon="User"
            clearable
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item :label="t('login.password')" prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            :placeholder="t('login.enterPassword')"
            prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            class="login-button"
            :loading="loading"
            @click="handleLogin"
          >
            {{ loading ? t('login.signingIn') : t('login.signIn') }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-footer">
        <el-switch
          v-model="isDark"
          :active-text="t('login.dark')"
          :inactive-text="t('login.light')"
          @change="toggleTheme"
        />
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
const loginFormRef = ref<FormInstance>()
const loading = ref(false)


const loginForm = reactive({
  username: '',
  password: ''
})

const loginRules: FormRules = {
  username: [
    { required: true, message: t('login.usernameRequired'), trigger: 'blur' },
    { min: 2, max: 50, message: t('login.usernameLength'), trigger: 'blur' }
  ],
  password: [
    { required: true, message: t('login.passwordRequired'), trigger: 'blur' },
    { min: 6, max: 100, message: t('login.passwordLength'), trigger: 'blur' }
  ]
}

const { isDark, toggleDark } = useTheme()
const toggleTheme = (val: boolean) => { isDark.value = val }

const handleLogin = async () => {
  if (!loginFormRef.value) return

  await loginFormRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      const res: any = await authApi.login({
        username: loginForm.username,
        password: loginForm.password
      })

      if (res.code !== 0 && res.code !== 200) {
        ElMessage.error(res.message || t('login.loginFailed'))
        return
      }
      const data = res.data
      if (!data?.accessToken) {
        ElMessage.error(t('login.noToken'))
        return
      }
      localStorage.setItem('access_token', data.accessToken)
      if (data.refreshToken) {
        localStorage.setItem('refresh_token', data.refreshToken)
      }

      // Fetch user role
      try {
        const meRes: any = await (await import('@/api/chat')).userApi.me()
        const meData = meRes.data || meRes
        if (meData?.role) {
          localStorage.setItem('user_role', meData.role)
          localStorage.setItem('user_name', meData.username || '')
        }
      } catch {}

      ElMessage.success(t('login.loginSuccess'))
      router.push('/')
    } catch (error: any) {
      // Error already handled by axios interceptor
    } finally {
      loading.value = false
    }
  })
}

onMounted(() => {
  // theme initialized by useTheme
})
</script>

<style scoped>
.login-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.login-container.dark {
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
}

.login-card {
  width: 100%;
  max-width: 420px;
  padding: 40px;
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.dark .login-card {
  background: #1d1e2c;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.4);
}

.login-header {
  text-align: center;
  margin-bottom: 36px;
}

.login-title {
  font-size: 32px;
  font-weight: 700;
  color: #303133;
  margin: 0 0 8px 0;
  letter-spacing: -0.5px;
}

.dark .login-title {
  color: #e5eaf3;
}

.login-subtitle {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

.dark .login-subtitle {
  color: #a3a6ad;
}

.login-button {
  width: 100%;
  height: 44px;
  font-size: 16px;
  border-radius: 8px;
}

.login-footer {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}

:deep(.el-form-item__label) {
  font-weight: 500;
}

:deep(.el-input__wrapper) {
  border-radius: 8px;
}

/* Dark mode input fields on login page */
.dark :deep(.el-input__wrapper) {
  background: #262637;
  box-shadow: 0 0 0 1px #363647 inset;
}
.dark :deep(.el-input__inner) {
  color: #e5eaf3;
}
.dark :deep(.el-form-item__label) {
  color: #a3a6ad;
}
.dark :deep(.el-switch__label) {
  color: #a3a6ad;
}
@media (max-width: 767px) {
  .login-card {
    padding: 24px 20px;
    border-radius: 12px;
  }
  .login-title {
    font-size: 24px;
  }
  .login-header {
    margin-bottom: 24px;
  }
}
</style>
