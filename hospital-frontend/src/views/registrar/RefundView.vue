<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelRegister,
  fetchPatientBillsByQuery,
  fetchPatientRefundsByQuery,
  fetchPatientRegistersByQuery,
  refundBill,
} from '../../api/registrar'

const medicalRecordNo = ref('')
const idCard = ref('')
const realName = ref('')
const loading = ref(false)
const refundingId = ref(null)
const cancellingId = ref(null)
const bills = ref([])
const refunds = ref([])
const registers = ref([])
const resolvedPatient = ref(null)

const candidateVisible = ref(false)
const candidates = ref([])

const statusMap = {
  0: { label: '待支付', type: 'info' },
  1: { label: '已支付', type: 'success' },
  2: { label: '已退款', type: 'warning' },
}

const bizLabel = {
  REGISTER: '挂号',
  REGIST: '挂号',
  INSPECTION: '检验',
  CHECK: '检查',
  PRESCRIPTION: '处方',
  DISPOSAL: '处置',
}

const visitStateTag = {
  0: 'info',
  1: 'success',
  2: 'warning',
  3: '',
  4: 'info',
}

function buildQueryParams(extra = {}) {
  const params = { ...extra }
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

function applyPatientData(data) {
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
}

function isRegisterBill(row) {
  return row.bizType === 'REGISTER' || row.bizType === 'REGIST'
}

function filterRefundableBills(list) {
  return (list ?? []).filter((row) => row.status === 1 && !isRegisterBill(row))
}

async function loadPatientData(params) {
  loading.value = true
  try {
    const [regRes, billRes, refundRes] = await Promise.all([
      fetchPatientRegistersByQuery(params),
      fetchPatientBillsByQuery({ ...params, status: 1 }),
      fetchPatientRefundsByQuery(params),
    ])
    const regData = regRes.data ?? {}
    const billData = billRes.data ?? {}
    const refundData = refundRes.data ?? {}

    if (regData.multiple && regData.candidates?.length) {
      candidates.value = regData.candidates
      candidateVisible.value = true
      registers.value = []
      bills.value = []
      refunds.value = []
      resolvedPatient.value = null
      return
    }

    applyPatientData(regData)
    registers.value = regData.list ?? []
    bills.value = filterRefundableBills(billData.list)
    refunds.value = refundData.list ?? []

    if (!registers.value.length && !bills.value.length && !refunds.value.length) {
      ElMessage.info('该患者暂无可退号或可退费记录')
    }
  } catch (err) {
    ElMessage.error(err.message || '查询失败')
    registers.value = []
    bills.value = []
    refunds.value = []
    resolvedPatient.value = null
  } finally {
    loading.value = false
  }
}

async function onSearch() {
  if (!hasAnySearchInput()) {
    ElMessage.warning('请输入病历号、身份证号或姓名')
    return
  }
  await loadPatientData(buildQueryParams())
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
  await loadPatientData({ patientId: row.patientId })
}

async function reloadAfterAction() {
  if (resolvedPatient.value?.medicalRecordNo) {
    await loadPatientData({
      medicalRecordNo: resolvedPatient.value.medicalRecordNo,
    })
  } else {
    await onSearch()
  }
}

async function onCancelRegister(row) {
  const isPending = row.visitState === 0
  const feeText = row.registFee != null ? ` ¥${row.registFee}` : ''
  const title = isPending ? '确认取消挂号' : '确认退号'
  const deptDoctor = [row.deptName, row.doctorName].filter(Boolean).join(' · ')
  const level = row.registLevelName ? `（${row.registLevelName}）` : ''
  try {
    await ElMessageBox.confirm(
      isPending
        ? `确认取消【${deptDoctor}${level}】待支付挂号？取消后号源将释放。`
        : `确认为【${deptDoctor}${level}】退号并退还挂号费${feeText}？仅未接诊的挂号可退号。`,
      title,
      { type: 'warning' },
    )
  } catch {
    return
  }
  cancellingId.value = row.registerId
  try {
    const res = await cancelRegister(row.registerId, {
      reason: isPending ? '窗口取消待支付挂号' : '窗口退号',
    })
    ElMessage.success(res.data?.message || (isPending ? '取消成功' : '退号成功'))
    await reloadAfterAction()
  } catch (err) {
    ElMessage.error(err.message || '操作失败')
  } finally {
    cancellingId.value = null
  }
}

async function onRefund(row) {
  const itemName = row.billTitle || row.itemName || '费用项目'
  try {
    await ElMessageBox.confirm(
      `确认为【${itemName}】退费 ¥${row.amount ?? row.totalAmount}？\n未执行的检验/检查/处置/未发药处方方可退费。`,
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
    await reloadAfterAction()
  } catch (err) {
    ElMessage.error(err.message || '退费失败')
  } finally {
    refundingId.value = null
  }
}

function formatGender(gender) {
  if (gender === 1) return '男'
  if (gender === 2) return '女'
  return '—'
}
</script>

<template>
  <div class="refund-page">
    <div class="page-head">
      <div>
        <h2 class="page-title">退费退号</h2>
        <p class="page-desc">
          挂号费请通过<strong>退号</strong>办理（待支付 10 分钟内可取消；<strong>已挂号且医生未叫号</strong>可退号退款）；检验/检查/处方/处置等已支付未执行项目在本页<strong>退费</strong>。
          病历号、身份证号按<strong>完整精确</strong>匹配；姓名按<strong>精确</strong>匹配，重名时需点选患者。
        </p>
      </div>
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
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="onSearch">查询</el-button>
        </el-form-item>
      </el-form>

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

      <section class="section-block">
        <h3 class="section-title">挂号记录 · 退号</h3>
        <el-table
          v-loading="loading"
          :data="registers"
          stripe
          empty-text="查询后将显示可退号的挂号记录"
        >
          <el-table-column label="就诊日期" width="120">
            <template #default="{ row }">{{ row.workDate || '—' }}</template>
          </el-table-column>
          <el-table-column prop="noonLabel" label="午别" width="72" />
          <el-table-column prop="deptName" label="科室" min-width="100" />
          <el-table-column prop="doctorName" label="医生" min-width="100" />
          <el-table-column prop="registLevelName" label="号别" width="100" />
          <el-table-column label="挂号费" width="90" align="right">
            <template #default="{ row }">
              <span v-if="row.registFee != null" class="fee">¥{{ row.registFee }}</span>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="visitStateTag[row.visitState] || 'info'" size="small">
                {{ row.visitStateLabel || row.visitState }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="row.cancellable"
                type="primary"
                link
                :loading="cancellingId === row.registerId"
                @click="onCancelRegister(row)"
              >
                {{ row.visitState === 0 ? '取消' : '退号' }}
              </el-button>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="section-block">
        <h3 class="section-title">已支付项目 · 退费</h3>
        <el-table
          v-loading="loading"
          :data="bills"
          stripe
          empty-text="查询后将显示可退费的已支付项目（不含挂号费）"
        >
          <el-table-column label="费用项目" min-width="200">
            <template #default="{ row }">{{ row.billTitle || row.itemName || '—' }}</template>
          </el-table-column>
          <el-table-column label="类型" width="110">
            <template #default="{ row }">
              {{ bizLabel[row.bizType] || row.bizType || '—' }}
            </template>
          </el-table-column>
          <el-table-column label="金额" width="100" align="right">
            <template #default="{ row }">
              <span class="fee">¥{{ row.amount ?? row.totalAmount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusMap[row.status]?.type || 'info'" size="small">
                {{ statusMap[row.status]?.label || row.statusText || row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button
                type="primary"
                link
                :loading="refundingId === row.id"
                @click="onRefund(row)"
              >
                退费
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="section-block">
        <h3 class="section-title">退款记录</h3>
        <el-table
          v-loading="loading"
          :data="refunds"
          stripe
          empty-text="查询后将显示该患者的退款流水"
        >
          <el-table-column prop="refundId" label="退款号" width="100" />
          <el-table-column label="项目" min-width="180">
            <template #default="{ row }">{{ row.billTitle || '—' }}</template>
          </el-table-column>
          <el-table-column label="金额" width="100" align="right">
            <template #default="{ row }">
              <span class="fee">¥{{ row.refundAmount ?? row.amount }}</span>
            </template>
          </el-table-column>
          <el-table-column label="渠道" width="100">
            <template #default="{ row }">{{ row.channelLabel || row.channel || '—' }}</template>
          </el-table-column>
          <el-table-column prop="reason" label="原因" min-width="120" />
          <el-table-column label="退款时间" min-width="160">
            <template #default="{ row }">{{ row.refundTime || '—' }}</template>
          </el-table-column>
        </el-table>
      </section>
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
  </div>
</template>

<style scoped>
.refund-page {
  max-width: 1080px;
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

.section-block {
  margin-top: 20px;
}

.section-block:first-of-type {
  margin-top: 4px;
}

.section-title {
  margin: 0 0 10px;
  font-size: 15px;
  font-weight: 600;
  color: #334155;
}

.fee {
  color: #ea580c;
  font-weight: 600;
}

.muted {
  color: #94a3b8;
}
</style>
