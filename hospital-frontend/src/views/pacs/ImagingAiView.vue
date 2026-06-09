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

function onAgentAiAnalysis() {
  ElMessage.info('影像 AI 分析由智能体服务对接，报告请手工录入至检查队列 resultText')
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
      description="打开 CT 影像阅片；AI 分析报告由智能体生成后，请在检查队列录入 resultText。"
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
            <p class="agent-hint">契约未定义 ai-report 接口；联调时由智能体侧提供分析结果。</p>
            <el-button type="primary" @click="onAgentAiAnalysis">
              了解 AI 分析流程
            </el-button>
          </el-card>
        </div>
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

.agent-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
}

.ml-card {
  border-color: #fde68a;
}

.agent-card {
  border-color: #99f6e4;
}

.empty-hint {
  color: #64748b;
  font-size: 14px;
  padding: 24px 0;
}
</style>
