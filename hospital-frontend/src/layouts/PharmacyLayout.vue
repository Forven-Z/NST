<script setup>
import StaffShell from '../components/layout/StaffShell.vue'

const menuItems = [
  { path: '/pharmacy/pending', label: '待发药' },
  { path: '/pharmacy/drugs', label: '药品管理' },
  { path: '/pharmacy/my-schedules', label: '我的排班' },
]
</script>

<template>
  <StaffShell title="云脑药房" accent="#7c3aed" :menu-items="menuItems">
    <router-view />
  </StaffShell>
</template>
