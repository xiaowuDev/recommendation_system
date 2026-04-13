<script setup lang="ts">
defineProps<{ text?: string }>()
</script>

<template>
  <div class="spinner-wrap">
    <div class="spinner">
      <span class="spinner-ring"></span>
      <span class="spinner-core"></span>
    </div>
    <span v-if="text" class="spinner-text">{{ text }}</span>
  </div>
</template>

<style scoped>
.spinner-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
}
.spinner {
  position: relative;
  width: 22px;
  height: 22px;
}

.spinner-ring,
.spinner-core {
  position: absolute;
  inset: 0;
  border-radius: 50%;
}

.spinner-ring {
  border: 2px solid rgba(255, 255, 255, 0.08);
  border-top-color: var(--color-primary);
  animation: spin 0.9s linear infinite;
}

.spinner-core {
  inset: 6px;
  background: radial-gradient(circle, var(--color-primary-hover), rgba(225, 191, 120, 0.2));
  box-shadow: 0 0 20px rgba(225, 191, 120, 0.28);
  animation: pulse 1.2s var(--ease-smooth) infinite;
}

.spinner-text {
  color: var(--color-text-secondary);
  font-size: 13px;
}

@keyframes spin { to { transform: rotate(360deg); } }

@keyframes pulse {
  0%, 100% { transform: scale(0.9); opacity: 0.8; }
  50% { transform: scale(1.05); opacity: 1; }
}
</style>
