<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { INTEGRATION_ROUTES } from '../../config/integrations'
import { fetchImagingStudies } from '../../api/pacs'

const router = useRouter()
const loading = ref(false)
const statusFilter = ref('')
const list = ref([])

const statusMap = {
  PENDING: { label: '待缴费/待登记', type: 'info' },
  IN_PROGRESS: { label: '检查中', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
}

onMounted(loadList)

async function loadList() {
  loading.value = true
  try {
    const res = await fetchImagingStudies({
      status: statusFilter.value || undefined,
    })
    list.value = res.data?.list ?? []
  } catch (err) {
    ElMessage.error(err.message || '加载影像任务失败')
  } finally {
    loading.value = false
  }
}

function goImagingAi(row) {
  router.push({
    path: INTEGRATION_ROUTES.imagingAiWorkbench,
    query: {
      checkRequestId: row.checkRequestId,
      patientName: row.patientName,
      itemName: row.itemName,
    },
  })
}
</script>

<template>
  <div class="imaging-page">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="影像任务"
      description="对应 imaging_study / check_request。大模型组可通过「打开影像 AI 工作台」进入 CT 推理界面，生成 AI 检查报告后由放射科医师核对录入。"
      class="tip"
    />

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>影像任务列表</span>
          <div class="filters">
            <el-select v-model="statusFilter" clearable placeholder="全部状态" style="width: 140px" @change="loadList">
              <el-option label="待处理" value="PENDING" />
              <el-option label="检查中" value="IN_PROGRESS" />
              <el-option label="已完成" value="COMPLETED" />
            </el-select>
            <el-button :loading="loading" @click="loadList">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="list" empty-text="暂无影像任务">
        <el-table-column prop="medicalRecordNo" label="病历号" width="150" />
        <el-table-column prop="patientName" label="患者" width="100" />
        <el-table-column prop="itemName" label="检查项目" min-width="140" />
        <el-table-column prop="modality" label="模态" width="80" />
        <el-table-column label="上传" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.uploadStatus === 'UPLOADED' ? 'success' : 'info'">
              {{ row.uploadStatus === 'UPLOADED' ? '已登记' : '待上传' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="statusMap[row.status]?.type || 'info'">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="报告" width="90">
          <template #default="{ row }">
            {{ row.resultReady ? '已出' : '—' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.uploadStatus === 'UPLOADED' && !row.resultReady"
              type="warning"
              link
              @click="goImagingAi(row)"
            >
              影像 AI 工作台
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.imaging-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tip {
  border-radius: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.filters {
  display: flex;
  gap: 8px;
}
</style>
