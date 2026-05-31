<template>
  <div class="message-row" :class="[msg.role, { editing: isEditing, dark: isDark }]">
    <template v-if="msg.role === 'user'">
      <div class="message-content user-msg-content">
        <div class="message-meta">You &middot; {{ formatTime(msg.createdAt) }}</div>
        <template v-if="!isEditing">
          <div class="message-text">{{ msg.content }}</div>
          <div class="message-actions user-actions">
            <button class="msg-action-btn" @click="startEdit" :title="t('chat.editMessage')">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
            </button>
          </div>
        </template>
        <template v-else>
          <div class="edit-area">
            <textarea ref="editTextarea" v-model="editContent" class="edit-textarea" rows="3" @keydown="handleEditKeydown" />
            <div class="edit-actions">
              <button class="edit-btn edit-save" @click="submitEdit">{{ t('common.submit') }}</button>
              <button class="edit-btn edit-cancel" @click="cancelEdit">{{ t('common.cancel') }}</button>
            </div>
          </div>
        </template>
      </div>
      <div class="message-avatar">
        <el-avatar :size="32" :icon="UserFilled" />
      </div>
    </template>
    <template v-else>
      <div class="message-avatar">
        <el-avatar :size="32" class="assistant-avatar"><el-icon><Monitor /></el-icon></el-avatar>
      </div>
      <div class="message-content">
        <div class="message-meta">
          Assistant
          <span v-if="msg.agentName" class="agent-label"> &middot; {{ msg.agentName }}</span>
          &middot; {{ formatTime(msg.createdAt) }}
        </div>
        <template v-if="msg.segments && msg.segments.length > 0">
          <template v-for="(seg, si) in msg.segments" :key="si">
            <div v-if="seg.type === 'tool_call'" class="tool-call-card">
              <div class="tool-call-header">
                <el-icon class="tool-icon"><SetUp /></el-icon>
                <span class="tool-name">{{ seg.toolName }}</span>
              </div>
              <pre v-if="seg.content" class="tool-args">{{ formatToolArgs(seg.content) }}</pre>
            </div>
            <div v-else-if="seg.type === 'tool_result'" class="tool-result-card">
              <div class="tool-result-header">
                <el-icon class="tool-icon"><Finished /></el-icon>
                <span>{{ seg.toolName }} result</span>
              </div>
              <pre class="tool-result-content">{{ seg.content }}</pre>
            </div>
            <div v-else class="message-text markdown-body" v-html="renderMarkdown(seg.content)" />
          </template>
        </template>
        <div v-else class="message-text markdown-body" v-html="renderMarkdown(msg.content)" />
        <div class="message-footer">
          <div class="message-actions assistant-actions">
            <button class="msg-action-btn" @click="handleCopy" :title="copied ? t('chat.copied') : t('chat.copy')">
              <template v-if="!copied">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
              </template>
              <template v-else>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
              </template>
              <span class="action-label">{{ copied ? t('chat.copied') : t('chat.copy') }}</span>
            </button>
            <button class="msg-action-btn" @click="$emit('regenerate', index)" :title="t('chat.regenerate')">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
              <span class="action-label">{{ t('chat.regenerate') }}</span>
            </button>
          </div>
          <span class="message-time">{{ formatTime(msg.createdAt) }}</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, inject } from 'vue'
import { Monitor, UserFilled, SetUp, Finished } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import { useI18n } from 'vue-i18n'

interface MessageSegment {
  type: 'text' | 'tool_call' | 'tool_result' | 'workflow_status'
  content: string
  toolName?: string
}

interface Message {
  role: 'user' | 'assistant'
  content: string
  segments?: MessageSegment[]
  createdAt?: string
  agentName?: string
}

const props = defineProps<{
  msg: Message
  index: number
  md: MarkdownIt
}>()

const emit = defineEmits<{
  (e: 'regenerate', index: number): void
  (e: 'edit', index: number, content: string): void
}>()

const { t } = useI18n()
const isDark = inject('isDark', ref(false))
const copied = ref(false)
const isEditing = ref(false)
const editContent = ref('')
const editTextarea = ref<HTMLTextAreaElement | null>(null)

const renderMarkdown = (content: string): string => {
  if (!content) return ''
  return props.md.render(content)
}

const formatToolArgs = (args: string): string => {
  try {
    const parsed = JSON.parse(args)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return args
  }
}

const formatTime = (dateStr?: string): string => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  if (isNaN(d.getTime())) return ''
  const mo = (d.getMonth() + 1).toString().padStart(2, '0')
  const day = d.getDate().toString().padStart(2, '0')
  const h = d.getHours().toString().padStart(2, '0')
  const m = d.getMinutes().toString().padStart(2, '0')
  return mo + '-' + day + ' ' + h + ':' + m
}

const handleCopy = async () => {
  const text = props.msg.content
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = text
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
    copied.value = true
    setTimeout(() => { copied.value = false }, 2000)
  } catch (e) {
    // fallback failed
  }
}

const startEdit = () => {
  isEditing.value = true
  editContent.value = props.msg.content
  nextTick(() => {
    editTextarea.value?.focus()
  })
}

const cancelEdit = () => {
  isEditing.value = false
}

const submitEdit = () => {
  if (editContent.value.trim()) {
    emit('edit', props.index, editContent.value.trim())
    isEditing.value = false
  }
}

const handleEditKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    submitEdit()
  } else if (e.key === 'Escape') {
    cancelEdit()
  }
}
</script>

<style scoped>
.message-row {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  align-items: flex-start;
}
.message-row.user {
  flex-direction: row;
  justify-content: flex-end;
}
.message-avatar { flex-shrink: 0; margin-top: 2px; }
.assistant-avatar { background: var(--cc-accent, #3370ff) !important; }
.message-content {
  flex: 1;
  min-width: 0;
  max-width: 720px;
}
.message-row.user .message-content {
  flex: none;
  max-width: 520px;
  width: fit-content;
  background: #e8f0ff;
  padding: 10px 14px;
  border-radius: 12px 12px 4px 12px;
}
.dark .message-row.user .message-content {
  background: #1a2a44;
}
.message-row.user .message-content .message-meta {
  color: rgba(0,0,0,0.45);
}
.dark .message-row.user .message-content .message-meta {
  color: rgba(255,255,255,0.5);
}
.message-meta {
  font-size: 11px;
  color: var(--cc-text-muted, #8f959e);
  margin-bottom: 4px;
}
.agent-label {
  color: var(--cc-accent, #3370ff);
  font-weight: 500;
}
.message-text {
  font-size: 14px;
  line-height: 1.7;
  color: var(--cc-text-primary, #1f2329);
  word-break: break-word;
}
.message-actions {
  display: flex;
  gap: 4px;
  margin-top: 4px;
  opacity: 0;
  transition: opacity 0.15s;
}
.message-row:hover .message-actions {
  opacity: 1;
}
.msg-action-btn {
  background: none;
  border: 1px solid transparent;
  cursor: pointer;
  color: var(--cc-text-muted, #8f959e);
  padding: 3px 8px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  transition: all 0.15s;
}
.msg-action-btn:hover {
  color: var(--cc-text-primary, #1f2329);
  background: var(--cc-bg-tertiary, #eef0f4);
  border-color: var(--cc-border, #e8eaed);
}
.dark .msg-action-btn:hover {
  color: #e5eaf3;
  background: #363637;
  border-color: #363637;
}
.action-label {
  font-size: 11px;
}
.message-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.message-time {
  font-size: 11px;
  color: var(--cc-text-muted, #8f959e);
}
.edit-area {
  margin-top: 4px;
}
.edit-textarea {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--cc-accent, #3370ff);
  border-radius: 6px;
  background: var(--cc-bg-primary, #fff);
  color: var(--cc-text-primary, #1f2329);
  font-size: 14px;
  font-family: inherit;
  resize: vertical;
  outline: none;
  box-sizing: border-box;
  line-height: 1.5;
}
.edit-actions {
  display: flex;
  gap: 6px;
  margin-top: 6px;
  justify-content: flex-end;
}
.edit-btn {
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  border: none;
  transition: opacity 0.15s;
}
.edit-btn:hover { opacity: 0.85; }
.edit-save {
  background: var(--cc-accent, #3370ff);
  color: #fff;
}
.edit-cancel {
  background: var(--cc-bg-tertiary, #eef0f4);
  color: var(--cc-text-secondary, #646a73);
}
.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3) {
  margin: 16px 0 8px;
  font-weight: 600;
}
.markdown-body :deep(p) { margin: 6px 0; }
.markdown-body :deep(pre) {
  background: var(--cc-bg-tertiary, #eef0f4);
  border-radius: 6px;
  padding: 12px 16px;
  overflow-x: auto;
  margin: 8px 0;
  font-size: 13px;
  line-height: 1.5;
}
.markdown-body :deep(code) {
  background: var(--cc-bg-tertiary, #eef0f4);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
}
.markdown-body :deep(pre code) { background: none; padding: 0; }
.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 8px 0;
}
.markdown-body :deep(th), .markdown-body :deep(td) {
  border: 1px solid var(--cc-border, #e8eaed);
  padding: 8px 12px;
  text-align: left;
}
.markdown-body :deep(th) { background: var(--cc-bg-tertiary, #eef0f4); font-weight: 600; }
.tool-call-card {
  background: #fff8e6;
  border: 1px solid #ffe7a0;
  border-radius: 6px;
  margin: 6px 0;
  overflow: hidden;
}
.dark .tool-call-card { background: #2a2518; border-color: #4a3f28; }
.tool-call-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  background: rgba(255,150,0,0.08);
}
.tool-icon { color: var(--cc-accent, #3370ff); }
.tool-name { color: var(--cc-text-primary, #1f2329); }
.tool-args {
  margin: 0;
  padding: 8px 12px;
  font-size: 12px;
  line-height: 1.4;
  max-height: 200px;
  overflow-y: auto;
  color: var(--cc-text-secondary, #646a73);
}
.tool-result-card {
  background: #e8f5e9;
  border: 1px solid #a5d6a7;
  border-radius: 6px;
  margin: 6px 0;
  overflow: hidden;
}
.dark .tool-result-card { background: #1a2a1c; border-color: #2a4a2c; }
.tool-result-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 600;
  background: rgba(52,199,89,0.08);
}
.tool-result-content {
  margin: 0;
  padding: 8px 12px;
  font-size: 12px;
  line-height: 1.4;
  max-height: 200px;
  overflow-y: auto;
  color: var(--cc-text-secondary, #646a73);
}
</style>
