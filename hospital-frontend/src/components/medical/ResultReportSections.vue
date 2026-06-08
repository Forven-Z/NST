<script setup>
defineProps({
  instrumentData: { type: String, default: '' },
  aiReportText: { type: String, default: '' },
  doctorReportText: { type: String, default: '' },
  aiReportStatus: { type: String, default: 'PENDING' },
  editableAi: { type: Boolean, default: false },
  editableDoctor: { type: Boolean, default: false },
  showInstrument: { type: Boolean, default: true },
  showAi: { type: Boolean, default: true },
  showDoctor: { type: Boolean, default: true },
})

const emit = defineEmits(['update:aiReportText', 'update:doctorReportText'])

const aiStatusMap = {
  PENDING: { label: '待生成', type: 'info' },
  READY: { label: '已生成', type: 'success' },
  FAILED: { label: '生成失败', type: 'danger' },
}
</script>

<template>
  <div class="report-sections">
    <section v-if="showInstrument" class="section instrument">
      <div class="section-head">
        <span class="section-title">仪器原始数据</span>
        <el-tag size="small" type="info">只读 · 不可修改</el-tag>
      </div>
      <pre class="readonly-block">{{ instrumentData || '（等待仪器上传数据…）' }}</pre>
    </section>

    <section v-if="showAi" class="section ai">
      <div class="section-head">
        <span class="section-title">AI 智能报告</span>
        <el-tag size="small" :type="aiStatusMap[aiReportStatus]?.type || 'info'">
          {{ aiStatusMap[aiReportStatus]?.label || aiReportStatus }}
        </el-tag>
      </div>
      <el-input
        v-if="editableAi"
        :model-value="aiReportText"
        type="textarea"
        :rows="6"
        placeholder="AI 报告生成后可在此核对、修改…"
        @update:model-value="emit('update:aiReportText', $event)"
      />
      <pre v-else class="readonly-block ai-text">{{ aiReportText || '（AI 报告尚未生成）' }}</pre>
    </section>

    <section v-if="showDoctor" class="section doctor">
      <div class="section-head">
        <span class="section-title">医师意见</span>
        <el-tag v-if="editableDoctor" size="small" type="warning">可编辑</el-tag>
      </div>
      <el-input
        v-if="editableDoctor"
        :model-value="doctorReportText"
        type="textarea"
        :rows="3"
        placeholder="在 AI 报告基础上补充医师签阅意见…"
        @update:model-value="emit('update:doctorReportText', $event)"
      />
      <pre v-else class="readonly-block">{{ doctorReportText || '（无补充意见）' }}</pre>
    </section>
  </div>
</template>

<style scoped>
.report-sections {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.section {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 12px;
}

.section.instrument {
  background: #f8fafc;
}

.section.ai {
  background: #f0fdfa;
  border-color: #99f6e4;
}

.section.doctor {
  background: #fffbeb;
  border-color: #fde68a;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.section-title {
  font-weight: 600;
  font-size: 13px;
  color: #334155;
}

.readonly-block {
  margin: 0;
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.55;
  color: #475569;
  font-family: inherit;
}

.ai-text {
  color: #134e4a;
}
</style>
