<script setup>
import { computed } from 'vue'
import {
  buildDiseaseNames,
  buildRecordSections,
  medicalRecordStatusLabel,
} from '../../utils/medicalRecordSections'

const props = defineProps({
  record: { type: Object, default: null },
  medicalRecordStatus: { type: Number, default: null },
  hasMedicalRecord: { type: Boolean, default: false },
  hasRecordDraft: { type: Boolean, default: false },
})

const sections = computed(() => buildRecordSections(props.record))
const diseaseNames = computed(() => buildDiseaseNames(props.record))
const statusLabel = computed(() => medicalRecordStatusLabel(props.medicalRecordStatus))
const statusType = computed(() => {
  if (props.medicalRecordStatus === 2) return 'success'
  if (props.medicalRecordStatus === 1) return 'warning'
  return 'info'
})
const showEmpty = computed(() => !props.hasRecordDraft || sections.value.length === 0)
</script>

<template>
  <div class="visit-record-readonly">
    <div v-if="hasRecordDraft" class="record-meta">
      <el-tag :type="statusType" size="small">病历 {{ statusLabel }}</el-tag>
      <span v-if="!hasMedicalRecord" class="muted">患者端尚未可见</span>
    </div>

    <template v-if="!showEmpty">
      <div v-if="diseaseNames" class="record-block">
        <div class="block-title">ICD 诊断</div>
        <pre class="readonly">{{ diseaseNames }}</pre>
      </div>
      <div v-for="item in sections" :key="item.label" class="record-block">
        <div class="block-title">{{ item.label }}</div>
        <pre class="readonly">{{ item.value }}</pre>
      </div>
    </template>

    <el-empty v-else :image-size="64">
      <template #description>
        <span v-if="!hasRecordDraft">医生尚未书写病历</span>
        <span v-else-if="hasMedicalRecord">暂无文书内容</span>
        <span v-else>病历已保存，尚未确诊提交</span>
      </template>
    </el-empty>
  </div>
</template>

<style scoped>
.visit-record-readonly {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.record-meta {
  display: flex;
  align-items: center;
  gap: 10px;
}

.muted {
  color: #94a3b8;
  font-size: 13px;
}

.record-block {
  padding: 12px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.block-title {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 8px;
}

.readonly {
  margin: 0;
  white-space: pre-wrap;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.65;
  color: #1e293b;
}
</style>
