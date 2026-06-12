<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  applyAiSchedulingReplace,
  createAdminSchedule,
  fetchAdminSchedules,
  fetchAiSchedulingSuggest,
  fetchDepartments,
  fetchEmployees,
  fetchRegistLevels,
  publishAdminSchedule,
  updateAdminSchedule,
} from '../../api/admin'
import {
  approveLeaveRequest,
  fetchAdminLeaveRequests,
  rejectLeaveRequest,
} from '../../api/scheduling'
import { useMock } from '../../utils/mock'

const loading = ref(false)
const leaveLoading = ref(false)
const aiLoading = ref(false)
const saving = ref(false)
const replacingId = ref(null)
const leaveActionId = ref(null)
const deptFilter = ref(null)
const leaveTab = ref('pending')
const schedules = ref([])
const leaveRequests = ref([])
const allDepts = ref([])
const registLevels = ref([])
const aiSuggestions = ref([])
const candidateEmployees = ref([])
const editCandidates = ref([])
const publishingId = ref(null)
const creating = ref(false)

const pendingLeaves = computed(() => leaveRequests.value.filter((l) => l.status === 0))
const approvedLeaves = computed(() => leaveRequests.value.filter((l) => l.status === 1))

const editVisible = ref(false)
const editForm = reactive({
  schedulingId: null,
  deptId: null,
  employeeId: null,
  employeeName: '',
  timeRange: '',
  totalQuota: 20,
  remainQuota: 15,
  usedQuota: 0,
  registFee: 20,
  needsSubstitute: false,
})

const createVisible = ref(false)
const createForm = reactive({
  scheduleKind: 1,
  deptId: null,
  employeeId: null,
  workDate: '',
  noonType: 1,
  registLevelId: 1,
  totalQuota: 20,
})

const outpatientDepts = computed(() => allDepts.value.filter((d) => d.deptType === 1))

const editRemainQuota = computed(() => Math.max(0, editForm.totalQuota - (editForm.usedQuota ?? 0)))

const publishStatusMap = {
  0: { label: '草稿', type: 'info' },
  1: { label: '已发布', type: 'success' },
  2: { label: '已取消', type: 'danger' },
}

onMounted(async () => {
  const [deptRes, levelRes] = await Promise.all([
    fetchDepartments({ pageSize: 100 }),
    fetchRegistLevels({ pageSize: 20 }),
  ])
  allDepts.value = deptRes.data?.list ?? []
  registLevels.value = levelRes.data?.list ?? []
  await Promise.all([loadSchedules(), loadLeaveRequests()])
})

async function loadCandidatesForCreate() {
  if (!createForm.deptId) {
    candidateEmployees.value = []
    return
  }
  try {
    const res = await fetchEmployees({
      deptId: createForm.deptId,
      scheduleKind: createForm.scheduleKind,
      delmark: 0,
      pageSize: 100,
    })
    candidateEmployees.value = res.data?.list ?? []
    if (!candidateEmployees.value.some((e) => e.employeeId === createForm.employeeId)) {
      createForm.employeeId = candidateEmployees.value[0]?.employeeId ?? null
    }
  } catch {
    candidateEmployees.value = []
  }
}

async function loadCandidatesForEdit(deptId) {
  if (!deptId) {
    editCandidates.value = []
    return
  }
  try {
    const res = await fetchEmployees({ deptId, delmark: 0, pageSize: 100 })
    editCandidates.value = res.data?.list ?? []
  } catch {
    editCandidates.value = []
  }
}

function openCreate() {
  Object.assign(createForm, {
    scheduleKind: 1,
    deptId: outpatientDepts.value[0]?.id ?? allDepts.value[0]?.id ?? null,
    employeeId: null,
    workDate: '',
    noonType: 1,
    registLevelId: 1,
    totalQuota: 20,
  })
  createVisible.value = true
  loadCandidatesForCreate()
}

