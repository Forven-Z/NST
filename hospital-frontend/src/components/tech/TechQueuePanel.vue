<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { INTEGRATION_ROUTES, TRIAGE_LEVEL_MAP } from '../../config/integrations'
import ResultReportSections from '../medical/ResultReportSections.vue'
import LabReportSheet from '../medical/LabReportSheet.vue'
import CheckReportSheet from '../medical/CheckReportSheet.vue'
import DisposalRecordSheet from '../medical/DisposalRecordSheet.vue'
import { mergeCheckReportAfterLlm } from '../../utils/checkReport'

const props = defineProps({
  title: { type: String, default: '待执行队列' },
  requestIdKey: { type: String, required: true },
  techType: { type: String, default: 'INSPECTION' },
  fetchQueue: { type: Function, required: true },
  executeRequest: { type: Function, required: true },
  saveResult: { type: Function, required: true },
  generateAiSuggestion: { type: Function, default: null },
  fetchResultDetail: { type: Function, default: null },
  generateAiReport: { type: Function, default: null },
  defaultStatus: { type: Number, default: 20 },
  workflowHint: { type: String, default: '' },
  showTriage: { type: Boolean, default: false },
})

const router = useRouter()

const useReportSections = computed(
  () => typeof props.fetchResultDetail === 'function'
    && typeof props.generateAiReport === 'function',
)

const isPacsCheck = computed(() => props.techType === 'CHECK')

const isLabReport = computed(
  () => props.techType === 'INSPECTION' && useReportSections.value,
)

const isCheckReport = computed(
  () => props.techType === 'CHECK' && useReportSections.value,
)

const isDisposalRecord = computed(
  () => props.techType === 'DISPOSAL'
    && typeof props.fetchResultDetail === 'function',
)

const structuredEntry = computed(
  () => isLabReport.value || isCheckReport.value || isDisposalRecord.value || useReportSections.value,
)

const loading = ref(false)
const executingId = ref(null)
const savingId = ref(null)
const generatingAiId = ref(null)
const detailLoading = ref(false)
const statusFilter = ref(props.defaultStatus)
const list = ref([])

const resultDialogVisible = ref(false)
const resultText = ref('')
const instrumentData = ref('')
const aiReportText = ref('')
const doctorReportText = ref('')
const aiReportStatus = ref('PENDING')
const labReport = ref(null)
const checkReport = ref(null)
const disposalRecord = ref(null)
/** entry=录入可编辑 | review=审阅只读 | readonly=已发布查看 */
const dialogMode = ref('entry')
const currentRow = ref(null)

const isEditableEntry = computed(() => dialogMode.value === 'entry')

const resultDialogTitle = computed(() => {
  const name = currentRow.value?.itemName || ''
  if (dialogMode.value === 'review') {
    return `${isDisposalRecord.value ? '审阅处置记录' : isCheckReport.value ? '审阅检查报告' : '审阅检验报告'} · ${name}`
  }
  if (dialogMode.value === 'readonly') {
    return `${isDisposalRecord.value ? '查看处置记录' : isCheckReport.value ? '查看检查报告' : '查看检验报告'} · ${name}`
  }
  return `${isDisposalRecord.value ? '录入处置记录' : isCheckReport.value ? '录入检查报告' : '录入结果'} · ${name}`
})

const statusMap = {
  10: { label: '已开立', type: 'info' },
  20: { label: '已缴费', type: 'warning' },
  30: { label: '执行中', type: 'primary' },
  40: { label: '已出结果', type: 'success' },
}

onMounted(loadList)

function rowId(row) {
  return row[props.requestIdKey]
}

async function loadList() {
  loading.value = true
  try {
    const res = await props.fetchQueue({
      status: statusFilter.value,
      page: 1,
      pageSize: 50,
    })
    list.value = res.data?.list ?? []
  } catch (err) {
    ElMessage.error(err.message || '加载队列失败')
  } finally {
    loading.value = false
  }
}

async function onExecuteAndGoImaging(row) {
  const id = rowId(row)
  executingId.value = id
  try {
    await props.executeRequest(id)
    ElMessage.success('已开始执行，正在进入影像 AI 工作台')
    goImagingAi(row)
  } catch (err) {
    ElMessage.error(err.message || '执行失败')
  } finally {
    executingId.value = null
  }
}

async function onExecute(row) {
  const id = rowId(row)
  executingId.value = id
  try {
    await props.executeRequest(id)
    ElMessage.success('已开始执行，请完成报告录入')
    await openResultDialog({ ...row, status: 30 }, 'entry')
    await loadList()
  } catch (err) {
    ElMessage.error(err.message || '执行失败')
  } finally {
    executingId.value = null
  }
}

