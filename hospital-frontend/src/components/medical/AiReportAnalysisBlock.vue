<script setup>
import { computed } from 'vue'

const props = defineProps({
  aiReportText: { type: String, default: '' },
  doctorReportText: { type: String, default: '' },
  aiReportStatus: { type: String, default: 'PENDING' },
  editableAi: { type: Boolean, default: false },
  editableDoctor: { type: Boolean, default: false },
  aiSectionTitle: { type: String, default: '诊断印象' },
  doctorSectionTitle: { type: String, default: '医师意见' },
  aiPlaceholder: { type: String, default: '点击「生成 AI 报告」后，将根据上方检查数据归纳诊断印象…' },
  doctorPlaceholder: { type: String, default: '在 AI 报告基础上补充签阅意见…' },
})

const emit = defineEmits(['update:aiReportText', 'update:doctorReportText'])

const aiStatusMap = {
  PENDING: { label: '待生成', type: 'info' },
  READY: { label: '已生成', type: 'success' },
  FAILED: { label: '生成失败', type: 'danger' },
}
</script>

<template>
  <section class="analysis-zone">
    <div class="zone-label">AI 报告区</div>

    <div class="section-head">
      <span class="section-title">{{ aiSectionTitle }}</span>
      <el-tag size="small" :type="aiStatusMap[aiReportStatus]?.type || 'info'">
        {{ aiStatusMap[aiReportStatus]?.label || aiReportStatus }}
      </el-tag>
    </div>
    <el-input
      v-if="editableAi"
      :model-value="aiReportText"
      type="textarea"
      :rows="5"
      :placeholder="aiPlaceholder"
      @update:model-value="emit('update:aiReportText', $event)"
    />
    <pre v-else class="readonly">{{ aiReportText || '（AI 报告尚未生成）' }}</pre>

    <div class="section-head doctor-head">
      <span class="section-title">{{ doctorSectionTitle }}</span>
      <el-tag v-if="editableDoctor" size="small" type="warning">可编辑</el-tag>
    </div>
    <el-input
      v-if="editableDoctor"
      :model-value="doctorReportText"
      type="textarea"
      :rows="3"
      :placeholder="doctorPlaceholder"
      @update:model-value="emit('update:doctorReportText', $event)"
    />
    <pre v-else class="readonly">{{ doctorReportText || '（无补充意见）' }}</pre>
  </section>
</template>

<style scoped>
.analysis-zone {
  border: 1px solid #bae6fd;
  border-radius: 6px;
  padding: 12px 14px;
  background: linear-gradient(180deg, #f0f9ff 0%, #f8fafc 100%);
}

.zone-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.5px;
  color: #0369a1;
  margin-bottom: 8px;
  text-transform: uppercase;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.doctor-head {
  margin-top: 12px;
}

.section-title {
  font-weight: 600;
  font-size: 13px;
  color: #0f172a;
}

.readonly {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.55;
  font-family: inherit;
  font-size: 13px;
  color: #475569;
}
</style>
