import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import i18n from '@/i18n'

const t = i18n.global.t

const api = axios.create({
  baseURL: '/api',
  timeout: 60000
})

// Refresh token state
let isRefreshing = false
let pendingRequests: ((token: string) => void)[] = []

function onTokenRefreshed(token: string) {
  pendingRequests.forEach(cb => cb(token))
  pendingRequests = []
}

api.interceptors.request.use(config => {
  const token = localStorage.getItem('access_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  response => {
    const data = response.data
    if (data.code && data.code !== 200) {
      const msg = data.i18nKey ? t(data.i18nKey, data.args) : (data.message || t('common.requestFailed'))
      ElMessage.error(msg)
      return Promise.reject(new Error(msg))
    }
    return data
  },
  async error => {
    const originalRequest = error.config

    if (error.response?.status === 401 && !originalRequest._retry) {
      const refreshToken = localStorage.getItem('refresh_token')
      if (!refreshToken) {
        localStorage.removeItem('access_token')
        localStorage.removeItem('refresh_token')
        router.push('/login')
        return Promise.reject(error)
      }

      if (isRefreshing) {
        // Queue this request until refresh completes
        return new Promise((resolve) => {
          pendingRequests.push((token: string) => {
            originalRequest.headers.Authorization = `Bearer ${token}`
            resolve(api(originalRequest))
          })
        })
      }

      originalRequest._retry = true
      isRefreshing = true

      try {
        const res = await axios.post('/api/v1/auth/refresh', { refreshToken })
        const data = res.data?.data || res.data
        const newToken = data.accessToken

        if (newToken) {
          localStorage.setItem('access_token', newToken)
          if (data.refreshToken) {
            localStorage.setItem('refresh_token', data.refreshToken)
          }
          onTokenRefreshed(newToken)
          originalRequest.headers.Authorization = `Bearer ${newToken}`
          return api(originalRequest)
        } else {
          throw new Error('No access token in refresh response')
        }
      } catch (refreshError) {
        localStorage.removeItem('access_token')
        localStorage.removeItem('refresh_token')
        pendingRequests = []
        router.push('/login')
        ElMessage.error(t('login.expired'))
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
    }

    const data = error.response?.data
    if (data?.i18nKey) {
      ElMessage.error(t(data.i18nKey, data.args))
    } else {
      ElMessage.error(data?.message || t('common.requestFailed'))
    }
    return Promise.reject(error)
  }
)

export default api
