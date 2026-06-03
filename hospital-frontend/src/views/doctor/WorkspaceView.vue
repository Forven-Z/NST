<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { callPatient, createInspectionOrder, createPrescription, fetchDoctorQueue, fetchInspectionResult, fetchMedicalRecord, saveMedicalRecord } from '../../api/doctor'

const loading = ref(false)
const saving = ref(false)
const callingId = ref(null)
const queue = ref([])
const visitStateFilter = ref('all')
const currentRegisterId = ref(null)
const currentPatient = ref(null)

const recordForm = reactive({
  readme: '',
  present: '',
  presentTreat: '',
  history: '',
  allergy: '',
  physique: '',
  diagnosis: '',
  cure: '',
  checkAdvice: '',
  inspectionAdvice: '',
})

const orderingInspection = ref(false)
const inspectionResult = ref(null)
const lastInspectionId = ref(null)

/** seed: INS-BLOOD 血常规 */
const DEFAULT_INSPECTION_ITEM_ID = 2
/** seed: 阿莫西林 */
const DEFAULT_DRUG_ID = 1

const orderingPrescription = ref(false)
const lastPrescription = ref(null)

const visitStateMap = {
  0: { label: '待支付', type: 'info' },
  1: { label: '已挂号', type: 'warning' },
  2: { label: '接诊中', type: 'success' },
  3: { label: '看诊结束', type: 'info' },
}

onMounted(loadQueue)

async function loadQueue() {
  loading.value = true
  try {
    const res = await fetchDoctorQueue({
      visitState: visitStateFilter.value === 'all' ? undefined : Number(visitStateFilter.value),
      page: 1,
      pageSize: 50,
    })
    queue.value = res.data?.list ?? []
  } catch (err) {
    ElMessage.error(err.message || '加载队列失败')
  } finally {
    loading.value = false
  }
}

async function onCall(row) {
  callingId.value = row.registerId
  try {
    await callPatient(row.registerId)
    ElMessage.success(`已开始接诊：${row.patientName}`)
    currentRegisterId.value = row.registerId
    currentPatient.value = row
    await loadQueue()
    await loadMedicalRecord(row.registerId)
  } catch (err) {
    ElMessage.error(err.message || '叫号失败')
  } finally {
    callingId.value = null
  }
}

async function onSelectRow(row) {
  if (row.visitState !== 2) {
    ElMessage.info('请先叫号后再编辑病历')
    return
  }
  currentRegisterId.value = row.registerId
  currentPatient.value = row
  await loadMedicalRecord(row.registerId)
}

async function loadMedicalRecord(registerId) {
  try {
    const res = await fetchMedicalRecord(registerId)
    const data = res.data || {}
    Object.assign(recordForm, {
      readme: data.readme || '',
      present: data.present || '',
      presentTreat: data.presentTreat || '',
      history: data.history || '',
      allergy: data.allergy || '',
      physique: data.physique || '',
      diagnosis: data.diagnosis || '',
      cure: data.cure || '',
      checkAdvice: data.checkAdvice || '',
      inspectionAdvice: data.inspectionAdvice || '',
    })
  } catch (err) {
    ElMessage.error(err.message || '加载病历失败')
  }
}

