<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  applyAiSchedulingReplace,
  fetchAdminSchedules,
  fetchAiSchedulingSuggest,
  fetchDepartments,
  updateAdminSchedule,
} from '../../api/admin'

const loading = ref(false)
const aiLoading = ref(false)
const saving = ref(false)
const replacingId = ref(null)
const deptFilter = ref(null)
const schedules = ref([])
const outpatientDepts = ref([])
const aiSuggestions = ref([])

const editVisible = ref(false)
const editForm = reactive({
  schedulingId: null,
  employeeName: '',
  timeRange: '',
  totalQuota: 20,
  remainQuota: 15,
  registFee: 20,
})

onMounted(async () => {
  const deptRes = await fetchDepartments({ deptType: 1, pageSize: 20 })
  outpatientDepts.value = deptRes.data?.list ?? []
  await loadSchedules()
})

async function loadSchedules() {
  loading.value = true
  try {
    const res = await fetchAdminSchedules({
      deptId: deptFilter.value || undefined,
      pageSize: 100,
    })
    schedules.value = res.data?.list ?? []
  } catch (err) {
    ElMessage.error(err.message || '加载排班失败')
  } finally {
    loading.value = false
  }
}

async function onAiSuggest() {
  aiLoading.value = true
  try {
    const res = await fetchAiSchedulingSuggest({ deptId: deptFilter.value || undefined })
    aiSuggestions.value = res.data?.suggestions ?? []
    ElMessage.success(res.data?.message || 'AI 排班建议已生成')
  } catch (err) {
    ElMessage.error(err.message || '获取 AI 排班建议失败')
  } finally {
    aiLoading.value = false
  }
}

function getAiSuggestion(schedulingId) {
  return aiSuggestions.value.find((s) => s.schedulingId === schedulingId)
}

async function onApplyAiReplace(suggestion) {
  if (!suggestion?.replaceable || !suggestion.proposedSchedule) {
    return ElMessage.info('该条 AI 建议无需替换排班')
  }
  try {
    await ElMessageBox.confirm(
      `将应用 AI 建议：${suggestion.suggestion}`,
      '应用 AI 替换排班',
      { type: 'warning' },
    )
  } catch {
    return
  }
  replacingId.value = suggestion.schedulingId
  try {
    await applyAiSchedulingReplace(suggestion.schedulingId, suggestion.proposedSchedule)
    ElMessage.success('已应用 AI 推荐排班')
    await loadSchedules()
  } catch (err) {
    ElMessage.error(err.message || '替换失败')
  } finally {
    replacingId.value = null
  }
}

function openEdit(row) {
  Object.assign(editForm, {
    schedulingId: row.schedulingId,
    employeeName: row.employeeName,
    timeRange: row.timeRange,
    totalQuota: row.totalQuota,
    remainQuota: row.remainQuota,
    registFee: row.registFee,
  })
  editVisible.value = true
}

