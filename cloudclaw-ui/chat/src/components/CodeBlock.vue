<template>
  <div class="code-block-wrapper" :class="{ dark: isDark }">
    <div class="code-block-header">
      <span class="code-lang">{{ language || 'text' }}</span>
      <button class="code-copy-btn" @click="handleCopy" :title="copied ? t('chat.copied') : t('chat.copy')">
        <template v-if="!copied">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
        </template>
        <template v-else>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="20 6 9 17 4 12"/></svg>
        </template>
      </button>
    </div>
    <div class="code-block-body" :class="{ collapsed: isCollapsed }">
      <pre class="code-block-pre" ref="codeRef"><code v-html="highlightedCode"></code></pre>
    </div>
    <div v-if="canCollapse" class="code-block-footer">
      <button class="code-expand-btn" @click="isCollapsed = !isCollapsed">
        {{ isCollapsed ? t('chat.expandCode') : t('collapseCode') }}
        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ rotated: !isCollapsed }"><polyline points="6 9 12 15 18 9"/></svg>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, inject } from 'vue'
import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import python from 'highlight.js/lib/languages/python'
import java from 'highlight.js/lib/languages/java'
import bash from 'highlight.js/lib/languages/bash'
import sql from 'highlight.js/lib/languages/sql'
import json from 'highlight.js/lib/languages/json'
import yaml from 'highlight.js/lib/languages/yaml'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import plaintext from 'highlight.js/lib/languages/plaintext'
import { useI18n } from 'vue-i18n'

hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('js', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('ts', typescript)
hljs.registerLanguage('python', python)
hljs.registerLanguage('py', python)
hljs.registerLanguage('java', java)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('shell', bash)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('json', json)
hljs.registerLanguage('yaml', yaml)
hljs.registerLanguage('yml', yaml)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('css', css)
hljs.registerLanguage('plaintext', plaintext)
hljs.registerLanguage('text', plaintext)

const props = defineProps<{
  code: string
  language?: string
}>()

const { t } = useI18n()
const isDark = inject('isDark', ref(false))
const copied = ref(false)
const isCollapsed = ref(false)
const codeRef = ref<HTMLElement | null>(null)

const COLLAPSE_THRESHOLD = 30

const lineCount = computed(() => props.code.split('\n').length)
const canCollapse = computed(() => lineCount.value > COLLAPSE_THRESHOLD)

// Default collapsed if over threshold
if (lineCount.value > COLLAPSE_THRESHOLD) {
  isCollapsed.value = true
}

const highlightedCode = computed(() => {
  const lang = props.language || ''
  if (lang && hljs.getLanguage(lang)) {
    try {
      return hljs.highlight(props.code, { language: lang, ignoreIllegals: true }).value
    } catch (_) { /* fallback */ }
  }
  // Try auto-detect
  try {
    return hljs.highlightAuto(props.code).value
  } catch (_) { /* fallback */ }
  // Escape HTML
  return props.code.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
})

const handleCopy = async () => {
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(props.code)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = props.code
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
</script>

<style scoped>
.code-block-wrapper {
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--cc-border, #e8eaed);
  margin: 8px 0;
  background: #f6f8fa;
}
.code-block-wrapper.dark {
  background: #161b22;
  border-color: #30363d;
}
.code-block-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background: rgba(0,0,0,0.04);
  border-bottom: 1px solid var(--cc-border, #e8eaed);
}
.dark .code-block-header {
  background: rgba(255,255,255,0.04);
  border-bottom-color: #30363d;
}
.code-lang {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--cc-text-muted, #8f959e);
  letter-spacing: 0.5px;
}
.code-copy-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: var(--cc-text-muted, #8f959e);
  padding: 4px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  transition: all 0.15s;
}
.code-copy-btn:hover {
  color: var(--cc-text-primary, #1f2329);
  background: rgba(0,0,0,0.06);
}
.dark .code-copy-btn:hover {
  color: #e5eaf3;
  background: rgba(255,255,255,0.08);
}
.code-block-body {
  overflow-x: auto;
  transition: max-height 0.3s ease;
}
.code-block-body.collapsed {
  max-height: 480px;
  overflow: hidden;
  position: relative;
}
.code-block-body.collapsed::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 60px;
  background: linear-gradient(transparent, #f6f8fa);
  pointer-events: none;
}
.dark .code-block-body.collapsed::after {
  background: linear-gradient(transparent, #161b22);
}
.code-block-pre {
  margin: 0;
  padding: 12px 16px;
  font-size: 13px;
  line-height: 1.5;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  counter-reset: line;
  overflow-x: auto;
}
.code-block-pre code {
  font-family: inherit;
}
.code-block-footer {
  display: flex;
  justify-content: center;
  border-top: 1px solid var(--cc-border, #e8eaed);
  padding: 4px 0;
}
.dark .code-block-footer {
  border-top-color: #30363d;
}
.code-expand-btn {
  background: none;
  border: none;
  cursor: pointer;
  color: var(--cc-accent, #3370ff);
  font-size: 12px;
  padding: 4px 12px;
  display: flex;
  align-items: center;
  gap: 4px;
  transition: opacity 0.15s;
}
.code-expand-btn:hover {
  opacity: 0.8;
}
.code-expand-btn svg.rotated {
  transform: rotate(180deg);
}
</style>
