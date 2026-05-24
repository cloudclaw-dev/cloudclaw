<template>
  <router-view />
</template>

<script setup lang="ts">
import { onErrorCaptured } from 'vue'

onErrorCaptured((err, instance, info) => {
  console.error('[CloudClaw] Uncaught error:', err, info)
  // Return false to prevent the error from propagating further
  return false
})
</script>
