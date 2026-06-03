<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import AiAssistantPanel from '../components/doctor/AiAssistantPanel.vue'

const router = useRouter()
const auth = useAuthStore()

const displayName = computed(() => auth.user?.realName || '医生')
const deptName = computed(() => auth.user?.deptName || '')

function logout() {
  auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="doctor-layout">
    <header class="topbar">
      <div class="topbar-left">
        <span class="logo">云脑门诊</span>
        <span class="doctor-info">{{ displayName }} · {{ deptName }}</span>
      </div>
      <el-button link type="danger" @click="logout">退出登录</el-button>
    </header>

    <div class="body">
      <main class="main-pane">
        <router-view />
      </main>
      <aside class="ai-pane">
        <AiAssistantPanel />
      </aside>
    </div>
  </div>
</template>

<style scoped>
.doctor-layout {
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
  color: #0f766e;
  margin-right: 16px;
}

.doctor-info {
  color: #475569;
  font-size: 14px;
}

.body {
  flex: 1;
  display: flex;
  min-height: 0;
}

.main-pane {
  flex: 7;
  padding: 16px;
  overflow: auto;
}

.ai-pane {
  flex: 3;
  min-width: 280px;
  border-left: 1px solid #e2e8f0;
  background: #fff;
}
</style>
