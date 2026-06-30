<script setup>
import { ref } from 'vue'
import VisitMedicalRecordReadonly from './VisitMedicalRecordReadonly.vue'
import RegisterOrdersPanel from './RegisterOrdersPanel.vue'

defineProps({
  hub: { type: Object, default: null },
  hubLoading: { type: Boolean, default: false },
  hubError: { type: String, default: '' },
  patientId: { type: Number, default: null },
  currentRegisterId: { type: Number, default: null },
})

const activeAnchor = ref('record')

function scrollToSection(anchor) {
  activeAnchor.value = anchor
  document.getElementById(`visit-hub-${anchor}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<template>
  <div v-loading="hubLoading" class="hub-panel">
    <el-empty v-if="hubError" :description="hubError" />

    <template v-else-if="hub">
      <div class="hub-sticky">
        <div class="hub-title">
          {{ hub.registerSummary?.visitDateLabel || '—' }}
          · {{ hub.registerSummary?.noonLabel || '' }}
        </div>
        <div class="hub-sub">
          {{ hub.registerSummary?.deptName }} · {{ hub.registerSummary?.doctorName }}
          · {{ hub.registerSummary?.visitStateLabel }}
        </div>
        <div class="hub-meta muted">
          {{ hub.registerSummary?.patientName }}
          · {{ hub.registerSummary?.medicalRecordNo }}
        </div>
        <div class="anchor-bar">
          <button
            type="button"
            class="anchor-chip"
            :class="{ active: activeAnchor === 'record' }"
            @click="scrollToSection('record')"
          >
            病历文书
          </button>
          <button
            type="button"
            class="anchor-chip"
            :class="{ active: activeAnchor === 'orders' }"
            @click="scrollToSection('orders')"
          >
            本次医嘱
          </button>
        </div>
      </div>

      <section id="visit-hub-record" class="hub-section">
        <h4 class="section-head">病历文书</h4>
        <VisitMedicalRecordReadonly
          :record="hub.medicalRecord"
          :medical-record-status="hub.medicalRecordStatus"
          :has-medical-record="hub.hasMedicalRecord"
          :has-record-draft="hub.hasRecordDraft"
        />
      </section>

      <section id="visit-hub-orders" class="hub-section">
        <h4 class="section-head">本次医嘱</h4>
        <RegisterOrdersPanel
          mode="history"
          readonly
          embedded
          :register-id="hub.registerSummary?.registerId"
          :patient-id="patientId"
          :prefetched-orders="hub.orders"
          :current-register-id="currentRegisterId"
        />
      </section>
    </template>

    <el-empty v-else-if="!hubLoading" description="请选择左侧就诊记录" />
  </div>
</template>

<style scoped>
.hub-panel {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding: 16px;
}

.hub-sticky {
  position: sticky;
  top: 0;
  z-index: 2;
  background: #fff;
  padding-bottom: 12px;
  margin-bottom: 8px;
  border-bottom: 1px dashed #e2e8f0;
}

.hub-title {
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
}

.hub-sub {
  margin-top: 4px;
  font-size: 14px;
  color: #334155;
}

.hub-meta {
  margin-top: 4px;
  font-size: 13px;
}

.muted {
  color: #94a3b8;
}

.anchor-bar {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.anchor-chip {
  border: 1px solid #cbd5e1;
  background: #fff;
  color: #475569;
  border-radius: 999px;
  padding: 4px 14px;
  font-size: 13px;
  cursor: pointer;
}

.anchor-chip.active {
  background: #0f766e;
  border-color: #0f766e;
  color: #fff;
}

.hub-section {
  margin-top: 20px;
  scroll-margin-top: 120px;
}

.section-head {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}
</style>
