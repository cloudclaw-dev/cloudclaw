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
          <div class="prompt-desc">{{ prompt.text }}</div>
        </div>
      </div>
    </div>
    <el-button type="primary" size="large" @click="$emit('newSession')">
      <el-icon><Plus /></el-icon> {{ t('chat.newSession') }}
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { ref, inject } from 'vue'
import { Plus, Promotion } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'

const emit = defineEmits<{
  (e: 'newSession'): void
  (e: 'startWithPrompt', text: string): void
}>()

const { t } = useI18n()
const isDark = inject('isDark', ref(false))

const suggestions = [
  { icon: '\u270b', title: t('chat.promptGreeting'), text: t('chat.promptGreetingText') },
  { icon: '\u270d\ufe0f', title: t('chat.promptWriting'), text: t('chat.promptWritingText') },
  { icon: '\u{1f4bb}', title: t('chat.promptCode'), text: t('chat.promptCodeText') },
  { icon: '\u{1f4ca}', title: t('chat.promptAnalysis'), text: t('chat.promptAnalysisText') },
]
</script>

<style scoped>
.welcome-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 40px 20px;
  min-height: calc(100vh - 56px - 80px);
  max-width: 600px;
  margin: 0 auto;
  color: var(--cc-text-secondary, #646a73);
}
.welcome-icon {
  margin-bottom: 16px;
  color: var(--cc-accent, #3370ff);
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
  max-width: 500px;
  margin: 0 auto 24px;
}
.prompt-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px;
  border-radius: 12px;
  border: 1px solid var(--cc-border, #e8eaed);
  cursor: pointer;
  text-align: left;
  transition: all 0.2s;
}
.welcome-section.dark .prompt-card {
  border-color: #363637;
}
.prompt-card:hover {
  border-color: var(--cc-accent, #3370ff);
  box-shadow: 0 2px 8px rgba(51,112,255,0.1);
}
.prompt-icon { font-size: 20px; flex-shrink: 0; }
.prompt-content { flex: 1; min-width: 0; }
.prompt-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--cc-text-primary, #1f2329);
  margin-bottom: 2px;
}
.prompt-desc {
  font-size: 12px;
  color: var(--cc-text-muted, #8f959e);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 767px) {
  .suggested-prompts { grid-template-columns: 1fr; }
}
</style>
