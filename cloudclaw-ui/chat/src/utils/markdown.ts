import MarkdownIt from 'markdown-it'
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
import html from 'highlight.js/lib/languages/xml'
import plaintext from 'highlight.js/lib/languages/plaintext'
import 'highlight.js/styles/github.css'
import 'highlight.js/styles/github-dark.css'

// Register languages
const langs: [string, any][] = [
  ['javascript', javascript], ['js', javascript],
  ['typescript', typescript], ['ts', typescript],
  ['python', python], ['py', python],
  ['java', java],
  ['bash', bash], ['shell', bash],
  ['sql', sql],
  ['json', json],
  ['yaml', yaml], ['yml', yaml],
  ['xml', xml], ['html', html],
  ['css', css],
  ['plaintext', plaintext], ['text', plaintext],
]
langs.forEach(([name, mod]) => hljs.registerLanguage(name, mod))

// Create markdown renderer
const md = new MarkdownIt({
  html: false, linkify: true, typographer: true,
  highlight(str: string, lang: string) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        const highlighted = hljs.highlight(str, { language: lang, ignoreIllegals: true }).value
        return `<pre class="hljs" data-lang="${lang}"><code data-code="${lang}">${highlighted}</code></pre>`
      } catch (_) { /* fallback */ }
    }
    return '<pre class="hljs"><code>' + md.utils.escapeHtml(str) + '</code></pre>'
  }
})

export const renderMarkdown = (content: string): string => content ? md.render(content) : ''

export { md }