async function onCreateSchedule() {
  if (createForm.scheduleKind === 2) {
    return ElMessage.warning('科室值班排班尚未支持，请选择门诊出诊')
  }
  if (!createForm.employeeId) return ElMessage.warning('请选择人员（来自员工管理列表）')
  if (!createForm.workDate) return ElMessage.warning('请选择出诊日期')
  creating.value = true
  try {
    const emp = candidateEmployees.value.find((e) => e.employeeId === createForm.employeeId)
    const level = registLevels.value.find((l) => l.id === createForm.registLevelId)
    await createAdminSchedule({
      ...createForm,
      employeeName: emp?.realName,
      registLevelName: level?.levelName,
      registFee: level?.registFee ?? level?.fee,
    })
    ElMessage.success('排班草稿已创建，请发布')
    createVisible.value = false
    await loadSchedules()
  } catch (err) {
    ElMessage.error(err.message || '创建失败')
  } finally {
    creating.value = false
  }
}

async function onPublish(row) {
  publishingId.value = row.schedulingId
  try {
    await publishAdminSchedule(row.schedulingId)
    ElMessage.success('排班已发布')
    await loadSchedules()
  } catch (err) {
    ElMessage.error(err.message || '发布失败')
  } finally {
    publishingId.value = null
  }
}

async function loadSchedules() {
  loading.value = true
  try {
    const res = await fetchAdminSchedules({
      deptId: deptFilter.value || undefined,
      pageSize: 100,
    })
    schedules.value = sortSchedules(res.data?.list ?? [])
  } catch (err) {
    ElMessage.error(err.message || '加载排班失败')
  } finally {
    loading.value = false
  }
}

async function loadLeaveRequests() {
  leaveLoading.value = true
  try {
    const res = await fetchAdminLeaveRequests({})
    leaveRequests.value = res.data?.list ?? []
  } catch (err) {
    ElMessage.error(err.message || '加载请假失败')
  } finally {
    leaveLoading.value = false
  }
}

async function onAiSuggest() {
  aiLoading.value = true
  aiSuggestions.value = []
  try {
    const res = await fetchAiSchedulingSuggest({ deptId: deptFilter.value || undefined })
    aiSuggestions.value = res.data?.suggestions ?? []
    ElMessage.success(res.data?.message || 'AI 排班建议已生成')
  } catch (err) {
    ElMessage.warning(err.message || 'AI 排班建议尚未接入')
  } finally {
    aiLoading.value = false
  }
}

function getAiSuggestion(schedulingId) {
  return aiSuggestions.value.find((s) => s.schedulingId === schedulingId)
}

async function onApplyAiReplace(suggestion) {
  const schedulingId = suggestion?.schedulingId
  if (!schedulingId) return

  if (useMock() && suggestion?.replaceable && suggestion.proposedSchedule) {
    try {
      await ElMessageBox.confirm(
        `将应用 AI 建议：${suggestion.suggestion}`,
        suggestion.leaveDriven ? '应用请假替班' : '应用 AI 替换排班',
        { type: 'warning' },
      )
    } catch {
      return
    }
    replacingId.value = schedulingId
    try {
      await updateAdminSchedule(schedulingId, suggestion.proposedSchedule)
      ElMessage.success('已应用 AI 推荐排班')
      await Promise.all([loadSchedules(), loadLeaveRequests()])
    } catch (err) {
      ElMessage.error(err.message || '替换失败')
    } finally {
      replacingId.value = null
    }
    return
  }

  replacingId.value = schedulingId
  try {
    await applyAiSchedulingReplace(schedulingId, suggestion?.proposedSchedule)
  } catch (err) {
    ElMessage.warning(err.message || 'AI 替班尚未接入')
  } finally {
    replacingId.value = null
  }
}

function openEdit(row) {
  Object.assign(editForm, {
    schedulingId: row.schedulingId,
    deptId: row.deptId,
    employeeId: row.employeeId,
    employeeName: row.employeeName,
    timeRange: row.timeRange,
    totalQuota: row.totalQuota,
    remainQuota: row.remainQuota,
    usedQuota: row.usedQuota ?? Math.max(0, row.totalQuota - row.remainQuota),
    registFee: row.registFee,
    needsSubstitute: row.needsSubstitute,
  })
  editVisible.value = true
  loadCandidatesForEdit(row.deptId)
}

function onDoctorChange(empId) {
  const doc = editCandidates.value.find((d) => d.employeeId === empId)
  if (doc) {
    editForm.employeeName = doc.realName
    editForm.employeeId = doc.employeeId
  }
}

