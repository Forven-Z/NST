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

const hasAiPreselect = computed(() => props.preselectedDrugIds.length > 0)

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    selectedDrugIds.value = [...props.preselectedDrugIds]
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

function buildDrugItem(drug) {
  return {
    drugId: drug.id,
    quantity: 1,
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
  <el-dialog :model-value="modelValue" title="开立处方" width="520px" @close="onClose">
    <el-alert
      v-if="hasAiPreselect"
      type="success"
      :closable="false"
      show-icon
      class="ai-tip"
      title="AI 已根据草稿为您预勾选部分药品，请核对后确认"
    />
    <div v-loading="loading" class="checkbox-list">
      <el-checkbox-group v-model="selectedDrugIds">
        <el-checkbox
          v-for="d in drugs"
          :key="d.id"
          :value="d.id"
          class="checkbox-item"
        >
          <span class="item-name">{{ d.drugName }}</span>
          <span class="item-price">¥{{ d.retailPrice }}/{{ d.unit }}</span>
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

.checkbox-list {
  max-height: 360px;
  overflow-y: auto;
}

.checkbox-item {
  display: flex;
  align-items: center;
  width: 100%;
  margin-bottom: 10px;
  height: auto;
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
</style>
