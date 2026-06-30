<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchScheduleTemplate, replaceScheduleTemplate } from '../../api/admin'

const props = defineProps({
  visible: { type: Boolean, default: false },
  doctors: { type: Array, default: () => [] },
  registLevels: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:visible', 'saved'])

const selectedEmployeeId = ref(null)
const saving = ref(false)
const loading = ref(false)
const slots = ref([])

const weekdays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const noonTypes = [
  { type: 1, label: '上午' },
  { type: 2, label: '下午' },
  { type: 3, label: '晚上' },
]

const slotMap = computed(() => {
  const map = new Map()
  for (const s of slots.value) {
    map.set(`${s.weekday}|${s.noonType}`, s)
  }
  return map
})

watch(
  () => props.visible,
  (open) => {
    if (open) {
      selectedEmployeeId.value = props.doctors[0]?.employeeId ?? null
      if (selectedEmployeeId.value) loadTemplate()
    }
  },
)

watch(selectedEmployeeId, () => {
  if (props.visible && selectedEmployeeId.value) loadTemplate()
})

async function loadTemplate() {
  loading.value = true
  try {
    const res = await fetchScheduleTemplate(selectedEmployeeId.value)
    slots.value = (res.data?.slots ?? []).map((s) => ({ ...s, enabled: s.enabled !== 0 && s.enabled !== false }))
  } catch (err) {
    ElMessage.error(err.message || '加载模板失败')
  } finally {
    loading.value = false
  }
}

function defaultQuota(registLevelId) {
  return Number(registLevelId) === 2 ? 15 : 30
}

function getCell(weekday, noonType) {
  return slotMap.value.get(`${weekday}|${noonType}`)
}

function setCell(weekday, noonType, patch) {
  const key = `${weekday}|${noonType}`
  const existing = slotMap.value.get(key)
  if (existing) {
    Object.assign(existing, patch)
    return
  }
  slots.value.push({
    weekday,
    noonType,
    registLevelId: 1,
    totalQuota: 30,
    enabled: true,
    ...patch,
  })
}

function toggleCell(weekday, noonType, enabled) {
  if (!enabled) {
    slots.value = slots.value.filter((s) => !(s.weekday === weekday && s.noonType === noonType))
    return
  }
  setCell(weekday, noonType, { enabled: true, registLevelId: 1, totalQuota: 30 })
}

async function onSave() {
  if (!selectedEmployeeId.value) return ElMessage.warning('请选择医生')
  saving.value = true
  try {
    await replaceScheduleTemplate(selectedEmployeeId.value, {
      slots: slots.value.filter((s) => s.enabled !== false).map((s) => ({
        weekday: s.weekday,
        noonType: s.noonType,
        registLevelId: s.registLevelId,
        totalQuota: s.totalQuota ?? defaultQuota(s.registLevelId),
        enabled: s.enabled !== false,
      })),
    })
    ElMessage.success('固定模板已保存')
    emit('saved')
    emit('update:visible', false)
  } catch (err) {
    ElMessage.error(err.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function close() {
  emit('update:visible', false)
}
</script>

<template>
  <el-drawer :model-value="visible" title="管理固定模板" size="720px" @close="close">
    <el-form label-width="72px">
      <el-form-item label="医生">
        <el-select v-model="selectedEmployeeId" filterable style="width: 240px">
          <el-option
            v-for="d in doctors"
            :key="d.employeeId"
            :label="`${d.realName}（${d.title || '—'}）`"
            :value="d.employeeId"
          />
        </el-select>
      </el-form-item>
    </el-form>

    <div v-loading="loading" class="template-grid">
      <table>
        <thead>
          <tr>
            <th>午别</th>
            <th v-for="(label, idx) in weekdays" :key="label">{{ label }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="noon in noonTypes" :key="noon.type">
            <td>{{ noon.label }}</td>
            <td v-for="wd in 7" :key="`${noon.type}-${wd}`">
              <div class="cell">
                <el-switch
                  :model-value="!!getCell(wd, noon.type)"
                  @change="(v) => toggleCell(wd, noon.type, v)"
                />
                <template v-if="getCell(wd, noon.type)">
                  <el-select
                    :model-value="getCell(wd, noon.type).registLevelId"
                    size="small"
                    style="width: 72px"
                    @update:model-value="(v) => setCell(wd, noon.type, { registLevelId: v, totalQuota: defaultQuota(v) })"
                  >
                    <el-option
                      v-for="l in registLevels"
                      :key="l.id"
                      :label="l.levelName"
                      :value="l.id"
                    />
                  </el-select>
                  <el-input-number
                    :model-value="getCell(wd, noon.type).totalQuota"
                    size="small"
                    :min="1"
                    :max="99"
                    @update:model-value="(v) => setCell(wd, noon.type, { totalQuota: v })"
                  />
                </template>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onSave">保存模板</el-button>
    </template>
  </el-drawer>
</template>

<style scoped>
.template-grid table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.template-grid th,
.template-grid td {
  border: 1px solid #e2e8f0;
  padding: 8px;
  text-align: center;
}

.cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}
</style>
