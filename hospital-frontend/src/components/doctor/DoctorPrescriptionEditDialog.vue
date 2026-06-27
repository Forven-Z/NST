<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchDrugs, fetchRegisterOrders, resubmitPrescription, updatePrescription } from '../../api/doctor'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  registerId: { type: Number, default: null },
  prescriptionId: { type: Number, default: null },
  rejectReason: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue', 'saved'])

const loading = ref(false)
const saving = ref(false)
const resubmitting = ref(false)
const items = ref([])
const drugs = ref([])
const addDrugId = ref(null)

const availableDrugsForAdd = computed(() =>
  drugs.value.filter((d) => !items.value.some((i) => i.drugId === d.id)),
)

const totalAmount = computed(() =>
  items.value.reduce((sum, row) => {
    const drug = drugs.value.find((d) => d.id === row.drugId)
    const price = Number(drug?.retailPrice ?? row.unitPrice) || 0
    return sum + price * (Number(row.quantity) || 0)
  }, 0),
)

watch(
  () => [props.modelValue, props.prescriptionId, props.registerId],
  async ([open, id, registerId]) => {
    if (!open || !id || !registerId) return
    loading.value = true
    addDrugId.value = null
    items.value = []
    try {
      const [ordersRes, drugRes] = await Promise.all([
        fetchRegisterOrders(registerId),
        fetchDrugs({ pageSize: 50 }),
      ])
      drugs.value = drugRes.data?.list ?? []
      const rx = (ordersRes.data?.prescriptions ?? []).find(
        (r) => r.prescriptionId === id,
      )
      if (!rx?.items?.length) {
        ElMessage.warning('未找到处方明细')
        emit('update:modelValue', false)
        return
      }
      items.value = rx.items.map((it) => ({
        drugId: it.drugId,
        drugName: it.drugName,
        drugFormat: it.drugFormat || '',
        unitPrice: it.unitPrice,
        quantity: Number(it.quantity) || 1,
        usageMethod: it.usageMethod || '口服',
        dosage: it.dosage || '',
        frequency: it.frequency || 'tid',
        days: it.days ?? 7,
        entrust: it.entrust || '',
      }))
    } catch (err) {
      ElMessage.error(err.message || '加载处方失败')
      emit('update:modelValue', false)
    } finally {
      loading.value = false
    }
  },
)

function onClose() {
  addDrugId.value = null
  items.value = []
  emit('update:modelValue', false)
}

function buildItemFromDrug(drug) {
  return {
    drugId: drug.id,
    drugName: drug.drugName,
    drugFormat: drug.drugFormat || '',
    unitPrice: drug.retailPrice,
    quantity: 1,
    usageMethod: '口服',
    dosage: drug.drugDosage || '1片',
    frequency: 'tid',
    days: 7,
    entrust: '',
  }
}

function drugOptionsForRow(row) {
  return drugs.value.filter(
    (d) => d.id === row.drugId || !items.value.some((i) => i.drugId === d.id),
  )
}

function onDrugChange(row, drugId) {
  const drug = drugs.value.find((d) => d.id === drugId)
  if (!drug) return
  const prevQty = row.quantity
  Object.assign(row, buildItemFromDrug(drug), { quantity: prevQty })
}

function removeItem(index) {
  items.value.splice(index, 1)
}

function onAddDrug() {
  if (!addDrugId.value) return ElMessage.warning('请选择要添加的药品')
  const drug = drugs.value.find((d) => d.id === addDrugId.value)
  if (!drug) return
  items.value.push(buildItemFromDrug(drug))
  addDrugId.value = null
}

function buildPayloadItems() {
  return items.value.map((row) => ({
    drugId: row.drugId,
    quantity: row.quantity,
    usageMethod: row.usageMethod,
    dosage: row.dosage,
    frequency: row.frequency,
    days: row.days,
    entrust: row.entrust,
  }))
}