async function onSaveEdit() {
  if (!editForm.schedulingId) return
  if (editForm.needsSubstitute) {
    const original = schedules.value.find((s) => s.schedulingId === editForm.schedulingId)
    if (original && editForm.employeeId === original.employeeId) {
      return ElMessage.warning('待替班班次请选择另一名医生后再保存')
    }
  }
  saving.value = true
  try {
    const payload = useMock()
      ? {
          ...editForm,
          remainQuota: editRemainQuota.value,
          employeeTitle: editCandidates.value.find((d) => d.employeeId === editForm.employeeId)?.title,
          employeeName:
            editCandidates.value.find((d) => d.employeeId === editForm.employeeId)?.realName
            ?? editForm.employeeName,
        }
      : {
          employeeId: editForm.employeeId,
          totalQuota: editForm.totalQuota,
        }
    const res = await updateAdminSchedule(editForm.schedulingId, payload)
    ElMessage.success(res.data?.message || '排班已更新')
    editVisible.value = false
    await Promise.all([loadSchedules(), loadLeaveRequests()])
  } catch (err) {
    ElMessage.error(err.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onApproveLeave(row) {
  leaveActionId.value = row.leaveRequestId
  try {
    await approveLeaveRequest(row.leaveRequestId, { adminName: '系统管理员' })
    ElMessage.success('已批准请假，请安排替班')
    await Promise.all([loadSchedules(), loadLeaveRequests()])
  } catch (err) {
    ElMessage.error(err.message || '操作失败')
  } finally {
    leaveActionId.value = null
  }
}

async function onRejectLeave(row) {
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因（可选）', '驳回请假', {
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
    })
    leaveActionId.value = row.leaveRequestId
    await rejectLeaveRequest(row.leaveRequestId, { remark: value, adminName: '系统管理员' })
    ElMessage.success('已驳回')
    await loadLeaveRequests()
  } catch (err) {
    if (err !== 'cancel' && err?.message) ElMessage.error(err.message)
  } finally {
    leaveActionId.value = null
  }
}

function sortSchedules(list) {
  return [...list].sort((a, b) => {
    const dateCmp = String(a.workDate || '').localeCompare(String(b.workDate || ''))
    if (dateCmp !== 0) return dateCmp
    return (a.noonType ?? 0) - (b.noonType ?? 0)
  })
}

function rowClassName({ row }) {
  if (row.pendingLeave) return 'row-pending-leave'
  if (row.needsSubstitute) return 'row-needs-sub'
  return ''
}
</script>

