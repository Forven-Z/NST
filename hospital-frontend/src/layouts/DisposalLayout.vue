<script setup>
import StaffShell from '../components/layout/StaffShell.vue'

const menuItems = [{ path: '/disposal/queue', label: '处置队列' }]
</script>

<template>
  <StaffShell title="云脑处置科" accent="#c026d3" :menu-items="menuItems">
    <router-view />
  </StaffShell>
</template>
