<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createPrescription,
  createPrescriptionAiDraft,
  updatePrescriptionAiDraft,
} from '../../api/doctor'
import { fetchDrugs } from '../../api/doctor'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  registerId: { type: Number, default: null },
  recordForm: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['update:modelValue', 'confirmed'])

const loading = ref(false)
const saving = ref(false)
const confirming = ref(false)
const draftId = ref(null)
const aiReason = ref('')
const items = ref([])
const drugs = ref([])

watch(
  () => props.modelValue,
  async (open) => {
    if (!open || !props.registerId) return
    loading.value = true
    try {
      const [draftRes, drugRes] = await Promise.all([
        createPrescriptionAiDraft({
          registerId: props.registerId,
          medicalRecord: { ...props.recordForm },
        }),
        fetchDrugs({ pageSize: 50 }),
      ])
      drugs.value = drugRes.data?.list ?? []
      const draft = draftRes.data || {}
      if (!draft.draftId) {
        ElMessage.info(draft.message || 'AI 处方草稿暂不可用')
        emit('update:modelValue', false)
        return
      }
      draftId.value = draft.draftId
      aiReason.value = draft.aiReason || ''
      items.value = (draft.items || []).map((it) => ({ ...it }))
    } catch (err) {
      ElMessage.error(err.message || '加载处方草稿失败')
      emit('update:modelValue', false)
    } finally {
      loading.value = false
    }
  },
)

function onClose() {
  emit('update:modelValue', false)
}

function drugLabel(drugId) {
  return drugs.value.find((d) => d.id === drugId)?.drugName || `药品 #${drugId}`
}

function removeItem(index) {
  items.value.splice(index, 1)
}

async function onSaveEdit() {
  if (!draftId.value || !items.value.length) return ElMessage.warning('请至少保留一种药品')
  saving.value = true
  try {
    const res = await updatePrescriptionAiDraft(draftId.value, { items: items.value })
    items.value = res.data?.items || items.value
    ElMessage.success('处方草稿已保存')
  } catch (err) {
    ElMessage.error(err.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onConfirm() {
  if (!draftId.value || !props.registerId || !items.value.length) {
    return ElMessage.warning('请至少保留一种药品')
  }
  confirming.value = true
  try {
    await updatePrescriptionAiDraft(draftId.value, { items: items.value })
    const res = await createPrescription({
      registerId: props.registerId,
      draftId: draftId.value,
      items: items.value,
      remark: 'AI 辅助处方',
    })
    ElMessage.success(`处方 #${res.data?.prescriptionId} 已开立，请患者缴费后取药`)
    emit('confirmed', res.data)
    onClose()
  } catch (err) {
    ElMessage.error(err.message || '确认处方失败')
  } finally {
    confirming.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="编辑并确认 · AI 处方草稿"
    width="680px"
    destroy-on-close
    @close="onClose"
  >
    <div v-loading="loading">
      <el-alert
        v-if="aiReason"
        type="info"
        :closable="false"
        show-icon
        class="reason-alert"
        :title="aiReason"
      />

      <el-table :data="items" size="small" border empty-text="暂无药品">
        <el-table-column label="药品" width="140">
          <template #default="{ row }">{{ row.drugName || drugLabel(row.drugId) }}</template>
        </el-table-column>
        <el-table-column label="数量" width="72">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" :min="1" :max="99" size="small" controls-position="right" />
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
        <el-table-column label="天数" width="72">
          <template #default="{ row }">
            <el-input-number v-model="row.days" :min="1" :max="30" size="small" controls-position="right" />
          </template>
        </el-table-column>
        <el-table-column label="" width="48" fixed="right">
          <template #default="{ $index }">
            <el-button link type="danger" @click="removeItem($index)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <el-button @click="onClose">取消</el-button>
      <el-button :loading="saving" :disabled="loading" @click="onSaveEdit">保存编辑</el-button>
      <el-button type="primary" :loading="confirming" :disabled="loading" @click="onConfirm">确认提交处方</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.reason-alert {
  margin-bottom: 12px;
}
</style>
