<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ResultReportSections from '../../components/medical/ResultReportSections.vue'
import { INTEGRATION_ENV } from '../../config/integrations'
import { fetchPacsResultDetail, generatePacsAiReport } from '../../api/pacs'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const generating = ref(false)
const detail = ref(null)

const checkRequestId = computed(() => Number(route.query.checkRequestId) || null)
const patientName = computed(() => route.query.patientName || detail.value?.patientName || '-')
const itemName = computed(() => route.query.itemName || detail.value?.itemName || '-')

const ctModelUrl = computed(() => {
  if (!INTEGRATION_ENV.ctModelViewerUrl || !checkRequestId.value) return ''
  const base = INTEGRATION_ENV.ctModelViewerUrl.replace(/\/$/, '')
  const sep = base.includes('?') ? '&' : '?'
  return `${base}${sep}checkRequestId=${checkRequestId.value}`
})

onMounted(loadDetail)

async function loadDetail() {
  if (!checkRequestId.value) return
  loading.value = true
  try {
    const res = await fetchPacsResultDetail(checkRequestId.value)
    detail.value = res.data
  } catch (err) {
    ElMessage.error(err.message || '加载检查详情失败')
  } finally {
    loading.value = false
  }
}

function openCtModelViewer() {
  if (ctModelUrl.value) {
    window.open(ctModelUrl.value, '_blank', 'noopener')
    return
  }
  ElMessage.info('请配置 VITE_CT_MODEL_URL 指向大模型训练组的 CT 影像软件')
}

async function onAgentAiAnalysis() {
  if (!checkRequestId.value) return
  generating.value = true
  try {
    const res = await generatePacsAiReport(checkRequestId.value)
    detail.value = res.data
    ElMessage.success('智能体已生成 AI 影像分析报告，请返回队列核对录入')
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
      description="两个独立入口：① 大模型训练组 CT 阅片软件（跳转，含脑部 CT 选取进度条等，由对方实现）；② 智能体组生成 AI 影像分析报告（本系统 API 对接）。"
    />

    <el-card v-loading="loading" shadow="never">
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
        请从「检查队列」或「影像任务」点击「影像 AI 工作台」进入。
      </div>

      <template v-else>
        <div class="action-cards">
          <el-card shadow="hover" class="action-card ml-card">
            <h4>大模型训练组 · CT 影像</h4>
            <p>跳转到训练组的 CT 软件画面（脑部 CT 进度条、区域选取等由对方实现）。</p>
            <el-button type="warning" @click="openCtModelViewer">
              打开大模型 CT 影像
            </el-button>
          </el-card>

          <el-card shadow="hover" class="action-card agent-card">
            <h4>智能体组 · AI 影像分析</h4>
            <p>基于影像与仪器数据，由智能体服务生成 AI 检查报告（可返回队列修改后录入）。</p>
            <el-button type="primary" :loading="generating" @click="onAgentAiAnalysis">
              生成 AI 影像分析
            </el-button>
          </el-card>
        </div>

        <ResultReportSections
          v-if="detail"
          class="report-block"
          :instrument-data="detail.instrumentData"
          :ai-report-text="detail.aiReportText"
          :doctor-report-text="detail.doctorReportText"
          :ai-report-status="detail.aiReportStatus"
          :editable-ai="false"
          :editable-doctor="false"
        />
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
  margin-bottom: 16px;
}

@media (max-width: 720px) {
  .action-cards {
    grid-template-columns: 1fr;
  }
}

.action-card h4 {
  margin: 0 0 8px;
  font-size: 15px;
}

.action-card p {
  margin: 0 0 12px;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
  min-height: 40px;
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

.empty-hint {
  color: #64748b;
  font-size: 14px;
  padding: 24px 0;
}
</style>
