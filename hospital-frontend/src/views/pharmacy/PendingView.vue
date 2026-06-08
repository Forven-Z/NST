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
  10: { label: '待缴费', type: 'info' },
  20: { label: '待发药', type: 'warning' },
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
      `请核对患者身份与处方内容后发药\n患者：${row.patientName}（${row.medicalRecordNo}）\n处方ID：${row.prescriptionId}`,
      '确认发药',
      { type: 'warning' },
    )
  } catch {
    return
  }

  dispensingId.value = row.prescriptionId
  try {
    await dispensePrescription(row.prescriptionId)
    ElMessage.success('发药成功，请交代用药注意事项')
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
      `退药后需至收费窗口退费\n处方 #${row.prescriptionId} · ${row.patientName}`,
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

function formatItems(row) {
  if (!row.items?.length) return '—'
  return row.items.map((i) => `${i.drugName}×${i.quantity}`).join('、')
}
</script>

<template>
  <div class="pharmacy-page">
    <div class="page-head">
      <h2 class="page-title">处方发药</h2>
      <p class="page-desc">
        门诊处方须「医生开立 → 患者缴费 → 药房发药」；发药前须核对病历号、姓名与处方内容，交代用法用量。
      </p>
    </div>

    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-header">
          <span>处方列表</span>
          <div class="filters">
            <el-radio-group v-model="statusFilter" @change="loadList">
              <el-radio-button :label="20">待发药（已缴费）</el-radio-button>
              <el-radio-button :label="30">已发药</el-radio-button>
            </el-radio-group>
            <el-button :loading="loading" @click="loadList">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="list" empty-text="暂无处方（Mock：李小红 MR202606040002 有待发药处方）">
        <el-table-column prop="prescriptionId" label="处方ID" width="100" />
        <el-table-column prop="medicalRecordNo" label="病历号" width="140" />
        <el-table-column prop="patientName" label="患者" width="100" />
        <el-table-column prop="doctorName" label="开方医生" width="100" />
        <el-table-column label="药品" min-width="160">
          <template #default="{ row }">{{ formatItems(row) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="90">
          <template #default="{ row }">¥{{ row.totalAmount }}</template>
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
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.pharmacy-page {
  max-width: 1100px;
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
  flex-wrap: wrap;
}

.filters {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
