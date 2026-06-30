<script setup>
defineProps({
  footer: { type: Object, default: () => ({}) },
  /** lab | check | disposal */
  variant: { type: String, default: 'lab' },
  disclaimer: { type: String, default: '' },
})
</script>

<template>
  <div class="sheet-footer">
    <div v-if="variant === 'lab'">
      执行：{{ footer.executeTime || '—' }} · 报告：{{ footer.reportTime || '—' }}
    </div>
    <div v-else-if="variant === 'check'">
      检查：{{ footer.examTime || footer.executeTime || '—' }} · 报告：{{ footer.reportTime || '—' }}
    </div>
    <div v-else>
      执行：{{ footer.executeTime || '—' }} · 记录：{{ footer.recordTime || footer.reportTime || '—' }}
    </div>
    <div v-if="variant === 'lab'">
      送检：{{ footer.orderingDoctorName || '—' }}
      · 检验：{{ footer.testerName || '—' }}
      · 报告：{{ footer.reporterName || footer.testerName || '—' }}
      · 审核：{{ footer.reviewerName || '待审核' }}
    </div>
    <div v-else-if="variant === 'check'">
      送检：{{ footer.orderingDoctorName || '—' }}
      · 检查：{{ footer.executorName || '—' }}
      · 报告：{{ footer.reporterName || '—' }}
      · 审核：{{ footer.reviewerName || '待审核' }}
    </div>
    <div v-else>
      开立：{{ footer.orderingDoctorName || '—' }}
      · 执行：{{ footer.executorName || '—' }}
      · 记录：{{ footer.recorderName || '—' }}
      · 审核：{{ footer.reviewerName || '待审核' }}
    </div>
    <div v-if="disclaimer" class="disclaimer">{{ disclaimer }}</div>
  </div>
</template>

<style scoped>
@import './medReportSheet.css';
</style>
