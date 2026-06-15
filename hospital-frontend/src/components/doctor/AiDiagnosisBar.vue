<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createCheckAiDraft,
  createDisposalAiDraft,
  createInspectionAiDraft,
  fetchDiagnosisSuggest,
} from '../../api/doctor'
import { useDoctorWorkspaceStore } from '../../stores/doctorWorkspace'
import AiClinicalDraftDialog from './AiClinicalDraftDialog.vue'

const props = defineProps({
  registerId: { type: Number, default: null },
  disabled: { type: Boolean, default: false },
  recordForm: { type: Object, default: () => ({}) },
  aiDiagnosis: { type: String, default: '' },
  recordSaved: { type: Boolean, default: false },
})

const emit = defineEmits(['update:aiDiagnosis', 'orders-changed'])

const workspace = useDoctorWorkspaceStore()
const loadingSuggest = ref(false)
const loadingDraft = ref('')
const suggestFlags = ref({
  needCheck: false,
  needInspection: false,
  needDisposal: false,
})
const hasSuggested = ref(false)

const draftDialogVisible = ref(false)
const activeDraftType = ref('CHECK')
const activeDraft = ref(null)

function formatDiagnosisText(data) {
  const lines = []
  if (data.reason) lines.push(data.reason)
  if (data.suggestions?.length) {
    if (lines.length) lines.push('')
    data.suggestions.forEach((s, i) => lines.push(`${i + 1}. ${s}`))
  }
  return lines.join('\n')
}

async function onSuggest() {
  if (!props.registerId) {
    ElMessage.warning('请先选择接诊中的患者')
    return
  }
  if (!props.recordForm.readme?.trim()) {
    ElMessage.warning('请先填写主诉等病历信息')
    return
  }
  if (!props.recordSaved) {
    ElMessage.warning('建议先保存病历，再执行 AI 智能诊断（ADR-015）')
  }
  loadingSuggest.value = true
  try {
    const res = await fetchDiagnosisSuggest({
      registerId: props.registerId,
      ...props.recordForm,
    })
    const data = res.data || {}
    emit('update:aiDiagnosis', formatDiagnosisText(data))
    suggestFlags.value = {
      needCheck: !!data.needCheck,
      needInspection: !!data.needInspection,
      needDisposal: !!data.needDisposal,
    }
    hasSuggested.value = true
    ElMessage.success('AI 分支建议已生成，请据需生成草稿并确认提交')
  } catch (err) {
    ElMessage.error(err.message || '获取诊断失败')
  } finally {
    loadingSuggest.value = false
  }
}

async function onAiDraft(type) {
  if (!props.registerId) {
    ElMessage.warning('请先选择接诊中的患者')
    return
  }
  const creators = {
    CHECK: createCheckAiDraft,
    INSPECTION: createInspectionAiDraft,
    DISPOSAL: createDisposalAiDraft,
  }
  loadingDraft.value = type
  try {
    const draftRes = await creators[type]({
      registerId: props.registerId,
      medicalRecord: { ...props.recordForm },
    })
    const draft = draftRes.data
    if (!draft?.draftId) {
      ElMessage.info(draft?.message || '草稿生成失败')
      return
    }
    workspace.setAiDraft(type, draft)
    activeDraftType.value = type
    activeDraft.value = draft
    draftDialogVisible.value = true
  } catch (err) {
    ElMessage.error(err.message || '生成草稿失败')
  } finally {
    loadingDraft.value = ''
  }
}

function onDraftConfirmed() {
  emit('orders-changed')
}

function labelOf(type) {
  return { CHECK: '检查', INSPECTION: '检验', DISPOSAL: '处置' }[type] || type
}
</script>

<template>
  <div class="ai-bar">
    <div class="ai-bar-head">
      <span class="label">AI 辅助诊疗（ADR-015）</span>
      <el-button
        size="small"
        :disabled="disabled || !registerId"
        :loading="loadingSuggest"
        @click="onSuggest"
      >
        AI 智能诊断
      </el-button>
    </div>

    <el-form-item label="AI 初步诊断（可编辑）" class="diagnosis-field">
      <el-input
        :model-value="aiDiagnosis"
        type="textarea"
        :rows="4"
        placeholder="填写主诉后点击「AI 智能诊断」获取分支建议；可编辑后保存病历"
        :disabled="disabled || !registerId"
        @update:model-value="emit('update:aiDiagnosis', $event)"
      />
    </el-form-item>

    <div class="draft-actions">
      <el-tooltip :disabled="hasSuggested && suggestFlags.needCheck" content="请先执行 AI 智能诊断">
        <el-button
          size="small"
          :disabled="disabled || !registerId || (hasSuggested && !suggestFlags.needCheck)"
          :loading="loadingDraft === 'CHECK'"
          @click="onAiDraft('CHECK')"
        >
          生成检查草稿
        </el-button>
      </el-tooltip>
      <el-tooltip :disabled="hasSuggested && suggestFlags.needInspection" content="请先执行 AI 智能诊断">
        <el-button
          size="small"
          :disabled="disabled || !registerId || (hasSuggested && !suggestFlags.needInspection)"
          :loading="loadingDraft === 'INSPECTION'"
          @click="onAiDraft('INSPECTION')"
        >
          生成检验草稿
        </el-button>
      </el-tooltip>
      <el-tooltip :disabled="hasSuggested && suggestFlags.needDisposal" content="请先执行 AI 智能诊断">
        <el-button
          size="small"
          :disabled="disabled || !registerId || (hasSuggested && !suggestFlags.needDisposal)"
          :loading="loadingDraft === 'DISPOSAL'"
          @click="onAiDraft('DISPOSAL')"
        >
          生成处置草稿
        </el-button>
      </el-tooltip>
    </div>
    <p class="hint">
      流程：保存病历 → AI 智能诊断（needCheck 等分支）→ 生成草稿 → 编辑 → 确认提交 → 患者缴费
    </p>

    <AiClinicalDraftDialog
      v-model="draftDialogVisible"
      :draft-type="activeDraftType"
      :draft="activeDraft"
      @confirmed="onDraftConfirmed"
    />
  </div>
</template>

<style scoped>
.ai-bar {
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #f0fdfa;
  border: 1px solid #99f6e4;
  border-radius: 8px;
}

.ai-bar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.label {
  font-weight: 600;
  color: #0f766e;
  font-size: 14px;
}

.diagnosis-field {
  margin-bottom: 8px;
}

.diagnosis-field :deep(.el-form-item__label) {
  color: #0f766e;
  font-weight: 500;
}

.draft-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: #64748b;
}
</style>
