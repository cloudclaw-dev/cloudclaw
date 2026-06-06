import { ref, watch } from 'vue'

const isDark = ref(false)

// Initialize from localStorage
const saved = localStorage.getItem('theme')
if (saved === 'dark') {
  isDark.value = true
  document.documentElement.classList.add('dark')
}

// Sync to DOM + localStorage
watch(isDark, (val) => {
  document.documentElement.classList.toggle('dark', val)
  localStorage.setItem('theme', val ? 'dark' : 'light')
})

export function useTheme() {
  const toggleDark = () => {
    isDark.value = !isDark.value
  }
  return { isDark, toggleDark }
}
