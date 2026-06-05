<script setup>
import TechQueuePanel from '../../components/tech/TechQueuePanel.vue'
import { executePacsRequest, fetchPacsQueue, savePacsResult } from '../../api/pacs'
</script>

<template>
  <TechQueuePanel
    title="检查待执行队列"
    tech-type="CHECK"
    request-id-key="checkRequestId"
    workflow-hint="患者缴费后至放射科登记；影像医师完成检查后录入报告。Mock 演示：赵大爷 MR202606040003 有头部 CT 待执行。"
    :fetch-queue="fetchPacsQueue"
    :execute-request="executePacsRequest"
    :save-result="savePacsResult"
  />
</template>
