<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ResultReportSections from '../medical/ResultReportSections.vue'
import { INTEGRATION_ROUTES, TRIAGE_LEVEL_MAP } from '../../config/integrations'

const props = defineProps({
  title: { type: String, default: '待执行队列' },
  requestIdKey: { type: String, required: true },
  techType: { type: String, default: 'INSPECTION' },
  fetchQueue: { type: Function, required: true },
  executeRequest: { type: Function, required: true },
  saveResult: { type: Function, required: true },
  generateAiSuggestion: { type: Function, default: null },
  generateAiReport: { type: Function, default: null },
  fetchResultDetail: { type: Function, default: null },
  useReportSections: { type: Boolean, default: false },
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
const detailLoading = ref(false)
const resultText = ref('')
const resultAttachment = ref('')
const instrumentData = ref('')
const aiReportText = ref('')
const doctorReportText = ref('')
const aiReportStatus = ref('PENDING')
const criticalItems = ref([])
const currentRow = ref(null)

const isReadOnlyResult = computed(() => currentRow.value?.status === 40)
const resultDialogWidth = computed(() => (props.useReportSections ? '720px' : '560px'))

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

async function onExecute(row) {
  const id = rowId(row)
  executingId.value = id
  try {
    await props.executeRequest(id)
    ElMessage.success('已开始执行，请录入检查结果')
    await loadList()
  } catch (err) {
    ElMessage.error(err.message || '执行失败')
  } finally {
    executingId.value = null
  }
}

function applyDetailData(data) {
  if (!data) return
  resultText.value = data.resultText || ''
  resultAttachment.value = data.resultAttachment || ''
  instrumentData.value = data.instrumentData || ''
  aiReportText.value = data.aiReportText || ''
  doctorReportText.value = data.doctorReportText || ''
  aiReportStatus.value = data.aiReportStatus || 'PENDING'
  criticalItems.value = data.criticalItems ?? []
}

async function openResultDialog(row) {
  currentRow.value = row
  applyDetailData(row)
  resultDialogVisible.value = true

  if (!props.fetchResultDetail) return

  detailLoading.value = true
  try {
    const res = await props.fetchResultDetail(rowId(row))
    applyDetailData(res.data)
  } catch (err) {
    ElMessage.error(err.message || '加载结果详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function onGenerateAiReport() {
  const generator = props.generateAiReport || props.generateAiSuggestion
  if (!generator || !currentRow.value) return
  const id = rowId(currentRow.value)
  generatingAiId.value = id
  try {
    const res = await generator(id)
    const data = res.data
    if (props.useReportSections) {
      applyDetailData(data)
      if (data?.aiReportText) {
        ElMessage.success('AI 报告已生成，请核对后补充医师意见')
      } else {
        ElMessage.info('暂无 AI 报告')
      }
      return
    }
    const text = data?.resultText || data?.aiReportText || ''
    if (text) {
      resultText.value = text
      ElMessage.success('AI 建议已填入结果文本，请核对后保存')
    } else {
      ElMessage.info('暂无 AI 建议')
    }
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
  const id = rowId(currentRow.value)
  savingId.value = id
  try {
    if (props.useReportSections) {
      if (!aiReportText.value.trim() && !doctorReportText.value.trim()) {
        ElMessage.warning('请生成或填写 AI 报告，或补充医师意见')
        return
      }
      if (criticalItems.value.length > 0) {
        const lines = criticalItems.value.map((item) => {
          const arrow = item.flag === 'HIGH' ? '偏高' : '偏低'
          return `${item.name}：${item.value} ${item.unit}（参考 ${item.refRange}，${arrow}）`
        })
        try {
          await ElMessageBox.confirm(
            `检测到以下危急值项：\n\n${lines.join('\n')}\n\n确认发布报告？`,
            '危急值确认',
            {
              type: 'warning',
              confirmButtonText: '确认发布',
              cancelButtonText: '返回修改',
            },
          )
        } catch {
          return
        }
      }
      await props.saveResult(id, {
        aiReportText: aiReportText.value.trim(),
        doctorReportText: doctorReportText.value.trim(),
        resultAttachment: resultAttachment.value.trim() || undefined,
      })
    } else {
      if (!resultText.value.trim()) {
        ElMessage.warning('请填写结果文本')
        return
      }
      await props.saveResult(id, {
        resultText: resultText.value.trim(),
        resultAttachment: resultAttachment.value.trim() || undefined,
      })
    }
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
        <el-table-column label="操作" :width="techType === 'CHECK' ? 260 : 200" fixed="right">
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
              v-if="row.status === 20 || row.status === 30"
              type="success"
              link
              @click="openResultDialog(row)"
            >
              录入结果
            </el-button>
            <el-button
              v-if="row.status === 40"
              type="primary"
              link
              @click="openResultDialog(row)"
            >
              查看结果
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="resultDialogVisible"
      :title="`${isReadOnlyResult ? '查看结果' : '录入结果'} · ${currentRow?.itemName || ''}`"
      :width="resultDialogWidth"
      destroy-on-close
    >
      <div v-loading="detailLoading">
        <ResultReportSections
          v-if="useReportSections"
          :instrument-data="instrumentData"
          :ai-report-text="aiReportText"
          :doctor-report-text="doctorReportText"
          :ai-report-status="aiReportStatus"
          :editable-ai="!isReadOnlyResult"
          :editable-doctor="!isReadOnlyResult"
          @update:ai-report-text="aiReportText = $event"
          @update:doctor-report-text="doctorReportText = $event"
        />
        <el-form v-else label-position="top">
          <el-form-item label="结果文本（resultText）" :required="!isReadOnlyResult">
            <el-input
              v-model="resultText"
              type="textarea"
              :rows="8"
              :readonly="isReadOnlyResult"
              placeholder="按 API §5.7.3 填写检查结果或检验报告正文"
            />
          </el-form-item>
        </el-form>
        <el-form label-position="top" class="attachment-form">
          <el-form-item label="结果附件（resultAttachment，可选）">
            <el-input
              v-model="resultAttachment"
              :readonly="isReadOnlyResult"
              placeholder="如 minio://bucket/key/report.pdf"
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button
          v-if="(generateAiReport || generateAiSuggestion) && !isReadOnlyResult"
          :loading="generatingAiId === rowId(currentRow)"
          @click="onGenerateAiReport"
        >
          {{ useReportSections ? '生成 AI 报告' : '生成 AI 建议填入' }}
        </el-button>
        <el-button @click="resultDialogVisible = false">{{ isReadOnlyResult ? '关闭' : '取消' }}</el-button>
        <el-button
          v-if="!isReadOnlyResult"
          type="primary"
          :loading="!!savingId"
          @click="onSaveResult"
        >
          保存并发布
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

.attachment-form {
  margin-top: 12px;
}

.muted {
  color: #94a3b8;
  font-size: 12px;
}
</style>
