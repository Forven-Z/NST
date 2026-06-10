<script setup>

import TechQueuePanel from '../../components/tech/TechQueuePanel.vue'

import {

  executeLisRequest,

  fetchLisQueue,

  generateLisAiReport,

  saveLisResult,

} from '../../api/lis'



async function generateLisAiSuggestion(id) {

  const res = await generateLisAiReport(id)

  return { data: { resultText: res.data?.aiReportText || res.data?.resultText || '' } }

}

</script>



<template>

  <TechQueuePanel

    title="检验待执行队列"

    tech-type="INSPECTION"

    request-id-key="inspectionRequestId"

    workflow-hint="流程：患者缴费 → 开始执行 → 录入 resultText（可选 AI 建议填入）→ 医生工作站查看结果。API：PUT /lis/requests/{id}/result"

    :fetch-queue="fetchLisQueue"

    :execute-request="executeLisRequest"

    :save-result="saveLisResult"

    :generate-ai-suggestion="generateLisAiSuggestion"

    show-triage

  />

</template>

<script setup>

import TechQueuePanel from '../../components/tech/TechQueuePanel.vue'

import {

  executeLisRequest,

  fetchLisQueue,

  generateLisAiReport,

  saveLisResult,

} from '../../api/lis'



async function generateLisAiSuggestion(id) {

  const res = await generateLisAiReport(id)

  return { data: { resultText: res.data?.aiReportText || res.data?.resultText || '' } }

}

</script>



<template>

  <TechQueuePanel

    title="检验待执行队列"

    tech-type="INSPECTION"

    request-id-key="inspectionRequestId"

    workflow-hint="流程：患者缴费 → 开始执行 → 录入 resultText（可选 AI 建议填入）→ 医生工作站查看结果。API：PUT /lis/requests/{id}/result"

    :fetch-queue="fetchLisQueue"

    :execute-request="executeLisRequest"

    :save-result="saveLisResult"

    :generate-ai-suggestion="generateLisAiSuggestion"

    show-triage

  />

</template>

