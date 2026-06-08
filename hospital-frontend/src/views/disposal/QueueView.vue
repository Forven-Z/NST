<script setup>
import TechQueuePanel from '../../components/tech/TechQueuePanel.vue'
import {
  executeDisposalRequest,
  fetchDisposalQueue,
  fetchDisposalResultDetail,
  generateDisposalAiReport,
  saveDisposalResult,
} from '../../api/disposal'
</script>

<template>
  <TechQueuePanel
    title="处置待执行队列"
    tech-type="DISPOSAL"
    request-id-key="disposalRequestId"
    workflow-hint="处置项目须医生开立并缴费后执行。执行记录为只读，AI 可生成摘要，医师补充后录入发布。"
    :fetch-queue="fetchDisposalQueue"
    :execute-request="executeDisposalRequest"
    :save-result="saveDisposalResult"
    :fetch-result-detail="fetchDisposalResultDetail"
    :generate-ai-report="generateDisposalAiReport"
  />
</template>
