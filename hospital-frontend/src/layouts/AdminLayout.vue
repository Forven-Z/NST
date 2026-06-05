<script setup>
import StaffShell from '../components/layout/StaffShell.vue'

const menuItems = [
  { path: '/admin/dict', label: '基础字典' },
  { path: '/admin/scheduling', label: '排班维护' },
]
</script>

<template>
  <StaffShell title="云脑管理" accent="#475569" :menu-items="menuItems">
    <router-view />
  </StaffShell>
</template>
