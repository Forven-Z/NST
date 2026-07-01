<script setup>
import TechQueuePanel from '../../components/tech/TechQueuePanel.vue'
import {
  executeLisRequest,
  fetchLisQueue,
  fetchLisResultDetail,
  generateLisAiReport,
  saveLisResult,
} from '../../api/lis'
</script>

<template>
  <TechQueuePanel
    title="检验待执行队列"
    tech-type="INSPECTION"
    request-id-key="inspectionRequestId"
    workflow-hint="流程：患者缴费 → 开始执行（仪器结果入库）→ 生成 AI 报告 → 检验师签阅 → 发布"
    :fetch-queue="fetchLisQueue"
    :fetch-result-detail="fetchLisResultDetail"
    :execute-request="executeLisRequest"
    :save-result="saveLisResult"
    :generate-ai-report="generateLisAiReport"
    show-triage
  />
</template>
