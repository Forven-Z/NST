<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchPatientVisitHub, fetchPatientVisits } from '../../api/doctor'
import VisitHubReadonlyPanel from './VisitHubReadonlyPanel.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  patientId: { type: Number, default: null },
  patientName: { type: String, default: '' },
  medicalRecordNo: { type: String, default: '' },
  currentRegisterId: { type: Number, default: null },
})

const emit = defineEmits(['update:modelValue'])

const listLoading = ref(false)
const hubLoading = ref(false)
const hubError = ref('')
const visits = ref([])
const selectedRegisterId = ref(null)
const hub = ref(null)

const drawerTitle = computed(() => {
  const name = props.patientName || '患者'
  const mr = props.medicalRecordNo ? `（${props.medicalRecordNo}）` : ''
  return `既往就诊 · ${name}${mr}`
})

watch(
  () => props.modelValue,
  (open) => {
    if (open && props.patientId) {
      loadVisits()
    }
    if (!open) {
      visits.value = []
      selectedRegisterId.value = null
      hub.value = null
      hubError.value = ''
    }
  },
)

watch(
  () => props.patientId,
  (id) => {
    if (props.modelValue && id) {
      loadVisits()
    }
  },
)

async function loadVisits() {
  if (!props.patientId) return
  listLoading.value = true
  hubError.value = ''
  try {
    const res = await fetchPatientVisits(props.patientId, { page: 1, pageSize: 50 })
    visits.value = res.data?.list ?? []
    if (!visits.value.length) {
      selectedRegisterId.value = null
      hub.value = null
      return
    }
    const preferred = visits.value.find((v) => v.registerId !== props.currentRegisterId)
      || visits.value[0]
    selectVisit(preferred.registerId)
  } catch (err) {
    ElMessage.error(err.message || '加载就诊列表失败')
    visits.value = []
  } finally {
    listLoading.value = false
  }
}

async function selectVisit(registerId) {
  if (!registerId || !props.patientId) return
  selectedRegisterId.value = registerId
  hubLoading.value = true
  hubError.value = ''
  hub.value = null
  try {
    const res = await fetchPatientVisitHub(props.patientId, registerId)
    hub.value = res.data
  } catch (err) {
    hubError.value = err.message || '加载就诊详情失败'
  } finally {
    hubLoading.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}

function isCurrentVisit(registerId) {
  return props.currentRegisterId != null && registerId === props.currentRegisterId
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="drawerTitle"
    size="860px"
    append-to-body
    destroy-on-close
    @close="close"
  >
    <template #header>
      <div class="drawer-header">
        <span>{{ drawerTitle }}</span>
        <el-button link type="primary" :loading="listLoading" @click="loadVisits">刷新</el-button>
      </div>
    </template>

    <div class="drawer-body">
      <aside v-loading="listLoading" class="visit-list">
        <el-empty v-if="!listLoading && !visits.length" description="暂无既往就诊" />
        <button
          v-for="item in visits"
          :key="item.registerId"
          type="button"
          class="visit-item"
          :class="{ active: selectedRegisterId === item.registerId }"
          @click="selectVisit(item.registerId)"
        >
          <div class="visit-item-head">
            <span class="visit-date">{{ item.visitDateLabel }} · {{ item.noonLabel }}</span>
            <el-tag v-if="isCurrentVisit(item.registerId)" size="small" type="success">本次</el-tag>
          </div>
          <div class="visit-dept">{{ item.deptName }} · {{ item.doctorName }}</div>
          <div class="visit-tags">
            <el-tag size="small" :type="item.visitState === 3 ? 'info' : 'warning'">
              {{ item.visitStateLabel }}
            </el-tag>
            <el-tag v-if="item.hasMedicalRecord" size="small" type="success">已提交病历</el-tag>
            <el-tag v-else-if="item.medicalRecordStatus === 1" size="small" type="warning">病历草稿</el-tag>
          </div>
          <div v-if="item.orderCount" class="visit-count muted">
            医嘱 {{ item.orderCount }} · 报告 {{ item.reportReadyCount }}
          </div>
          <div v-if="item.summarySnippet" class="visit-snippet">{{ item.summarySnippet }}</div>
        </button>
      </aside>

      <VisitHubReadonlyPanel
        :hub="hub"
        :hub-loading="hubLoading"
        :hub-error="hubError"
        :patient-id="patientId"
        :current-register-id="currentRegisterId"
      />
    </div>
  </el-drawer>
</template>

<style scoped>
.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 24px;
  font-weight: 600;
}

.drawer-body {
  display: flex;
  height: calc(100vh - 120px);
  min-height: 480px;
  margin: -20px;
  border-top: 1px solid #e2e8f0;
}

.visit-list {
  width: 280px;
  flex-shrink: 0;
  overflow-y: auto;
  border-right: 1px solid #e2e8f0;
  padding: 12px;
  background: #f8fafc;
}

.visit-item {
  display: block;
  width: 100%;
  text-align: left;
  border: 1px solid #e2e8f0;
  background: #fff;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.visit-item:hover {
  border-color: #94a3b8;
}

.visit-item.active {
  border-color: #0f766e;
  box-shadow: 0 0 0 1px #0f766e;
}

.visit-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.visit-date {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.visit-dept {
  margin-top: 6px;
  font-size: 13px;
  color: #475569;
}

.visit-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.visit-count {
  margin-top: 6px;
  font-size: 12px;
}

.visit-snippet {
  margin-top: 8px;
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.muted {
  color: #94a3b8;
}
</style>
