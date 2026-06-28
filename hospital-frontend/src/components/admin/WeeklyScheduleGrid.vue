<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  doctors: { type: Array, default: () => [] },
  slots: { type: Array, default: () => [] },
  weekStart: { type: String, required: true },
  registLevels: { type: Array, default: () => [] },
})

const emit = defineEmits(['change'])

const popoverKey = ref('')
const editDraft = ref({
  employeeId: null,
  workDate: '',
  noonType: 1,
  schedulingId: null,
  registLevelId: 1,
  totalQuota: 30,
})

const weekDays = computed(() => {
  const labels = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  return labels.map((label, index) => ({
    label,
    workDate: addDays(props.weekStart, index),
  }))
})

const slotMap = computed(() => {
  const map = new Map()
  for (const slot of props.slots) {
    map.set(`${slot.employeeId}|${slot.workDate}|${slot.noonType}`, slot)
  }
  return map
})

function addDays(iso, days) {
  const d = new Date(`${iso}T12:00:00`)
  d.setDate(d.getDate() + days)
  return d.toISOString().slice(0, 10)
}

function defaultQuota(registLevelId) {
  return Number(registLevelId) === 2 ? 15 : 30
}

function getSlot(employeeId, workDate, noonType) {
  return slotMap.value.get(`${employeeId}|${workDate}|${noonType}`) || null
}

function segmentClass(slot) {
  if (!slot) return 'segment-empty'
  if (slot.needsSubstitute) return 'segment-substitute'
  if ((slot.publishStatus ?? 1) === 1) return 'segment-published'
  return 'segment-draft'
}

function segmentLabel(slot) {
  if (!slot) return '—'
  const short = slot.registLevelId === 2 ? '专' : '普'
  return `${short}${slot.totalQuota}`
}

function segmentKey(employeeId, workDate, noonType) {
  return `${employeeId}|${workDate}|${noonType}`
}

function isPopoverOpen(employeeId, workDate, noonType) {
  return popoverKey.value === segmentKey(employeeId, workDate, noonType)
}

function closePopover() {
  popoverKey.value = ''
}

function openSegment(employeeId, workDate, noonType) {
  const slot = getSlot(employeeId, workDate, noonType)
  if (slot?.needsSubstitute) return
  const key = segmentKey(employeeId, workDate, noonType)
  if (popoverKey.value === key) {
    closePopover()
    return
  }
  popoverKey.value = key
  editDraft.value = {
    employeeId,
    workDate,
    noonType,
    schedulingId: slot?.schedulingId ?? null,
    registLevelId: slot?.registLevelId ?? 1,
    totalQuota: slot?.totalQuota ?? defaultQuota(slot?.registLevelId ?? 1),
  }
}

function onLevelChange(levelId) {
  editDraft.value.registLevelId = levelId
  if (!getSlot(editDraft.value.employeeId, editDraft.value.workDate, editDraft.value.noonType)) {
    editDraft.value.totalQuota = defaultQuota(levelId)
  }
}

function applySegment() {
  const slot = getSlot(editDraft.value.employeeId, editDraft.value.workDate, editDraft.value.noonType)
  const published = (slot?.publishStatus ?? 0) === 1
  emit('change', {
    schedulingId: editDraft.value.schedulingId,
    employeeId: editDraft.value.employeeId,
    workDate: editDraft.value.workDate,
    noonType: editDraft.value.noonType,
    registLevelId: published ? undefined : editDraft.value.registLevelId,
    totalQuota: editDraft.value.totalQuota,
  })
  closePopover()
}

function clearSegment() {
  const slot = getSlot(editDraft.value.employeeId, editDraft.value.workDate, editDraft.value.noonType)
  if (!slot || (slot.publishStatus ?? 1) === 1) return
  emit('change', {
    schedulingId: slot.schedulingId ?? undefined,
    employeeId: slot.employeeId,
    workDate: slot.workDate,
    noonType: slot.noonType,
    clear: true,
  })
  closePopover()
}

const canEditLevel = computed(() => {
  const slot = getSlot(editDraft.value.employeeId, editDraft.value.workDate, editDraft.value.noonType)
  return !slot || (slot.publishStatus ?? 0) === 0
})
</script>

