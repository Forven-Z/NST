<script setup>
import StaffShell from '../components/layout/StaffShell.vue'

const menuItems = [{ path: '/pacs/queue', label: '检查队列' }]
</script>

<template>
  <StaffShell title="云脑检查科" accent="#0891b2" :menu-items="menuItems">
    <router-view />
  </StaffShell>
</template>
