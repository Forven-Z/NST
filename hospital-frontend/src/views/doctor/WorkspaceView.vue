<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  callPatient,
  createCheckOrder,
  createDisposalOrder,
  createInspectionOrder,
  createPrescription,
  fetchDoctorQueue,
  fetchMedicalRecord,
  finishVisit,
  saveMedicalRecord,
} from '../../api/doctor'
import AiDiagnosisBar from '../../components/doctor/AiDiagnosisBar.vue'
import DoctorPrescriptionDialog from '../../components/doctor/DoctorPrescriptionDialog.vue'
import DoctorTechOrderDialog from '../../components/doctor/DoctorTechOrderDialog.vue'
import RegisterOrdersPanel from '../../components/doctor/RegisterOrdersPanel.vue'
import { TRIAGE_LEVEL_MAP } from '../../config/integrations'
import { useDoctorWorkspaceStore } from '../../stores/doctorWorkspace'

const loading = ref(false)
const saving = ref(false)
const callingId = ref(null)
const finishingId = ref(null)
const queue = ref([])
const visitStateFilter = ref('all')
const currentRegisterId = ref(null)
const currentPatient = ref(null)
const ordersPanelRef = ref(null)

const workspace = useDoctorWorkspaceStore()

const techDialogVisible = ref(false)
const techOrderType = ref('INSPECTION')
const rxDialogVisible = ref(false)
const aiDiagnosisText = ref('')

const techPreselectedIds = computed(() => workspace.getPreselectedTechIds(techOrderType.value))
const rxPreselectedDrugIds = computed(() => workspace.getPreselectedDrugIds())

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
    workspace.clearForNewPatient()
    aiDiagnosisText.value = ''
    await loadQueue()
    await loadMedicalRecord(row.registerId)
    ordersPanelRef.value?.reload?.()
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
  workspace.clearForNewPatient()
  aiDiagnosisText.value = ''
  await loadMedicalRecord(row.registerId)
  ordersPanelRef.value?.reload?.()
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
    aiDiagnosisText.value = data.diagnosis || ''
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
    await saveMedicalRecord(currentRegisterId.value, {
      ...recordForm,
      diagnosis: aiDiagnosisText.value.trim() || recordForm.diagnosis,
    })
    ElMessage.success('病历已保存')
  } catch (err) {
    ElMessage.error(err.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function openTechDialog(type) {
  if (!currentRegisterId.value) return ElMessage.warning('请先选择接诊中的患者')
  techOrderType.value = type
  techDialogVisible.value = true
}

function openRxDialog() {
  if (!currentRegisterId.value) return ElMessage.warning('请先选择接诊中的患者')
  rxDialogVisible.value = true
}

async function onTechOrderConfirm(data) {
  const items = data.items || []
  if (!items.length) return
  try {
    let lastRes
    for (const item of items) {
      const payload = { registerId: data.registerId, ...item }
      if (techOrderType.value === 'CHECK') {
        lastRes = await createCheckOrder(payload)
      } else if (techOrderType.value === 'DISPOSAL') {
        lastRes = await createDisposalOrder(payload)
      } else {
        lastRes = await createInspectionOrder(payload)
      }
    }
    const count = items.length
    ElMessage.success(
      count > 1
        ? `已开立 ${count} 项医嘱`
        : lastRes?.data?.message || `已开立：${lastRes?.data?.itemName}`,
    )
    ordersPanelRef.value?.reload?.()
  } catch (err) {
    ElMessage.error(err.message || '开立失败')
  }
}

function getTechDraftMeta(techId) {
  return workspace.getDraftItemMeta(techOrderType.value, techId)
}

async function onPrescriptionConfirm(data) {
  try {
    const res = await createPrescription(data)
    const itemCount = data.items?.length ?? 1
    ElMessage.success(
      itemCount > 1
        ? `已开立处方 #${res.data?.prescriptionId}（${itemCount} 种药品），请患者缴费后至药房取药`
        : `已开立处方 #${res.data?.prescriptionId}，请患者缴费后至药房取药`,
    )
    ordersPanelRef.value?.reload?.()
  } catch (err) {
    ElMessage.error(err.message || '开处方失败')
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
    aiDiagnosisText.value = ''
    workspace.clearForNewPatient()
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
      description="患者挂号并缴费 → 医生叫号接诊 → 书写病历/开单 → 患者再次缴费 → 检验/检查/处置/药房 → 结束看诊"
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
        <el-table-column label="AI 分诊" width="100">
          <template #default="{ row }">
            <el-tooltip v-if="row.triageNote" :content="row.triageNote" placement="top">
              <el-tag
                size="small"
                :type="TRIAGE_LEVEL_MAP[row.triageLevel]?.type || 'info'"
              >
                {{ TRIAGE_LEVEL_MAP[row.triageLevel]?.label || '待分诊' }}
              </el-tag>
            </el-tooltip>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
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
            <el-button :disabled="!currentRegisterId" @click="openTechDialog('CHECK')">开检查</el-button>
            <el-button :disabled="!currentRegisterId" @click="openTechDialog('INSPECTION')">开检验</el-button>
            <el-button :disabled="!currentRegisterId" @click="openTechDialog('DISPOSAL')">开处置</el-button>
            <el-button :disabled="!currentRegisterId" @click="openRxDialog">开处方</el-button>
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

      <AiDiagnosisBar
        v-model:ai-diagnosis="aiDiagnosisText"
        :register-id="currentRegisterId"
        :record-form="recordForm"
        :disabled="!currentRegisterId"
      />

      <el-form v-if="currentRegisterId" label-position="top" class="record-form">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="主诉" required>
              <el-input v-model="recordForm.readme" type="textarea" :rows="2" placeholder="如：头痛 3 天，加重 1 天" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="现病史">
              <el-input v-model="recordForm.present" type="textarea" :rows="2" placeholder="起病情况、伴随症状" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="现病治疗情况">
              <el-input v-model="recordForm.presentTreat" placeholder="就诊前已接受治疗说明" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="既往史 / 个人史">
              <el-input v-model="recordForm.history" placeholder="高血压、糖尿病等慢性病史" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="过敏史">
              <el-input v-model="recordForm.allergy" placeholder="无则填「无」" />
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
          <el-col :span="12">
            <el-form-item label="检查建议">
              <el-input v-model="recordForm.checkAdvice" placeholder="拟开检查项目说明" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="检验建议">
              <el-input v-model="recordForm.inspectionAdvice" placeholder="拟开检验项目说明" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="处理意见">
              <el-input v-model="recordForm.cure" type="textarea" :rows="2" placeholder="进一步检查、用药、随访建议" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <el-empty v-else description="从队列选择「接诊中」患者，或对「已挂号」患者点击叫号" />

      <RegisterOrdersPanel ref="ordersPanelRef" :register-id="currentRegisterId" class="orders-panel" />
    </el-card>

    <DoctorTechOrderDialog
      v-model="techDialogVisible"
      :order-type="techOrderType"
      :register-id="currentRegisterId"
      :preselected-ids="techPreselectedIds"
      :get-draft-meta="getTechDraftMeta"
      @confirm="onTechOrderConfirm"
    />
    <DoctorPrescriptionDialog
      v-model="rxDialogVisible"
      :register-id="currentRegisterId"
      :preselected-drug-ids="rxPreselectedDrugIds"
      @confirm="onPrescriptionConfirm"
    />
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

.orders-panel {
  margin-top: 16px;
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
