<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '../../stores/auth'
import {
  cancelLeaveRequest,
  fetchMySchedules,
  submitScheduleLeaveRequest,
} from '../../api/scheduling'

const auth = useAuthStore()
const loading = ref(false)
const submittingId = ref(null)
const schedules = ref([])

const leaveDialogVisible = ref(false)
const leaveReason = ref('')
const leaveTarget = ref(null)

const employeeId = computed(() => auth.user?.employeeId)

onMounted(loadSchedules)

async function loadSchedules() {
  if (!employeeId.value) return
  loading.value = true
  try {
    const res = await fetchMySchedules({
      employeeId: employeeId.value,
      workDateFrom: new Date().toISOString().slice(0, 10),
    })
    schedules.value = res.data?.list ?? []
  } catch (err) {
    ElMessage.error(err.message || '加载排班失败')
  } finally {
    loading.value = false
  }
}

function openLeave(row) {
  if (!row.canRequestLeave) {
    return ElMessage.info('该班次不可请假（可能已有申请或已过期）')
  }
  leaveTarget.value = row
  leaveReason.value = ''
  leaveDialogVisible.value = true
}

async function onSubmitLeave() {
  if (!leaveTarget.value || !employeeId.value) return
  if (!leaveReason.value.trim()) return ElMessage.warning('请填写请假原因')
  submittingId.value = leaveTarget.value.schedulingId
  try {
    const res = await submitScheduleLeaveRequest(leaveTarget.value.schedulingId, {
      employeeId: employeeId.value,
      reason: leaveReason.value.trim(),
    })
    ElMessage.success(res.data?.message || '请假已提交')
    leaveDialogVisible.value = false
    await loadSchedules()
  } catch (err) {
    ElMessage.error(err.message || '提交失败')
  } finally {
    submittingId.value = null
  }
}

async function onCancelLeave(row) {
  if (!row.leaveRequestId || row.leaveStatus !== 0) return
  try {
    await ElMessageBox.confirm('确定撤销该请假申请？', '撤销请假', { type: 'warning' })
  } catch {
    return
  }
  try {
    await cancelLeaveRequest(row.leaveRequestId, { employeeId: employeeId.value })
    ElMessage.success('已撤销')
    await loadSchedules()
  } catch (err) {
    ElMessage.error(err.message || '撤销失败')
  }
}

function leaveTagType(status) {
  if (status === 0) return 'warning'
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'info'
}
</script>

<template>
  <div class="my-sched-page">
    <div class="page-head">
      <h2 class="page-title">我的排班</h2>
      <p class="page-desc">
        查看本人排班（门诊出诊 / 科室值班 / 窗口班等）。对尚未开始的班次可提交请假，由管理员审批后安排替班。
      </p>
    </div>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>未来 7 日出诊安排</span>
          <el-button :loading="loading" @click="loadSchedules">刷新</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="schedules" stripe empty-text="暂无未来排班">
        <el-table-column prop="workDate" label="日期" width="110" />
        <el-table-column prop="noonLabel" label="午别" width="72" />
        <el-table-column prop="timeRange" label="时段" width="110" />
        <el-table-column prop="deptName" label="科室" width="88" />
        <el-table-column label="班次类型" width="100">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.scheduleType === 'DUTY' ? 'success' : row.registLevelId === 2 ? 'warning' : 'info'"
            >
              {{ row.registLevelName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="号源" width="110">
          <template #default="{ row }">
            <span v-if="row.scheduleType === 'DUTY'" class="muted">值班</span>
            <span v-else>余 {{ row.remainQuota }} / {{ row.totalQuota }}</span>
          </template>
        </el-table-column>
        <el-table-column label="请假状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.leaveStatus !== null" size="small" :type="leaveTagType(row.leaveStatus)">
              {{ row.leaveStatusLabel }}
            </el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.canRequestLeave"
              type="primary"
              link
              :loading="submittingId === row.schedulingId"
              @click="openLeave(row)"
            >
              请假
            </el-button>
            <el-button
              v-else-if="row.leaveStatus === 0"
              type="danger"
              link
              @click="onCancelLeave(row)"
            >
              撤销
            </el-button>
            <span v-else-if="row.leaveStatus === 1" class="muted tip">待管理员替班</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="leaveDialogVisible" title="提交请假申请" width="480px" destroy-on-close>
      <template v-if="leaveTarget">
        <el-descriptions :column="1" border size="small" class="leave-summary">
          <el-descriptions-item label="班次">
            {{ leaveTarget.workDate }} {{ leaveTarget.noonLabel }}（{{ leaveTarget.timeRange }}）
          </el-descriptions-item>
          <el-descriptions-item label="科室 / 号别">
            {{ leaveTarget.deptName }} · {{ leaveTarget.registLevelName }}
          </el-descriptions-item>
          <el-descriptions-item label="备注">
            <template v-if="leaveTarget.scheduleType === 'DUTY'">值班班次请假</template>
            <template v-else>已挂号 {{ leaveTarget.usedQuota ?? 0 }} 人，批准后须安排替班</template>
          </el-descriptions-item>
        </el-descriptions>
        <el-form label-position="top" class="reason-form">
          <el-form-item label="请假原因" required>
            <el-input
              v-model="leaveReason"
              type="textarea"
              :rows="3"
              placeholder="如：参加学术会议、病假等"
            />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="leaveDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="!!submittingId" @click="onSubmitLeave">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.my-sched-page {
  max-width: 960px;
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

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.leave-summary {
  margin-bottom: 12px;
}

.reason-form {
  margin-top: 8px;
}

.muted {
  color: #94a3b8;
  font-size: 12px;
}

.tip {
  font-size: 12px;
}
</style>