function reportHasEntry() {
  if (isLabReport.value && labReport.value) {
    const ai = labReport.value.analysis?.aiReportText?.trim() || ''
    const doctor = labReport.value.analysis?.doctorReportText?.trim() || ''
    return !!(ai || doctor)
  }
  if (isCheckReport.value && checkReport.value) {
    const ai = checkReport.value.analysis?.aiReportText?.trim() || ''
    const doctor = checkReport.value.analysis?.doctorReportText?.trim() || ''
    const findings = checkReport.value.findings?.findingsText?.trim() || ''
    return !!(ai || doctor || findings)
  }
  if (isDisposalRecord.value && disposalRecord.value) {
    const process = disposalRecord.value.record?.processText?.trim() || ''
    const outcome = disposalRecord.value.record?.outcomeText?.trim() || ''
    return !!(process || outcome)
  }
  return !!(aiReportText.value.trim() || doctorReportText.value.trim())
}

async function openResultDialog(row, mode = 'entry') {
  currentRow.value = row
  dialogMode.value = mode
  resultText.value = row.resultText || ''
  instrumentData.value = ''
  aiReportText.value = ''
  doctorReportText.value = ''
  aiReportStatus.value = 'PENDING'
  labReport.value = null
  checkReport.value = null
  disposalRecord.value = null

  if (structuredEntry.value) {
    detailLoading.value = true
    try {
      const res = await props.fetchResultDetail(rowId(row))
      const d = res.data || {}
      if (isLabReport.value) {
        labReport.value = { ...d }
      } else if (isCheckReport.value) {
        checkReport.value = { ...d }
      } else if (isDisposalRecord.value) {
        disposalRecord.value = { ...d }
      } else {
        instrumentData.value = d.instrumentData || ''
        aiReportText.value = d.aiReportText || ''
        doctorReportText.value = d.doctorReportText || ''
        aiReportStatus.value = d.aiReportStatus || 'PENDING'
      }
    } catch (err) {
      ElMessage.error(err.message || '加载报告详情失败')
      return
    } finally {
      detailLoading.value = false
    }
  }

  if (mode === 'review' && structuredEntry.value && !reportHasEntry()) {
    ElMessage.warning('报告尚未录入，请先由录入医师完成录入')
    return
  }

  resultDialogVisible.value = true
}

async function onGenerateAiSuggestion() {
  if (!props.generateAiSuggestion || !currentRow.value) return
  const id = rowId(currentRow.value)
  generatingAiId.value = id
  try {
    const res = await props.generateAiSuggestion(id)
    const text = res.data?.resultText || res.data?.aiReportText || ''
    if (text) {
      resultText.value = text
      ElMessage.success('AI 建议已填入结果文本，请核对后保存')
    } else {
      ElMessage.info('暂无 AI 建议')
    }
  } catch (err) {
    ElMessage.error(err.message || 'AI 建议生成失败')
  } finally {
    generatingAiId.value = null
  }
}

async function onGenerateAiReport() {
  if (!props.generateAiReport || !currentRow.value) return
  const id = rowId(currentRow.value)
  let findingsText
  if (isCheckReport.value) {
    findingsText = checkReport.value?.findings?.findingsText?.trim() || ''
    if (!findingsText) {
      ElMessage.warning('请先填写 CT 所见')
      return
    }
  }
  generatingAiId.value = id
  try {
    const res = isCheckReport.value
      ? await props.generateAiReport(id, findingsText)
      : await props.generateAiReport(id)
    const d = res.data || {}
    if (isLabReport.value) {
      labReport.value = { ...d }
    } else if (isCheckReport.value) {
      checkReport.value = mergeCheckReportAfterLlm(checkReport.value, d)
    } else {
      aiReportText.value = d.aiReportText || ''
      aiReportStatus.value = d.aiReportStatus || 'READY'
    }
    ElMessage.success('AI 报告已生成')
  } catch (err) {
    ElMessage.error(err.message || 'AI 报告生成失败')
  } finally {
    generatingAiId.value = null
  }
}

function updateCheckFindings(text) {
  if (!checkReport.value) return
  checkReport.value = {
    ...checkReport.value,
    findings: { ...checkReport.value.findings, findingsText: text },
    findingsText: text,
  }
}

function updateCheckAi(text) {
  if (!checkReport.value) return
  checkReport.value = {
    ...checkReport.value,
    analysis: { ...checkReport.value.analysis, aiReportText: text },
    aiReportText: text,
  }
}

function updateCheckDoctor(text) {
  if (!checkReport.value) return
  checkReport.value = {
    ...checkReport.value,
    analysis: { ...checkReport.value.analysis, doctorReportText: text },
    doctorReportText: text,
  }
}

function updateLabAi(text) {
  if (!labReport.value) return
  labReport.value = {
    ...labReport.value,
    analysis: { ...labReport.value.analysis, aiReportText: text },
    aiReportText: text,
  }
}

