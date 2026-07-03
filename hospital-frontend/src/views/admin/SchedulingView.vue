<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  applyAiSchedulingReplace,
  applyScheduleTemplate,
  batchPublishSchedules,
  batchUpsertSchedules,
  copyScheduleWeek,
  createAdminSchedule,
  fetchWeekGrid,
  fetchAiSchedulingSuggest,
  fetchDepartments,
  fetchEmployees,
  fetchRegistLevels,
  publishAdminSchedule,
  updateAdminSchedule,
} from '../../api/admin'
import WeeklyScheduleGrid from '../../components/admin/WeeklyScheduleGrid.vue'
import ScheduleTemplateDrawer from '../../components/admin/ScheduleTemplateDrawer.vue'
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
const aiRiskItems = ref([])
const aiWarnings = ref([])
const candidateEmployees = ref([])
const editCandidates = ref([])
const publishingId = ref(null)
const creating = ref(false)
const weekStart = ref(getMondayIso(new Date()))
const weekGrid = ref({ doctors: [], slots: [], draftCount: 0, publishedCount: 0 })
const pendingChanges = ref([])
const templateDrawerVisible = ref(false)
const rulesDialogVisible = ref(false)
const rulesDraft = ref('')

const RULES_STORAGE_KEY = 'scheduling-ai-rules'
const DEFAULT_RULES_TEXT = `1. 普通医生：每人每周休 1 天，其余天上、下午各 1 班，号额 30。
2. 专家（主任/副主任/教授）：每周约 3 个半天，号额 15。
3. 每个开诊半天至少 1 名医生；空档优先用普通医生补位。
4. 替班：同科室、同类型（普通↔普通），且同日期同午别无冲突。`

function loadRulesText() {
  try {
    const saved = localStorage.getItem(RULES_STORAGE_KEY)
    return saved?.trim() ? saved : DEFAULT_RULES_TEXT
  } catch {
    return DEFAULT_RULES_TEXT
  }
}

const rulesText = ref(loadRulesText())
const gridSaving = ref(false)
const copyingWeek = ref(false)
const applyingTemplate = ref(false)
const batchPublishing = ref(false)

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
  deptFilter.value = outpatientDepts.value[0]?.id ?? allDepts.value[0]?.id ?? null
  await Promise.all([loadWeekGrid(), loadLeaveRequests()])
})

function getMondayIso(date) {
  const d = new Date(date)
  const day = d.getDay()
  const diff = day === 0 ? -6 : 1 - day
  d.setDate(d.getDate() + diff)
  return d.toISOString().slice(0, 10)
}

function addDaysIso(iso, days) {
  const d = new Date(`${iso}T12:00:00`)
  d.setDate(d.getDate() + days)
  return d.toISOString().slice(0, 10)
}

function openRulesDialog() {
  rulesDraft.value = rulesText.value
  rulesDialogVisible.value = true
}

function saveRules() {
  const text = rulesDraft.value.trim()
  if (!text) {
    ElMessage.warning('排班规则不能为空')
    return
  }
  rulesText.value = text
  try {
    localStorage.setItem(RULES_STORAGE_KEY, text)
  } catch {
    ElMessage.warning('规则已生效，但未能写入本地缓存')
  }
  rulesDialogVisible.value = false
  ElMessage.success('排班规则已保存')
}

function aiSuggestPayload(mode) {
  return {
    deptId: deptFilter.value || undefined,
    weekStart: weekStart.value,
    mode,
    rulesText: rulesText.value || undefined,
  }
}

const weekEndLabel = computed(() => addDaysIso(weekStart.value, 6))

const NOON_META = {
  1: { noonLabel: '上午', timeRange: '08:00-12:00' },
  2: { noonLabel: '下午', timeRange: '13:00-17:00' },
  3: { noonLabel: '晚上', timeRange: '18:00-21:00' },
}

function defaultGridQuota(registLevelId) {
  return Number(registLevelId) === 2 ? 15 : 30
}

function changeMatches(c, change) {
  if (c.clear || change.clear) return false
  if (c.schedulingId != null && change.schedulingId != null) {
    return c.schedulingId === change.schedulingId
  }
  return c.employeeId === change.employeeId
    && c.workDate === change.workDate
    && c.noonType === change.noonType
}

function findSlotIndex(slots, change) {
  if (change.schedulingId != null) {
    const byId = slots.findIndex((s) => s.schedulingId === change.schedulingId)
    if (byId >= 0) return byId
  }
  return slots.findIndex(
    (s) => s.employeeId === change.employeeId
      && s.workDate === change.workDate
      && s.noonType === change.noonType,
  )
}

function registLevelMeta(levelId) {
  const level = registLevels.value.find((l) => l.id === levelId)
  return {
    registLevelId: levelId,
    registLevelName: level?.levelName ?? '',
    registFee: level?.registFee ?? level?.fee ?? 0,
  }
}

