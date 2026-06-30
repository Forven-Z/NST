<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  fetchPatientBillsByQuery,
  fetchPatientPaymentsByQuery,
  fetchShiftSummary,
  windowCharge,
} from '../../api/registrar'

const route = useRoute()
const medicalRecordNo = ref('')
const idCard = ref('')
const realName = ref('')
const loading = ref(false)
const charging = ref(false)
const bills = ref([])
const payments = ref([])
const selectedRows = ref([])
const payChannel = ref('CASH')
const tableRef = ref(null)
const autoSelectPending = ref(false)
const resolvedPatient = ref(null)
const activeTab = ref('pending')
const shiftVisible = ref(false)
const shiftLoading = ref(false)
const shiftSummary = ref(null)
const workDate = ref(new Date().toISOString().slice(0, 10))

const candidateVisible = ref(false)
const candidates = ref([])

const totalAmount = computed(() =>
  selectedRows.value.reduce((sum, row) => sum + Number(row.amount ?? row.totalAmount ?? 0), 0),
)

function buildQueryParams(extra = {}) {
  const params = { status: 0, ...extra }
  const mrn = medicalRecordNo.value.trim()
  const card = idCard.value.trim()
  const name = realName.value.trim()
  if (mrn) params.medicalRecordNo = mrn
  else if (card) params.idCard = card.toUpperCase()
  else if (name) params.realName = name
  else if (extra.patientId) params.patientId = extra.patientId
  return params
}

function hasAnySearchInput() {
  return !!(
    medicalRecordNo.value.trim()
    || idCard.value.trim()
    || realName.value.trim()
  )
}

async function loadBills(params) {
  loading.value = true
  try {
    const res = await fetchPatientBillsByQuery(params)
    const data = res.data ?? {}

    if (data.multiple && data.candidates?.length) {
      candidates.value = data.candidates
      candidateVisible.value = true
      bills.value = []
      payments.value = []
      selectedRows.value = []
      resolvedPatient.value = null
      return
    }

    bills.value = data.list ?? []
    selectedRows.value = []
    resolvedPatient.value = {
      patientId: data.patientId,
      medicalRecordNo: data.medicalRecordNo,
      realName: data.realName,
      gender: data.gender,
      age: data.age,
    }
    if (data.medicalRecordNo) {
      medicalRecordNo.value = data.medicalRecordNo
    }

    if (autoSelectPending.value && bills.value.length) {
      await nextTick()
      bills.value.forEach((row) => tableRef.value?.toggleRowSelection(row, true))
      autoSelectPending.value = false
    }
    if (activeTab.value === 'pending' && !bills.value.length) {
      ElMessage.info('该患者暂无待缴账单（可能已全部缴费）')
    }

    if (resolvedPatient.value?.patientId) {
      await loadPayments({ patientId: resolvedPatient.value.patientId })
    }
  } catch (err) {
    ElMessage.error(err.message || '查询失败')
    bills.value = []
    payments.value = []
    resolvedPatient.value = null
  } finally {
    loading.value = false
  }
}

async function loadPayments(params) {
  try {
    const res = await fetchPatientPaymentsByQuery(params)
    const data = res.data ?? {}
    if (data.multiple && data.candidates?.length) return
    payments.value = data.list ?? []
  } catch {
    payments.value = []
  }
}

async function onSearch() {
  if (!hasAnySearchInput()) {
    ElMessage.warning('请输入病历号、身份证号或姓名')
    return
  }
  await loadBills(buildQueryParams())
}

function onIdCardBlur() {
  const normalized = idCard.value.trim().toUpperCase()
  if (normalized !== idCard.value) {
    idCard.value = normalized
  }
}

async function onPickCandidate(row) {
  candidateVisible.value = false
  realName.value = row.realName ?? realName.value
  resolvedPatient.value = {
    patientId: row.patientId,
    medicalRecordNo: row.medicalRecordNo,
    realName: row.realName,
    gender: row.gender,
    age: row.age,
  }
  await loadBills({ status: 0, patientId: row.patientId })
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
    const data = res.data ?? {}
    const channelText = data.channelLabel || data.payChannel || payChannel.value
    const pid = data.paymentId
    ElMessage.success(
      pid
        ? `${data.message || '收费成功'}（流水 #${pid} · ${channelText}）`
        : data.message || '收费成功',
    )
    if (resolvedPatient.value?.medicalRecordNo) {
      medicalRecordNo.value = resolvedPatient.value.medicalRecordNo
      idCard.value = ''
      realName.value = ''
      await loadBills({ status: 0, medicalRecordNo: resolvedPatient.value.medicalRecordNo })
    } else {
      await onSearch()
    }
  } catch (err) {
    ElMessage.error(err.message || '收费失败')
  } finally {
    charging.value = false
  }
}

