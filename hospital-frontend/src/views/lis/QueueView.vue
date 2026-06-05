<script setup>
import TechQueuePanel from '../../components/tech/TechQueuePanel.vue'
import { executeLisRequest, fetchLisQueue, saveLisResult } from '../../api/lis'
</script>

<template>
  <TechQueuePanel
    title="检验待执行队列"
    tech-type="INSPECTION"
    request-id-key="inspectionRequestId"
    workflow-hint="患者缴费后至检验科采血/留样；检验师执行并录入报告，医生工作站可查看结果。Mock 演示：赵大爷 MR202606040003 有血常规待执行。"
    :fetch-queue="fetchLisQueue"
    :execute-request="executeLisRequest"
    :save-result="saveLisResult"
  />
</template>
