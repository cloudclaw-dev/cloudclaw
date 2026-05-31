<template>
  <div class="skeleton-screen">
    <div v-for="i in count" :key="i" class="skeleton-item" :class="{ 'skeleton-message': type === 'message', 'skeleton-session': type === 'session' }">
      <div v-if="type === 'message'" class="skeleton-msg-layout">
        <div class="skeleton-avatar" />
        <div class="skeleton-lines">
          <div class="skeleton-line" :style="{ width: getWidth(i) }" />
          <div class="skeleton-line short" :style="{ width: getShortWidth(i) }" />
        </div>
      </div>
      <div v-else class="skeleton-session-layout">
        <div class="skeleton-line" :style="{ width: getWidth(i) }" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  type: 'message' | 'session'
  count?: number
}>()

const getWidth = (i: number) => (60 + (i * 7) % 30) + '%'
const getShortWidth = (i: number) => (30 + (i * 13) % 40) + '%'
</script>

<style scoped>
.skeleton-screen {
  padding: 8px;
}
.skeleton-item {
  margin-bottom: 16px;
}
.skeleton-msg-layout {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.skeleton-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--cc-bg-tertiary, #eef0f4);
  flex-shrink: 0;
  animation: skeleton-pulse 1.5s ease-in-out infinite;
}
.skeleton-lines {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.skeleton-line {
  height: 14px;
  border-radius: 4px;
  background: var(--cc-bg-tertiary, #eef0f4);
  animation: skeleton-pulse 1.5s ease-in-out infinite;
}
.skeleton-line.short {
  height: 12px;
}
.skeleton-session-layout {
  padding: 10px 12px;
}
@keyframes skeleton-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}
</style>
