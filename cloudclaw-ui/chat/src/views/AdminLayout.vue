<template>
  <div class="admin-layout" :class="{ dark: isDark }">
    <!-- Left menu -->
    <!-- Mobile hamburger -->
        <div v-if="isMobile" class="mobile-header">
          <button class="mobile-menu-btn" @click="toggleMobileNav">
            <el-icon :size="20"><component :is="mobileNavVisible ? Close : Expand" /></el-icon>
          </button>
          <img src="@/assets/logo.png" alt="CC" class="mobile-logo" />
          <span class="mobile-title">CloudClaw</span>
          <span class="mobile-badge">Admin</span>
        </div>
        <!-- Mobile overlay -->
        <div v-if="isMobile && mobileNavVisible" class="mobile-nav-overlay" @click="mobileNavVisible = false" />
        <el-aside :width="adminNavCollapsed ? '64px' : '260px'" class="nav-bar"
           :class="{ collapsed: adminNavCollapsed, 'mobile-open': isMobile && mobileNavVisible }">
      <!-- Fixed header -->
      <a href="http://cloudclaw.run" target="_blank" rel="noopener" class="nav-bar-header-link" style="text-decoration:none">
        <div class="nav-bar-header">
          <img src="@/assets/logo.png" alt="CC" class="nav-logo" />
          <span v-if="!adminNavCollapsed" class="nav-brand">CloudClaw</span>
        </div>
        <div v-if="!adminNavCollapsed" class="nav-version">v{{ systemVersion }} · {{ systemMode }}</div>
      </a>

      <!-- Back button -->
      <div >
        <div class="nav-bar-item" @click="router.push('/')" :title="adminNavCollapsed ? t('nav.backToChat') : ''">
          <el-icon :size="20"><ArrowLeft /></el-icon>
          <span class="nav-bar-label" :class="{ 'label-hidden': adminNavCollapsed }">{{ adminNavCollapsed ? '' : t('nav.backToChat') }}</span>
        </div>
      </div>

      <div class="nav-bar-divider" />

      <!-- Menu items -->
      <div class="nav-bar-menu">
        <div v-for="item in menuItems" :key="item.path"
             class="nav-bar-item"
             :class="{ active: route.path === item.path }"
             @click="router.push(item.path)"
             :title="adminNavCollapsed ? item.label : ''">
          <el-icon :size="20"><component :is="item.icon" /></el-icon>
          <span class="nav-bar-label" :class="{ 'label-hidden': adminNavCollapsed }">{{ item.label }}</span>
        </div>
      </div>

      <!-- Fixed bottom -->
      <div class="nav-bar-bottom">
        <div class="nav-bar-divider" />
        <div class="nav-bar-item" @click="toggleLocale">
          <span class="locale-icon">{{ locale === 'zh' ? 'EN' : '中' }}</span>
          <span class="nav-bar-label" :class="{ 'label-hidden': adminNavCollapsed }">{{ locale === 'zh' ? 'English' : '中文' }}</span>
        </div>
        <div class="nav-bar-item" @click="toggleDark" :title="isDark ? 'Light' : 'Dark'">
          <el-icon :size="18"><component :is="isDark ? Sunny : Moon" /></el-icon>
          <span class="nav-bar-label" :class="{ 'label-hidden': adminNavCollapsed }">{{ isDark ? t('login.light') : t('login.dark') }}</span>
        </div>
        <div class="nav-bar-item" @click="handleLogout" :title="t('nav.logout')">
          <el-icon :size="18"><SwitchButton /></el-icon>
          <span class="nav-bar-label" :class="{ 'label-hidden': adminNavCollapsed }">{{ t('nav.logout') }}</span>
        </div>
        <div class="nav-bar-item collapse-toggle" @click="adminNavCollapsed = !adminNavCollapsed">
          <el-icon :size="18" :class="{ 'rotate-icon': !adminNavCollapsed }"><Fold /></el-icon>
          <span class="nav-bar-label" :class="{ 'label-hidden': adminNavCollapsed }">{{ adminNavCollapsed ? t('nav.expandMenu') : t('nav.collapseMenu') }}</span>
        </div>
      </div>
    </el-aside>

    <!-- Content -->
    <el-main class="admin-content"><router-view /></el-main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import api from '@/api'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useTheme } from '@/utils/theme'
