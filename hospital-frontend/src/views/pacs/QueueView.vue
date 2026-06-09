<script setup>
import TechQueuePanel from '../../components/tech/TechQueuePanel.vue'
import { executePacsRequest, fetchPacsQueue, savePacsResult } from '../../api/pacs'
</script>

<template>
  <TechQueuePanel
    title="检查待执行队列"
    tech-type="CHECK"
    request-id-key="checkRequestId"
    workflow-hint="流程：患者缴费 → 开始执行 → 影像 AI 工作台（可选阅片）→ 录入 resultText。API：PUT /pacs/requests/{id}/result"
    show-triage
    :fetch-queue="fetchPacsQueue"
    :execute-request="executePacsRequest"
    :save-result="savePacsResult"
  />
</template>
