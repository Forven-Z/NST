<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const SAMPLE_RESULTS = {
  INSPECTION: `白细胞计数 WBC 6.2×10⁹/L（参考 3.5-9.5）
红细胞计数 RBC 4.65×10¹²/L
血红蛋白 Hb 138 g/L
血小板 PLT 210×10⁹/L
中性粒细胞比例 62%
结论：血常规未见明显异常。`,
  CHECK: `检查部位：头颅 CT 平扫
影像所见：脑实质密度均匀，未见明显占位性病变；脑室系统大小形态正常；中线结构居中。
印象：头颅 CT 平扫未见明显异常。`,
  DISPOSAL: `处置项目：洗胃
过程：经口置入胃管，用 0.9% 氯化钠溶液反复灌洗至洗出液澄清。
结果：洗胃完成，患者生命体征平稳，安返观察。`,
}

const props = defineProps({
  title: { type: String, default: '待执行队列' },
  requestIdKey: { type: String, required: true },
  techType: { type: String, default: 'INSPECTION' },
  fetchQueue: { type: Function, required: true },
  executeRequest: { type: Function, required: true },
  saveResult: { type: Function, required: true },
  defaultStatus: { type: Number, default: 20 },
  workflowHint: { type: String, default: '' },
})

const loading = ref(false)
const executingId = ref(null)
const savingId = ref(null)
const statusFilter = ref(props.defaultStatus)
const list = ref([])

const resultDialogVisible = ref(false)
const resultText = ref('')
const currentRow = ref(null)

const statusMap = {
  10: { label: '已开立', type: 'info' },
  20: { label: '已缴费', type: 'warning' },
  30: { label: '执行中', type: 'primary' },
  40: { label: '已出结果', type: 'success' },
}

onMounted(loadList)

function rowId(row) {
  return row[props.requestIdKey]
}

async function loadList() {
  loading.value = true
  try {
    const res = await props.fetchQueue({
      status: statusFilter.value,
      page: 1,
      pageSize: 50,
    })
    list.value = res.data?.list ?? []
  } catch (err) {
    ElMessage.error(err.message || '加载队列失败')
  } finally {
    loading.value = false
  }
}

async function onExecute(row) {
  const id = rowId(row)
  executingId.value = id
  try {
    await props.executeRequest(id)
    ElMessage.success('已开始执行，完成后请录入结果')
    await loadList()
  } catch (err) {
    ElMessage.error(err.message || '执行失败')
  } finally {
    executingId.value = null
  }
}

function openResultDialog(row) {
  currentRow.value = row
  resultText.value = row.resultText || ''
  resultDialogVisible.value = true
}

async function onSaveResult() {
  if (!resultText.value.trim()) {
    ElMessage.warning('请填写结果')
    return
  }
  const id = rowId(currentRow.value)
  savingId.value = id
  try {
    await props.saveResult(id, { resultText: resultText.value.trim() })
    ElMessage.success('结果已保存，医生可在工作站查看')
    resultDialogVisible.value = false
    await loadList()
  } catch (err) {
    ElMessage.error(err.message || '保存失败')
  } finally {
    savingId.value = null
  }
}

async function onQuickFill() {
  try {
    await ElMessageBox.confirm('填入该类型项目的示例报告？', '快捷录入', { type: 'info' })
    resultText.value = SAMPLE_RESULTS[props.techType] || SAMPLE_RESULTS.INSPECTION
  } catch {
    /* cancelled */
  }
}
</script>

<template>
  <div class="tech-panel">
    <el-alert
      v-if="workflowHint"
      type="info"
      :closable="false"
      show-icon
      class="flow-tip"
      :title="title"
      :description="workflowHint"
    />

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ title }}</span>
          <div class="filters">
            <el-radio-group v-model="statusFilter" @change="loadList">
              <el-radio-button :label="20">已缴费待执行</el-radio-button>
              <el-radio-button :label="30">执行中</el-radio-button>
              <el-radio-button :label="40">已出结果</el-radio-button>
            </el-radio-group>
            <el-button :loading="loading" @click="loadList">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="list" empty-text="暂无待处理申请（须患者先缴费）">
        <el-table-column prop="medicalRecordNo" label="病历号" width="150" />
        <el-table-column prop="patientName" label="患者" width="100" />
        <el-table-column prop="itemName" label="项目" min-width="140" />
        <el-table-column label="金额" width="90">
          <template #default="{ row }">¥{{ row.itemPrice ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'" size="small">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 20"
              type="primary"
              link
              :loading="executingId === rowId(row)"
              @click="onExecute(row)"
            >
              开始执行
            </el-button>
            <el-button
              v-if="row.status === 20 || row.status === 30"
              type="success"
              link
              @click="openResultDialog(row)"
            >
              录入结果
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="resultDialogVisible" title="录入结果" width="560px">
      <el-input v-model="resultText" type="textarea" :rows="10" placeholder="检验/检查/处置报告正文..." />
      <template #footer>
        <el-button @click="onQuickFill">填入示例报告</el-button>
        <el-button @click="resultDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="!!savingId" @click="onSaveResult">保存并发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.tech-panel {
  max-width: 1100px;
}

.flow-tip {
  margin-bottom: 12px;
  border-radius: 8px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.filters {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
