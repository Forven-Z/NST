<script setup>
import StaffShell from '../components/layout/StaffShell.vue'

const menuItems = [
  { path: '/registrar/register', label: '窗口挂号' },
  { path: '/registrar/charge', label: '窗口收费' },
  { path: '/registrar/refund', label: '退费退号' },
]
</script>

<template>
  <StaffShell title="云脑挂号收费" accent="#ea580c" :menu-items="menuItems">
    <router-view />
  </StaffShell>
</template>
