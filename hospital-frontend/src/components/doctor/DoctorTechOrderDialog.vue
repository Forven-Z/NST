<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchMedicalTechnologies } from '../../api/admin'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  orderType: { type: String, required: true },
  registerId: { type: Number, default: null },
  preselectedIds: { type: Array, default: () => [] },
  getDraftMeta: { type: Function, default: null },
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const titleMap = {
  CHECK: '开立检查',
  INSPECTION: '开立检验',
  DISPOSAL: '开立处置',
}

const loading = ref(false)
const techList = ref([])
const selectedIds = ref([])

const dialogTitle = computed(() => titleMap[props.orderType] || '开立医嘱')
const hasAiPreselect = computed(() => props.preselectedIds.length > 0)

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    selectedIds.value = [...props.preselectedIds]
    loading.value = true
    try {
      const res = await fetchMedicalTechnologies({ pageSize: 50 })
      techList.value = (res.data?.list ?? []).filter((t) => t.techType === props.orderType)
    } catch (err) {
      ElMessage.error(err.message || '加载医技项目失败')
    } finally {
      loading.value = false
    }
  },
)

function onClose() {
  emit('update:modelValue', false)
}

function onSubmit() {
  if (!props.registerId) return ElMessage.warning('请先选择接诊患者')
  if (!selectedIds.value.length) return ElMessage.warning('请至少勾选一项')
  const items = selectedIds.value.map((id) => {
    const meta = props.getDraftMeta?.(id) || {}
    return {
      medicalTechnologyId: id,
      purpose: meta.purpose || '',
      bodyPart: meta.bodyPart || '',
    }
  })
  emit('confirm', { registerId: props.registerId, items })
  onClose()
}
</script>

<template>
  <el-dialog :model-value="modelValue" :title="dialogTitle" width="520px" @close="onClose">
    <el-alert
      v-if="hasAiPreselect"
      type="success"
      :closable="false"
      show-icon
      class="ai-tip"
      title="AI 已根据草稿为您预勾选部分项目，请核对后确认"
    />
    <div v-loading="loading" class="checkbox-list">
      <el-checkbox-group v-model="selectedIds">
        <el-checkbox
          v-for="t in techList"
          :key="t.id"
          :value="t.id"
          class="checkbox-item"
        >
          <span class="item-name">{{ t.itemName }}</span>
          <span class="item-price">¥{{ t.price }}</span>
          <el-tag
            v-if="preselectedIds.includes(t.id)"
            size="small"
            type="success"
            class="ai-tag"
          >
            AI 推荐
          </el-tag>
        </el-checkbox>
      </el-checkbox-group>
      <el-empty v-if="!loading && !techList.length" description="暂无可用项目" />
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
