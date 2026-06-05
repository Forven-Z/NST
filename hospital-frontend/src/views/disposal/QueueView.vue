<script setup>
import TechQueuePanel from '../../components/tech/TechQueuePanel.vue'
import { executeDisposalRequest, fetchDisposalQueue, saveDisposalResult } from '../../api/disposal'
</script>

<template>
  <TechQueuePanel
    title="处置待执行队列"
    tech-type="DISPOSAL"
    request-id-key="disposalRequestId"
    workflow-hint="处置项目（如洗胃、输液等）须医生开立并缴费后，由处置科执行并记录。医生开单后可在收费处缴费，队列即出现。"
    :fetch-queue="fetchDisposalQueue"
    :execute-request="executeDisposalRequest"
    :save-result="saveDisposalResult"
  />
</template>
