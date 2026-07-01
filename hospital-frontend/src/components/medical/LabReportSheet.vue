<script setup>
import { computed } from 'vue'
import { flagClass, flagLabel } from '../../utils/labReport'
import AiReportAnalysisBlock from './AiReportAnalysisBlock.vue'
import MedReportShell from './MedReportShell.vue'
import MedReportInfoGrid from './MedReportInfoGrid.vue'
import MedReportFooter from './MedReportFooter.vue'

const props = defineProps({
  report: { type: Object, default: null },
  editableDoctor: { type: Boolean, default: false },
  editableAi: { type: Boolean, default: false },
  showAnalysis: { type: Boolean, default: true },
})

const emit = defineEmits(['update:doctorReportText', 'update:aiReportText'])

const header = computed(() => props.report?.header || {})
const items = computed(() => props.report?.items || [])
const analysis = computed(() => props.report?.analysis || {})
const footer = computed(() => props.report?.footer || {})
</script>

<template>
  <MedReportShell
    v-if="report"
    hospital-title="云脑医院检验报告单"
    :report-title="report.reportTitle"
    :report-no="report.reportNo"
  >
    <MedReportInfoGrid :header="header" variant="lab" />

    <section class="data-zone">
      <div class="zone-head">
        <span class="zone-label">检验数据区</span>
        <el-tag size="small" type="info">仪器自动填充</el-tag>
      </div>
      <table class="result-table">
        <thead>
          <tr>
            <th>检验项目</th>
            <th>结果</th>
            <th>单位</th>
            <th>参考范围</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(item, idx) in items" :key="item.code || idx">
            <td>{{ item.name }}</td>
            <td :class="flagClass(item.flag)">
              {{ item.result }}<span v-if="flagLabel(item.flag)" class="flag">{{ flagLabel(item.flag) }}</span>
            </td>
            <td>{{ item.unit || '—' }}</td>
            <td>{{ item.refRange || '—' }}</td>
          </tr>
        </tbody>
      </table>
    </section>

    <AiReportAnalysisBlock
      v-if="showAnalysis"
      :ai-report-text="analysis.aiReportText"
      :doctor-report-text="analysis.doctorReportText"
      :ai-report-status="analysis.aiReportStatus"
      :editable-ai="editableAi"
      :editable-doctor="editableDoctor"
      ai-section-title="诊断分析"
      doctor-section-title="检验医师意见"
      ai-placeholder="点击「生成 AI 报告」后，将根据上方检验数据归纳分析…"
      doctor-placeholder="在 AI 分析基础上补充检验医师签阅意见…"
      @update:ai-report-text="emit('update:aiReportText', $event)"
      @update:doctor-report-text="emit('update:doctorReportText', $event)"
    />

    <MedReportFooter
      :footer="footer"
      variant="lab"
      disclaimer="本结果仅对该份标本负责"
    />
  </MedReportShell>
</template>

<style scoped>
@import './medReportSheet.css';
</style>
