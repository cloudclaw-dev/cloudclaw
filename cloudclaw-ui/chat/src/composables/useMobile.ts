import { ref, onMounted, onUnmounted } from 'vue'

const isMobile = ref(false)

const checkMobile = () => {
  isMobile.value = window.innerWidth < 768
}

let listeners = 0

export function useMobile() {
  listeners++
  if (listeners === 1) {
    checkMobile()
    window.addEventListener('resize', checkMobile)
  }

  onUnmounted(() => {
    listeners--
    if (listeners === 0) {
      window.removeEventListener('resize', checkMobile)
    }
  })

  return { isMobile }
}