import {
  ArrowLeft, Odometer, SetUp, Connection, Reading, Cpu, User, Grid, Monitor,
  Sunny, Moon, SwitchButton, Fold, Close, Expand, ChatSquare
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const { t, locale } = useI18n()

const adminNavCollapsed = ref(false)
const isMobile = ref(false)
const mobileNavVisible = ref(false)

const checkMobile = () => {
  isMobile.value = window.innerWidth < 768
  if (!isMobile.value) mobileNavVisible.value = false
}
const toggleMobileNav = () => { mobileNavVisible.value = !mobileNavVisible.value }

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})
onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})
const systemVersion = ref('')
const systemMode = ref('standalone')

const menuItems = computed(() => [
  { path: '/admin/dashboard', label: t('dashboard.title'), icon: Odometer },
  { path: '/admin/agents', label: 'Agent', icon: SetUp },
  { path: '/admin/mcp', label: 'MCP', icon: Connection },
  { path: '/admin/skills', label: t('nav.skill'), icon: Reading },
  { path: '/admin/llm', label: 'LLM', icon: Cpu },
  { path: '/admin/users', label: t('nav.user'), icon: User },
  { path: '/admin/sandboxes', label: t('nav.sandbox'), icon: Grid },
  { path: '/admin/monitor', label: t('nav.monitor'), icon: Monitor },
  { path: '/admin/channel', label: t('nav.channel'), icon: ChatSquare },
])

const toggleLocale = () => { locale.value = locale.value === 'zh' ? 'en' : 'zh' }
const { isDark, toggleDark } = useTheme()
onMounted(async () => {
  try {
    const infoRes: any = await api.get('/admin/stats/info')
    const info = infoRes?.data?.data || infoRes?.data || infoRes
    if (info?.version) systemVersion.value = info.version
    if (info?.mode) systemMode.value = info.mode
  } catch (e) { /* ignore */ }
})

const handleLogout = () => {
  localStorage.removeItem('access_token')
  localStorage.removeItem('refresh_token')
  router.push('/login')
}
</script>