function updateLabDoctor(text) {
  if (!labReport.value) return
  labReport.value = {
    ...labReport.value,
    analysis: { ...labReport.value.analysis, doctorReportText: text },
    doctorReportText: text,
  }
}

function updateDisposalProcess(text) {
  if (!disposalRecord.value) return
  disposalRecord.value = {
    ...disposalRecord.value,
    record: { ...disposalRecord.value.record, processText: text },
  }
}

function updateDisposalOutcome(text) {
  if (!disposalRecord.value) return
  disposalRecord.value = {
    ...disposalRecord.value,
    record: { ...disposalRecord.value.record, outcomeText: text },
  }
}

function goImagingAi(row, { view = false } = {}) {
  const query = {
    checkRequestId: rowId(row),
    patientName: row.patientName,
    itemName: row.itemName,
  }
  if (view) query.view = '1'
  router.push({
    path: INTEGRATION_ROUTES.imagingAiWorkbench,
    query,
  })
}

function onRecaptureCheckSnapshots() {
  if (!currentRow.value) return
  resultDialogVisible.value = false
  ElMessage.info('请在工作台调整层面后保存快照，完成后返回队列继续录入')
  goImagingAi(currentRow.value)
}

async function onSaveEntry() {
  await submitResult({ pendingReview: true }, '录入已保存，待审核医师发布')
}

async function onPublishReview() {
  await submitResult({ signAsReviewerOnly: true }, '报告已发布')
}

async function submitResult(signOpts, successMessage) {
  const id = rowId(currentRow.value)
  let payload

  if (structuredEntry.value) {
    if (isLabReport.value) {
      const ai = labReport.value?.analysis?.aiReportText?.trim() || ''
      const doctor = labReport.value?.analysis?.doctorReportText?.trim() || ''
      if (!signOpts.signAsReviewerOnly && !ai && !doctor) {
        ElMessage.warning('请生成 AI 报告或填写检验医师意见')
        return
      }
      payload = { aiReportText: ai, doctorReportText: doctor, ...signOpts }
    } else if (isCheckReport.value) {
      const findings = checkReport.value?.findings?.findingsText?.trim() || ''
      const ai = checkReport.value?.analysis?.aiReportText?.trim() || ''
      const doctor = checkReport.value?.analysis?.doctorReportText?.trim() || ''
      if (!signOpts.signAsReviewerOnly && !ai && !doctor) {
        ElMessage.warning('请生成 AI 报告或填写检查医师意见')
        return
      }
      payload = { findingsText: findings, aiReportText: ai, doctorReportText: doctor, ...signOpts }
    } else if (isDisposalRecord.value) {
      const process = disposalRecord.value?.record?.processText?.trim() || ''
      const outcome = disposalRecord.value?.record?.outcomeText?.trim() || ''
      if (!signOpts.signAsReviewerOnly && !process && !outcome) {
        ElMessage.warning('请填写处置过程或观察与结果')
        return
      }
      payload = { processText: process, outcomeText: outcome, ...signOpts }
    } else if (!signOpts.signAsReviewerOnly && !aiReportText.value.trim() && !doctorReportText.value.trim()) {
      ElMessage.warning('请生成 AI 报告或填写医师意见')
      return
    } else {
      payload = {
        aiReportText: aiReportText.value.trim(),
        doctorReportText: doctorReportText.value.trim(),
        ...signOpts,
      }
    }
  } else {
    if (!signOpts.signAsReviewerOnly && !resultText.value.trim()) {
      ElMessage.warning('请填写结果文本')
      return
    }
    payload = { resultText: resultText.value.trim(), ...signOpts }
  }

  savingId.value = id
  try {
    await props.saveResult(id, payload)
    ElMessage.success(successMessage)
    resultDialogVisible.value = false
    await loadList()
  } catch (err) {
    ElMessage.error(err.message || '保存失败')
  } finally {
    savingId.value = null
  }
}
</script>

