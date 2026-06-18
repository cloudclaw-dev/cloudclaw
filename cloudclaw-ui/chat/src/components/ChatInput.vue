<template>
  <el-footer class="input-area" height="auto">
    <div class="input-wrapper">
      <el-input
        v-model="inputMessage"
        type="textarea"
        :autosize="{ minRows: 1, maxRows: 6 }"
        :placeholder="$t('chat.inputPlaceholder')"
        :disabled="isStreaming"
        ref="inputRef"
        @keydown="handleInputKeydown"
        @focus="handleFocus"
      />
      <el-button v-if="!isStreaming" type="primary" :icon="Promotion" circle class="send-button" :disabled="!inputMessage.trim()" @click="$emit('send')" />
      <el-button v-else type="danger" :icon="VideoPause" circle class="send-button" @click="$emit('stop')" />
    </div>
    <div v-if="contextStats" class="context-bar">
      <div class="context-bar-track">
        <div class="context-bar-fill" :class="contextClass" :style="{ width: contextStats.usagePercent + '%' }" />
      </div>
      <span class="context-bar-label">Context: {{ formatTokens(contextStats.totalTokens) }} / {{ formatTokens(contextStats.maxTokens) }} ({{ contextStats.usagePercent }}%)</span>
    </div>
  </el-footer>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, inject } from 'vue'
import { Promotion, VideoPause } from '@element-plus/icons-vue'

const props = defineProps<{
  modelValue: string
  isStreaming: boolean
  contextStats: any
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: string): void
  (e: 'send'): void
  (e: 'stop'): void
}>()

const isMobile = inject('isMobile', ref(false))
const inputRef = ref<any>(null)

const inputMessage = ref(props.modelValue)
watch(() => props.modelValue, (val) => { inputMessage.value = val })
watch(inputMessage, (val) => { emit('update:modelValue', val) })

const contextClass = computed(() => {
  const p = props.contextStats?.usagePercent || 0
  if (p < 50) return 'context-ok'
  if (p < 80) return 'context-warn'
  return 'context-danger'
})

import { computed } from 'vue'

const formatTokens = (tokens: number): string => {
  if (tokens >= 1000) return (tokens / 1000).toFixed(1) + 'K'
  return tokens.toString()
}

const handleInputKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    emit('send')
  }
}

const handleFocus = () => {
  if (isMobile.value) {
    nextTick(() => {
      setTimeout(() => {
        const textarea = inputRef.value?.$el?.querySelector('textarea')
        if (textarea) {
          textarea.scrollIntoView({ behavior: 'smooth', block: 'end' })
        }
      }, 300)
    })
  }
}
</script>

<style scoped>
.input-area {
  padding: 0 !important;
  background: var(--cc-bg-secondary, #f7f8fa);
  border-top: 1px solid var(--cc-border, #e8eaed);
}
.input-wrapper {
  max-width: 820px;
  margin: 0 auto;
  padding: 12px 24px;
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
.input-wrapper .el-textarea {
  flex: 1;
}
.input-wrapper .el-textarea :deep(.el-textarea__inner) {
  border-radius: 10px;
  border: 1px solid var(--cc-border, #e8eaed);
  background: var(--cc-bg-primary, #fff);
  padding: 10px 14px;
  font-size: 14px;
  resize: none;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}
.input-wrapper .el-textarea :deep(.el-textarea__inner):focus {
  border-color: var(--cc-accent, #3370ff);
  box-shadow: 0 0 0 2px rgba(51,112,255,0.15);
}
.send-button {
  width: 38px !important;
  height: 38px !important;
  border-radius: 10px !important;
}
.context-bar {
  max-width: 820px;
  margin: 0 auto;
  padding: 0 24px 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.context-bar-track {
  flex: 1;
  height: 3px;
  background: var(--cc-bg-tertiary, #eef0f4);
  border-radius: 2px;
  overflow: hidden;
}
.context-bar-fill { height: 100%; border-radius: 2px; transition: width 0.3s; }
.context-ok { background: var(--cc-success, #34c759); }
.context-warn { background: var(--cc-warning, #ff9500); }
.context-danger { background: var(--cc-danger, #ff3b30); }
.context-bar-label { font-size: 11px; color: var(--cc-text-muted, #8f959e); white-space: nowrap; }

@media (max-width: 767px) {
  .input-wrapper {
    padding: 8px 12px;
    padding-bottom: calc(8px + env(safe-area-inset-bottom, 0px));
  }
  .input-wrapper .el-textarea :deep(.el-textarea__inner) {
    font-size: 16px; /* Prevent iOS auto-zoom */
  }
  .send-button {
    width: 44px !important;
    height: 44px !important;
  }
  .context-bar {
    display: none; /* Hide context bar on mobile */
  }
}

/* ===== Dark Mode ===== */
:global(.dark) .input-wrapper .el-textarea :deep(.el-textarea__inner) {
  background: #1d1e1f;
  border-color: #363637;
  color: #e5eaf3;
}
:global(.dark) .input-wrapper .el-textarea :deep(.el-textarea__inner):focus {
  border-color: #3370ff;
  box-shadow: 0 0 0 2px rgba(51,112,255,0.2);
}
</style>