function currentDeptMeta() {
  const dept = allDepts.value.find((d) => d.id === deptFilter.value)
  return { deptId: deptFilter.value, deptName: dept?.deptName ?? '' }
}

function recountGridStats(slots) {
  let draftCount = 0
  let publishedCount = 0
  for (const slot of slots) {
    const status = slot.publishStatus ?? 0
    if (status === 0) draftCount += 1
    else if (status === 1) publishedCount += 1
  }
  return { draftCount, publishedCount }
}

function syncGridSlots(nextSlots) {
  const stats = recountGridStats(nextSlots)
  weekGrid.value = {
    ...weekGrid.value,
    slots: nextSlots,
    draftCount: stats.draftCount,
    publishedCount: stats.publishedCount,
  }
  schedules.value = sortSchedules(nextSlots)
}

function applyPreviewChange(change) {
  const slots = [...(weekGrid.value.slots ?? [])]

  if (change.clear) {
    const idx = findSlotIndex(slots, change)
    if (idx >= 0) slots.splice(idx, 1)
    syncGridSlots(slots)
    return
  }

  const idx = findSlotIndex(slots, change)
  const existing = idx >= 0 ? slots[idx] : null
  const doctor = weekGrid.value.doctors?.find((d) => d.employeeId === change.employeeId)
  const dept = currentDeptMeta()
  const noon = NOON_META[change.noonType] ?? NOON_META[1]

  if (existing) {
    const published = (existing.publishStatus ?? 0) === 1
    const usedQuota = existing.usedQuota ?? 0
    const totalQuota = change.totalQuota ?? existing.totalQuota
    const levelId = published
      ? existing.registLevelId
      : (change.registLevelId ?? existing.registLevelId ?? 1)
    slots[idx] = {
      ...existing,
      ...registLevelMeta(levelId),
      totalQuota,
      remainQuota: Math.max(0, totalQuota - usedQuota),
    }
  } else {
    const levelId = change.registLevelId ?? 1
    const totalQuota = change.totalQuota ?? defaultGridQuota(levelId)
    slots.push({
      schedulingId: null,
      employeeId: change.employeeId,
      employeeName: doctor?.realName ?? '',
      employeeTitle: doctor?.title ?? '',
      workDate: change.workDate,
      noonType: change.noonType,
      ...noon,
      ...dept,
      ...registLevelMeta(levelId),
      totalQuota,
      usedQuota: 0,
      remainQuota: totalQuota,
      publishStatus: 0,
      pendingLeave: false,
      needsSubstitute: false,
      leaveSubstituted: false,
      scheduleKind: 1,
    })
  }
  syncGridSlots(slots)
}

function onGridChange(change) {
  const idx = pendingChanges.value.findIndex((c) => changeMatches(c, change))
  if (change.clear) {
    if (idx >= 0) {
      const pending = pendingChanges.value[idx]
      if (pending.schedulingId != null) {
        pendingChanges.value[idx] = { schedulingId: pending.schedulingId, clear: true }
      } else {
        pendingChanges.value.splice(idx, 1)
      }
    } else if (change.schedulingId != null) {
      pendingChanges.value.push({ schedulingId: change.schedulingId, clear: true })
    }
    applyPreviewChange(change)
    return
  }
  const payload = { ...change }
  if (payload.schedulingId == null) {
    delete payload.schedulingId
  }
  if (idx >= 0) pendingChanges.value[idx] = payload
  else pendingChanges.value.push(payload)
  applyPreviewChange(change)
}

async function loadWeekGrid() {
  if (!deptFilter.value) {
    weekGrid.value = { doctors: [], slots: [], draftCount: 0, publishedCount: 0 }
    schedules.value = []
    return
  }
  loading.value = true
  pendingChanges.value = []
  try {
    const res = await fetchWeekGrid({ deptId: deptFilter.value, weekStart: weekStart.value })
    weekGrid.value = res.data ?? { doctors: [], slots: [], draftCount: 0, publishedCount: 0 }
    weekStart.value = weekGrid.value.weekStart || weekStart.value
    schedules.value = sortSchedules(weekGrid.value.slots ?? [])
    if (weekGrid.value.prefilledFromTemplate) {
      ElMessage.info('已根据固定模板自动预填本周草稿')
    }
  } catch (err) {
    ElMessage.error(err.message || '加载周排班失败')
  } finally {
    loading.value = false
  }
}

async function onSaveGrid() {
  if (!pendingChanges.value.length) return ElMessage.info('暂无修改')
  gridSaving.value = true
  try {
    await batchUpsertSchedules({
      deptId: deptFilter.value,
      weekStart: weekStart.value,
      changes: pendingChanges.value,
    })
    ElMessage.success('排班已保存')
    await loadWeekGrid()
  } catch (err) {
    ElMessage.error(err.message || '保存失败')
  } finally {
    gridSaving.value = false
  }
}

