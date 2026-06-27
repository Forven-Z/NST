<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import ResultReportSections from '../medical/ResultReportSections.vue'
import LabReportSheet from '../medical/LabReportSheet.vue'
import CheckReportSheet from '../medical/CheckReportSheet.vue'
import DisposalRecordSheet from '../medical/DisposalRecordSheet.vue'
import {
  buildRegisterResultsFromOrders,
  fetchOrderResult,
  fetchRegisterOrders,
} from '../../api/doctor'
import DoctorPrescriptionEditDialog from './DoctorPrescriptionEditDialog.vue'

const props = defineProps({
  registerId: { type: Number, default: null },
})

const loading = ref(false)
const resultLoading = ref(false)
const orders = ref(null)
const resultsMap = ref({})
const resultDialogVisible = ref(false)
const resultDetail = ref(null)
const rxEditVisible = ref(false)
const rxEditId = ref(null)
const rxRejectReason = ref('')

const statusMap = {
  10: { label: '已开立', type: 'info' },
  15: { label: '药师驳回', type: 'danger' },
  20: { label: '已缴费', type: 'warning' },
  30: { label: '执行完成', type: 'primary' },
  40: { label: '已出结果', type: 'success' },
  50: { label: '已退费', type: 'danger' },
}

const resultFetchers = {
  inspection: (id) => fetchOrderResult('inspection', id),
  check: (id) => fetchOrderResult('check', id),
  disposal: (id) => fetchOrderResult('disposal', id),
}

const isLabReport = computed(() => resultDetail.value?.reportType === 'lab')
const isCheckReport = computed(() => resultDetail.value?.reportType === 'check')
const isDisposalRecord = computed(() => resultDetail.value?.reportType === 'disposal')

const useReportSections = computed(() => {
  const detail = resultDetail.value
  if (!detail) return false
  if (detail.reportType === 'lab' || detail.reportType === 'check' || detail.reportType === 'disposal') {
    return false
  }
  return !!(detail.instrumentData || detail.aiReportText || detail.doctorReportText)
})

watch(
  () => props.registerId,
  (id) => {
    resultDialogVisible.value = false
    resultDetail.value = null
    resultsMap.value = {}
    if (id) loadOrders()
    else orders.value = null
  },
  { immediate: true },
)

async function loadOrders() {
  if (!props.registerId) return
  loading.value = true
  try {
    const ordersRes = await fetchRegisterOrders(props.registerId)
    orders.value = ordersRes.data
    const map = {}
    for (const item of buildRegisterResultsFromOrders(ordersRes.data)) {
      map[`${item.kind}-${item.requestId}`] = item
    }
    resultsMap.value = map
  } catch (err) {
    ElMessage.error(err.message || '加载医嘱失败')
    orders.value = null
    resultsMap.value = {}
  } finally {
    loading.value = false
  }
}

async function onViewResult(row) {
  if (row.kind === 'prescription') {
    if (row.status === 15) {
      rxEditId.value = row.requestId
      rxRejectReason.value = row.rejectReason || ''
      rxEditVisible.value = true
      return
    }
    return ElMessage.info('处方无文字报告，请至药房查看发药状态')
  }
  if (row.status < 40) {
    return ElMessage.warning('结果尚未出具')
  }

  const fetchResult = resultFetchers[row.kind]
  if (!fetchResult) {
    return ElMessage.warning('暂不支持查看该类型结果')
  }

  resultDialogVisible.value = true
  resultLoading.value = true
  resultDetail.value = { ...row }

  try {
    const res = await fetchResult(row.requestId)
    resultDetail.value = {
      ...row,
      ...res.data,
      reportTime: res.data?.reportTime ?? res.data?.resultTime ?? row.reportTime,
    }
  } catch (err) {
    resultDialogVisible.value = false
    resultDetail.value = null
    ElMessage.error(err.message || '加载结果失败')
  } finally {
    resultLoading.value = false
  }
}

defineExpose({ reload: loadOrders })
</script>

<template>
  <el-card v-if="registerId" shadow="never" class="orders-card">
    <template #header>
      <div class="card-header">
        <span>本次就诊医嘱</span>
        <el-button link type="primary" :loading="loading" @click="loadOrders">刷新</el-button>
      </div>
    </template>

    <el-table v-loading="loading" :data="orders?.list ?? []" empty-text="暂无开立医嘱" size="small">
      <el-table-column prop="typeLabel" label="类型" width="88" />
      <el-table-column prop="itemName" label="项目" min-width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.type || 'info'" size="small">
            {{ row.statusLabel || statusMap[row.status]?.label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button
            v-if="row.kind === 'prescription' && row.status === 15"
            link
            type="danger"
            @click="onViewResult(row)"
          >
            修改处方
          </el-button>
          <el-button
            v-else-if="row.kind !== 'prescription'"
            link
            type="primary"
            :disabled="row.status < 40"
            @click="onViewResult(row)"
          >
            查看结果
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="resultDialogVisible"
      :title="`${resultDetail?.typeLabel || ''} · ${resultDetail?.itemName || ''}`"
      :width="isLabReport || isCheckReport || isDisposalRecord || useReportSections ? '860px' : '560px'"
      destroy-on-close
    >
      <div v-if="resultDetail" v-loading="resultLoading">
        <LabReportSheet
          v-if="isLabReport"
          :report="resultDetail"
        />
        <CheckReportSheet
          v-else-if="isCheckReport"
          :report="resultDetail"
        />
        <DisposalRecordSheet
          v-else-if="isDisposalRecord"
          :report="resultDetail"
        />
        <ResultReportSections
          v-else-if="useReportSections"
          :instrument-data="resultDetail.instrumentData"
          :ai-report-text="resultDetail.aiReportText"
          :doctor-report-text="resultDetail.doctorReportText"
          :ai-report-status="resultDetail.aiReportStatus || 'PENDING'"
        />
        <el-descriptions v-else :column="1" border>
          <el-descriptions-item label="结果文本">
            <pre class="result-text">{{ resultDetail.resultText || '（无）' }}</pre>
          </el-descriptions-item>
        </el-descriptions>

        <el-descriptions v-if="!resultLoading && !isLabReport && !isCheckReport && !isDisposalRecord" :column="1" border class="result-meta">
          <el-descriptions-item v-if="resultDetail.reportTime" label="报告时间">
            {{ resultDetail.reportTime }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>

    <DoctorPrescriptionEditDialog
      v-model="rxEditVisible"
      :register-id="registerId"
      :prescription-id="rxEditId"
      :reject-reason="rxRejectReason"
      @saved="loadOrders"
    />
  </el-card>
</template>

<style scoped>
.orders-card {
  border-radius: 10px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.result-text {
  margin: 0;
  white-space: pre-wrap;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.6;
}

.result-meta {
  margin-top: 12px;
}
</style>
