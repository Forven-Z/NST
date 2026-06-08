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

const props = defineProps({
  registerId: { type: Number, default: null },
  disabled: { type: Boolean, default: false },
  recordForm: { type: Object, default: () => ({}) },
  aiDiagnosis: { type: String, default: '' },
})

const emit = defineEmits(['update:aiDiagnosis'])

const workspace = useDoctorWorkspaceStore()
const loadingSuggest = ref(false)
const loadingDraft = ref('')

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
  loadingSuggest.value = true
  try {
    const res = await fetchDiagnosisSuggest({
      registerId: props.registerId,
      ...props.recordForm,
    })
    emit('update:aiDiagnosis', formatDiagnosisText(res.data || {}))
    ElMessage.success('AI 初步诊断已生成，可编辑后保存病历')
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
    const draftRes = await creators[type]({ registerId: props.registerId })
    const draft = draftRes.data
    if (!draft?.draftId) {
      ElMessage.info('草稿生成失败')
      return
    }
    workspace.setAiDraft(type, draft)
    ElMessage.success(`AI ${labelOf(type)}草稿已生成，请在右侧 AI 助理查看`)
  } catch (err) {
    ElMessage.error(err.message || '生成草稿失败')
  } finally {
    loadingDraft.value = ''
  }
}

function labelOf(type) {
  return { CHECK: '检查', INSPECTION: '检验', DISPOSAL: '处置' }[type] || type
}
</script>

<template>
  <div class="ai-bar">
    <div class="ai-bar-head">
      <span class="label">AI 辅助诊疗</span>
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
        placeholder="填写主诉等信息后点击「AI 智能诊断」，AI 将生成初步诊断；您可修改或补充后保存病历"
        :disabled="disabled || !registerId"
        @update:model-value="emit('update:aiDiagnosis', $event)"
      />
    </el-form-item>

    <div class="draft-actions">
      <el-button
        size="small"
        :disabled="disabled || !registerId"
        :loading="loadingDraft === 'CHECK'"
        @click="onAiDraft('CHECK')"
      >
        AI 生成检查草稿
      </el-button>
      <el-button
        size="small"
        :disabled="disabled || !registerId"
        :loading="loadingDraft === 'INSPECTION'"
        @click="onAiDraft('INSPECTION')"
      >
        AI 生成检验草稿
      </el-button>
      <el-button
        size="small"
        :disabled="disabled || !registerId"
        :loading="loadingDraft === 'DISPOSAL'"
        @click="onAiDraft('DISPOSAL')"
      >
        AI 生成处置草稿
      </el-button>
    </div>
    <p class="hint">流程：填写病历 → AI 智能诊断 → 生成草稿（右侧助理）→ 开单勾选确认 → 保存病历</p>
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
