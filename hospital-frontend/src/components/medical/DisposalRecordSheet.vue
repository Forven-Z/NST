<script setup>
import { computed } from 'vue'
import { processPlaceholder } from '../../utils/disposalRecord'
import MedReportShell from './MedReportShell.vue'
import MedReportInfoGrid from './MedReportInfoGrid.vue'
import MedReportFooter from './MedReportFooter.vue'

const props = defineProps({
  report: { type: Object, default: null },
  editable: { type: Boolean, default: false },
})

const emit = defineEmits(['update:processText', 'update:outcomeText'])

const header = computed(() => props.report?.header || {})
const record = computed(() => props.report?.record || {})
const footer = computed(() => props.report?.footer || {})

const processHint = computed(() => processPlaceholder(header.value.itemName))
</script>

<template>
  <MedReportShell
    v-if="report"
    hospital-title="云脑医院处置记录"
    :report-title="report.reportTitle"
    :report-no="report.recordNo"
  >
    <MedReportInfoGrid :header="header" variant="disposal" />

    <section class="data-zone">
      <div class="zone-head">
        <span class="zone-label">处置数据区</span>
        <el-tag v-if="editable" size="small" type="warning">可编辑</el-tag>
      </div>

      <div class="subsection">
        <div class="subsection-title">处置过程</div>
        <el-input
          v-if="editable"
          :model-value="record.processText"
          type="textarea"
          :rows="5"
          :placeholder="processHint"
          @update:model-value="emit('update:processText', $event)"
        />
        <pre v-else class="readonly">{{ record.processText || '（未填写）' }}</pre>
      </div>

      <div class="subsection">
        <div class="subsection-title">观察与结果</div>
        <el-input
          v-if="editable"
          :model-value="record.outcomeText"
          type="textarea"
          :rows="4"
          placeholder="患者反应、生命体征、注意事项、后续建议…"
          @update:model-value="emit('update:outcomeText', $event)"
        />
        <pre v-else class="readonly">{{ record.outcomeText || '（未填写）' }}</pre>
      </div>
    </section>

    <MedReportFooter
      :footer="footer"
      variant="disposal"
      disclaimer="本记录仅供临床参考，以签阅内容为准"
    />
  </MedReportShell>
</template>

<style scoped>
@import './medReportSheet.css';
</style>