async function onSaveRecord() {
  if (!currentRegisterId.value) {
    ElMessage.warning('请先选择接诊中的患者')
    return
  }
  saving.value = true
  try {
    await saveMedicalRecord(currentRegisterId.value, { ...recordForm })
    ElMessage.success('病历已保存')
  } catch (err) {
    ElMessage.error(err.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onOrderInspection() {
  if (!currentRegisterId.value) {
    ElMessage.warning('请先选择接诊中的患者')
    return
  }
  orderingInspection.value = true
  inspectionResult.value = null
  try {
    const res = await createInspectionOrder({
      registerId: currentRegisterId.value,
      medicalTechnologyId: DEFAULT_INSPECTION_ITEM_ID,
      purpose: 'Routine blood test',
      bodyPart: 'Blood',
    })
    lastInspectionId.value = res.data?.inspectionRequestId
    ElMessage.success(`已开立检验：${res.data?.itemName}，请患者缴费`)
  } catch (err) {
    ElMessage.error(err.message || '开检验失败')
  } finally {
    orderingInspection.value = false
  }
}

async function onOrderPrescription() {
  if (!currentRegisterId.value) {
    ElMessage.warning('请先选择接诊中的患者')
    return
  }
  orderingPrescription.value = true
  try {
    const res = await createPrescription({
      registerId: currentRegisterId.value,
      remark: 'Outpatient prescription',
      items: [{
        drugId: DEFAULT_DRUG_ID,
        quantity: 2,
        usageMethod: 'oral',
        dosage: '0.5g',
        frequency: 'tid',
        days: 7,
        entrust: 'after meal',
      }],
    })
    lastPrescription.value = res.data
    ElMessage.success(`已开立处方：${res.data?.prescriptionNo}，请患者缴费后至药房取药`)
  } catch (err) {
    ElMessage.error(err.message || '开处方失败')
  } finally {
    orderingPrescription.value = false
  }
}

async function onFetchInspectionResult() {
  if (!lastInspectionId.value) {
    ElMessage.info('请先开立检验')
    return
  }
  try {
    const res = await fetchInspectionResult(lastInspectionId.value)
    inspectionResult.value = res.data
    ElMessage.success('已获取检验结果')
  } catch (err) {
    ElMessage.error(err.message || '暂无检验结果')
  }
}

function formatGender(gender) {
  if (gender === 1) return '男'
  if (gender === 2) return '女'
  return '-'
}
</script>

<template>
  <div class="workspace">
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-header">
          <span>今日候诊队列</span>
          <div class="filters">
            <el-radio-group v-model="visitStateFilter" @change="loadQueue">
              <el-radio-button label="all">全部</el-radio-button>
              <el-radio-button label="1">已挂号</el-radio-button>
              <el-radio-button label="2">接诊中</el-radio-button>
            </el-radio-group>
            <el-button :loading="loading" @click="loadQueue">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="queue"
        highlight-current-row
        empty-text="暂无候诊患者（需患者完成挂号并模拟支付）"
        @row-click="onSelectRow"
      >
        <el-table-column prop="medicalRecordNo" label="病历号" width="140" />
        <el-table-column prop="patientName" label="姓名" width="100" />
        <el-table-column label="性别" width="70">
          <template #default="{ row }">{{ formatGender(row.gender) }}</template>
        </el-table-column>
        <el-table-column prop="age" label="年龄" width="70" />
        <el-table-column prop="registLevelName" label="号别" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="visitStateMap[row.visitState]?.type || 'info'" size="small">
              {{ visitStateMap[row.visitState]?.label || row.visitState }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.visitState === 1"
              type="primary"
              link
              :loading="callingId === row.registerId"
              @click.stop="onCall(row)"
            >
              叫号
            </el-button>
            <span v-else-if="row.visitState === 2" class="muted">接诊中</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="section-card record-card">
      <template #header>
        <div class="card-header">
          <span>
            电子病历
            <template v-if="currentPatient">
              — {{ currentPatient.patientName }}（{{ currentPatient.medicalRecordNo }}）
            </template>
          </span>
          <div class="header-actions">
            <el-button
              :disabled="!currentRegisterId"
              :loading="orderingPrescription"
              @click="onOrderPrescription"
            >
              开阿莫西林
            </el-button>
            <el-button
              :disabled="!currentRegisterId"
              :loading="orderingInspection"
              @click="onOrderInspection"
            >
              开血常规
            </el-button>
            <el-button
              :disabled="!lastInspectionId"
              @click="onFetchInspectionResult"
            >
              查看检验结果
            </el-button>
            <el-button
              type="primary"
              :loading="saving"
              :disabled="!currentRegisterId"
              @click="onSaveRecord"
            >
              保存病历
            </el-button>
          </div>
        </div>
      </template>

      <el-form v-if="currentRegisterId" label-position="top" class="record-form">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="主诉">
              <el-input v-model="recordForm.readme" type="textarea" :rows="2" placeholder="头痛三天..." />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="现病史">
              <el-input v-model="recordForm.present" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="过敏史">
              <el-input v-model="recordForm.allergy" placeholder="青霉素过敏" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="体格检查">
              <el-input v-model="recordForm.physique" placeholder="T 36.5℃..." />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="初步诊断">
              <el-input v-model="recordForm.diagnosis" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="处理意见">
              <el-input v-model="recordForm.cure" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="检查建议">
              <el-input v-model="recordForm.checkAdvice" placeholder="头部 CT" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="检验建议">
              <el-input v-model="recordForm.inspectionAdvice" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-alert
          v-if="lastPrescription"
          type="info"
          :closable="false"
          show-icon
          :title="`处方 ${lastPrescription.prescriptionNo}`"
          :description="`金额 ¥${lastPrescription.totalAmount}，状态：待缴费`"
          class="inspection-result"
        />
        <el-alert
          v-if="inspectionResult"
          type="success"
          :closable="false"
          show-icon
          :title="`检验结果：${inspectionResult.itemName}`"
          :description="inspectionResult.resultText"
          class="inspection-result"
        />
      </el-form>
      <el-empty v-else description="请从队列中选择「接诊中」患者，或先叫号" />
    </el-card>
  </div>
</template>

<style scoped>
.workspace {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-card {
  border-radius: 10px;
}

.record-card {
  min-height: 360px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.header-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.inspection-result {
  margin-top: 12px;
}

.filters {
  display: flex;
  align-items: center;
  gap: 12px;
}

.muted {
  color: #94a3b8;
  font-size: 13px;
}

.record-form {
  padding-top: 4px;
}
</style>