<template>
  <div class="week-grid-wrap">
    <table class="week-grid">
      <thead>
        <tr>
          <th class="doctor-col">医生</th>
          <th v-for="day in weekDays" :key="day.workDate">
            <div>{{ day.label }}</div>
            <div class="date-sub">{{ day.workDate.slice(5) }}</div>
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="doc in doctors" :key="doc.employeeId">
          <td class="doctor-col">
            <div class="doctor-name">{{ doc.realName }}</div>
            <div class="doctor-title">{{ doc.title || '—' }}</div>
          </td>
          <td v-for="day in weekDays" :key="`${doc.employeeId}-${day.workDate}`" class="day-cell">
            <div
              v-for="noon in [{ t: 1, l: '上' }, { t: 2, l: '下' }, { t: 3, l: '晚' }]"
              :key="noon.t"
              class="segment-row"
            >
              <span class="noon-tag">{{ noon.l }}</span>
              <el-popover
                :visible="isPopoverOpen(doc.employeeId, day.workDate, noon.t)"
                placement="bottom"
                :width="220"
                trigger="manual"
                @update:visible="(v) => { if (!v && isPopoverOpen(doc.employeeId, day.workDate, noon.t)) closePopover() }"
              >
                <template #reference>
                  <button
                    type="button"
                    class="segment-btn"
                    :class="segmentClass(getSlot(doc.employeeId, day.workDate, noon.t))"
                    @click.stop="openSegment(doc.employeeId, day.workDate, noon.t)"
                  >
                    {{ segmentLabel(getSlot(doc.employeeId, day.workDate, noon.t)) }}
                  </button>
                </template>
                <div class="popover-body">
                  <el-form label-width="56px" size="small">
                    <el-form-item label="号别">
                      <el-select
                        v-model="editDraft.registLevelId"
                        :disabled="!canEditLevel"
                        style="width: 100%"
                        @change="onLevelChange"
                      >
                        <el-option
                          v-for="l in registLevels"
                          :key="l.id"
                          :label="l.levelName"
                          :value="l.id"
                        />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="号源">
                      <el-input-number v-model="editDraft.totalQuota" :min="1" :max="99" />
                    </el-form-item>
                  </el-form>
                  <div class="popover-actions">
                    <el-button size="small" @click="closePopover">取消</el-button>
                    <el-button
                      v-if="canEditLevel && getSlot(editDraft.employeeId, editDraft.workDate, editDraft.noonType)"
                      size="small"
                      type="danger"
                      @click="clearSegment"
                    >
                      清除
                    </el-button>
                    <el-button size="small" type="primary" @click="applySegment">确定</el-button>
                  </div>
                </div>
              </el-popover>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.week-grid-wrap {
  overflow-x: auto;
}

.week-grid {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.week-grid th,
.week-grid td {
  border: 1px solid #e2e8f0;
  vertical-align: top;
}

.week-grid th {
  background: #f8fafc;
  padding: 8px 6px;
  text-align: center;
}

.date-sub {
  color: #64748b;
  font-size: 11px;
}

.doctor-col {
  min-width: 88px;
  padding: 8px;
  background: #f8fafc;
}

.doctor-name {
  font-weight: 600;
}

.doctor-title {
  color: #64748b;
  font-size: 11px;
}

.day-cell {
  min-width: 92px;
  padding: 4px;
}

.segment-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 2px;
}

.noon-tag {
  width: 14px;
  color: #94a3b8;
  flex-shrink: 0;
}

.segment-btn {
  flex: 1;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  padding: 2px 4px;
  cursor: pointer;
  background: #fff;
  font-size: 11px;
}

.segment-empty {
  color: #94a3b8;
  background: #f8fafc;
}

.segment-draft {
  background: #f1f5f9;
  border-color: #cbd5e1;
}

.segment-published {
  background: #ecfdf5;
  border-color: #86efac;
  color: #166534;
}

.segment-substitute {
  background: #fef2f2;
  border-color: #fca5a5;
  color: #b91c1c;
}

.popover-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}
</style>
