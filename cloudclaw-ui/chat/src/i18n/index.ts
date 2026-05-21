import { createI18n } from 'vue-i18n'
import zh from './zh'
import en from './en'

const saved = localStorage.getItem('cloudclaw-locale')
const browserLang = navigator.language.startsWith('zh') ? 'zh' : 'en'

const i18n = createI18n({
  legacy: false,
  locale: saved || browserLang,
  fallbackLocale: 'zh',
  messages: { zh, en }
})

export default i18n
