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
    workflow-hint="流程：患者缴费 → 开始执行 → 生成 AI 报告 → 补充医师意见 → 发布。API：GET result-detail · POST ai-report · POST result"
    :fetch-queue="fetchDisposalQueue"
    :fetch-result-detail="fetchDisposalResultDetail"
    :execute-request="executeDisposalRequest"
    :save-result="saveDisposalResult"
    :generate-ai-report="generateDisposalAiReport"
  />
</template>
