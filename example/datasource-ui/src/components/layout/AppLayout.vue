<script setup lang="ts">
import AppHeader from "./AppHeader.vue";
</script>

<template>
  <div class="app-layout">
    <div class="ambient ambient-a"></div>
    <div class="ambient ambient-b"></div>
    <div class="ambient ambient-c"></div>
    <AppHeader />
    <main class="app-main">
      <div class="content-shell">
        <router-view v-slot="{ Component, route }">
          <transition name="page-slide" mode="out-in">
            <component :is="Component" :key="route.fullPath" />
          </transition>
        </router-view>
      </div>
    </main>
  </div>
</template>

<style scoped>
.app-layout {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  isolation: isolate;
}

.ambient {
  position: fixed;
  inset: auto;
  pointer-events: none;
  filter: blur(64px);
  opacity: 0.55;
  z-index: -1;
}

.ambient-a {
  width: 320px;
  height: 320px;
  top: 88px;
  left: -80px;
  background: rgba(123, 207, 255, 0.12);
}

.ambient-b {
  width: 420px;
  height: 420px;
  right: -110px;
  top: 140px;
  background: rgba(225, 191, 120, 0.12);
}

.ambient-c {
  width: 360px;
  height: 360px;
  left: 28%;
  bottom: -160px;
  background: rgba(118, 211, 156, 0.08);
}

.app-main {
  flex: 1;
  width: 100%;
  padding: 24px 32px 36px;
}

.content-shell {
  max-width: 1680px;
  margin: 0 auto;
}

@media (max-width: 1920px) {
  .app-main {
    padding: 24px 28px 36px;
  }
}

@media (max-width: 640px) {
  .app-main {
    padding: 20px 14px 28px;
  }
}
</style>
