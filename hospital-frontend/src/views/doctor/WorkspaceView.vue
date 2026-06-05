<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  callPatient,
  createCheckOrder,
  createInspectionOrder,
  createPrescription,
  fetchDoctorQueue,
  fetchInspectionResult,
  fetchMedicalRecord,
  finishVisit,
  saveMedicalRecord,
} from '../../api/doctor'
import AiDiagnosisBar from '../../components/doctor/AiDiagnosisBar.vue'

const loading = ref(false)
const saving = ref(false)
const callingId = ref(null)
const finishingId = ref(null)
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
const orderingPrescription = ref(false)
const orderingCheck = ref(false)
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
  if (!currentRegisterId.value) return ElMessage.warning('请先选择接诊中的患者')
  orderingInspection.value = true
  inspectionResult.value = null
  try {
    const res = await createInspectionOrder({
      registerId: currentRegisterId.value,
      medicalTechnologyId: 2,
      purpose: '感染/贫血筛查',
      bodyPart: '血液',
    })
    lastInspectionId.value = res.data?.inspectionRequestId
    ElMessage.success(`已开立检验：${res.data?.itemName}，请患者至收费处缴费后至检验科采血`)
  } catch (err) {
    ElMessage.error(err.message || '开检验失败')
  } finally {
    orderingInspection.value = false
  }
}

async function onOrderCheck() {
  if (!currentRegisterId.value) return ElMessage.warning('请先选择接诊中的患者')
  orderingCheck.value = true
  try {
    const res = await createCheckOrder({
      registerId: currentRegisterId.value,
      medicalTechnologyId: 1,
      purpose: '排除颅内病变',
      bodyPart: '头部',
      fromAi: false,
    })
    ElMessage.success(`已开立检查：${res.data?.itemName || '头部 CT'}，请患者缴费后至放射科登记`)
  } catch (err) {
    ElMessage.error(err.message || '开检查失败')
  } finally {
    orderingCheck.value = false
  }
}

async function onOrderPrescription() {
  if (!currentRegisterId.value) return ElMessage.warning('请先选择接诊中的患者')
  orderingPrescription.value = true
  try {
    const res = await createPrescription({
      registerId: currentRegisterId.value,
      remark: '门诊处方',
      items: [{
        drugId: 1,
        quantity: 2,
        usageMethod: '口服',
        dosage: '0.5g',
        frequency: 'tid',
        days: 7,
        entrust: '饭后服用',
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
  if (!lastInspectionId.value) return ElMessage.info('请先开立检验并完成缴费、检验科出报告')
  try {
    const res = await fetchInspectionResult(lastInspectionId.value)
    inspectionResult.value = res.data
    ElMessage.success('已获取检验结果')
  } catch (err) {
    ElMessage.error(err.message || '暂无检验结果')
  } finally {
    /* noop */
  }
}

async function onFinishVisit() {
  if (!currentRegisterId.value) return
  finishingId.value = currentRegisterId.value
  try {
    await finishVisit(currentRegisterId.value)
    ElMessage.success('看诊已结束')
    currentRegisterId.value = null
    currentPatient.value = null
    await loadQueue()
  } catch (err) {
    ElMessage.error(err.message || '操作失败')
  } finally {
    finishingId.value = null
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
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="flow-tip"
      title="门诊流程"
      description="患者挂号并缴费 → 医生叫号接诊 → 书写病历/开单 → 患者再次缴费 → 检验/检查/药房/处置 → 结束看诊"
    />

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
        empty-text="暂无候诊患者（Mock 演示：王小明 MR202606040001 已挂号待叫号）"
        @row-click="onSelectRow"
      >
        <el-table-column prop="medicalRecordNo" label="病历号" width="150" />
        <el-table-column prop="patientName" label="姓名" width="100" />
        <el-table-column label="性别" width="70">
          <template #default="{ row }">{{ formatGender(row.gender) }}</template>
        </el-table-column>
        <el-table-column prop="age" label="年龄" width="70" />
        <el-table-column prop="registLevelName" label="号别" width="90" />
        <el-table-column prop="noonLabel" label="午别" width="72" />
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
              · {{ currentPatient.registLevelName }}
            </template>
          </span>
          <div class="header-actions">
            <el-button :disabled="!currentRegisterId" :loading="orderingCheck" @click="onOrderCheck">
              开检查（头部 CT）
            </el-button>
            <el-button :disabled="!currentRegisterId" :loading="orderingInspection" @click="onOrderInspection">
              开检验（血常规）
            </el-button>
            <el-button :disabled="!currentRegisterId" :loading="orderingPrescription" @click="onOrderPrescription">
              开处方（阿莫西林）
            </el-button>
            <el-button :disabled="!lastInspectionId" @click="onFetchInspectionResult">
              查看检验结果
            </el-button>
            <el-button type="primary" :loading="saving" :disabled="!currentRegisterId" @click="onSaveRecord">
              保存病历
            </el-button>
            <el-button
              type="success"
              :loading="finishingId === currentRegisterId"
              :disabled="!currentRegisterId"
              @click="onFinishVisit"
            >
              结束看诊
            </el-button>
          </div>
        </div>
      </template>

      <AiDiagnosisBar :register-id="currentRegisterId" :disabled="!currentRegisterId" />

      <el-form v-if="currentRegisterId" label-position="top" class="record-form">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="主诉" required>
              <el-input v-model="recordForm.readme" type="textarea" :rows="2" placeholder="如：头痛 3 天，加重 1 天" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="现病史">
              <el-input v-model="recordForm.present" type="textarea" :rows="2" placeholder="起病情况、伴随症状、既往治疗" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="既往史 / 个人史">
              <el-input v-model="recordForm.history" placeholder="高血压、糖尿病等慢性病史" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="过敏史">
              <el-input v-model="recordForm.allergy" placeholder="无则填「无」；青霉素过敏等须醒目标注" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="体格检查">
              <el-input v-model="recordForm.physique" placeholder="T、P、R、BP，重点阳性体征" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="初步诊断">
              <el-input v-model="recordForm.diagnosis" placeholder="如：头痛待查" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="处理意见">
              <el-input v-model="recordForm.cure" type="textarea" :rows="2" placeholder="进一步检查、用药、随访建议" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-alert
          v-if="lastPrescription"
          type="info"
          :closable="false"
          show-icon
          :title="`处方 ${lastPrescription.prescriptionNo}`"
          :description="`金额 ¥${lastPrescription.totalAmount}，状态：待患者缴费`"
          class="result-alert"
        />
        <el-alert
          v-if="inspectionResult"
          type="success"
          :closable="false"
          show-icon
          :title="`检验结果：${inspectionResult.itemName}`"
          :description="inspectionResult.resultText"
          class="result-alert"
        />
      </el-form>
      <el-empty v-else description="从队列选择「接诊中」患者，或对「已挂号」患者点击叫号" />
    </el-card>
  </div>
</template>

<style scoped>
.workspace {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.flow-tip {
  border-radius: 8px;
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
  flex-wrap: wrap;
}

.header-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.result-alert {
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
