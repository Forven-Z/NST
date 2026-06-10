<script setup>
import TechQueuePanel from '../../components/tech/TechQueuePanel.vue'
import {
  executeDisposalRequest,
  fetchDisposalQueue,
  generateDisposalAiReport,
  saveDisposalResult,
} from '../../api/disposal'

async function generateDisposalAiSuggestion(id) {
  const res = await generateDisposalAiReport(id)
  return { data: { resultText: res.data?.aiReportText || res.data?.resultText || '' } }
}
</script>

<template>
  <TechQueuePanel
    title="处置待执行队列"
    tech-type="DISPOSAL"
    request-id-key="disposalRequestId"
    workflow-hint="处置项目须医生开立并缴费后执行。录入 resultText 后医生可查看。API：POST /tech/disposal/{id}/result"
    :fetch-queue="fetchDisposalQueue"
    :execute-request="executeDisposalRequest"
    :save-result="saveDisposalResult"
    :generate-ai-suggestion="generateDisposalAiSuggestion"
  />
</template>
