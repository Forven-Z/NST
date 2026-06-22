<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  fetchDoctorsByDept,
  fetchOutpatientDepartments,
  fetchRegistrarSchedules,
  fetchSettleCategories,
  windowRegister,
} from '../../api/registrar'
import { applyIdCardToForm, calcAgeFromBirthDate, isValidIdCard, parseIdCard } from '../../utils/id-card'

const loading = ref(false)
const submitting = ref(false)
const departments = ref([])
const doctors = ref([])
const schedules = ref([])
const settleCategories = ref([])
const selectedSchedulingId = ref(null)
const lastReceipt = ref(null)
const receiptVisible = ref(false)
const registLevelFilter = ref(null)
const router = useRouter()
const scheduleTableWrap = ref(null)
const scheduleTableHeight = ref(280)

const regularDoctors = computed(() => doctors.value.filter((d) => d.role === 'regular' || d.clinicRole === 'REGULAR'))
const expertDoctors = computed(() => doctors.value.filter((d) => d.role === 'expert' || d.clinicRole === 'EXPERT'))

const form = reactive({
  patientName: '',
  idCard: '',
  gender: 1,
  age: '',
  birthDate: '',
  phone: '',
  address: '',
  needRecordBook: false,
  settleCategoryId: 1,
  deptId: null,
  employeeId: null,
})

const selectedSchedule = computed(() =>
  schedules.value.find((s) => s.schedulingId === selectedSchedulingId.value),
)

const totalSelectedAmount = computed(() => {
  let sum = selectedSchedule.value?.registFee ?? 0
  if (form.needRecordBook) sum += 1
  return sum
})

const canSubmit = computed(
  () =>
    form.patientName.trim()
    && form.idCard.trim()
    && form.phone.trim()
    && form.deptId
    && selectedSchedulingId.value
    && selectedSchedule.value?.remainQuota > 0,
)

function clearPatientFields() {
  form.patientName = ''
  form.idCard = ''
  form.gender = 1
  form.age = ''
  form.birthDate = ''
  form.phone = ''
  form.address = ''
  form.needRecordBook = false
}

function syncScheduleTableHeight() {
  nextTick(() => {
    requestAnimationFrame(() => {
      const el = scheduleTableWrap.value
      if (el?.clientHeight) {
        scheduleTableHeight.value = Math.max(el.clientHeight, 200)
      }
    })
  })
}