<style scoped>
/* Reuse identical nav-bar styles from ChatLayout */
.admin-layout {
  display: flex;
  height: 100vh;
  height: 100dvh;
  background: var(--cc-bg-primary, #fff);
}
.nav-bar {
  display: flex;
  flex-direction: column;
  background: #f0f1f3;
  border-right: none;
  transition: width 0.25s ease;
  overflow: hidden;
  flex-shrink: 0;
  height: 100vh;
  height: 100dvh;
}
html.dark .nav-bar { background: #1e1f22; border-right-color: #2d2d2f; }
.nav-bar.collapsed { width: 64px; }

.nav-bar-header { padding: 10px 0 6px; display: flex; align-items: center; justify-content: center; gap: 10px; white-space: nowrap; overflow: hidden; }
.nav-bar.collapsed .nav-bar-header { justify-content: center; }
.nav-logo { width: 32px; height: 32px; border-radius: 8px; flex-shrink: 0; }
.nav-brand { font-size: 16px; font-weight: 700; color: var(--cc-text-primary, #1f2329); }
html.dark .nav-brand { color: #e5eaf3; }
.nav-bar-header-link { color: inherit; text-decoration: none; }
.nav-version { font-size: 10px; color: var(--cc-text-muted, #999); text-align: center; margin-top: -2px; margin-bottom: 2px; letter-spacing: 0.5px; }
html.dark .nav-version { color: #6b6b6b; }
.nav-bar.collapsed .nav-version { display: none; }

.nav-bar-menu { display: flex; flex-direction: column; gap: 2px; padding: 4px 8px; flex: 1; min-height: 0; overflow-y: auto; }
.nav-bar.collapsed .nav-bar-menu { align-items: center; padding: 4px 6px; }

.nav-bar-item {
  height: 40px; border-radius: 8px; display: flex; align-items: center; gap: 10px;
  padding: 0 12px; cursor: pointer; color: #5a5e66; font-size: 13px;
  transition: all 0.15s; user-select: none; white-space: nowrap; overflow: hidden;
}
.nav-bar.collapsed .nav-bar-item { width: 52px; height: 46px; justify-content: center; flex-direction: column; gap: 2px; padding: 0; }
.nav-bar-item:hover { background: rgba(0,0,0,0.06); color: #303133; }
.nav-bar-item.active { background: rgba(51,112,255,0.08); color: var(--cc-accent, #3370ff); }
html.dark .nav-bar-item { color: #b0b0b0; }
html.dark .nav-bar-item:hover { background: rgba(255,255,255,0.06); color: #ccc; }
html.dark .nav-bar-item.active { background: rgba(51,112,255,0.15); color: #5b9aff; }
.nav-bar-label { font-size: 13px; line-height: 1; transition: opacity 0.2s, width 0.2s; overflow: hidden; }
.nav-bar.collapsed .nav-bar-label { font-size: 10px; }
.label-hidden { display: none; }
.nav-bar.collapsed .label-hidden { display: none; }

.locale-icon {
  width: 20px;
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  color: #5a5e66;
}

.nav-bar-divider { height: 1px; background: rgba(0,0,0,0.08); margin: 6px 0; }
.nav-bar.collapsed .nav-bar-divider { width: 32px; margin: 4px auto; }
html.dark .nav-bar-divider { background: rgba(255,255,255,0.08); }

.collapse-toggle { margin-top: 4px; }
.rotate-icon { transform: rotate(180deg); }

.nav-bar-bottom { display: flex; flex-direction: column; gap: 2px; padding: 4px 8px 12px; }

.admin-content {
  flex: 1;
  overflow-y: auto;
  padding: 0 !important;
  background: var(--cc-bg-secondary, #f7f8fa);
  min-width: 0;
  min-height: 0;
}
html.dark .admin-content { background: #0a0a0a; }

/* ===== Mobile ===== */
.mobile-header {
  display: none;
}
.mobile-nav-overlay {
  display: none;
}

@media (max-width: 767px) {
  .admin-layout {
    flex-direction: column;
    height: 100dvh;
  }
  .mobile-header {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 14px;
    padding-top: calc(8px + env(safe-area-inset-top, 0px));
    background: var(--cc-bg-primary, #fff);
    border-bottom: 1px solid var(--el-border-color-light, #e4e7ed);
    position: sticky;
    top: 0;
    z-index: 100;
    flex-shrink: 0;
    box-shadow: 0 1px 4px rgba(0,0,0,0.04);
  }
  html.dark .mobile-header { background: #1a1a1a; border-bottom-color: #2d2d2f; box-shadow: 0 1px 4px rgba(0,0,0,0.2); }
  .mobile-menu-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    border: none;
    border-radius: 8px;
    background: var(--cc-bg-tertiary, #eef0f4);
    color: var(--cc-text-primary, #1f2329);
    cursor: pointer;
    transition: background 0.15s;
    flex-shrink: 0;
  }
  .mobile-menu-btn:hover { background: rgba(51,112,255,0.1); color: var(--cc-accent, #3370ff); }
  html.dark .mobile-menu-btn { background: #2d2d2f; color: #e5eaf3; }
  html.dark .mobile-menu-btn:hover { background: rgba(51,112,255,0.2); color: #5b9aff; }
  .mobile-logo { width: 26px; height: 26px; border-radius: 6px; flex-shrink: 0; }
  .mobile-title { font-size: 16px; font-weight: 700; color: var(--cc-text-primary, #1f2329); letter-spacing: -0.3px; }
  html.dark .mobile-title { color: #e5eaf3; }
  .mobile-badge {
    font-size: 10px;
    font-weight: 600;
    color: var(--cc-accent, #3370ff);
    background: rgba(51,112,255,0.1);
    padding: 2px 8px;
    border-radius: 4px;
    letter-spacing: 0.5px;
  }
  html.dark .mobile-badge { color: #5b9aff; background: rgba(91,154,255,0.15); }

  .nav-bar {
    position: fixed;
    top: 0;
    left: 0;
    z-index: 200;
    transform: translateX(-100%);
    transition: transform 0.25s ease;
    box-shadow: 2px 0 12px rgba(0,0,0,0.15);
  }
  .nav-bar.mobile-open {
    transform: translateX(0);
  }
  .nav-bar.collapsed { width: 260px; } /* override collapsed on mobile */

  .mobile-nav-overlay {
    display: block;
    position: fixed;
    top: 0; left: 0; right: 0; bottom: 0;
    background: rgba(0,0,0,0.4);
    z-index: 150;
  }

  .collapse-toggle { display: none; } /* hide collapse on mobile */
}
</style>
