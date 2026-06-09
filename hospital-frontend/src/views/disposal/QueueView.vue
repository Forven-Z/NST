<script setup>
import TechQueuePanel from '../../components/tech/TechQueuePanel.vue'
import { executeDisposalRequest, fetchDisposalQueue, saveDisposalResult } from '../../api/disposal'
</script>

<template>
  <TechQueuePanel
    title="处置待执行队列"
    tech-type="DISPOSAL"
    request-id-key="disposalRequestId"
    workflow-hint="处置项目须医生开立并缴费后执行。录入 resultText 后医生可查看。API：PUT /disposal/requests/{id}/result"
    :fetch-queue="fetchDisposalQueue"
    :execute-request="executeDisposalRequest"
    :save-result="saveDisposalResult"
  />
</template>
