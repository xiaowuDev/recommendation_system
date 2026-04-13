<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ data: unknown }>()
const formatted = computed(() => {
  try {
    return JSON.stringify(props.data, null, 2)
  } catch {
    return String(props.data)
  }
})
</script>

<template>
  <pre class="json-viewer"><code>{{ formatted }}</code></pre>
</template>

<style scoped>
.json-viewer {
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 12px 16px;
  font-size: 12px;
  line-height: 1.5;
  overflow-x: auto;
  color: var(--color-text-secondary);
  max-height: 300px;
  overflow-y: auto;
}
</style>
