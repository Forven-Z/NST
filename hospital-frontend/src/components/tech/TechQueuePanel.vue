<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { INTEGRATION_ROUTES, TRIAGE_LEVEL_MAP } from '../../config/integrations'
import ResultReportSections from '../medical/ResultReportSections.vue'

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

const loading = ref(false)
const executingId = ref(null)
const savingId = ref(null)
const generatingAiId = ref(null)
const detailLoading = ref(false)
const statusFilter = ref(props.defaultStatus)
const list = ref([])

const resultDialogVisible = ref(false)
const resultText = ref('')
const resultAttachment = ref('')
const instrumentData = ref('')
const aiReportText = ref('')
const doctorReportText = ref('')
const aiReportStatus = ref('PENDING')
const currentRow = ref(null)

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
    ElMessage.success('已开始执行，请录入结果')
    await loadList()
  } catch (err) {
    ElMessage.error(err.message || '执行失败')
  } finally {
    executingId.value = null
  }
}

async function openResultDialog(row) {
  currentRow.value = row
  resultText.value = row.resultText || ''
  resultAttachment.value = row.resultAttachment || ''
  instrumentData.value = ''
  aiReportText.value = ''
  doctorReportText.value = ''
  aiReportStatus.value = 'PENDING'

  if (useReportSections.value) {
    detailLoading.value = true
    try {
      const res = await props.fetchResultDetail(rowId(row))
      const d = res.data || {}
      instrumentData.value = d.instrumentData || ''
      aiReportText.value = d.aiReportText || ''
      doctorReportText.value = d.doctorReportText || ''
      aiReportStatus.value = d.aiReportStatus || 'PENDING'
      resultAttachment.value = d.resultAttachment || ''
    } catch (err) {
      ElMessage.error(err.message || '加载报告详情失败')
      return
    } finally {
      detailLoading.value = false
    }
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
  generatingAiId.value = id
  try {
    const res = await props.generateAiReport(id)
    const d = res.data || {}
    aiReportText.value = d.aiReportText || ''
    aiReportStatus.value = d.aiReportStatus || 'READY'
    ElMessage.success('AI 报告已生成')
  } catch (err) {
    ElMessage.error(err.message || 'AI 报告生成失败')
  } finally {
    generatingAiId.value = null
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

async function onSaveResult() {
  const id = rowId(currentRow.value)
  let payload

  if (useReportSections.value) {
    if (!aiReportText.value.trim() && !doctorReportText.value.trim()) {
      ElMessage.warning('请生成 AI 报告或填写医师意见')
      return
    }
    payload = {
      aiReportText: aiReportText.value.trim(),
      doctorReportText: doctorReportText.value.trim(),
      resultAttachment: resultAttachment.value.trim() || undefined,
    }
  } else {
    if (!resultText.value.trim()) {
      ElMessage.warning('请填写结果文本')
      return
    }
    payload = {
      resultText: resultText.value.trim(),
      resultAttachment: resultAttachment.value.trim() || undefined,
    }
  }

  savingId.value = id
  try {
    await props.saveResult(id, payload)
    ElMessage.success('结果已保存，医生可在工作站查看')
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
        <el-table-column label="操作" :width="isPacsCheck ? 160 : 200" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 20 && isPacsCheck"
              type="primary"
              link
              :loading="executingId === rowId(row)"
              @click="onExecuteAndGoImaging(row)"
            >
              开始执行
            </el-button>
            <el-button
              v-else-if="row.status === 20"
              type="primary"
              link
              :loading="executingId === rowId(row)"
              @click="onExecute(row)"
            >
              开始执行
            </el-button>
            <el-button
              v-if="isPacsCheck && row.status === 30"
              type="warning"
              link
              @click="goImagingAi(row)"
            >
              影像 AI 工作台
            </el-button>
            <el-button
              v-if="(!isPacsCheck && row.status === 20) || row.status === 30"
              type="success"
              link
              @click="openResultDialog(row)"
            >
              录入结果
            </el-button>
            <el-button
              v-if="isPacsCheck && row.status === 40"
              type="primary"
              link
              @click="goImagingAi(row, { view: true })"
            >
              查看影像
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="resultDialogVisible"
      :title="`录入结果 · ${currentRow?.itemName || ''}`"
      :width="useReportSections ? '720px' : '560px'"
      destroy-on-close
    >
      <div v-if="useReportSections" v-loading="detailLoading">
        <ResultReportSections
          v-model:ai-report-text="aiReportText"
          v-model:doctor-report-text="doctorReportText"
          :instrument-data="instrumentData"
          :ai-report-status="aiReportStatus"
          editable-ai
          editable-doctor
        />
        <el-form label-position="top" style="margin-top: 12px">
          <el-form-item label="结果附件（resultAttachment，可选）">
            <el-input
              v-model="resultAttachment"
              placeholder="如 minio://bucket/key/report.pdf"
            />
          </el-form-item>
        </el-form>
      </div>
      <el-form v-else label-position="top">
        <el-form-item label="结果文本（resultText）" required>
          <el-input
            v-model="resultText"
            type="textarea"
            :rows="8"
            placeholder="按 API §5.7.3 填写检查结果或检验报告正文"
          />
        </el-form-item>
        <el-form-item label="结果附件（resultAttachment，可选）">
          <el-input
            v-model="resultAttachment"
            placeholder="如 minio://bucket/key/report.pdf"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button
          v-if="useReportSections"
          :loading="generatingAiId === rowId(currentRow)"
          @click="onGenerateAiReport"
        >
          生成 AI 报告
        </el-button>
        <el-button
          v-else-if="generateAiSuggestion"
          :loading="generatingAiId === rowId(currentRow)"
          @click="onGenerateAiSuggestion"
        >
          生成 AI 建议填入
        </el-button>
        <el-button @click="resultDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="!!savingId" @click="onSaveResult">保存并发布</el-button>
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
</style>
