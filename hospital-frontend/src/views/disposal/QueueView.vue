<script setup>
import TechQueuePanel from '../../components/tech/TechQueuePanel.vue'
import {
  executeDisposalRequest,
  fetchDisposalQueue,
  fetchDisposalResultDetail,
  saveDisposalResult,
} from '../../api/disposal'
</script>

<template>
  <TechQueuePanel
    title="处置待执行队列"
    tech-type="DISPOSAL"
    request-id-key="disposalRequestId"
    workflow-hint="流程：患者缴费 → 开始执行 → 填写处置过程与观察结果 → 签阅发布"
    :fetch-queue="fetchDisposalQueue"
    :fetch-result-detail="fetchDisposalResultDetail"
    :execute-request="executeDisposalRequest"
    :save-result="saveDisposalResult"
  />
</template>
