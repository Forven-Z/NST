<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'

const props = defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '智慧云脑诊疗平台 · HIS' },
  accent: { type: String, default: '#0f766e' },
  menuItems: {
    type: Array,
    default: () => [],
  },
})

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const displayName = computed(() => auth.user?.realName || '职员')
const deptName = computed(() => auth.user?.deptName || '')
const nowText = computed(() => {
  const d = new Date()
  return d.toLocaleString('zh-CN', { hour12: false })
})

function logout() {
  auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="staff-shell">
    <header class="topbar" :style="{ borderBottomColor: accent }">
      <div class="topbar-left">
        <div class="brand">
          <span class="logo" :style="{ color: accent }">{{ title }}</span>
          <span class="subtitle">{{ subtitle }}</span>
        </div>
        <span class="staff-info">{{ displayName }}<template v-if="deptName"> · {{ deptName }}</template></span>
      </div>
      <div class="topbar-right">
        <span class="clock">{{ nowText }}</span>
        <el-button link type="danger" @click="logout">退出</el-button>
      </div>
    </header>

    <div class="body">
      <aside v-if="menuItems.length" class="sidebar">
        <div class="sidebar-title">功能菜单</div>
        <el-menu
          :default-active="route.path"
          router
          class="side-menu"
          :background-color="'#1e293b'"
          :text-color="'#cbd5e1'"
          :active-text-color="'#ffffff'"
        >
          <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
            {{ item.label }}
          </el-menu-item>
        </el-menu>
      </aside>
      <main class="main-pane">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.staff-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #eef2f6;
}

.topbar {
  height: 56px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 3px solid #0f766e;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 20px;
}

.brand {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.logo {
  font-weight: 700;
  font-size: 16px;
}

.subtitle {
  font-size: 11px;
  color: #94a3b8;
}

.staff-info {
  color: #475569;
  font-size: 14px;
  padding-left: 20px;
  border-left: 1px solid #e2e8f0;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.clock {
  font-size: 13px;
  color: #64748b;
  font-variant-numeric: tabular-nums;
}

.body {
  flex: 1;
  display: flex;
  min-height: 0;
}

.sidebar {
  width: 208px;
  background: #1e293b;
  flex-shrink: 0;
}

.sidebar-title {
  padding: 14px 16px 8px;
  font-size: 11px;
  letter-spacing: 0.08em;
  color: #64748b;
  text-transform: uppercase;
}

.side-menu {
  border-right: none;
}

.main-pane {
  flex: 1;
  padding: 20px;
  overflow: auto;
}
</style>