async function onSave() {
  if (!props.prescriptionId || !items.value.length) {
    return ElMessage.warning('请至少保留一种药品')
  }
  saving.value = true
  try {
    await updatePrescription(props.prescriptionId, { items: buildPayloadItems() })
    ElMessage.success('处方已保存')
    emit('saved')
  } catch (err) {
    ElMessage.error(err.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onResubmit() {
  if (!props.prescriptionId) return
  if (!items.value.length) return ElMessage.warning('请至少保留一种药品')
  resubmitting.value = true
  try {
    await updatePrescription(props.prescriptionId, { items: buildPayloadItems() })
    await resubmitPrescription(props.prescriptionId)
    ElMessage.success('已重新提交，请通知患者缴费')
    emit('saved')
    onClose()
  } catch (err) {
    ElMessage.error(err.message || '重新提交失败')
  } finally {
    resubmitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="修改驳回处方"
    width="820px"
    destroy-on-close
    @close="onClose"
  >
    <div v-loading="loading">
      <el-alert
        v-if="rejectReason"
        type="warning"
        :closable="false"
        show-icon
        class="reject-alert"
        :title="`药师驳回原因：${rejectReason}`"
      />
      <p class="hint-text">可删除缺货药品、更换或添加其他药品，确认后重新提交给患者缴费。</p>

      <el-table :data="items" size="small" border empty-text="请添加至少一种药品">
        <el-table-column label="药品" min-width="150">
          <template #default="{ row }">
            <el-select
              :model-value="row.drugId"
              filterable
              size="small"
              class="drug-select"
              @change="(id) => onDrugChange(row, id)"
            >
              <el-option
                v-for="d in drugOptionsForRow(row)"
                :key="d.id"
                :label="d.drugName"
                :value="d.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="规格" width="110">
          <template #default="{ row }">
            <span class="spec-text">{{ row.drugFormat || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="100">
          <template #default="{ row }">
            <el-input-number
              v-model="row.quantity"
              :min="1"
              :max="99"
              size="small"
              controls-position="right"
            />
          </template>
        </el-table-column>
        <el-table-column label="用法" width="80">
          <template #default="{ row }">
            <el-input v-model="row.usageMethod" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="剂量" width="80">
          <template #default="{ row }">
            <el-input v-model="row.dosage" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="频次" width="72">
          <template #default="{ row }">
            <el-input v-model="row.frequency" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="天数" width="88">
          <template #default="{ row }">
            <el-input-number
              v-model="row.days"
              :min="1"
              :max="30"
              size="small"
              controls-position="right"
            />
          </template>
        </el-table-column>
        <el-table-column label="" width="52" fixed="right">
          <template #default="{ $index }">
            <el-button link type="danger" @click="removeItem($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="add-row">
        <el-select
          v-model="addDrugId"
          filterable
          clearable
          placeholder="选择药品添加"
          size="small"
          class="add-select"
          :disabled="!availableDrugsForAdd.length"
        >
          <el-option
            v-for="d in availableDrugsForAdd"
            :key="d.id"
            :label="`${d.drugName}${d.drugFormat ? `（${d.drugFormat}）` : ''}`"
            :value="d.id"
          />
        </el-select>
        <el-button size="small" type="primary" plain :disabled="!addDrugId" @click="onAddDrug">
          添加药品
        </el-button>
        <span v-if="items.length" class="total-line">合计：<strong>¥{{ totalAmount.toFixed(2) }}</strong></span>
      </div>
    </div>

    <template #footer>
      <el-button @click="onClose">取消</el-button>
      <el-button :loading="saving" :disabled="loading || !items.length" @click="onSave">
        保存修改
      </el-button>
      <el-button
        type="primary"
        :loading="resubmitting"
        :disabled="loading || !items.length"
        @click="onResubmit"
      >
        重新提交
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.reject-alert {
  margin-bottom: 8px;
}

.hint-text {
  margin: 0 0 12px;
  font-size: 13px;
  color: #64748b;
}

.spec-text {
  font-size: 12px;
  color: #64748b;
}

.drug-select {
  width: 100%;
}

.add-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.add-select {
  width: 260px;
}

.total-line {
  margin-left: auto;
  font-size: 14px;
  color: #334155;
}
</style>