function formatGender(gender) {
  if (gender === 1) return '男'
  if (gender === 2) return '女'
  return '—'
}

function onTabChange(tab) {
  activeTab.value = tab
}

async function openShiftSummary() {
  shiftVisible.value = true
  shiftLoading.value = true
  try {
    const res = await fetchShiftSummary({ workDate: workDate.value })
    shiftSummary.value = res.data ?? null
  } catch (err) {
    ElMessage.error(err.message || '加载当班汇总失败')
    shiftSummary.value = null
  } finally {
    shiftLoading.value = false
  }
}

async function reloadShiftSummary() {
  shiftLoading.value = true
  try {
    const res = await fetchShiftSummary({ workDate: workDate.value })
    shiftSummary.value = res.data ?? null
  } catch (err) {
    ElMessage.error(err.message || '加载失败')
  } finally {
    shiftLoading.value = false
  }
}

onMounted(() => {
  const raw = route.query.medicalRecordNo ?? route.query.mr
  const no = typeof raw === 'string' ? raw.trim() : ''
  if (no) {
    medicalRecordNo.value = no
    autoSelectPending.value = true
    loadBills({ status: 0, medicalRecordNo: no })
  }
})
</script>

<template>
  <div class="charge-page">
    <div class="page-head">
      <div>
        <h2 class="page-title">窗口收费</h2>
        <p class="page-desc">
          门诊实行「先缴费后执行」：挂号费、检验/检查/处方/处置费均需先在本窗口结清，患者方可进入下一环节。
          病历号、身份证号按<strong>完整精确</strong>匹配；姓名按<strong>精确</strong>匹配，重名时需点选患者。
        </p>
      </div>
      <el-button type="primary" plain @click="openShiftSummary">当班汇总</el-button>
    </div>

    <el-card shadow="never" class="panel-card">
      <el-form inline class="search-form" @submit.prevent="onSearch">
        <el-form-item label="病历号">
          <el-input
            v-model="medicalRecordNo"
            placeholder="完整病历号"
            clearable
            style="width: 200px"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input
            v-model="idCard"
            placeholder="18位身份证号"
            maxlength="18"
            clearable
            style="width: 220px"
            @blur="onIdCardBlur"
            @keyup.enter="onSearch"
          />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input
            v-model="realName"
            placeholder="完整姓名"
            clearable
            style="width: 140px"
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

      <el-tabs v-model="activeTab" class="bill-tabs" @tab-change="onTabChange">
        <el-tab-pane label="待缴账单" name="pending" />
        <el-tab-pane label="已付流水" name="paid" />
      </el-tabs>

      <div v-if="resolvedPatient?.medicalRecordNo" class="patient-banner">
        <span class="patient-banner-label">当前患者</span>
        <span v-if="resolvedPatient.realName" class="patient-banner-name">{{ resolvedPatient.realName }}</span>
        <span class="patient-banner-item">
          病历号 <strong>{{ resolvedPatient.medicalRecordNo }}</strong>
        </span>
        <span v-if="resolvedPatient.gender != null" class="patient-banner-item">
          {{ formatGender(resolvedPatient.gender) }}
        </span>
        <span v-if="resolvedPatient.age != null && resolvedPatient.age !== ''" class="patient-banner-item">
          {{ resolvedPatient.age }} 岁
        </span>
      </div>

      <el-table
        v-if="activeTab === 'pending'"
        ref="tableRef"
        v-loading="loading"
        :data="bills"
        stripe
        empty-text="输入病历号、身份证号或姓名查询待缴项目"
        @selection-change="(rows) => (selectedRows = rows)"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column label="费用项目" min-width="200">
          <template #default="{ row }">{{ row.billTitle || row.itemName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="bizType" label="类型" width="110">
          <template #default="{ row }">
            {{
              {
                REGISTER: '挂号',
                REGIST: '挂号',
                INSPECTION: '检验',
                CHECK: '检查',
                PRESCRIPTION: '处方',
                DISPOSAL: '处置',
              }[row.bizType] || row.bizType || '—'
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

      <el-table
        v-else
        v-loading="loading"
        :data="payments"
        stripe
        empty-text="查询后将显示该患者的已付流水"
      >
        <el-table-column prop="paymentId" label="流水号" width="100" />
        <el-table-column prop="summary" label="摘要" min-width="200" />
        <el-table-column label="金额" width="100" align="right">
          <template #default="{ row }">
            <span class="fee">¥{{ row.amount ?? row.totalAmount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="支付方式" width="100">
          <template #default="{ row }">{{ row.channelLabel || row.channel || '—' }}</template>
        </el-table-column>
        <el-table-column label="支付时间" min-width="160">
          <template #default="{ row }">{{ row.paidAt || row.payTime || '—' }}</template>
        </el-table-column>
      </el-table>

      <div v-if="activeTab === 'pending' && bills.length" class="settle-bar">
        <div class="settle-info">
          已选 <strong>{{ selectedRows.length }}</strong> 项，合计
          <span class="fee-lg">¥{{ totalAmount.toFixed(2) }}</span>
        </div>
        <el-button type="primary" size="large" :loading="charging" @click="onCharge">
          确认收费
        </el-button>
      </div>
    </el-card>

    <el-dialog v-model="candidateVisible" title="重名患者，请选择" width="560px" align-center>
      <el-table :data="candidates" stripe @row-click="onPickCandidate">
        <el-table-column prop="realName" label="姓名" width="100" />
        <el-table-column prop="medicalRecordNo" label="病历号" min-width="150" />
        <el-table-column label="性别" width="72">
          <template #default="{ row }">{{ formatGender(row.gender) }}</template>
        </el-table-column>
        <el-table-column prop="age" label="年龄" width="72" />
        <el-table-column prop="idCard" label="身份证" min-width="160" />
        <el-table-column label="操作" width="88" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="onPickCandidate(row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="shiftVisible" title="当班收费汇总" width="520px" align-center>
      <el-form inline class="shift-form">
        <el-form-item label="日期">
          <el-date-picker
            v-model="workDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 160px"
            @change="reloadShiftSummary"
          />
        </el-form-item>
      </el-form>
      <div v-loading="shiftLoading">
        <template v-if="shiftSummary">
          <div class="shift-grid">
            <div class="shift-stat">
              <span class="shift-label">收费笔数</span>
              <strong>{{ shiftSummary.paymentCount ?? 0 }}</strong>
            </div>
            <div class="shift-stat">
              <span class="shift-label">收费合计</span>
              <strong class="fee">¥{{ shiftSummary.paymentTotal ?? 0 }}</strong>
            </div>
            <div class="shift-stat">
              <span class="shift-label">退费笔数</span>
              <strong>{{ shiftSummary.refundCount ?? 0 }}</strong>
            </div>
            <div class="shift-stat">
              <span class="shift-label">退费合计</span>
              <strong class="fee">¥{{ shiftSummary.refundTotal ?? 0 }}</strong>
            </div>
          </div>
          <div class="shift-net">
            净收 <span class="fee-lg">¥{{ shiftSummary.netTotal ?? 0 }}</span>
          </div>
          <div v-if="shiftSummary.paymentsByChannel?.length" class="shift-channels">
            <h4>收费按渠道</h4>
            <el-table :data="shiftSummary.paymentsByChannel" size="small" stripe>
              <el-table-column prop="channelLabel" label="渠道" />
              <el-table-column prop="count" label="笔数" width="72" />
              <el-table-column label="金额" width="100" align="right">
                <template #default="{ row }">¥{{ row.totalAmount }}</template>
              </el-table-column>
            </el-table>
          </div>
        </template>
        <el-empty v-else-if="!shiftLoading" description="暂无汇总数据" />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.charge-page {
  max-width: 1080px;
}

.page-head {
  margin-bottom: 16px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.bill-tabs {
  margin-bottom: 12px;
}

.shift-form {
  margin-bottom: 8px;
}

.shift-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.shift-stat {
  padding: 12px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.shift-label {
  display: block;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 4px;
}

.shift-net {
  margin-bottom: 16px;
  font-size: 14px;
  color: #475569;
}

.shift-channels h4 {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
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

.page-desc strong {
  color: #475569;
  font-weight: 600;
}

.panel-card {
  border-radius: 10px;
  border: 1px solid #e2e8f0;
}

.search-form {
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.patient-banner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 16px;
  margin: 0 0 14px;
  padding: 10px 14px;
  border-radius: 8px;
  background: #f0f9ff;
  border: 1px solid #bae6fd;
  font-size: 13px;
  color: #334155;
}

.patient-banner-label {
  font-weight: 600;
  color: #0369a1;
}

.patient-banner-name {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.patient-banner-item strong {
  font-weight: 600;
  color: #0f172a;
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