async function onSaveEdit() {
  if (!editForm.schedulingId) return
  if (editForm.remainQuota > editForm.totalQuota) {
    return ElMessage.warning('剩余号源不能大于总号源')
  }
  saving.value = true
  try {
    await updateAdminSchedule(editForm.schedulingId, { ...editForm })
    ElMessage.success('排班已更新')
    editVisible.value = false
    await loadSchedules()
  } catch (err) {
    ElMessage.error(err.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="sched-page">
    <div class="page-head">
      <h2 class="page-title">排班维护</h2>
      <p class="page-desc">
        AI 智能排班由智能体组生成建议；管理员可「应用 AI 替换」或「手工编辑」调整。Mock 模式下变更即时生效。
      </p>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <span class="label">筛选科室</span>
        <el-select v-model="deptFilter" clearable placeholder="全部门诊科室" style="width: 160px" @change="loadSchedules">
          <el-option v-for="d in outpatientDepts" :key="d.id" :label="d.deptName" :value="d.id" />
        </el-select>
        <el-button :loading="loading" @click="loadSchedules">刷新</el-button>
        <el-button type="primary" :loading="aiLoading" @click="onAiSuggest">
          获取 AI 排班建议
        </el-button>
        <el-tag type="warning" size="small">智能体组对接</el-tag>
      </div>

      <el-table v-loading="loading" :data="schedules" stripe max-height="480" empty-text="暂无排班">
        <el-table-column prop="workDate" label="日期" width="110" />
        <el-table-column prop="noonLabel" label="午别" width="72" />
        <el-table-column prop="timeRange" label="时段" width="110" />
        <el-table-column prop="deptId" label="科室" width="90">
          <template #default="{ row }">
            {{ outpatientDepts.find((d) => d.id === row.deptId)?.deptName }}
          </template>
        </el-table-column>
        <el-table-column prop="employeeName" label="医生" width="88" />
        <el-table-column label="号别" width="88">
          <template #default="{ row }">
            <el-tag size="small" :type="row.registLevelId === 2 ? 'warning' : 'info'">
              {{ row.registLevelName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="号源" width="110">
          <template #default="{ row }">余 {{ row.remainQuota }} / {{ row.totalQuota }}</template>
        </el-table-column>
        <el-table-column label="挂号费" width="80">
          <template #default="{ row }">¥{{ row.registFee }}</template>
        </el-table-column>
        <el-table-column label="标记" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.aiApplied" size="small" type="success">AI已替换</el-tag>
            <el-tag v-else-if="row.manualEdited" size="small" type="info">手工编辑</el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">手工编辑</el-button>
            <el-button
              v-if="getAiSuggestion(row.schedulingId)?.replaceable"
              link
              type="warning"
              :loading="replacingId === row.schedulingId"
              @click="onApplyAiReplace(getAiSuggestion(row.schedulingId))"
            >
              应用 AI 替换
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-card v-if="aiSuggestions.length" shadow="never" class="ai-suggest-card">
        <template #header>
          <span>AI 排班建议详情</span>
        </template>
        <div v-for="s in aiSuggestions" :key="s.schedulingId" class="suggest-item">
          <div class="suggest-head">
            <strong>{{ s.workDate }} {{ s.noonLabel }} · {{ s.employeeName }}</strong>
            <el-tag size="small">置信度 {{ Math.round((s.confidence || 0) * 100) }}%</el-tag>
          </div>
          <p>{{ s.suggestion }}</p>
          <el-button
            v-if="s.replaceable"
            size="small"
            type="warning"
            :loading="replacingId === s.schedulingId"
            @click="onApplyAiReplace(s)"
          >
            应用此 AI 替换
          </el-button>
        </div>
      </el-card>
    </el-card>

    <el-dialog v-model="editVisible" title="手工编辑排班" width="480px" destroy-on-close>
      <el-form label-width="88px">
        <el-form-item label="医生">
          <el-input v-model="editForm.employeeName" />
        </el-form-item>
        <el-form-item label="时段">
          <el-input v-model="editForm.timeRange" placeholder="如 08:00-12:00" />
        </el-form-item>
        <el-form-item label="总号源">
          <el-input-number v-model="editForm.totalQuota" :min="1" :max="99" />
        </el-form-item>
        <el-form-item label="剩余号源">
          <el-input-number v-model="editForm.remainQuota" :min="0" :max="99" />
        </el-form-item>
        <el-form-item label="挂号费">
          <el-input-number v-model="editForm.registFee" :min="0" :max="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sched-page {
  max-width: 1100px;
}

.page-head {
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.page-desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.toolbar .label {
  font-size: 13px;
  color: #64748b;
}

.ai-suggest-card {
  margin-top: 16px;
  border-radius: 8px;
}

.suggest-item {
  padding: 8px 0;
  border-bottom: 1px solid #f1f5f9;
}

.suggest-item:last-child {
  border-bottom: none;
}

.suggest-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.suggest-item p {
  margin: 0 0 8px;
  font-size: 13px;
  color: #475569;
}

.muted {
  color: #94a3b8;
  font-size: 12px;
}
</style>
