<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchCheckResult,
  fetchDisposalResult,
  fetchInspectionResult,
  fetchRegisterOrders,
} from '../../api/doctor'

const props = defineProps({
  registerId: { type: Number, default: null },
})

const loading = ref(false)
const orders = ref(null)
const resultDialogVisible = ref(false)
const resultDetail = ref(null)

const statusMap = {
  10: { label: '已开立', type: 'info' },
  20: { label: '已缴费', type: 'warning' },
  30: { label: '执行完成', type: 'primary' },
  40: { label: '已出结果', type: 'success' },
  50: { label: '已退费', type: 'danger' },
}

watch(
  () => props.registerId,
  (id) => {
    resultDialogVisible.value = false
    resultDetail.value = null
    if (id) loadOrders()
    else orders.value = null
  },
  { immediate: true },
)

async function loadOrders() {
  if (!props.registerId) return
  loading.value = true
  try {
    const res = await fetchRegisterOrders(props.registerId)
    orders.value = res.data
  } catch (err) {
    ElMessage.error(err.message || '加载医嘱失败')
    orders.value = null
  } finally {
    loading.value = false
  }
}

async function onViewResult(row) {
  try {
    let res
    if (row.kind === 'inspection') {
      res = await fetchInspectionResult(row.requestId)
    } else if (row.kind === 'check') {
      res = await fetchCheckResult(row.requestId)
    } else if (row.kind === 'disposal') {
      res = await fetchDisposalResult(row.requestId)
    } else {
      return ElMessage.info('处方无文字报告，请至药房查看发药状态')
    }
    resultDetail.value = { ...row, ...res.data }
    resultDialogVisible.value = true
  } catch (err) {
    ElMessage.warning(err.message || '结果尚未出具')
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
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button
            v-if="row.kind !== 'prescription'"
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
      width="560px"
      destroy-on-close
    >
      <template v-if="resultDetail">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="结果文本">
            <pre class="result-text">{{ resultDetail.resultText || '（无）' }}</pre>
          </el-descriptions-item>
          <el-descriptions-item v-if="resultDetail.resultAttachment" label="附件">
            {{ resultDetail.resultAttachment }}
          </el-descriptions-item>
          <el-descriptions-item v-if="resultDetail.reportTime" label="报告时间">
            {{ resultDetail.reportTime }}
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-dialog>
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
</style>
