<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchPatientBillsByQuery, windowCharge } from '../../api/registrar'

const route = useRoute()
const medicalRecordNo = ref('')
const idCard = ref('')
const realName = ref('')
const loading = ref(false)
const charging = ref(false)
const bills = ref([])
const selectedRows = ref([])
const payChannel = ref('CASH')
const tableRef = ref(null)
const autoSelectPending = ref(false)
const resolvedPatient = ref(null)

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
      selectedRows.value = []
      resolvedPatient.value = null
      return
    }

    bills.value = data.list ?? []
    selectedRows.value = []
    resolvedPatient.value = {
      patientId: data.patientId,
      medicalRecordNo: data.medicalRecordNo,
    }
    if (data.medicalRecordNo) {
      medicalRecordNo.value = data.medicalRecordNo
    }

    if (autoSelectPending.value && bills.value.length) {
      await nextTick()
      bills.value.forEach((row) => tableRef.value?.toggleRowSelection(row, true))
      autoSelectPending.value = false
    }
    if (!bills.value.length) {
      ElMessage.info('该患者暂无待缴账单（可能已全部缴费）')
    }
  } catch (err) {
    ElMessage.error(err.message || '查询失败')
    bills.value = []
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
    ElMessage.success(res.data?.message || '收费成功')
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

      <p v-if="resolvedPatient?.medicalRecordNo" class="resolved-hint">
        当前患者：病历号 <strong>{{ resolvedPatient.medicalRecordNo }}</strong>
      </p>

      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="bills"
        stripe
        empty-text="输入病历号、身份证号或姓名查询待缴项目"
        @selection-change="(rows) => (selectedRows = rows)"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="id" label="账单ID" min-width="100" />
        <el-table-column label="费用项目" min-width="160">
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
.charge-page {
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

.resolved-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: #64748b;
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
