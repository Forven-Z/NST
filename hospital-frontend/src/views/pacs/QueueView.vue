<script setup>
import TechQueuePanel from '../../components/tech/TechQueuePanel.vue'
import {
  executePacsRequest,
  fetchPacsQueue,
  fetchPacsResultDetail,
  generatePacsLlmReport,
  savePacsResult,
} from '../../api/pacs'
</script>

<template>
  <TechQueuePanel
    title="检查待执行队列"
    tech-type="CHECK"
    request-id-key="checkRequestId"
    workflow-hint="流程：患者缴费 → 开始执行 → 影像 AI 工作台采图 → 录入检查所见 → 生成 AI 报告 → 检查医师签阅发布"
    show-triage
    :fetch-queue="fetchPacsQueue"
    :fetch-result-detail="fetchPacsResultDetail"
    :execute-request="executePacsRequest"
    :save-result="savePacsResult"
    :generate-ai-report="generatePacsLlmReport"
  />
</template>
