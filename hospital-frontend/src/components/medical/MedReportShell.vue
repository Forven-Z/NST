<script setup>
defineProps({
  hospitalTitle: { type: String, required: true },
  reportTitle: { type: String, default: '' },
  reportNo: { type: String, default: '—' },
})
</script>

<template>
  <div class="med-report-sheet">
    <div class="sheet-head">
      <div class="hospital">{{ hospitalTitle }}</div>
      <div class="meta-row">
        <span>{{ reportTitle }}</span>
        <span class="report-no">No. {{ reportNo }}</span>
      </div>
    </div>
    <slot />
  </div>
</template>

<style scoped>
@import './medReportSheet.css';
</style>
