<script setup>
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  dispensePrescription,
  fetchPrescriptionDetail,
  rejectPrescription,
} from '../../api/pharmacy'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  prescriptionId: { type: Number, default: null },
})

const emit = defineEmits(['update:modelValue', 'changed'])

const loading = ref(false)
const acting = ref(false)
const detail = ref(null)

const statusMap = {
  10: { label: '已开立', type: 'info' },
  15: { label: '药师驳回', type: 'danger' },
  20: { label: '待发药', type: 'warning' },
  30: { label: '已发药', type: 'success' },
  40: { label: '已退药', type: 'warning' },
  50: { label: '已退费', type: 'info' },
}

watch(
  () => [props.modelValue, props.prescriptionId],
  async ([open, id]) => {
    if (!open || !id) {
      detail.value = null
      return
    }
    loading.value = true
    try {
      const res = await fetchPrescriptionDetail(id)
      detail.value = res.data
    } catch (err) {
      ElMessage.error(err.message || '加载处方详情失败')
      emit('update:modelValue', false)
    } finally {
      loading.value = false
    }
  },
)

function onClose() {
  emit('update:modelValue', false)
}

async function onDispense() {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm(
      `请核对患者身份与处方内容后发药\n患者：${detail.value.patientName}（${detail.value.medicalRecordNo}）`,
      '确认发药',
      { type: 'warning' },
    )
  } catch {
    return
  }
  acting.value = true
  try {
    await dispensePrescription(detail.value.prescriptionId)
    ElMessage.success('发药成功，请交代用药注意事项')
    emit('changed')
    onClose()
  } catch (err) {
    ElMessage.error(err.message || '发药失败')
  } finally {
    acting.value = false
  }
}

async function onReject() {
  if (!detail.value) return
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt('请填写拒绝原因（必填）', '拒绝发药', {
      confirmButtonText: '确认拒绝',
      cancelButtonText: '取消',
      inputPattern: /\S+/,
      inputErrorMessage: '请填写拒绝原因',
    })
    reason = String(value || '').trim()
  } catch {
    return
  }
  if (!reason) {
    ElMessage.warning('请填写拒绝原因')
    return
  }
  acting.value = true
  try {
    await rejectPrescription(detail.value.prescriptionId, { reason })
    ElMessage.success('已拒绝发药并退费，处方已退回开方医生')
    emit('changed')
    onClose()
  } catch (err) {
    ElMessage.error(err.message || '拒绝发药失败')
  } finally {
    acting.value = false
  }
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    title="处方发药详情"
    size="640px"
    destroy-on-close
    @close="onClose"
  >
    <div v-loading="loading">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small" class="head-desc">
          <el-descriptions-item label="处方ID">{{ detail.prescriptionId }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusMap[detail.status]?.type || 'info'" size="small">
              {{ detail.statusLabel || statusMap[detail.status]?.label || detail.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="患者">{{ detail.patientName }}</el-descriptions-item>
          <el-descriptions-item label="病历号">{{ detail.medicalRecordNo }}</el-descriptions-item>
          <el-descriptions-item label="开方医生">{{ detail.doctorName }}</el-descriptions-item>
          <el-descriptions-item label="金额">¥{{ detail.totalAmount }}</el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="detail.status === 15 && detail.rejectReason"
          type="error"
          :closable="false"
          show-icon
          class="reject-alert"
          :title="`驳回原因：${detail.rejectReason}`"
        />

        <el-table :data="detail.items ?? []" size="small" border class="items-table">
          <el-table-column prop="drugName" label="药品" min-width="120" />
          <el-table-column prop="drugFormat" label="规格" width="100" />
          <el-table-column prop="quantity" label="数量" width="64" />
          <el-table-column prop="usageMethod" label="用法" width="72" />
          <el-table-column prop="dosage" label="剂量" width="72" />
          <el-table-column prop="frequency" label="频次" width="72" />
          <el-table-column prop="days" label="天数" width="56" />
          <el-table-column prop="stockQty" label="库存" width="64" />
          <el-table-column prop="amount" label="小计" width="72">
            <template #default="{ row }">¥{{ row.amount }}</template>
          </el-table-column>
        </el-table>

        <div v-if="detail.status === 20" class="drawer-actions">
          <el-button type="primary" :loading="acting" @click="onDispense">确认发药</el-button>
          <el-button type="danger" plain :loading="acting" @click="onReject">拒绝发药</el-button>
        </div>
      </template>
    </div>
  </el-drawer>
</template>

<style scoped>
.head-desc {
  margin-bottom: 12px;
}

.reject-alert {
  margin-bottom: 12px;
}

.items-table {
  margin-bottom: 16px;
}

.drawer-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}
</style>
