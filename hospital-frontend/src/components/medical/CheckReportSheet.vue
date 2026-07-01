<script setup>
import { computed } from 'vue'
import AiReportAnalysisBlock from './AiReportAnalysisBlock.vue'
import MedReportShell from './MedReportShell.vue'
import MedReportInfoGrid from './MedReportInfoGrid.vue'
import MedReportFooter from './MedReportFooter.vue'
import ReportSnapshotImg from './ReportSnapshotImg.vue'

const props = defineProps({
  report: { type: Object, default: null },
  editableFindings: { type: Boolean, default: false },
  editableAi: { type: Boolean, default: false },
  editableDoctor: { type: Boolean, default: false },
  showAnalysis: { type: Boolean, default: true },
  showRecapture: { type: Boolean, default: false },
})

const emit = defineEmits([
  'update:findingsText',
  'update:aiReportText',
  'update:doctorReportText',
  'recapture',
])

const header = computed(() => props.report?.header || {})
const findings = computed(() => props.report?.findings || {})
const analysis = computed(() => props.report?.analysis || {})
const footer = computed(() => props.report?.footer || {})

const snapshotPlanes = [  { key: 'axial', label: '轴位 Axial' },
  { key: 'coronal', label: '冠状 Coronal' },
  { key: 'sagittal', label: '矢状 Sagittal' },
]

const localSnapshots = computed(() => findings.value.localSnapshots || null)

function snapshotSrc(plane) {
  const local = localSnapshots.value
  if (local?.[plane]) return local[plane]
  const urls = findings.value.reportImages
  if (urls?.[plane]) return urls[plane]
  return ''
}

const hasSnapshots = computed(
  () => !!(snapshotSrc('axial') || snapshotSrc('coronal') || snapshotSrc('sagittal')),
)
</script>

<template>
  <MedReportShell
    v-if="report"
    hospital-title="云脑医院 CT 检查报告单"
    :report-title="report.reportTitle"
    :report-no="report.reportNo"
  >
    <MedReportInfoGrid :header="header" variant="check" />

    <section class="data-zone">
      <div class="zone-head">
        <span class="zone-label">检查数据区</span>
        <div class="zone-actions">
          <el-button
            v-if="showRecapture"
            size="small"
            type="primary"
            plain
            @click="emit('recapture')"
          >
            重新采图
          </el-button>
        </div>
      </div>

      <div v-if="hasSnapshots" class="snapshot-grid">
        <div v-for="plane in snapshotPlanes" :key="plane.key" class="snapshot-cell">
          <div class="snapshot-label">{{ plane.label }}</div>
          <ReportSnapshotImg
            v-if="snapshotSrc(plane.key)"
            :src="snapshotSrc(plane.key)"
            :alt="plane.label"
          />
          <div v-else class="snapshot-empty">未采图</div>
        </div>
      </div>

      <div v-if="findings.snapshotMeta" class="snapshot-meta">
        采图层面：Z={{ findings.snapshotMeta.sliceZ ?? '—' }}
        · Y={{ findings.snapshotMeta.sliceY ?? '—' }}
        · X={{ findings.snapshotMeta.sliceX ?? '—' }}
        <span v-if="findings.snapshotMeta.maskOverlayReady"> · 含 AI 掩码叠加</span>
      </div>

    </section>
    <section class="findings-zone">
      <div class="subsection-title">CT 所见</div>
      <el-input
        v-if="editableFindings"
        :model-value="findings.findingsText"
        type="textarea"
        :rows="8"
        placeholder="请结合上方三视图影像描述 CT 所见；填写后再点击「生成 AI 报告」"
        @update:model-value="emit('update:findingsText', $event)"
      />
      <pre v-else class="readonly">{{ findings.findingsText || '（检查所见尚未填写）' }}</pre>
    </section>

    <AiReportAnalysisBlock
      v-if="showAnalysis"
      :ai-report-text="analysis.aiReportText"
      :doctor-report-text="analysis.doctorReportText"
      :ai-report-status="analysis.aiReportStatus"
      :editable-ai="editableAi"
      :editable-doctor="editableDoctor"
      ai-section-title="诊断印象"
      doctor-section-title="检查医师意见"
      ai-placeholder="点击「生成 AI 报告」后，将根据上方检查所见归纳诊断印象…"
      doctor-placeholder="在 AI 诊断印象基础上补充签阅意见…"
      @update:ai-report-text="emit('update:aiReportText', $event)"
      @update:doctor-report-text="emit('update:doctorReportText', $event)"
    />

    <MedReportFooter
      :footer="footer"
      variant="check"
      disclaimer="本报告仅供临床参考，以医师签阅为准"
    />
  </MedReportShell>
</template>

<style scoped>
@import './medReportSheet.css';

.findings-zone {
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 10px 12px;
  background: #fff;
  margin-bottom: 12px;
}
</style>