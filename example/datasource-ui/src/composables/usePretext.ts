import { computed, type Ref } from 'vue'
import pretext from 'pretext'

export function usePretext(source: Ref<string>) {
  const html = computed(() => {
    if (!source.value) return ''
    try {
      return pretext(source.value)
    } catch {
      return source.value
    }
  })
  return { html }
}
