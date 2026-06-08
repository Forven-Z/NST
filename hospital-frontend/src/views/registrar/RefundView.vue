<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchPatientBills, refundBill } from '../../api/registrar'

const loading = ref(false)
const refundingId = ref(null)
const medicalRecordNo = ref('')
const bills = ref([])
const patientId = ref(null)

const demoMrList = ['MR202606040001', 'MR202606040002', 'MR202606040003']

const statusMap = {
  0: { label: '待支付', type: 'info' },
  1: { label: '已支付', type: 'success' },
  2: { label: '已退款', type: 'warning' },
}

const bizLabel = {
  REGIST: '挂号',
  INSPECTION: '检验',
  CHECK: '检查',
  PRESCRIPTION: '处方',
  DISPOSAL: '处置',
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

function fillDemo(mr) {
  medicalRecordNo.value = mr
  onSearch()
}

async function onRefund(row) {
  try {
    await ElMessageBox.confirm(
      `确认为账单 #${row.id}（${row.itemName || row.billTitle}）退费 ¥${row.amount}？\n未执行的检验/检查/处置/未发药处方方可退费。`,
      '确认退费',
      { type: 'warning' },
    )
  } catch {
    return
  }
  refundingId.value = row.id
  try {
    await refundBill({ billId: row.id, reason: '窗口退费' })
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
  <div class="refund-page">
    <div class="page-head">
      <h2 class="page-title">窗口退费</h2>
      <p class="page-desc">
        已支付但未执行的医技项目、未发药处方等可申请退费；挂号费一般当日有效，具体以医院规定为准。
      </p>
    </div>

    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-header">
          <span>账单查询</span>
          <div class="filters">
            <el-input v-model="medicalRecordNo" placeholder="病历号" style="width: 220px" @keyup.enter="onSearch" />
            <el-button type="primary" :loading="loading" @click="onSearch">查询</el-button>
          </div>
        </div>
      </template>

      <div class="demo-mr">
        <span class="demo-label">演示病历号：</span>
        <el-button v-for="mr in demoMrList" :key="mr" size="small" link type="primary" @click="fillDemo(mr)">
          {{ mr }}
        </el-button>
      </div>

      <p v-if="patientId" class="hint">患者 ID：{{ patientId }}</p>

      <el-table v-loading="loading" :data="bills" empty-text="输入病历号查询全部账单（含已支付）">
        <el-table-column prop="id" label="账单ID" width="100" />
        <el-table-column label="项目" min-width="160">
          <template #default="{ row }">{{ row.itemName || row.billTitle }}</template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ bizLabel[row.bizType] || row.bizType || '—' }}</template>
        </el-table-column>
        <el-table-column label="金额" width="90">
          <template #default="{ row }">¥{{ row.amount }}</template>
        </el-table-column>
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
  </div>
</template>

<style scoped>
.refund-page {
  max-width: 960px;
}

.page-head {
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.page-desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
}

.section-card {
  border-radius: 10px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.filters {
  display: flex;
  gap: 8px;
}

.demo-mr {
  margin-bottom: 12px;
  font-size: 13px;
  color: #64748b;
}

.hint {
  color: #64748b;
  font-size: 13px;
  margin: 0 0 12px;
}

.muted {
  color: #94a3b8;
}
</style>
