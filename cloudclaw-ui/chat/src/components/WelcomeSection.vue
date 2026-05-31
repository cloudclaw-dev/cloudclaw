<template>
  <div class="welcome-section" :class="{ dark: isDark }">
    <div class="welcome-icon"><el-icon :size="48"><Promotion /></el-icon></div>
    <h2>{{ t('dashboard.welcomeTitle') }}</h2>
    <p>{{ t('chat.newChat') }}</p>
    <!-- Suggested Prompts -->
    <div class="suggested-prompts">
      <div v-for="(prompt, i) in suggestions" :key="i" class="prompt-card" @click="$emit('startWithPrompt', prompt.text)">
        <div class="prompt-icon">{{ prompt.icon }}</div>
        <div class="prompt-content">
          <div class="prompt-title">{{ prompt.title }}</div>
          <div class="prompt-desc">{{ prompt.desc }}</div>
        </div>
      </div>
    </div>
    <el-button type="primary" size="large" @click="$emit('newSession')">
      <el-icon><Plus /></el-icon> {{ t('chat.newSession') }}
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { computed, inject, ref } from 'vue'
import { Promotion, Plus } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'

defineEmits<{
  (e: 'newSession'): void
  (e: 'startWithPrompt', text: string): void
}>()

const { t } = useI18n()
const isDark = inject('isDark', ref(false))

const suggestions = computed(() => [
  {
    icon: '\u{1F4AC}',
    title: t('chat.promptGreeting'),
    desc: t('chat.promptGreetingDesc'),
    text: t('chat.promptGreetingText')
  },
  {
    icon: '\u{1F4DD}',
    title: t('chat.promptWriting'),
    desc: t('chat.promptWritingDesc'),
    text: t('chat.promptWritingText')
  },
  {
    icon: '\u{1F4BB}',
    title: t('chat.promptCode'),
    desc: t('chat.promptCodeDesc'),
    text: t('chat.promptCodeText')
  },
  {
    icon: '\u{1F50D}',
    title: t('chat.promptAnalysis'),
    desc: t('chat.promptAnalysisDesc'),
    text: t('chat.promptAnalysisText')
  }
])
</script>

<style scoped>
.welcome-section {
  text-align: center;
  padding: 60px 20px 40px;
  color: var(--cc-text-secondary, #646a73);
}
.welcome-icon {
  width: 72px; height: 72px;
  border-radius: 18px;
  background: linear-gradient(135deg, #3370ff, #5b8def);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
  color: #fff;
}
.welcome-section h2 {
  font-size: 22px;
  font-weight: 600;
  color: var(--cc-text-primary, #1f2329);
  margin-bottom: 8px;
}
.welcome-section p { font-size: 14px; margin-bottom: 24px; }
.suggested-prompts {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  max-width: 560px;
  margin: 0 auto 28px;
}
.prompt-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 10px;
  border: 1px solid var(--cc-border, #e8eaed);
  cursor: pointer;
  text-align: left;
  transition: all 0.15s;
  background: var(--cc-bg-primary, #fff);
}
.dark .prompt-card {
  background: #1d1e1f;
  border-color: #363637;
}
.prompt-card:hover {
  border-color: var(--cc-accent, #3370ff);
  background: var(--cc-accent-light, #e8f0ff);
  box-shadow: 0 2px 8px rgba(51,112,255,0.1);
}
.dark .prompt-card:hover {
  background: #1a2a44;
}
.prompt-icon { font-size: 20px; flex-shrink: 0; line-height: 1; }
.prompt-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--cc-text-primary, #1f2329);
  margin-bottom: 2px;
}
.prompt-desc {
  font-size: 11px;
  color: var(--cc-text-muted, #8f959e);
  line-height: 1.4;
}

@media (max-width: 767px) {
  .welcome-section {
    padding: 40px 16px 20px;
  }
  .welcome-icon {
    width: 56px; height: 56px;
  }
  .welcome-section h2 {
    font-size: 18px;
  }
  .suggested-prompts {
    grid-template-columns: 1fr;
  }
}
</style>
