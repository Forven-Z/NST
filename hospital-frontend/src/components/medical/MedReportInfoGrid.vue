<script setup>
import { computed } from 'vue'

const props = defineProps({
  header: { type: Object, default: () => ({}) },
  /** lab | check | disposal */
  variant: { type: String, default: 'lab' },
})

const h = computed(() => props.header || {})
const remark = computed(() => h.value.orderRemark || h.value.remark || '')
</script>

<template>
  <div class="info-grid">
    <div><span class="label">姓名</span>{{ h.patientName || '—' }}</div>
    <div><span class="label">性别</span>{{ h.genderLabel || '—' }}</div>
    <div><span class="label">年龄</span>{{ h.ageLabel || '—' }}</div>
    <div><span class="label">病案号</span>{{ h.medicalRecordNo || '—' }}</div>
    <div v-if="variant === 'lab'"><span class="label">标本</span>{{ h.sampleType || '—' }}</div>
    <div v-else-if="variant === 'check'"><span class="label">模态</span>{{ h.modality || 'CT' }}</div>
    <div v-else><span class="label">处置项目</span>{{ h.itemName || '—' }}</div>
    <div><span class="label">送检科室</span>{{ h.department || '—' }}</div>
    <div v-if="variant === 'check'"><span class="label">检查部位</span>{{ h.bodyPart || '—' }}</div>
    <div v-if="variant === 'disposal' && h.bodyPart"><span class="label">部位</span>{{ h.bodyPart }}</div>
    <div class="span-3"><span class="label">临床诊断</span>{{ h.clinicalDiagnosis || '—' }}</div>
    <div v-if="h.purpose" class="span-3">
      <span class="label">{{ variant === 'lab' ? '检验目的' : variant === 'check' ? '检查目的' : '处置目的' }}</span>{{ h.purpose }}
    </div>
    <div v-if="remark" class="span-3"><span class="label">备注</span>{{ remark }}</div>
  </div>
</template>

<style scoped>
@import './medReportSheet.css';
</style>
