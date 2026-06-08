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
    workflow-hint="流程：AI 分诊负载均衡分配 → 患者缴费 → 开始执行（仪器数据只读上传）→ 智能体组生成 AI 检验报告 → 检验师核对修改后录入发布。无 CT 影像。"
    :fetch-queue="fetchLisQueue"
    :execute-request="executeLisRequest"
    :save-result="saveLisResult"
    :fetch-result-detail="fetchLisResultDetail"
    :generate-ai-report="generateLisAiReport"
    show-triage
  />
</template>
