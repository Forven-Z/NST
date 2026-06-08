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
    workflow-hint="流程：AI 分诊负载均衡 → 患者缴费 → 开始执行 → 影像 AI 工作台（大模型 CT 跳转 + 智能体 AI 分析）→ 医师核对录入。仪器数据不可改。"
    show-triage
    :fetch-queue="fetchPacsQueue"
    :execute-request="executePacsRequest"
    :save-result="savePacsResult"
    :fetch-result-detail="fetchPacsResultDetail"
    :generate-ai-report="generatePacsAiReport"
  />
</template>
