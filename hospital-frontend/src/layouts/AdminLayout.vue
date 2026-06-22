<script setup>
import StaffShell from '../components/layout/StaffShell.vue'

const menuItems = [
  { path: '/admin/departments', label: '科室管理' },
  { path: '/admin/employees', label: '员工管理' },
  { path: '/admin/dict', label: '基础字典' },
  { path: '/admin/scheduling', label: '排班维护' },
  { path: '/admin/finance', label: '财务汇总' },
]
</script>

<template>
  <StaffShell title="云脑管理" accent="#475569" :menu-items="menuItems">
    <router-view />
  </StaffShell>
</template>
