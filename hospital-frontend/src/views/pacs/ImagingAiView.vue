<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { INTEGRATION_ENV } from '../../config/integrations'

const route = useRoute()
const router = useRouter()

const checkRequestId = computed(() => Number(route.query.checkRequestId) || null)
const patientName = computed(() => route.query.patientName || '-')
const itemName = computed(() => route.query.itemName || '-')

const ctModelUrl = computed(() => {
  if (!INTEGRATION_ENV.ctModelViewerUrl || !checkRequestId.value) return ''
  const base = INTEGRATION_ENV.ctModelViewerUrl.replace(/\/$/, '')
  const sep = base.includes('?') ? '&' : '?'
  return `${base}${sep}checkRequestId=${checkRequestId.value}`
})

function openCtModelViewer() {
  if (ctModelUrl.value) {
    window.open(ctModelUrl.value, '_blank', 'noopener')
    return
  }
  ElMessage.info('请配置 VITE_CT_MODEL_URL 指向 CT 影像系统')
}

async function onAgentAiAnalysis() {
  if (!checkRequestId.value) return
  generating.value = true
  try {
    const res = await generatePacsAiReport(checkRequestId.value)
    detail.value = res.data
    ElMessage.success('AI 影像分析报告已生成，请返回队列核对录入')
  } catch (err) {
    ElMessage.error(err.message || 'AI 分析生成失败')
  } finally {
    generating.value = false
  }
}

function goBack() {
  router.push('/pacs/queue')
}
</script>

<template>
  <div class="imaging-ai-page">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="tip"
      title="影像 AI 工作台"
      description="打开 CT 影像阅片，或生成 AI 影像分析报告。"
    />

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <div>
            <span class="title">检查 #{{ checkRequestId || '—' }}</span>
            <span class="meta">{{ patientName }} · {{ itemName }}</span>
          </div>
          <el-button link @click="goBack">返回检查队列</el-button>
        </div>
      </template>

      <div v-if="!checkRequestId" class="empty-hint">
        请从「检查队列」点击「影像 AI 工作台」进入。
      </div>

      <template v-else>
        <div class="action-cards">
          <el-card shadow="hover" class="action-card ml-card">
            <h4>CT 影像</h4>
            <el-button type="warning" @click="openCtModelViewer">
              打开 CT 影像
            </el-button>
          </el-card>

          <el-card shadow="hover" class="action-card agent-card">
            <h4>AI 影像分析</h4>
            <el-button type="primary" :loading="generating" @click="onAgentAiAnalysis">
              生成 AI 影像分析
            </el-button>
          </el-card>
        </div>

        <el-card v-if="detail?.aiReportText || detail?.resultText" shadow="never" class="report-block">
          <template #header>AI 影像分析（供录入 resultText 参考）</template>
          <pre class="report-text">{{ detail.aiReportText || detail.resultText }}</pre>
        </el-card>
      </template>
    </el-card>
  </div>
</template>

<style scoped>
.imaging-ai-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 960px;
}

.tip {
  border-radius: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.title {
  font-weight: 600;
  margin-right: 12px;
}

.meta {
  color: #64748b;
  font-size: 13px;
}

.action-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

@media (max-width: 720px) {
  .action-cards {
    grid-template-columns: 1fr;
  }
}

.action-card h4 {
  margin: 0 0 12px;
  font-size: 15px;
}

.ml-card {
  border-color: #fde68a;
}

.agent-card {
  border-color: #99f6e4;
}

.report-block {
  margin-top: 8px;
}

.report-text {
  margin: 0;
  white-space: pre-wrap;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.6;
  color: #334155;
}

.empty-hint {
  color: #64748b;
  font-size: 14px;
  padding: 24px 0;
}
</style>
