<script setup>
import StaffShell from '../components/layout/StaffShell.vue'

const menuItems = [{ path: '/lis/queue', label: '检验队列' }]
</script>

<template>
  <StaffShell title="云脑检验科" accent="#2563eb" :menu-items="menuItems">
    <router-view />
  </StaffShell>
</template>
