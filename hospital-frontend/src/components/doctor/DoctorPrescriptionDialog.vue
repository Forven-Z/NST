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
const items = ref([])

const hasAiPreselect = computed(() => props.preselectedDrugIds.length > 0)

const totalAmount = computed(() =>
  items.value.reduce((sum, row) => {
    const price = Number(row.retailPrice) || 0
    const qty = Number(row.quantity) || 0
    return sum + price * qty
  }, 0),
)

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    selectedDrugIds.value = [...props.preselectedDrugIds]
    items.value = []
    loading.value = true
    try {
      const res = await fetchDrugs({ pageSize: 50 })
      drugs.value = res.data?.list ?? []
      syncItemsFromSelection(selectedDrugIds.value)
    } catch (err) {
      ElMessage.error(err.message || '加载药品失败')
    } finally {
      loading.value = false
    }
  },
)

function onClose() {
  selectedDrugIds.value = []
  items.value = []
  emit('update:modelValue', false)
}

function buildDrugItem(drug) {
  return {
    drugId: drug.id,
    drugName: drug.drugName,
    drugFormat: drug.drugFormat || '',
    unit: drug.unit || '盒',
    retailPrice: drug.retailPrice,
    quantity: 1,
    usageMethod: '口服',
    dosage: drug.drugDosage || (drug.drugFormat?.includes('g') ? '1粒' : '1片'),
    frequency: 'tid',
    days: 7,
    entrust: '饭后服用',
  }
}

function syncItemsFromSelection(ids) {
  for (const id of ids) {
    if (items.value.some((i) => i.drugId === id)) continue
    const drug = drugs.value.find((d) => d.id === id)
    if (drug) items.value.push(buildDrugItem(drug))
  }
  items.value = items.value.filter((i) => ids.includes(i.drugId))
}

function onSelectionChange(ids) {
  syncItemsFromSelection(ids)
}

function formatSpec(drug) {
  const parts = [drug.drugFormat, drug.drugDosage, drug.drugType].filter(Boolean)
  return parts.join(' · ') || '—'
}

function lineAmount(row) {
  return ((Number(row.retailPrice) || 0) * (Number(row.quantity) || 0)).toFixed(2)
}

function onSubmit() {
  if (!props.registerId) return ElMessage.warning('请先选择接诊患者')
  if (!items.value.length) return ElMessage.warning('请至少勾选一种药品')
  const payloadItems = items.value.map((row) => ({
    drugId: row.drugId,
    quantity: row.quantity,
    usageMethod: row.usageMethod,
    dosage: row.dosage,
    frequency: row.frequency,
    days: row.days,
    entrust: row.entrust,
  }))
  emit('confirm', {
    registerId: props.registerId,
    remark: '门诊处方',
    items: payloadItems,
  })
  onClose()
}
</script>

<template>
  <el-dialog :model-value="modelValue" title="开立处方" width="760px" destroy-on-close @close="onClose">
    <el-alert
      v-if="hasAiPreselect"
      type="success"
      :closable="false"
      show-icon
      class="ai-tip"
      title="AI 已根据草稿为您预勾选部分药品，请核对数量与用法后确认"
    />

    <div v-loading="loading">
      <p class="section-label">选择药品</p>
      <div class="checkbox-list">
        <el-checkbox-group v-model="selectedDrugIds" @change="onSelectionChange">
          <el-checkbox
            v-for="d in drugs"
            :key="d.id"
            :value="d.id"
            class="checkbox-item"
          >
            <span class="item-name">{{ d.drugName }}</span>
            <span class="item-spec">{{ formatSpec(d) }}</span>
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
        </el-checkbox-group>
        <el-empty v-if="!loading && !drugs.length" description="暂无可用药品" />
      </div>

      <template v-if="items.length">
        <p class="section-label">处方明细</p>
        <el-table :data="items" size="small" border class="items-table">
          <el-table-column prop="drugName" label="药品" min-width="120" />
          <el-table-column label="规格" min-width="120">
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
              <span class="unit-hint">{{ row.unit }}</span>
            </template>
          </el-table-column>
          <el-table-column label="用法" width="80">
            <template #default="{ row }">
              <el-input v-model="row.usageMethod" size="small" placeholder="口服" />
            </template>
          </el-table-column>
          <el-table-column label="剂量" width="80">
            <template #default="{ row }">
              <el-input v-model="row.dosage" size="small" placeholder="1片" />
            </template>
          </el-table-column>
          <el-table-column label="频次" width="72">
            <template #default="{ row }">
              <el-input v-model="row.frequency" size="small" placeholder="tid" />
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
          <el-table-column label="小计" width="72" align="right">
            <template #default="{ row }">¥{{ lineAmount(row) }}</template>
          </el-table-column>
        </el-table>
        <p class="total-line">处方合计：<strong>¥{{ totalAmount.toFixed(2) }}</strong></p>
      </template>
    </div>

    <template #footer>
      <el-button @click="onClose">取消</el-button>
      <el-button type="primary" :disabled="!items.length" @click="onSubmit">确认开立</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.ai-tip {
  margin-bottom: 12px;
}

.section-label {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.section-label:not(:first-of-type) {
  margin-top: 16px;
}

.checkbox-list {
  max-height: 200px;
  overflow-y: auto;
  padding: 4px 0;
}

.checkbox-item {
  display: flex;
  align-items: flex-start;
  width: 100%;
  margin-bottom: 10px;
  height: auto;
}

.item-name {
  margin-right: 8px;
  font-weight: 500;
}

.item-spec {
  flex: 1;
  color: #64748b;
  font-size: 12px;
  margin-right: 8px;
}

.item-price {
  color: #64748b;
  font-size: 13px;
  margin-right: 8px;
  white-space: nowrap;
}

.ai-tag {
  margin-left: 4px;
}

.items-table {
  margin-top: 4px;
}

.spec-text {
  font-size: 12px;
  color: #64748b;
}

.unit-hint {
  display: block;
  font-size: 11px;
  color: #94a3b8;
  text-align: center;
  margin-top: 2px;
}

.total-line {
  margin: 10px 0 0;
  text-align: right;
  font-size: 14px;
  color: #334155;
}
</style>
