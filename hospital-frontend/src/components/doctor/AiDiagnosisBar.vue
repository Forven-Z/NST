<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  confirmCheckAiDraft,
  confirmDisposalAiDraft,
  confirmInspectionAiDraft,
  createCheckAiDraft,
  createDisposalAiDraft,
  createInspectionAiDraft,
  fetchDiagnosisSuggest,
} from '../../api/doctor'

const props = defineProps({
  registerId: { type: Number, default: null },
  disabled: { type: Boolean, default: false },
})

const loadingSuggest = ref(false)
const loadingDraft = ref('')
const suggest = ref(null)

async function onSuggest() {
  if (!props.registerId) {
    ElMessage.warning('请先选择接诊中的患者')
    return
  }
  loadingSuggest.value = true
  suggest.value = null
  try {
    const res = await fetchDiagnosisSuggest({ registerId: props.registerId })
    suggest.value = res.data
    ElMessage.success('AI 诊断建议已生成')
  } catch (err) {
    ElMessage.error(err.message || '获取建议失败')
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
  const confirmers = {
    CHECK: confirmCheckAiDraft,
    INSPECTION: confirmInspectionAiDraft,
    DISPOSAL: confirmDisposalAiDraft,
  }
  loadingDraft.value = type
  try {
    const draftRes = await creators[type]({ registerId: props.registerId })
    const draft = draftRes.data
    if (!draft?.draftId) {
      ElMessage.info('草稿生成失败')
      return
    }
    await confirmers[type](draft.draftId)
    ElMessage.success(`AI ${labelOf(type)} 草稿已确认提交，请患者缴费`)
  } catch (err) {
    ElMessage.error(err.message || 'AI 开单失败')
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

    <el-alert
      v-if="suggest"
      type="info"
      :closable="false"
      show-icon
      class="suggest-alert"
      :title="suggest.reason || 'AI 建议'"
    >
      <p v-for="(line, i) in suggest.suggestions || []" :key="i">{{ line }}</p>
      <div class="flags">
        <el-tag v-if="suggest.needCheck" size="small" type="warning">建议检查</el-tag>
        <el-tag v-if="suggest.needInspection" size="small" type="warning">建议检验</el-tag>
        <el-tag v-if="suggest.needDisposal" size="small" type="warning">建议处置</el-tag>
      </div>
    </el-alert>

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
    <p class="hint">流程：智能诊断 → 生成草稿 → 确认提交（P4 将支持逐步编辑草稿）</p>
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

.suggest-alert {
  margin-bottom: 8px;
}

.flags {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
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
