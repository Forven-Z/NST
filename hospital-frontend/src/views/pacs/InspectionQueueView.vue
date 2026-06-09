<script setup>
import TechQueuePanel from '../../components/tech/TechQueuePanel.vue'
import { executeLisRequest, fetchLisQueue, saveLisResult } from '../../api/lis'
</script>

<template>
  <TechQueuePanel
    title="检验待执行队列（检查科）"
    tech-type="INSPECTION"
    request-id-key="inspectionRequestId"
    workflow-hint="流程：患者缴费 → 开始执行 → 录入 resultText。API：PUT /lis/requests/{id}/result"
    :fetch-queue="fetchLisQueue"
    :execute-request="executeLisRequest"
    :save-result="saveLisResult"
    show-triage
  />
</template>