<template>
  <div class="sched-page">
    <div class="page-head">
      <h2 class="page-title">排班维护</h2>
      <p class="page-desc">
        新建排班时从「员工管理」按科室筛选人员；发布后医生可在「我的排班」请假，本页审批并替班。
      </p>
    </div>

    <el-card shadow="never" class="leave-card">
      <template #header>
        <div class="card-header">
          <span>
            医生请假
            <el-badge v-if="pendingLeaves.length" :value="pendingLeaves.length" class="badge" />
          </span>
          <el-button :loading="leaveLoading" @click="loadLeaveRequests">刷新请假</el-button>
        </div>
      </template>

      <el-tabs v-model="leaveTab">
        <el-tab-pane :label="`待审批 (${pendingLeaves.length})`" name="pending">
          <el-table
            v-loading="leaveLoading"
            :data="pendingLeaves"
            empty-text="暂无待审批请假"
            size="small"
          >
            <el-table-column prop="employeeName" label="医生" width="88" />
            <el-table-column label="班次" min-width="160">
              <template #default="{ row }">
                {{ row.workDate }} {{ row.noonLabel }} · {{ row.registLevelName }}
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="原因" min-width="140" show-overflow-tooltip />
            <el-table-column label="已挂号" width="72">
              <template #default="{ row }">{{ row.usedQuota ?? 0 }}</template>
            </el-table-column>
            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button
                  link
                  type="success"
                  :loading="leaveActionId === row.leaveRequestId"
                  @click="onApproveLeave(row)"
                >
                  批准
                </el-button>
                <el-button link type="danger" @click="onRejectLeave(row)">驳回</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="`已批准待替班 (${approvedLeaves.length})`" name="approved">
          <el-table
            v-loading="leaveLoading"
            :data="approvedLeaves"
            empty-text="暂无待替班记录"
            size="small"
          >
            <el-table-column prop="employeeName" label="原医生" width="88" />
            <el-table-column label="班次" min-width="160">
              <template #default="{ row }">
                {{ row.workDate }} {{ row.noonLabel }} · {{ row.registLevelName }}
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="原因" min-width="120" show-overflow-tooltip />
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button
                  link
                  type="warning"
                  :loading="replacingId === row.schedulingId"
                  @click="onApplyAiReplace(getAiSuggestion(row.schedulingId) || { schedulingId: row.schedulingId })"
                >
                  AI 替班
                </el-button>
                <el-button
                  link
                  type="primary"
                  @click="openEdit(schedules.find((s) => s.schedulingId === row.schedulingId) || { schedulingId: row.schedulingId, deptId: row.deptId, employeeId: row.employeeId, employeeName: row.employeeName, timeRange: row.timeRange, totalQuota: 40, remainQuota: 35, registFee: 20, needsSubstitute: true })"
                >
                  手工换人
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-card shadow="never">
      <div class="toolbar">
        <span class="label">筛选科室</span>
        <el-select v-model="deptFilter" clearable placeholder="全部门诊科室" style="width: 160px" @change="loadSchedules">
          <el-option v-for="d in outpatientDepts" :key="d.id" :label="d.deptName" :value="d.id" />
        </el-select>
        <el-button type="primary" @click="openCreate">新建排班</el-button>
        <el-button :loading="loading" @click="loadSchedules">刷新排班</el-button>
        <el-button type="primary" :loading="aiLoading" @click="onAiSuggest">
          获取 AI 排班建议
        </el-button>
        <el-tag type="warning" size="small">含请假替班</el-tag>
      </div>

      <el-table
        v-loading="loading"
        :data="schedules"
        stripe
        class="schedule-table"
        max-height="420"
        empty-text="暂无排班"
        :row-class-name="rowClassName"
      >
        <el-table-column prop="workDate" label="日期" width="110" />
        <el-table-column prop="noonLabel" label="午别" width="72" />
        <el-table-column prop="timeRange" label="时段" width="110" />
        <el-table-column prop="deptName" label="科室" width="90" />
        <el-table-column prop="employeeName" label="医生" width="88" />
        <el-table-column label="班次" width="100">
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
          <template #default="{ row }">余 {{ row.remainQuota }} / {{ row.totalQuota }}</template>
        </el-table-column>
        <el-table-column label="状态" width="88">
          <template #default="{ row }">
            <el-tag size="small" :type="publishStatusMap[row.publishStatus ?? 1]?.type || 'info'">
              {{ publishStatusMap[row.publishStatus ?? 1]?.label || '已发布' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="请假状态" min-width="108" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.pendingLeave" size="small" type="warning">待审</el-tag>
            <el-tag v-else-if="row.needsSubstitute" size="small" type="danger">待替班</el-tag>
            <el-tag v-else-if="row.leaveSubstituted" size="small" type="success">已替班</el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="248" fixed="right" class-name="col-actions">
          <template #default="{ row }">
            <el-button
              v-if="(row.publishStatus ?? 1) === 0"
              link
              type="success"
              :loading="publishingId === row.schedulingId"
              @click="onPublish(row)"
            >
              发布
            </el-button>
            <el-button link type="primary" @click="openEdit(row)">手工编辑</el-button>
            <el-button
              v-if="row.needsSubstitute || getAiSuggestion(row.schedulingId)?.replaceable"
              link
              type="warning"
              :loading="replacingId === row.schedulingId"
              @click="onApplyAiReplace(getAiSuggestion(row.schedulingId) || { schedulingId: row.schedulingId })"
            >
              {{ row.needsSubstitute ? 'AI 替班' : 'AI 替换' }}
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
            <el-tag v-if="s.leaveDriven" size="small" type="danger">请假替班</el-tag>
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
            {{ s.leaveDriven ? '应用替班' : '应用此 AI 替换' }}
          </el-button>
        </div>
      </el-card>
    </el-card>

    <el-dialog v-model="editVisible" title="手工编辑排班" width="520px" destroy-on-close>
      <el-alert
        v-if="editForm.needsSubstitute"
        type="warning"
        :closable="false"
        show-icon
        class="edit-tip"
        title="该班次已有批准请假，请指定替班医生"
      />
      <el-form label-width="88px">
        <el-form-item label="替班医生">
          <el-select
            v-model="editForm.employeeId"
            filterable
            placeholder="选择本科室医生"
            style="width: 100%"
            @change="onDoctorChange"
          >
            <el-option
              v-for="d in editCandidates"
              :key="d.employeeId"
              :label="`${d.realName}（${d.title || d.roleTypeLabel}）`"
              :value="d.employeeId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="时段">
          <el-input v-model="editForm.timeRange" placeholder="如 08:00-12:00" readonly />
        </el-form-item>
        <el-form-item label="总号源">
          <el-input-number v-model="editForm.totalQuota" :min="1" :max="99" />
        </el-form-item>
        <el-form-item label="剩余号源">
          <span>{{ editRemainQuota }}</span>
        </el-form-item>
        <el-form-item label="挂号费">
          <span>¥{{ editForm.registFee }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createVisible" title="新建排班" width="540px" destroy-on-close>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="edit-tip"
        title="人员列表来自「员工管理」：先选科室与班次类型，再选出诊/值班职员"
      />
      <el-form label-width="96px">
        <el-form-item label="班次类型">
          <el-radio-group v-model="createForm.scheduleKind" @change="loadCandidatesForCreate">
            <el-radio :value="1">门诊出诊</el-radio>
            <el-radio :value="2">科室值班</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="科室" required>
          <el-select
            v-model="createForm.deptId"
            filterable
            style="width: 100%"
            @change="loadCandidatesForCreate"
          >
            <el-option
              v-for="d in (createForm.scheduleKind === 1 ? outpatientDepts : allDepts)"
              :key="d.id"
              :label="d.deptName"
              :value="d.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="职员" required>
          <el-select v-model="createForm.employeeId" filterable placeholder="本科室在职员工" style="width: 100%">
            <el-option
              v-for="e in candidateEmployees"
              :key="e.employeeId"
              :label="`${e.realName}（${e.title || e.roleTypeLabel}）`"
              :value="e.employeeId"
            />
          </el-select>
          <p v-if="createForm.deptId && !candidateEmployees.length" class="hint">
            该科室暂无匹配职员，请先在「员工管理」建档。
          </p>
        </el-form-item>
        <el-form-item label="出诊日期" required>
          <el-date-picker
            v-model="createForm.workDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="午别">
          <el-select v-model="createForm.noonType" style="width: 100%">
            <el-option label="上午" :value="1" />
            <el-option label="下午" :value="2" />
            <el-option label="晚上" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="createForm.scheduleKind === 1" label="号别">
          <el-select v-model="createForm.registLevelId" style="width: 100%">
            <el-option
              v-for="l in registLevels"
              :key="l.id"
              :label="`${l.levelName}（¥${l.registFee ?? l.fee}）`"
              :value="l.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="createForm.scheduleKind === 1" label="总号源">
          <el-input-number v-model="createForm.totalQuota" :min="1" :max="99" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="onCreateSchedule">保存草稿</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sched-page {
  width: 100%;
  max-width: 1280px;
}

.schedule-table {
  width: 100%;
}

:deep(.schedule-table .col-actions .cell) {
  padding-left: 12px;
}

:deep(.schedule-table th.el-table__cell:last-of-type:not(.el-table-fixed-column--right)) {
  padding-right: 16px;
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

.leave-card {
  margin-bottom: 16px;
  border-radius: 10px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.badge {
  margin-left: 8px;
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
  flex-wrap: wrap;
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

.edit-tip {
  margin-bottom: 12px;
}

.hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: #b45309;
}

:deep(.row-pending-leave) {
  background-color: #fffbeb !important;
}

:deep(.row-needs-sub) {
  background-color: #fef2f2 !important;
}
</style>
