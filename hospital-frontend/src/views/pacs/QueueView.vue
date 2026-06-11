<script setup>
import TechQueuePanel from '../../components/tech/TechQueuePanel.vue'
import {
  executePacsRequest,
  fetchPacsQueue,
  fetchPacsResultDetail,
  generatePacsAiReport,
  savePacsResult,
} from '../../api/pacs'
</script>

<template>
  <TechQueuePanel
    title="检查待执行队列"
    tech-type="CHECK"
    request-id-key="checkRequestId"
    workflow-hint="流程：患者缴费 → 开始执行 → 生成 AI 报告 → 补充医师意见 → 发布。API：GET result-detail · POST ai-report · POST result"
    use-report-sections
    show-triage
    :fetch-queue="fetchPacsQueue"
    :fetch-result-detail="fetchPacsResultDetail"
    :execute-request="executePacsRequest"
    :save-result="savePacsResult"
    :generate-ai-report="generatePacsAiReport"
  />
</template>
