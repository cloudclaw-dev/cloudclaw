<template>
  <div class="login-container" :class="{ dark: isDark }">
    <div class="login-card">
      <div class="login-header">
        <h1 class="login-title">CloudClaw</h1>
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
        <el-form-item label="Username" prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="Enter your username"
            prefix-icon="User"
            clearable
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item label="Password" prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="Enter your password"
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
            {{ loading ? 'Signing in...' : 'Sign In' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-footer">
        <el-switch
          v-model="isDark"
          active-text="Dark"
          inactive-text="Light"
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
import type { FormInstance, FormRules } from 'element-plus'
import { authApi } from '@/api/chat'

const router = useRouter()
const loginFormRef = ref<FormInstance>()
const loading = ref(false)
const isDark = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const loginRules: FormRules = {
  username: [
    { required: true, message: 'Please enter your username', trigger: 'blur' },
    { min: 2, max: 50, message: 'Username must be 2-50 characters', trigger: 'blur' }
  ],
  password: [
    { required: true, message: 'Please enter your password', trigger: 'blur' },
    { min: 6, max: 100, message: 'Password must be at least 6 characters', trigger: 'blur' }
  ]
}

const toggleTheme = (val: boolean) => {
  if (val) {
    document.documentElement.classList.add('dark')
  } else {
    document.documentElement.classList.remove('dark')
  }
  localStorage.setItem('theme', val ? 'dark' : 'light')
}

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
        ElMessage.error(res.message || 'Login failed')
        return
      }
      const data = res.data
      if (!data?.accessToken) {
        ElMessage.error('Login failed: no token received')
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

      ElMessage.success('Login successful')
      router.push('/')
    } catch (error: any) {
      // Error already handled by axios interceptor
    } finally {
      loading.value = false
    }
  })
}

onMounted(() => {
  const savedTheme = localStorage.getItem('theme')
  if (savedTheme === 'dark') {
    isDark.value = true
    document.documentElement.classList.add('dark')
  }
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
