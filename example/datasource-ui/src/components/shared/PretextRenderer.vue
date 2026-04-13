<script setup lang="ts">
import { toRef } from 'vue'
import { usePretext } from '@/composables/usePretext'

const props = defineProps<{ source: string }>()
const { html } = usePretext(toRef(props, 'source'))
</script>

<template>
  <div class="pretext-prose" v-html="html" />
</template>