<template>
  <div class="tech-panel">
    <el-alert
      v-if="workflowHint"
      type="info"
      :closable="false"
      show-icon
      class="flow-tip"
      :title="title"
      :description="workflowHint"
    />

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
          <div class="filters">
            <el-radio-group v-model="statusFilter" @change="loadList">
              <el-radio-button :label="20">已缴费待执行</el-radio-button>
              <el-radio-button :label="30">执行中</el-radio-button>
              <el-radio-button :label="40">已出结果</el-radio-button>
            </el-radio-group>
            <el-button :loading="loading" @click="loadList">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="list" empty-text="暂无待处理申请（须患者先缴费）">
        <el-table-column prop="medicalRecordNo" label="病历号" width="150" />
        <el-table-column prop="patientName" label="患者" width="100" />
        <el-table-column prop="itemName" label="项目" min-width="140" />
        <el-table-column v-if="showTriage" label="AI 分诊" width="96">
          <template #default="{ row }">
            <el-tooltip v-if="row.triageNote" :content="row.triageNote" placement="top">
              <el-tag size="small" :type="TRIAGE_LEVEL_MAP[row.triageLevel]?.type || 'info'">
                {{ TRIAGE_LEVEL_MAP[row.triageLevel]?.label || '普通' }}
              </el-tag>
            </el-tooltip>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="90">
          <template #default="{ row }">¥{{ row.itemPrice ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'" size="small">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" :width="isPacsCheck ? 200 : 160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 20"
              type="primary"
              link
              :loading="executingId === rowId(row)"
              @click="isPacsCheck ? onExecuteAndGoImaging(row) : onExecute(row)"
            >
              开始执行
            </el-button>
            <template v-if="row.status === 30">
              <el-button
                v-if="isPacsCheck"
                type="warning"
                link
                @click="goImagingAi(row)"
              >
                影像 AI 工作台
              </el-button>
              <el-button type="success" link @click="openResultDialog(row, 'entry')">
                录入
              </el-button>
              <el-button type="primary" link @click="openResultDialog(row, 'review')">
                审阅
              </el-button>
            </template>
            <template v-if="row.status === 40">
              <el-button type="success" link @click="openResultDialog(row, 'readonly')">
                查看报告
              </el-button>
              <el-button
                v-if="isPacsCheck"
                type="primary"
                link
                @click="goImagingAi(row, { view: true })"
              >
                查看影像
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="resultDialogVisible"
      :title="resultDialogTitle"
      :width="structuredEntry ? '860px' : '560px'"
      destroy-on-close
    >
      <div v-if="structuredEntry" v-loading="detailLoading" class="report-dialog-wrap">
        <LabReportSheet
          v-if="isLabReport && labReport"
          :report="labReport"
          :editable-ai="isEditableEntry"
          :editable-doctor="isEditableEntry"
          @update:ai-report-text="updateLabAi"
          @update:doctor-report-text="updateLabDoctor"
        />
        <CheckReportSheet
          v-else-if="isCheckReport && checkReport"
          :report="checkReport"
          :editable-findings="isEditableEntry"
          :editable-ai="isEditableEntry"
          :editable-doctor="isEditableEntry"
          :show-recapture="isEditableEntry"
          @update:findings-text="updateCheckFindings"
          @update:ai-report-text="updateCheckAi"
          @update:doctor-report-text="updateCheckDoctor"
          @recapture="onRecaptureCheckSnapshots"
        />
        <DisposalRecordSheet
          v-else-if="isDisposalRecord && disposalRecord"
          :report="disposalRecord"
          :editable="isEditableEntry"
          @update:process-text="updateDisposalProcess"
          @update:outcome-text="updateDisposalOutcome"
        />
        <template v-else>
          <ResultReportSections
            v-model:ai-report-text="aiReportText"
            v-model:doctor-report-text="doctorReportText"
            :instrument-data="instrumentData"
            :ai-report-status="aiReportStatus"
            :editable-ai="isEditableEntry"
            :editable-doctor="isEditableEntry"
          />
        </template>
      </div>
      <el-form v-else label-position="top">
        <el-form-item label="报告正文" required>
          <el-input
            v-model="resultText"
            type="textarea"
            :rows="8"
            :readonly="dialogMode === 'review' || dialogMode === 'readonly'"
            placeholder="填写检查结果或检验报告正文"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button
          v-if="dialogMode === 'entry' && useReportSections && !isDisposalRecord"
          type="primary"
          plain
          :loading="generatingAiId === rowId(currentRow)"
          @click="onGenerateAiReport"
        >
          生成 AI 报告
        </el-button>
        <el-button
          v-else-if="dialogMode === 'entry' && generateAiSuggestion && !structuredEntry"
          :loading="generatingAiId === rowId(currentRow)"
          @click="onGenerateAiSuggestion"
        >
          生成 AI 建议填入
        </el-button>
        <el-button @click="resultDialogVisible = false">
          {{ dialogMode === 'readonly' ? '关闭' : '取消' }}
        </el-button>
        <el-button
          v-if="dialogMode === 'entry'"
          type="primary"
          :loading="!!savingId"
          @click="onSaveEntry"
        >
          保存录入
        </el-button>
        <el-button
          v-if="dialogMode === 'review'"
          type="success"
          :loading="!!savingId"
          @click="onPublishReview"
        >
          发布
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.tech-panel {
  max-width: 1100px;
}

.flow-tip {
  margin-bottom: 12px;
  border-radius: 8px;
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

.muted {
  color: #94a3b8;
  font-size: 12px;
}

.sign-options {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 12px;
  padding: 8px 0;
}

.report-dialog-wrap {
  position: relative;
}
</style>
