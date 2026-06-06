<template>
  <div class="admin-layout" :class="{ dark: isDark }">
    <!-- Left menu -->
    <el-aside :width="adminNavCollapsed ? '64px' : '200px'" class="admin-nav-bar" :class="{ collapsed: adminNavCollapsed }">
      <!-- Fixed header -->
      <div class="admin-nav-header">
        <img src="@/assets/logo.png" alt="CC" class="nav-logo" />
        <span v-if="!adminNavCollapsed" class="nav-brand">CloudClaw</span>
      </div>

      <!-- Back button -->
      <div class="admin-nav-back">
        <div class="nav-bar-item" @click="router.push('/')" :title="adminNavCollapsed ? t('nav.backToChat') : ''">
          <el-icon :size="20"><ArrowLeft /></el-icon>
          <span class="nav-bar-label" :class="{ 'label-hidden': adminNavCollapsed }">{{ adminNavCollapsed ? '' : t('nav.backToChat') }}</span>
        </div>
      </div>

      <div class="nav-bar-divider" />

      <!-- Menu items -->
      <div class="admin-nav-menu">
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
      <div class="admin-nav-bottom">
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
import { ref, computed, inject, Ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  ArrowLeft, Odometer, SetUp, Connection, Reading, Cpu, User, Grid, Monitor,
  Sunny, Moon, SwitchButton, Fold
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const { t, locale } = useI18n()
const isDark = inject<Ref<boolean>>('isDark', ref(false))
const adminNavCollapsed = ref(false)

const menuItems = computed(() => [
  { path: '/admin/dashboard', label: t('dashboard.title'), icon: Odometer },
  { path: '/admin/agents', label: 'Agent', icon: SetUp },
  { path: '/admin/mcp', label: 'MCP', icon: Connection },
  { path: '/admin/skills', label: t('nav.skill'), icon: Reading },
  { path: '/admin/llm', label: 'LLM', icon: Cpu },
  { path: '/admin/users', label: t('nav.user'), icon: User },
  { path: '/admin/sandboxes', label: t('nav.sandbox'), icon: Grid },
  { path: '/admin/monitor', label: t('nav.monitor'), icon: Monitor },
])

const toggleLocale = () => { locale.value = locale.value === 'zh' ? 'en' : 'zh' }
const toggleDark = () => { isDark.value = !isDark.value }
const handleLogout = () => {
  localStorage.removeItem('access_token')
  localStorage.removeItem('refresh_token')
  router.push('/login')
}
</script>

<style scoped>
.admin-layout {
  display: flex;
  height: 100vh;
  background: var(--cc-bg-primary, #fff);
}

/* Reuse nav-bar styles from ChatLayout */
.admin-nav-bar {
  display: flex;
  flex-direction: column;
  background: var(--cc-bg-secondary, #f7f8fa);
  border-right: 1px solid var(--cc-border, #e8eaed);
  transition: width 0.2s;
  overflow: hidden;
  flex-shrink: 0;
}
:global(.dark) .admin-nav-bar {
  background: #141414;
  border-right-color: #363637;
}
.admin-nav-bar.collapsed { width: 64px; }

.admin-nav-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px;
  flex-shrink: 0;
}
.nav-logo { width: 28px; height: 28px; border-radius: 6px; }
.nav-brand { font-size: 16px; font-weight: 700; color: var(--cc-text-primary, #1f2329); }

.admin-nav-back { padding: 0 8px; flex-shrink: 0; }

.admin-nav-menu {
  flex: 1;
  overflow-y: auto;
  padding: 4px 8px;
}

.admin-nav-bottom {
  flex-shrink: 0;
  padding: 0 8px 8px;
}

/* nav-bar-item shared styles */
.nav-bar-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s;
  color: var(--cc-text-secondary, #646a73);
  font-size: 14px;
  white-space: nowrap;
}
.nav-bar-item:hover { background: var(--cc-bg-tertiary, rgba(0,0,0,0.04)); }
.nav-bar-item.active { background: rgba(51,112,255,0.08); color: var(--cc-accent, #3370ff); }
:global(.dark) .nav-bar-item:hover { background: rgba(255,255,255,0.06); }
:global(.dark) .nav-bar-item.active { background: rgba(51,112,255,0.15); color: #5b9aff; }

.nav-bar-label { overflow: hidden; text-overflow: ellipsis; }
.label-hidden { display: none; }

.locale-icon {
  width: 20px;
  text-align: center;
  font-size: 12px;
  font-weight: 600;
  color: var(--cc-text-secondary, #646a73);
}

.nav-bar-divider {
  height: 1px;
  background: var(--cc-border, #e8eaed);
  margin: 8px 12px;
}
:global(.dark) .nav-bar-divider { background: #363637; }

.collapse-toggle { margin-top: 4px; }
.rotate-icon { transform: rotate(180deg); }

.admin-content {
  flex: 1;
  overflow-y: auto;
  padding: 0 !important;
  background: var(--cc-bg-secondary, #f7f8fa);
}
:global(.dark) .admin-content { background: #0a0a0a; }
</style>
