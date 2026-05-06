<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useLocale } from '@/composables/useLocale'

const route = useRoute()
const { locale, setLocale, t } = useLocale()

const navItems = computed(() => [
  { path: '/', label: t('nav.dashboard') },
  { path: '/connections', label: t('nav.connections') },
  { path: '/ingestion', label: t('nav.ingestion') }
])
</script>

<template>
  <header class="app-header-shell">
    <div class="app-header">
      <router-link to="/" class="header-brand">
        <span class="brand-mark">
          <span class="brand-mark-core"></span>
        </span>
        <span class="brand-copy">
          <span class="brand-kicker">{{ t('brand.kicker') }}</span>
          <span class="brand-text">{{ t('brand.name') }}</span>
        </span>
      </router-link>

      <nav class="header-nav">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-link"
          :class="{ active: route.path === item.path }"
        >
          {{ item.label }}
        </router-link>
      </nav>

      <div class="header-meta">
        <div class="locale-switch" role="group" aria-label="language switcher">
          <button
            class="locale-btn"
            :class="{ active: locale === 'zh' }"
            @click="setLocale('zh')"
          >
            {{ t('locale.zh') }}
          </button>
          <button
            class="locale-btn"
            :class="{ active: locale === 'en' }"
            @click="setLocale('en')"
          >
            {{ t('locale.en') }}
          </button>
        </div>
        <span class="meta-pill">{{ t('brand.meta') }}</span>
      </div>
    </div>
  </header>
</template>

<style scoped>
.app-header-shell {
  position: sticky;
  top: 0;
  z-index: 20;
  padding: 18px 32px 0;
  max-width: 1744px;
  margin: 0 auto;
  width: 100%;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  min-height: 74px;
  padding: 14px 18px;
  border: 1px solid rgba(255, 255, 255, 0.07);
  border-radius: 999px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.06), rgba(255, 255, 255, 0.02)),
    rgba(8, 17, 27, 0.72);
  backdrop-filter: blur(24px);
  box-shadow: var(--shadow-sm);
}

.header-brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  text-decoration: none;
}

.brand-mark {
  position: relative;
  width: 38px;
  height: 38px;
  border-radius: 14px;
  border: 1px solid rgba(225, 191, 120, 0.22);
  background: linear-gradient(145deg, rgba(225, 191, 120, 0.18), rgba(123, 207, 255, 0.12));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

.brand-mark::before,
.brand-mark::after {
  content: '';
  position: absolute;
  inset: 8px;
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.brand-mark::after {
  inset: 14px;
  border-radius: 6px;
  border-color: rgba(225, 191, 120, 0.32);
}

.brand-mark-core {
  position: absolute;
  inset: 50%;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-primary-hover);
  transform: translate(-50%, -50%);
  box-shadow: 0 0 18px rgba(225, 191, 120, 0.48);
}

.brand-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.brand-kicker {
  color: var(--color-text-muted);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.brand-text {
  color: var(--color-text);
  font-family: var(--font-display);
  font-size: 21px;
  font-weight: 700;
  letter-spacing: -0.03em;
}

.header-nav {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.nav-link {
  padding: 9px 16px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
  text-decoration: none;
  transition:
    color var(--duration-fast) var(--ease-out),
    background var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out),
    box-shadow var(--duration-fast) var(--ease-out);
}

.nav-link:hover {
  color: var(--color-text);
  background: rgba(255, 255, 255, 0.04);
  transform: translateY(-1px);
}

.nav-link.active {
  color: #fff8eb;
  background: linear-gradient(135deg, rgba(241, 215, 165, 0.2), rgba(123, 207, 255, 0.06));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

.header-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.locale-switch {
  display: inline-flex;
  align-items: center;
  padding: 4px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.06);
  background: rgba(255, 255, 255, 0.03);
}

.locale-btn {
  padding: 8px 12px;
  border-radius: 999px;
  color: var(--color-text-secondary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  cursor: pointer;
  transition:
    color var(--duration-fast) var(--ease-out),
    background var(--duration-fast) var(--ease-out);
}

.locale-btn.active {
  color: #fff8eb;
  background: linear-gradient(135deg, rgba(241, 215, 165, 0.2), rgba(123, 207, 255, 0.06));
}

.meta-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 999px;
  color: var(--color-text-secondary);
  font-size: 12px;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.meta-pill::before {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-success);
  box-shadow: 0 0 12px rgba(95, 208, 157, 0.6);
}

@media (max-width: 980px) {
  .app-header {
    border-radius: var(--radius-lg);
    flex-wrap: wrap;
  }

  .header-nav {
    order: 3;
    width: 100%;
    justify-content: space-between;
  }
}

@media (max-width: 640px) {
  .app-header-shell {
    padding: 14px 14px 0;
  }

  .header-nav {
    gap: 6px;
    overflow-x: auto;
    justify-content: start;
  }

  .nav-link {
    white-space: nowrap;
  }

  .header-meta {
    width: 100%;
    flex-direction: column;
    align-items: stretch;
  }

  .meta-pill {
    width: 100%;
    justify-content: center;
  }

  .locale-switch {
    width: 100%;
    justify-content: center;
  }
}
</style>