async function onCopyWeek() {
  copyingWeek.value = true
  try {
    const res = await copyScheduleWeek({
      deptId: deptFilter.value,
      sourceWeekStart: addDaysIso(weekStart.value, -7),
      targetWeekStart: weekStart.value,
    })
    ElMessage.success(res.data?.message || '复制完成')
    await loadWeekGrid()
  } catch (err) {
    ElMessage.error(err.message || '复制失败')
  } finally {
    copyingWeek.value = false
  }
}

async function onApplyTemplate() {
  applyingTemplate.value = true
  try {
    const res = await applyScheduleTemplate({ deptId: deptFilter.value, weekStart: weekStart.value })
    ElMessage.success(res.data?.message || '模板已应用')
    await loadWeekGrid()
  } catch (err) {
    ElMessage.error(err.message || '应用模板失败')
  } finally {
    applyingTemplate.value = false
  }
}

async function onBatchPublish() {
  batchPublishing.value = true
  try {
    const res = await batchPublishSchedules({ deptId: deptFilter.value, weekStart: weekStart.value })
    ElMessage.success(res.data?.message || '批量发布完成')
    await loadWeekGrid()
  } catch (err) {
    ElMessage.error(err.message || '批量发布失败')
  } finally {
    batchPublishing.value = false
  }
}

function shiftWeek(delta) {
  weekStart.value = addDaysIso(weekStart.value, delta * 7)
  loadWeekGrid()
}

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
    return ElMessage.warning('科室值班排班暂未支持，请选择门诊出诊')
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
    await loadWeekGrid()
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
    await loadWeekGrid()
  } catch (err) {
    ElMessage.error(err.message || '发布失败')
  } finally {
    publishingId.value = null
  }
}

async function loadSchedules() {
  await loadWeekGrid()
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
  aiRiskItems.value = []
  aiWarnings.value = []
  try {
    const res = await fetchAiSchedulingSuggest(aiSuggestPayload('WEEK'))
    const changes = res.data?.changes ?? []
    for (const change of changes) {
      onGridChange(change)
    }
    aiSuggestions.value = res.data?.suggestions ?? []
    aiRiskItems.value = res.data?.riskItems ?? []
    aiWarnings.value = res.data?.warnings ?? []
    ElMessage.success(res.data?.message || `AI 已生成 ${changes.length} 条周排班草稿，请检查后保存`)
  } catch (err) {
    ElMessage.warning(err.message || 'AI 排班建议生成失败')
  } finally {
    aiLoading.value = false
  }
}

