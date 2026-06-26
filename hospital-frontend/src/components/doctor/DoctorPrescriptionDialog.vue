<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchDrugs } from '../../api/doctor'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  registerId: { type: Number, default: null },
  preselectedDrugIds: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const loading = ref(false)
const drugs = ref([])
const selectedDrugIds = ref([])
/** drugId -> 数量（盒/瓶等，与 drug.unit 一致） */
const selectedQuantities = ref({})

const hasAiPreselect = computed(() => props.preselectedDrugIds.length > 0)

function resetQuantitiesForSelection(ids) {
  const next = {}
  for (const id of ids) {
    next[id] = selectedQuantities.value[id] ?? 1
  }
  selectedQuantities.value = next
}

watch(selectedDrugIds, (ids) => {
  resetQuantitiesForSelection(ids)
})

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    selectedDrugIds.value = [...props.preselectedDrugIds]
    selectedQuantities.value = Object.fromEntries(
      props.preselectedDrugIds.map((id) => [id, 1]),
    )
    loading.value = true
    try {
      const res = await fetchDrugs({ pageSize: 50 })
      drugs.value = res.data?.list ?? []
    } catch (err) {
      ElMessage.error(err.message || '加载药品失败')
    } finally {
      loading.value = false
    }
  },
)

function onClose() {
  emit('update:modelValue', false)
}

function isSelected(drugId) {
  return selectedDrugIds.value.includes(drugId)
}

function quantityFor(drugId) {
  return selectedQuantities.value[drugId] ?? 1
}

function setQuantity(drugId, val) {
  selectedQuantities.value = {
    ...selectedQuantities.value,
    [drugId]: val ?? 1,
  }
}

function buildDrugItem(drug) {
  const qty = quantityFor(drug.id)
  return {
    drugId: drug.id,
    quantity: qty,
    usageMethod: '口服',
    dosage: drug.drugFormat?.includes('0.25') ? '0.5g' : '1片',
    frequency: 'tid',
    days: 7,
    entrust: '饭后服用',
  }
}

function onSubmit() {
  if (!props.registerId) return ElMessage.warning('请先选择接诊患者')
  if (!selectedDrugIds.value.length) return ElMessage.warning('请至少勾选一种药品')
  for (const id of selectedDrugIds.value) {
    const qty = quantityFor(id)
    if (!qty || qty < 1) {
      return ElMessage.warning('每种药品数量至少为 1')
    }
  }
  const items = selectedDrugIds.value
    .map((id) => drugs.value.find((d) => d.id === id))
    .filter(Boolean)
    .map(buildDrugItem)
  emit('confirm', {
    registerId: props.registerId,
    remark: '门诊处方',
    items,
  })
  onClose()
}
</script>

<template>
  <el-dialog :model-value="modelValue" title="开立处方" width="600px" @close="onClose">
    <el-alert
      v-if="hasAiPreselect"
      type="success"
      :closable="false"
      show-icon
      class="ai-tip"
      title="AI 已根据草稿为您预勾选部分药品，请核对数量后确认"
    />
    <p class="hint">勾选药品并设置数量（默认 1 盒/瓶）；用法用量提交后可在药房核对。</p>
    <div v-loading="loading" class="checkbox-list">
      <el-checkbox-group v-model="selectedDrugIds">
        <div v-for="d in drugs" :key="d.id" class="drug-row">
          <el-checkbox :value="d.id" class="checkbox-item">
            <span class="item-name">{{ d.drugName }}</span>
            <span class="item-price">¥{{ d.retailPrice }}/{{ d.unit || '盒' }}</span>
            <el-tag
              v-if="preselectedDrugIds.includes(d.id)"
              size="small"
              type="success"
              class="ai-tag"
            >
              AI 推荐
            </el-tag>
          </el-checkbox>
          <div v-if="isSelected(d.id)" class="qty-wrap" @click.stop>
            <span class="qty-label">数量</span>
            <el-input-number
              :model-value="quantityFor(d.id)"
              :min="1"
              :max="99"
              size="small"
              controls-position="right"
              @update:model-value="(v) => setQuantity(d.id, v)"
            />
            <span class="qty-unit">{{ d.unit || '盒' }}</span>
          </div>
        </div>
      </el-checkbox-group>
      <el-empty v-if="!loading && !drugs.length" description="暂无可用药品" />
    </div>
    <template #footer>
      <el-button @click="onClose">取消</el-button>
      <el-button type="primary" @click="onSubmit">确认开立</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.ai-tip {
  margin-bottom: 12px;
}

.hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
}

.checkbox-list {
  max-height: 400px;
  overflow-y: auto;
}

.drug-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  padding: 6px 8px;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
  background: #fafbfc;
}

.checkbox-item {
  flex: 1;
  min-width: 0;
  height: auto;
  margin-right: 0;
}

.item-name {
  margin-right: 8px;
}

.item-price {
  color: #64748b;
  font-size: 13px;
  margin-right: 8px;
}

.ai-tag {
  margin-left: 4px;
}

.qty-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.qty-label {
  font-size: 12px;
  color: #64748b;
}

.qty-unit {
  font-size: 12px;
  color: #94a3b8;
  min-width: 1.5em;
}
</style>
