<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ResultReportSections from '../medical/ResultReportSections.vue'
import { INTEGRATION_ROUTES, TRIAGE_LEVEL_MAP } from '../../config/integrations'

const props = defineProps({
  title: { type: String, default: '待执行队列' },
  requestIdKey: { type: String, required: true },
  techType: { type: String, default: 'INSPECTION' },
  fetchQueue: { type: Function, required: true },
  executeRequest: { type: Function, required: true },
  saveResult: { type: Function, required: true },
  fetchResultDetail: { type: Function, default: null },
  generateAiReport: { type: Function, default: null },
  defaultStatus: { type: Number, default: 20 },
  workflowHint: { type: String, default: '' },
  showTriage: { type: Boolean, default: false },
})

const router = useRouter()

const loading = ref(false)
const executingId = ref(null)
const savingId = ref(null)
const generatingAiId = ref(null)
const statusFilter = ref(props.defaultStatus)
const list = ref([])

const resultDialogVisible = ref(false)
const resultDetail = ref(null)
const aiReportText = ref('')
const doctorReportText = ref('')
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

async function loadResultDetail(row) {
  if (!props.fetchResultDetail) {
    return {
      instrumentData: '',
      aiReportText: row.aiReportText || '',
      doctorReportText: row.doctorReportText || '',
      aiReportStatus: row.aiReportStatus || 'PENDING',
    }
  }
  const res = await props.fetchResultDetail(rowId(row))
  return res.data || {}
}

async function onExecute(row) {
  const id = rowId(row)
  executingId.value = id
  try {
    await props.executeRequest(id)
    ElMessage.success(
      props.techType === 'CHECK'
        ? '已开始执行，请进入影像 AI 工作台：大模型组阅片 + 智能体组生成分析'
        : '已开始执行，可点击「生成 AI 检验报告」或由智能体自动生成后核对录入',
    )
    await loadList()
  } catch (err) {
    ElMessage.error(err.message || '执行失败')
  } finally {
    executingId.value = null
  }
}

async function openResultDialog(row) {
  currentRow.value = row
  try {
    const detail = await loadResultDetail(row)
    resultDetail.value = detail
    aiReportText.value = detail.aiReportText || ''
    doctorReportText.value = detail.doctorReportText || ''
    resultDialogVisible.value = true
  } catch (err) {
    ElMessage.error(err.message || '加载报告详情失败')
  }
}

async function onGenerateAiFromList(row) {
  currentRow.value = row
  await onGenerateAiReport(true)
}

async function onGenerateAiReport(openDialog = false) {
  if (!props.generateAiReport || !currentRow.value) return
  const id = rowId(currentRow.value)
  generatingAiId.value = id
  try {
    const cachedId = resultDetail.value?.inspectionRequestId
      ?? resultDetail.value?.checkRequestId
      ?? resultDetail.value?.disposalRequestId
    if (!resultDetail.value || cachedId !== id) {
      const base = await loadResultDetail(currentRow.value)
      resultDetail.value = base
      aiReportText.value = base.aiReportText || ''
      doctorReportText.value = base.doctorReportText || ''
    }
    const res = await props.generateAiReport(id)
    const detail = res.data || {}
    resultDetail.value = { ...resultDetail.value, ...detail }
    aiReportText.value = detail.aiReportText || ''
    if (openDialog) resultDialogVisible.value = true
    ElMessage.success(
      props.techType === 'CHECK' ? '智能体 AI 影像分析已生成' : '智能体 AI 检验报告已生成',
    )
  } catch (err) {
    ElMessage.error(err.message || 'AI 报告生成失败')
  } finally {
    generatingAiId.value = null
  }
}

function goImagingAi(row) {
  router.push({
    path: INTEGRATION_ROUTES.imagingAiWorkbench,
    query: {
      checkRequestId: rowId(row),
      patientName: row.patientName,
      itemName: row.itemName,
    },
  })
}

async function onSaveResult() {
  if (!aiReportText.value.trim() && !doctorReportText.value.trim()) {
    ElMessage.warning('请至少填写 AI 报告或医师意见')
    return
  }
  const id = rowId(currentRow.value)
  savingId.value = id
  try {
    await props.saveResult(id, {
      aiReportText: aiReportText.value.trim(),
      doctorReportText: doctorReportText.value.trim(),
    })
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
        <el-table-column label="操作" :width="techType === 'CHECK' ? 300 : 260" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 20"
              type="primary"
              link
              :loading="executingId === rowId(row)"
              @click="onExecute(row)"
            >
              开始执行
            </el-button>
            <el-button
              v-if="techType === 'CHECK' && (row.status === 20 || row.status === 30)"
              type="warning"
              link
              @click="goImagingAi(row)"
            >
              影像 AI 工作台
            </el-button>
            <el-button
              v-if="techType === 'INSPECTION' && (row.status === 20 || row.status === 30) && generateAiReport"
              type="primary"
              link
              :loading="generatingAiId === rowId(row)"
              @click="onGenerateAiFromList(row)"
            >
              生成 AI 检验报告
            </el-button>
            <el-button
              v-if="row.status === 20 || row.status === 30"
              type="success"
              link
              @click="openResultDialog(row)"
            >
              录入结果
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="resultDialogVisible"
      :title="`录入结果 · ${currentRow?.itemName || ''}`"
      width="680px"
      destroy-on-close
    >
      <ResultReportSections
        v-if="resultDetail"
        :instrument-data="resultDetail.instrumentData"
        :ai-report-text="aiReportText"
        :doctor-report-text="doctorReportText"
        :ai-report-status="resultDetail.aiReportStatus"
        editable-ai
        editable-doctor
        @update:ai-report-text="aiReportText = $event"
        @update:doctor-report-text="doctorReportText = $event"
      />
      <template #footer>
        <el-button
          v-if="generateAiReport"
          :loading="generatingAiId === rowId(currentRow)"
          @click="onGenerateAiReport"
        >
          {{ techType === 'CHECK' ? '智能体：生成 AI 影像分析' : '智能体：生成 AI 检验报告' }}
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
