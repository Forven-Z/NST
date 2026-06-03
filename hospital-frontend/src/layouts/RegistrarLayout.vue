<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const displayName = computed(() => auth.user?.realName || '收费员')

function logout() {
  auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="registrar-layout">
    <header class="topbar">
      <div class="topbar-left">
        <span class="logo">云脑收费</span>
        <span class="staff-info">{{ displayName }}</span>
      </div>
      <el-button link type="danger" @click="logout">退出登录</el-button>
    </header>
    <main class="main-pane">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.registrar-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f1f5f9;
}
.topbar {
  height: 56px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
}
.logo {
  font-weight: 600;
  color: #0369a1;
  margin-right: 16px;
}
.staff-info {
  color: #475569;
  font-size: 14px;
}
.main-pane {
  flex: 1;
  padding: 16px;
  overflow: auto;
}
</style>