async function onAiSubstituteSuggest() {
  aiLoading.value = true
  aiSuggestions.value = []
  aiRiskItems.value = []
  aiWarnings.value = []
  try {
    const res = await fetchAiSchedulingSuggest(aiSuggestPayload('SUBSTITUTE'))
    aiSuggestions.value = res.data?.suggestions ?? []
    aiRiskItems.value = res.data?.riskItems ?? []
    aiWarnings.value = res.data?.warnings ?? []
    ElMessage.success(res.data?.message || 'AI 已生成替班建议')
  } catch (err) {
    ElMessage.warning(err.message || 'AI 替班建议生成失败')
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

  if (suggestion?.replaceable && suggestion.proposedSchedule) {
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
      await applyAiSchedulingReplace(schedulingId, {
        ...suggestion.proposedSchedule,
        leaveRequestId: suggestion.leaveRequestId,
      })
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
    await applyAiSchedulingReplace(schedulingId, suggestion || {})
  } catch (err) {
    ElMessage.warning(err.message || 'AI 替班暂不可用')
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
        周排班网格批量编辑；发布后医生可在「我的排班」请假，本页审批并替班。
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
                  @click="onApplyAiReplace(getAiSuggestion(row.schedulingId) || row)"
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

    <el-card shadow="never" class="week-card">
      <div class="toolbar">
        <span class="label">科室</span>
        <el-select v-model="deptFilter" placeholder="选择门诊科室" style="width: 160px" @change="loadWeekGrid">
          <el-option v-for="d in outpatientDepts" :key="d.id" :label="d.deptName" :value="d.id" />
        </el-select>
        <el-button @click="shiftWeek(-1)">上一周</el-button>
        <span class="week-label">{{ weekStart }} ~ {{ weekEndLabel }}</span>
        <el-button @click="shiftWeek(1)">下一周</el-button>
        <el-button :loading="copyingWeek" @click="onCopyWeek">复制上周</el-button>
        <el-button :loading="applyingTemplate" @click="onApplyTemplate">应用模板</el-button>
        <el-button type="primary" :loading="gridSaving" :disabled="!pendingChanges.length" @click="onSaveGrid">
          保存{{ pendingChanges.length ? `(${pendingChanges.length})` : '' }}
        </el-button>
        <el-button
          type="success"
          :loading="batchPublishing"
          :disabled="!(weekGrid.draftCount > 0)"
          @click="onBatchPublish"
        >
          批量发布({{ weekGrid.draftCount ?? 0 }})
        </el-button>
        <el-button @click="templateDrawerVisible = true">管理模板</el-button>
        <el-button :loading="loading" @click="loadWeekGrid">刷新</el-button>
      </div>

      <WeeklyScheduleGrid
        v-loading="loading"
        :doctors="weekGrid.doctors ?? []"
        :slots="weekGrid.slots ?? []"
        :week-start="weekStart"
        :regist-levels="registLevels"
        @change="onGridChange"
      />
    </el-card>

    <el-card shadow="never" class="list-card">
      <template #header>
        <div class="card-header">
          <span>本周排班列表</span>
          <el-button plain @click="openRulesDialog">排班规则</el-button>
        </div>
      </template>

      <div class="toolbar list-toolbar">
        <el-button type="primary" plain @click="openCreate">补录单条</el-button>
        <el-button type="primary" :loading="aiLoading" @click="onAiSuggest">
          获取 AI 周排班建议
        </el-button>
        <el-button type="warning" plain :loading="aiLoading" @click="onAiSubstituteSuggest">
          AI 替班建议
        </el-button>
        <el-tag type="warning" size="small">周排班为草稿预填，替班建议单独生成</el-tag>
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
            <span v-else class="muted">-</span>
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
              @click="onApplyAiReplace(getAiSuggestion(row.schedulingId) || row)"
            >
              {{ row.needsSubstitute ? 'AI 替班' : 'AI 替换' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-card v-if="aiSuggestions.length || aiRiskItems.length || aiWarnings.length" shadow="never" class="ai-suggest-card">
        <template #header>
          <span>AI 建议详情</span>
        </template>
        <el-alert
          v-for="(w, index) in aiWarnings"
          :key="`ai-warning-${index}`"
          type="warning"
          :closable="false"
          show-icon
          class="ai-warning"
          :title="w"
        />
        <div v-for="risk in aiRiskItems" :key="`risk-${risk.type}-${risk.schedulingId || risk.title}`" class="suggest-item">
          <div class="suggest-head">
            <strong>{{ risk.title }}</strong>
            <el-tag
              size="small"
              :type="risk.level === 'HIGH' ? 'danger' : risk.level === 'MEDIUM' ? 'warning' : 'info'"
            >
              {{ risk.level || 'INFO' }}
            </el-tag>
          </div>
          <p>{{ risk.description }}</p>
          <p v-if="risk.suggestion">建议：{{ risk.suggestion }}</p>
        </div>
        <div v-for="s in aiSuggestions" :key="s.schedulingId" class="suggest-item">
          <div class="suggest-head">
            <strong>{{ s.workDate }} {{ s.noonLabel }} · {{ s.employeeName }}</strong>
            <el-tag v-if="s.leaveDriven" size="small" type="danger">请假替班</el-tag>
            <el-tag size="small">置信度 {{ Math.round((s.confidence || 0) * 100) }}%</el-tag>
          </div>
          <p>{{ s.suggestion }}</p>
          <p v-if="s.reason">原因：{{ s.reason }}</p>
          <p v-if="s.warnings?.length" class="warning-text">注意：{{ s.warnings.join('；') }}</p>
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

    <ScheduleTemplateDrawer
      v-model:visible="templateDrawerVisible"
      :doctors="weekGrid.doctors ?? []"
      :regist-levels="registLevels"
      @saved="loadWeekGrid"
    />

    <el-dialog v-model="rulesDialogVisible" title="排班规则" width="560px" destroy-on-close>
      <p class="rules-hint">以下规则会随 AI 排班请求一并提交；当前后端规则引擎尚未消费该字段，保存后仅在本机缓存。</p>
      <el-input
        v-model="rulesDraft"
        type="textarea"
        :rows="10"
        placeholder="请输入排班规则说明"
        resize="vertical"
      />
      <template #footer>
        <el-button @click="rulesDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRules">保存</el-button>
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

.week-card {
  margin-bottom: 16px;
  border-radius: 10px;
}

.week-label {
  font-size: 13px;
  color: #334155;
  min-width: 180px;
  text-align: center;
}

.list-card {
  border-radius: 10px;
}

.list-toolbar {
  margin-bottom: 12px;
}

.rules-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
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

.ai-warning {
  margin-bottom: 8px;
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

.warning-text {
  color: #b45309 !important;
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
