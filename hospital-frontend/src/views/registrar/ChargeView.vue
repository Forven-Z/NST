<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchPatientBills, windowCharge } from '../../api/registrar'

const medicalRecordNo = ref('')
const loading = ref(false)
const charging = ref(false)
const bills = ref([])
const selectedRows = ref([])
const payChannel = ref('CASH')

const demoMrList = ['MR202606040001', 'MR202606040002', 'MR202606040003']

const totalAmount = computed(() =>
  selectedRows.value.reduce((sum, row) => sum + Number(row.amount ?? row.totalAmount ?? 0), 0),
)

async function onSearch() {
  const no = medicalRecordNo.value.trim()
  if (!no) {
    ElMessage.warning('请输入病历号')
    return
  }
  loading.value = true
  try {
    const res = await fetchPatientBills(no, { status: 0 })
    bills.value = res.data?.list ?? res.data ?? []
    selectedRows.value = []
    if (!bills.value.length) ElMessage.info('该病历号暂无待缴账单（可能已全部缴费）')
  } catch (err) {
    ElMessage.error(err.message || '查询失败')
    bills.value = []
  } finally {
    loading.value = false
  }
}

function fillDemo(mr) {
  medicalRecordNo.value = mr
  onSearch()
}

async function onCharge() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请勾选待缴账单')
    return
  }
  charging.value = true
  try {
    const billIds = selectedRows.value.map((r) => r.id ?? r.billId)
    const res = await windowCharge({ billIds, payChannel: payChannel.value })
    ElMessage.success(res.data?.message || '收费成功')
    await onSearch()
  } catch (err) {
    ElMessage.error(err.message || '收费失败')
  } finally {
    charging.value = false
  }
}
</script>

<template>
  <div class="charge-page">
    <div class="page-head">
      <div>
        <h2 class="page-title">窗口收费</h2>
        <p class="page-desc">
          门诊实行「先缴费后执行」：挂号费、检验/检查/处方/处置费均需先在本窗口结清，患者方可进入下一环节。
        </p>
      </div>
    </div>

    <el-card shadow="never" class="panel-card">
      <el-form inline class="search-form" @submit.prevent="onSearch">
        <el-form-item label="病历号">
          <el-input
            v-model="medicalRecordNo"
            placeholder="扫描或输入病历号"
            clearable
            style="width: 280px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label="支付方式">
          <el-select v-model="payChannel" style="width: 120px">
            <el-option label="现金" value="CASH" />
            <el-option label="微信" value="WECHAT" />
            <el-option label="支付宝" value="ALIPAY" />
            <el-option label="医保" value="INSURANCE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="onSearch">查询待缴</el-button>
        </el-form-item>
      </el-form>

      <div class="demo-mr">
        <span class="demo-label">演示病历号：</span>
        <el-button
          v-for="mr in demoMrList"
          :key="mr"
          size="small"
          link
          type="primary"
          @click="fillDemo(mr)"
        >
          {{ mr }}
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="bills"
        stripe
        empty-text="输入病历号查询待缴项目"
        @selection-change="(rows) => (selectedRows = rows)"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="id" label="账单ID" min-width="100" />
        <el-table-column prop="itemName" label="费用项目" min-width="160" />
        <el-table-column prop="bizType" label="类型" width="110">
          <template #default="{ row }">
            {{
              { REGIST: '挂号', INSPECTION: '检验', CHECK: '检查', PRESCRIPTION: '处方', DISPOSAL: '处置' }[
                row.bizType
              ] || row.bizType || '—'
            }}
          </template>
        </el-table-column>
        <el-table-column label="金额" width="100" align="right">
          <template #default="{ row }">
            <span class="fee">¥{{ row.amount ?? row.totalAmount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="statusText" label="状态" width="100">
          <template #default="{ row }">
            <el-tag type="warning" size="small">{{ row.statusText || '待支付' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="bills.length" class="settle-bar">
        <div class="settle-info">
          已选 <strong>{{ selectedRows.length }}</strong> 项，合计
          <span class="fee-lg">¥{{ totalAmount.toFixed(2) }}</span>
        </div>
        <el-button type="primary" size="large" :loading="charging" @click="onCharge">
          确认收费
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.charge-page {
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

.panel-card {
  border-radius: 10px;
  border: 1px solid #e2e8f0;
}

.search-form {
  margin-bottom: 8px;
}

.demo-mr {
  margin-bottom: 16px;
  font-size: 13px;
  color: #64748b;
}

.demo-label {
  margin-right: 4px;
}

.fee {
  color: #ea580c;
  font-weight: 600;
}

.fee-lg {
  color: #ea580c;
  font-weight: 700;
  font-size: 20px;
  margin-left: 8px;
}

.settle-bar {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.settle-info {
  font-size: 14px;
  color: #475569;
}
</style>
