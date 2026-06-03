<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchPatientBills, refundBill } from '../../api/registrar'

const loading = ref(false)
const refundingId = ref(null)
const medicalRecordNo = ref('')
const bills = ref([])
const patientId = ref(null)

const statusMap = {
  0: { label: '待支付', type: 'info' },
  1: { label: '已支付', type: 'success' },
  2: { label: '已退款', type: 'warning' },
}

async function onSearch() {
  if (!medicalRecordNo.value.trim()) {
    ElMessage.warning('请输入病历号')
    return
  }
  loading.value = true
  try {
    const res = await fetchPatientBills(medicalRecordNo.value.trim())
    bills.value = res.data?.list ?? []
    patientId.value = res.data?.patientId
  } catch (err) {
    ElMessage.error(err.message || '查询失败')
  } finally {
    loading.value = false
  }
}

async function onRefund(row) {
  try {
    await ElMessageBox.confirm(`确认为账单 ${row.billNo} 退费 ¥${row.amount}？`, '确认退费', { type: 'warning' })
  } catch {
    return
  }
  refundingId.value = row.id
  try {
    await refundBill({ billId: row.id, reason: 'window refund' })
    ElMessage.success('退费成功')
    await onSearch()
  } catch (err) {
    ElMessage.error(err.message || '退费失败')
  } finally {
    refundingId.value = null
  }
}
</script>

<template>
  <el-card shadow="never" class="section-card">
    <template #header>
      <div class="card-header">
        <span>窗口退费</span>
        <div class="filters">
          <el-input v-model="medicalRecordNo" placeholder="病历号" style="width: 200px" @keyup.enter="onSearch" />
          <el-button type="primary" :loading="loading" @click="onSearch">查询</el-button>
        </div>
      </div>
    </template>

    <p v-if="patientId" class="hint">患者 ID：{{ patientId }}</p>

    <el-table v-loading="loading" :data="bills" empty-text="输入病历号查询账单">
      <el-table-column prop="billNo" label="账单号" width="150" />
      <el-table-column prop="billTitle" label="项目" min-width="140" />
      <el-table-column prop="bizType" label="类型" width="110" />
      <el-table-column prop="amount" label="金额" width="90" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.type || 'info'" size="small">
            {{ statusMap[row.status]?.label || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 1"
            type="primary"
            link
            :loading="refundingId === row.id"
            @click="onRefund(row)"
          >
            退费
          </el-button>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<style scoped>
.section-card { border-radius: 10px; }
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.filters { display: flex; gap: 8px; }
.hint { color: #64748b; font-size: 13px; margin: 0 0 12px; }
.muted { color: #94a3b8; }
</style>
