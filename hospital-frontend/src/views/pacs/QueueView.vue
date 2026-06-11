<script setup>
import TechQueuePanel from '../../components/tech/TechQueuePanel.vue'
import {
  executePacsRequest,
  fetchPacsQueue,
  generatePacsAiReport,
  savePacsResult,
} from '../../api/pacs'

async function generatePacsAiSuggestion(id) {
  const res = await generatePacsAiReport(id)
  return { data: { resultText: res.data?.aiReportText || res.data?.resultText || '' } }
}
</script>

<template>
  <TechQueuePanel
    title="检查待执行队列"
    tech-type="CHECK"
    request-id-key="checkRequestId"
    workflow-hint="流程：患者缴费 → 开始执行 → 影像 AI 工作台（可选）→ 录入 resultText。API：PUT /pacs/requests/{id}/result"
    show-triage
    :fetch-queue="fetchPacsQueue"
    :execute-request="executePacsRequest"
    :save-result="savePacsResult"
    :generate-ai-suggestion="generatePacsAiSuggestion"
  />
</template>