onMounted(async () => {
  syncScheduleTableHeight()
  window.addEventListener('resize', syncScheduleTableHeight)
  loading.value = true
  try {
    const [deptRes, settleRes] = await Promise.all([
      fetchOutpatientDepartments(),
      fetchSettleCategories(),
    ])
    departments.value = deptRes.data?.list ?? []
    settleCategories.value = settleRes.data?.list ?? []
    if (departments.value.length) {
      form.deptId = departments.value[0].id
      await loadDeptContext(form.deptId)
    }
  } catch (err) {
    ElMessage.error(err.message || '加载字典失败')
  } finally {
    loading.value = false
    syncScheduleTableHeight()
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', syncScheduleTableHeight)
})

async function loadDeptContext(deptId) {
  form.employeeId = null
  selectedSchedulingId.value = null
  registLevelFilter.value = null
  doctors.value = []
  schedules.value = []
  if (!deptId) return
  const [docRes, schedRes] = await Promise.all([
    fetchDoctorsByDept(deptId),
    fetchRegistrarSchedules({ deptId }),
  ])
  doctors.value = docRes.data?.list ?? []
  schedules.value = schedRes.data?.list ?? []
  syncScheduleTableHeight()
}

async function reloadSchedules() {
  if (!form.deptId) return
  const res = await fetchRegistrarSchedules({
    deptId: form.deptId,
    employeeId: form.employeeId || undefined,
    registLevelId: registLevelFilter.value || undefined,
  })
  schedules.value = res.data?.list ?? []
  syncScheduleTableHeight()
}

watch(selectedSchedule, () => syncScheduleTableHeight())

watch(
  () => form.deptId,
  async (deptId, prev) => {
    if (!deptId || deptId === prev) return
    try {
      await loadDeptContext(deptId)
    } catch (err) {
      ElMessage.error(err.message || '加载医生/排班失败')
    }
  },
)

watch(
  () => form.employeeId,
  async () => {
    selectedSchedulingId.value = null
    try {
      await reloadSchedules()
    } catch (err) {
      ElMessage.error(err.message || '加载排班失败')
    }
  },
)

watch(
  () => registLevelFilter.value,
  async () => {
    selectedSchedulingId.value = null
    try {
      await reloadSchedules()
    } catch (err) {
      ElMessage.error(err.message || '加载排班失败')
    }
  },
)

watch(
  () => form.idCard,
  (val) => {
    const normalized = String(val || '').trim().toUpperCase()
    if (normalized.length === 18 && isValidIdCard(normalized)) {
      applyIdCardToForm(normalized, form)
    }
  },
)

function onIdCardBlur() {
  const normalized = String(form.idCard || '').trim().toUpperCase()
  if (normalized !== form.idCard) {
    form.idCard = normalized
  }
  if (isValidIdCard(normalized)) {
    applyIdCardToForm(normalized, form)
  }
}

function selectDoctor(employeeId) {
  form.employeeId = form.employeeId === employeeId ? null : employeeId
}

function onScheduleRowClick(row) {
  if (row.remainQuota <= 0) {
    ElMessage.warning('该时段号源已满')
    return
  }
  selectedSchedulingId.value = row.schedulingId
}

function resetForm() {
  clearPatientFields()
  form.settleCategoryId = 1
  form.employeeId = null
  registLevelFilter.value = null
  selectedSchedulingId.value = null
  lastReceipt.value = null
  receiptVisible.value = false
}

function closeReceipt() {
  receiptVisible.value = false
}

function onReceiptClosed() {
  lastReceipt.value = null
}

function goToCharge() {
  const no = lastReceipt.value?.medicalRecordNo
  if (!no) return
  receiptVisible.value = false
  router.push({
    name: 'registrar-charge',
    query: { medicalRecordNo: no },
  })
}

function resolveBirthDateForSubmit() {
  if (form.birthDate) return form.birthDate
  const card = form.idCard?.trim()
  if (card && isValidIdCard(card)) {
    return parseIdCard(card)?.birthDate
  }
  if (form.age !== '' && form.age != null && !Number.isNaN(Number(form.age))) {
    const age = Number(form.age)
    if (age >= 0 && age <= 150) {
      const d = new Date()
      d.setFullYear(d.getFullYear() - age)
      const y = d.getFullYear()
      const m = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      return `${y}-${m}-${day}`
    }
  }
  return undefined
}

function resolveAgeForSubmit() {
  if (form.age !== '' && form.age != null && !Number.isNaN(Number(form.age))) {
    const age = Number(form.age)
    if (age >= 0 && age <= 150) return age
  }
  const birth = resolveBirthDateForSubmit()
  if (birth) {
    const fromBirth = calcAgeFromBirthDate(birth)
    if (fromBirth != null) return fromBirth
  }
  return undefined
}

async function onSubmit() {
  if (!canSubmit.value || !selectedSchedule.value) {
    ElMessage.warning('请完善患者信息并选择有效排班')
    return
  }
  submitting.value = true
  lastReceipt.value = null
  try {
    const res = await windowRegister({
      patientName: form.patientName.trim(),
      gender: form.gender,
      age: resolveAgeForSubmit(),
      birthDate: resolveBirthDateForSubmit(),
      idCard: form.idCard ? form.idCard.trim().toUpperCase() : undefined,
      phone: form.phone || undefined,
      address: form.address || undefined,
      needRecordBook: form.needRecordBook,
      settleCategoryId: form.settleCategoryId,
      deptId: form.deptId,
      employeeId: selectedSchedule.value.employeeId,
      registLevelId: selectedSchedule.value.registLevelId,
      schedulingId: selectedSchedule.value.schedulingId,
    })
    lastReceipt.value = {
      ...res.data,
      patientName: res.data?.patientName ?? form.patientName.trim(),
      billId: res.data?.billId ?? res.data?.billIds?.[0],
    }
    receiptVisible.value = true
    ElMessage.success('挂号成功')
    clearPatientFields()
    selectedSchedulingId.value = null
    await reloadSchedules()
  } catch (err) {
    ElMessage.error(err.message || '挂号失败')
  } finally {
    submitting.value = false
  }
}

function rowClassName({ row }) {
  if (row.schedulingId === selectedSchedulingId.value) return 'row-selected'
  if (row.remainQuota <= 0) return 'row-disabled'
  return ''
}
</script>

<template>
  <div v-loading="loading" class="register-page">
    <div class="page-head">
      <div>
        <h2 class="page-title">窗口挂号</h2>
        <p class="page-desc">
          录入患者信息 → 选择科室与排班 → 生成挂号单。各科室每个开诊半天均有普通号（医生轮流）；
          专家号仅副高及以上在固定时段出诊，非每时段都有。
        </p>
      </div>
      <el-tag type="info" effect="plain">今日 {{ new Date().toLocaleDateString('zh-CN') }}</el-tag>
    </div>

    <el-row :gutter="16" class="main-row">
      <!-- 患者信息 -->
      <el-col :xs="24" :lg="9" class="main-col">
        <el-card shadow="never" class="panel-card panel-card--left">
          <template #header>
            <div class="panel-header">
              <span class="panel-title">① 患者基本信息</span>
            </div>
          </template>

          <el-form label-width="88px" label-position="right" class="patient-form">
            <el-form-item label="姓名" required>
              <el-input v-model="form.patientName" placeholder="请输入患者姓名" clearable />
            </el-form-item>
            <el-form-item label="身份证" required>
              <el-input
                v-model="form.idCard"
                placeholder="18位身份证号"
                maxlength="18"
                clearable
                @blur="onIdCardBlur"
              />
            </el-form-item>
            <el-form-item label="性别">
              <el-radio-group v-model="form.gender">
                <el-radio :value="1">男</el-radio>
                <el-radio :value="2">女</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="年龄">
              <el-input v-model="form.age" placeholder="岁" style="width: 120px" />
            </el-form-item>
            <el-form-item label="手机号" required>
              <el-input v-model="form.phone" placeholder="11位手机号" maxlength="11" clearable />
            </el-form-item>
            <el-form-item label="住址">
              <el-input v-model="form.address" type="textarea" :rows="2" placeholder="选填" />
            </el-form-item>
            <el-form-item label="结算类别">
              <el-select v-model="form.settleCategoryId" style="width: 100%">
                <el-option
                  v-for="c in settleCategories"
                  :key="c.id"
                  :label="c.categoryName"
                  :value="c.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="病历本">
              <el-checkbox v-model="form.needRecordBook">需要购买病历本（¥1）</el-checkbox>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 选号 -->
      <el-col :xs="24" :lg="15" class="main-col">
        <el-card shadow="never" class="panel-card panel-card--right">
          <template #header>
            <div class="panel-header">
              <span class="panel-title">② 选择科室 · 医生 · 排班</span>
            </div>
          </template>

          <div class="right-panel-body">
            <div class="right-panel-top">
          <div class="section">
            <div class="section-label">门诊科室</div>
            <div class="dept-grid">
              <button
                v-for="dept in departments"
                :key="dept.id"
                type="button"
                class="dept-chip"
                :class="{ active: form.deptId === dept.id }"
                @click="form.deptId = dept.id"
              >
                {{ dept.deptName }}
              </button>
            </div>
          </div>

          <div v-if="regularDoctors.length" class="section">
            <div class="section-label">
              普通门诊医生
              <span class="section-hint">（主治医师/住院医师轮流出诊，各时段均有号）</span>
            </div>
            <div class="doctor-grid">
              <div
                v-for="doc in regularDoctors"
                :key="doc.employeeId"
                class="doctor-card"
                :class="{ active: form.employeeId === doc.employeeId }"
                @click="selectDoctor(doc.employeeId)"
              >
                <div class="doctor-avatar">{{ doc.realName.slice(0, 1) }}</div>
                <div class="doctor-meta">
                  <div class="doctor-name">{{ doc.realName }}</div>
                  <div class="doctor-title">{{ doc.title }}</div>
                  <el-tag size="small" type="info">普通门诊</el-tag>
                </div>
              </div>
            </div>
          </div>

          <div v-if="expertDoctors.length" class="section">
            <div class="section-label">
              专家门诊医生
              <span class="section-hint">（副主任/主任医师，仅固定时段出专家号）</span>
            </div>
            <div class="doctor-grid">
              <div
                v-for="doc in expertDoctors"
                :key="doc.employeeId"
                class="doctor-card expert"
                :class="{ active: form.employeeId === doc.employeeId }"
                @click="selectDoctor(doc.employeeId)"
              >
                <div class="doctor-avatar expert-avatar">{{ doc.realName.slice(0, 1) }}</div>
                <div class="doctor-meta">
                  <div class="doctor-name">{{ doc.realName }}</div>
                  <div class="doctor-title">{{ doc.title }}</div>
                  <el-tag size="small" type="warning">专家门诊</el-tag>
                  <div v-if="doc.expertSessionCount != null" class="expert-sessions">
                    近7天 {{ doc.expertSessionCount }} 个专家半天
                  </div>
                </div>
              </div>
            </div>
          </div>
            </div>

          <div class="section section--schedule">
            <div class="section-label-row">
              <span class="section-label">排班号源</span>
              <el-radio-group v-model="registLevelFilter" size="small">
                <el-radio-button :value="null">全部</el-radio-button>
                <el-radio-button :value="1">普通号</el-radio-button>
                <el-radio-button :value="2">专家号</el-radio-button>
              </el-radio-group>
            </div>
            <div ref="scheduleTableWrap" class="schedule-table-wrap">
            <el-table
              :data="schedules"
              stripe
              highlight-current-row
              :height="scheduleTableHeight"
              class="schedule-table"
              empty-text="请选择科室查看排班"
              :row-class-name="rowClassName"
              @row-click="onScheduleRowClick"
            >
              <el-table-column prop="workDate" label="日期" width="110" />
              <el-table-column prop="noonLabel" label="午别" width="72" />
              <el-table-column prop="timeRange" label="时段" width="110" />
              <el-table-column prop="employeeName" label="医生" width="88" />
              <el-table-column prop="employeeTitle" label="职称" min-width="100" />
              <el-table-column label="号别" width="88">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.registLevelId === 2 ? 'warning' : 'info'">
                    {{ row.registLevelName }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="挂号费" width="80">
                <template #default="{ row }">
                  <span class="fee">¥{{ row.registFee }}</span>
                </template>
              </el-table-column>
              <el-table-column label="号源" width="100">
                <template #default="{ row }">
                  <span :class="{ 'text-danger': row.remainQuota <= 0 }">
                    余 {{ row.remainQuota }} / {{ row.totalQuota }}
                  </span>
                </template>
              </el-table-column>
            </el-table>
            </div>
          </div>

          <div v-if="selectedSchedule" class="summary-bar">
            <div class="summary-item">
              <span class="k">科室</span>
              <span class="v">{{ departments.find((d) => d.id === form.deptId)?.deptName }}</span>
            </div>
            <div class="summary-item">
              <span class="k">医生</span>
              <span class="v">{{ selectedSchedule.employeeName }} · {{ selectedSchedule.employeeTitle }}</span>
            </div>
            <div class="summary-item">
              <span class="k">时段</span>
              <span class="v">
                {{ selectedSchedule.workDate }} {{ selectedSchedule.noonLabel }}
                <template v-if="selectedSchedule.timeRange">（{{ selectedSchedule.timeRange }}）</template>
              </span>
            </div>
            <div class="summary-item highlight">
              <span class="k">应收合计</span>
              <span class="v fee-lg">¥{{ totalSelectedAmount }}</span>
            </div>
          </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <div class="action-bar">
      <el-button @click="resetForm">重置</el-button>
      <el-button type="primary" size="large" :loading="submitting" :disabled="!canSubmit" @click="onSubmit">
        确认挂号
      </el-button>
    </div>

    <el-dialog
      v-model="receiptVisible"
      width="780px"
      align-center
      destroy-on-close
      class="receipt-dialog"
      @closed="onReceiptClosed"
    >
      <div v-if="lastReceipt" class="receipt-panel">
        <div class="receipt-bar">
          <div class="receipt-bar-head">
            <span class="receipt-title">挂号凭条</span>
            <el-tag type="success" size="small" effect="plain">挂号成功</el-tag>
          </div>
          <el-descriptions :column="4" border size="small" class="receipt-desc">
            <el-descriptions-item label="病历号">{{ lastReceipt.medicalRecordNo }}</el-descriptions-item>
            <el-descriptions-item label="患者">{{ lastReceipt.patientName }}</el-descriptions-item>
            <el-descriptions-item label="科室">{{ lastReceipt.deptName }}</el-descriptions-item>
            <el-descriptions-item label="医生">{{ lastReceipt.doctorName }}</el-descriptions-item>
            <el-descriptions-item label="就诊时间">
              {{ lastReceipt.workDate }} {{ lastReceipt.noonLabel }}
            </el-descriptions-item>
            <el-descriptions-item label="号别">{{ lastReceipt.registLevelName }}</el-descriptions-item>
            <el-descriptions-item label="挂号费">
              <span class="fee-lg">¥{{ lastReceipt.amount }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="账单 ID">{{ lastReceipt.billId }}</el-descriptions-item>
          </el-descriptions>
          <div class="receipt-bar-foot">
            <span class="receipt-msg">{{ lastReceipt.message }}</span>
            <div class="receipt-bar-actions">
              <el-button size="small" @click="closeReceipt">关闭</el-button>
              <el-button type="primary" size="small" @click="goToCharge">去缴费</el-button>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.register-page {
  /* 抵消 StaffShell .main-pane 的 20px padding，在内容区内贴边满屏 */
  margin: -20px;
  padding: 16px 20px 20px;
  width: auto;
  height: calc(100vh - 56px);
  max-height: calc(100vh - 56px);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  flex-shrink: 0;
  gap: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #0f172a;
}

.page-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: #64748b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: min(960px, 70vw);
}

.main-row {
  flex: 1;
  min-height: 0;
  margin-bottom: 0;
  width: 100%;
}

:deep(.main-row.el-row) {
  height: 100%;
  flex-wrap: nowrap;
  align-items: stretch;
}

:deep(.main-col.el-col) {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.panel-card {
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  flex: 1;
  min-height: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
}

:deep(.panel-card > .el-card__header) {
  flex-shrink: 0;
  padding: 14px 18px;
}

:deep(.panel-card > .el-card__body) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.panel-card--left :deep(.el-card__body) {
  overflow-y: auto;
}

.right-panel-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.right-panel-top {
  flex-shrink: 0;
  max-height: 38%;
  overflow-y: auto;
  padding-right: 4px;
}

.section--schedule {
  flex: 1;
  min-height: 180px;
  display: flex;
  flex-direction: column;
  margin-bottom: 0;
}

.schedule-table-wrap {
  flex: 1;
  min-height: 0;
}

.schedule-table {
  height: 100% !important;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-title {
  font-weight: 600;
  color: #334155;
}

.patient-form {
  padding-top: 4px;
}

.section {
  margin-bottom: 20px;
}

.section-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.section-label-row .section-label {
  margin-bottom: 0;
}

.expert-avatar {
  background: linear-gradient(135deg, #fbbf24, #d97706);
}

.expert-sessions {
  margin-top: 4px;
  font-size: 11px;
  color: #b45309;
}

.doctor-card.expert.active {
  border-color: #d97706;
  background: #fffbeb;
}

.section-label {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 10px;
}

.section-hint {
  font-weight: 400;
  color: #94a3b8;
  font-size: 12px;
}

.dept-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.dept-chip {
  border: 1px solid #cbd5e1;
  background: #fff;
  color: #334155;
  padding: 8px 18px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.15s;
}

.dept-chip:hover {
  border-color: #ea580c;
  color: #ea580c;
}

.dept-chip.active {
  background: #fff7ed;
  border-color: #ea580c;
  color: #c2410c;
  font-weight: 600;
}

.doctor-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
}

.doctor-card {
  display: flex;
  gap: 10px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  cursor: pointer;
  background: #fff;
  transition: all 0.15s;
}

.doctor-card:hover {
  border-color: #fdba74;
  box-shadow: 0 2px 8px rgba(234, 88, 12, 0.08);
}

.doctor-card.active {
  border-color: #ea580c;
  background: #fff7ed;
}

.doctor-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #fb923c, #ea580c);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  flex-shrink: 0;
}

.doctor-name {
  font-weight: 600;
  font-size: 14px;
  color: #1e293b;
}

.doctor-title {
  font-size: 12px;
  color: #64748b;
  margin: 2px 0 6px;
}

.fee {
  color: #ea580c;
  font-weight: 600;
}

.fee-lg {
  color: #ea580c;
  font-weight: 700;
  font-size: 18px;
}

.text-danger {
  color: #dc2626;
}

.summary-bar {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  padding: 14px 16px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px dashed #cbd5e1;
  flex-shrink: 0;
}

.summary-item .k {
  display: block;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 4px;
}

.summary-item .v {
  font-size: 14px;
  color: #1e293b;
}

.summary-item.highlight {
  grid-column: 1 / -1;
  padding-top: 8px;
  border-top: 1px solid #e2e8f0;
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  flex-shrink: 0;
  padding-top: 12px;
  border-top: 1px solid #e2e8f0;
  margin-top: auto;
}

@media (max-width: 991px) {
  .register-page {
    height: auto;
    max-height: none;
    overflow: visible;
    margin: 0;
    padding: 0;
  }

  :deep(.main-row.el-row) {
    flex-wrap: wrap;
    height: auto;
  }

  :deep(.main-col.el-col) {
    height: auto;
  }

  .page-desc {
    white-space: normal;
    max-width: none;
  }
}

.receipt-panel {
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: #fafafa;
  padding: 12px 16px;
}

:deep(.receipt-dialog) {
  --el-dialog-padding-primary: 16px 20px 20px;
  border-radius: 10px;
}

:deep(.receipt-dialog .el-dialog__header) {
  padding: 0;
  margin: 0;
  border: none;
}

:deep(.receipt-dialog .el-dialog__title) {
  display: none;
}

:deep(.receipt-dialog .el-dialog__headerbtn) {
  top: 14px;
  right: 14px;
  z-index: 1;
  width: 28px;
  height: 28px;
}

:deep(.receipt-dialog .el-dialog__body) {
  padding: 16px 20px 20px;
}

.receipt-bar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.receipt-title {
  font-weight: 600;
  font-size: 14px;
  color: #334155;
}

.receipt-desc {
  width: 100%;
}

.receipt-bar-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #e2e8f0;
}

.receipt-msg {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
}

.receipt-bar-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

:deep(.row-selected) {
  background-color: #fff7ed !important;
}

:deep(.row-disabled) {
  color: #94a3b8;
  cursor: not-allowed;
}
</style>
