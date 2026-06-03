<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dispensePrescription, fetchPendingPrescriptions, returnDrug } from '../../api/pharmacy'

const loading = ref(false)
const dispensingId = ref(null)
const returningId = ref(null)
const statusFilter = ref(20)
const list = ref([])

const statusMap = {
  10: { label: '已开立', type: 'info' },
  20: { label: '已缴费', type: 'warning' },
  30: { label: '已发药', type: 'success' },
}

onMounted(loadList)

async function loadList() {
  loading.value = true
  try {
    const res = await fetchPendingPrescriptions({
      status: statusFilter.value,
      page: 1,
      pageSize: 50,
    })
    list.value = res.data?.list ?? []
  } catch (err) {
    ElMessage.error(err.message || '加载待发药列表失败')
  } finally {
    loading.value = false
  }
}

async function onDispense(row) {
  try {
    await ElMessageBox.confirm(
      `确认为 ${row.patientName} 发放处方 ${row.prescriptionNo}？`,
      '确认发药',
      { type: 'warning' },
    )
  } catch {
    return
  }

  dispensingId.value = row.prescriptionId
  try {
    await dispensePrescription(row.prescriptionId)
    ElMessage.success('发药成功')
    await loadList()
  } catch (err) {
    ElMessage.error(err.message || '发药失败')
  } finally {
    dispensingId.value = null
  }
}

async function onReturn(row) {
  try {
    await ElMessageBox.confirm(
      `确认退药：处方 ${row.prescriptionNo}（${row.patientName}）？`,
      '确认退药',
      { type: 'warning' },
    )
  } catch {
    return
  }
  returningId.value = row.prescriptionId
  try {
    await returnDrug(row.prescriptionId)
    ElMessage.success('退药成功，请通知患者至收费窗口退费')
    await loadList()
  } catch (err) {
    ElMessage.error(err.message || '退药失败')
  } finally {
    returningId.value = null
  }
}
</script>

<template>
  <el-card shadow="never" class="section-card">
    <template #header>
      <div class="card-header">
        <span>处方发药</span>
        <div class="filters">
          <el-radio-group v-model="statusFilter" @change="loadList">
            <el-radio-button :label="20">待发药（已缴费）</el-radio-button>
            <el-radio-button :label="30">已发药</el-radio-button>
          </el-radio-group>
          <el-button :loading="loading" @click="loadList">刷新</el-button>
        </div>
      </div>
    </template>

    <el-table v-loading="loading" :data="list" empty-text="暂无处方">
      <el-table-column prop="prescriptionNo" label="处方号" width="160" />
      <el-table-column prop="medicalRecordNo" label="病历号" width="140" />
      <el-table-column prop="patientName" label="患者" width="100" />
      <el-table-column prop="doctorName" label="开方医生" width="100" />
      <el-table-column prop="totalAmount" label="金额" width="90" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.type || 'info'" size="small">
            {{ statusMap[row.status]?.label || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="orderTime" label="开方时间" min-width="160" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 20"
            type="primary"
            link
            :loading="dispensingId === row.prescriptionId"
            @click="onDispense(row)"
          >
            发药
          </el-button>
          <el-button
            v-if="row.status === 30"
            type="warning"
            link
            :loading="returningId === row.prescriptionId"
            @click="onReturn(row)"
          >
            退药
          </el-button>
          <span v-else-if="row.status !== 20" class="muted">—</span>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<style scoped>
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
  align-items: center;
  gap: 12px;
}

.muted {
  color: #94a3b8;
}
</style>
