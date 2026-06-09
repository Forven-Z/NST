<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  confirmCheckAiDraft,
  confirmDisposalAiDraft,
  confirmInspectionAiDraft,
  updateCheckAiDraft,
  updateDisposalAiDraft,
  updateInspectionAiDraft,
} from '../../api/doctor'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  draftType: { type: String, default: 'CHECK' },
  draft: { type: Object, default: null },
})

const emit = defineEmits(['update:modelValue', 'confirmed'])

const saving = ref(false)
const confirming = ref(false)
const aiReason = ref('')
const items = ref([])

const TYPE_LABEL = { CHECK: '检查', INSPECTION: '检验', DISPOSAL: '处置' }

const UPDATE_FN = {
  CHECK: updateCheckAiDraft,
  INSPECTION: updateInspectionAiDraft,
  DISPOSAL: updateDisposalAiDraft,
}

const CONFIRM_FN = {
  CHECK: confirmCheckAiDraft,
  INSPECTION: confirmInspectionAiDraft,
  DISPOSAL: confirmDisposalAiDraft,
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open || !props.draft) return
    aiReason.value = props.draft.aiReason || ''
    items.value = (props.draft.items || []).map((it) => ({ ...it }))
  },
)

function onClose() {
  emit('update:modelValue', false)
}

function removeItem(index) {
  items.value.splice(index, 1)
}

async function onSaveEdit() {
  if (!props.draft?.draftId) return
  if (!items.value.length) return ElMessage.warning('请至少保留一项')
  saving.value = true
  try {
    const fn = UPDATE_FN[props.draftType]
    const res = await fn(props.draft.draftId, { items: items.value, aiReason: aiReason.value })
    items.value = (res.data?.items || items.value).map((it) => ({ ...it }))
    ElMessage.success('草稿已保存')
  } catch (err) {
    ElMessage.error(err.message || '保存草稿失败')
  } finally {
    saving.value = false
  }
}

async function onConfirm() {
  if (!props.draft?.draftId) return
  if (!items.value.length) return ElMessage.warning('请至少保留一项')
  confirming.value = true
  try {
    const updateFn = UPDATE_FN[props.draftType]
    await updateFn(props.draft.draftId, { items: items.value, aiReason: aiReason.value })
    const confirmFn = CONFIRM_FN[props.draftType]
    const res = await confirmFn(props.draft.draftId)
    ElMessage.success(res.data?.message || '已确认提交，请患者缴费')
    emit('confirmed', { type: props.draftType, data: res.data })
    onClose()
  } catch (err) {
    ElMessage.error(err.message || '确认提交失败')
  } finally {
    confirming.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="`编辑并确认 · AI ${TYPE_LABEL[draftType] || ''}草稿`"
    width="640px"
    destroy-on-close
    @close="onClose"
  >
    <el-alert
      v-if="aiReason"
      type="info"
      :closable="false"
      show-icon
      class="reason-alert"
      :title="aiReason"
    />

    <el-table :data="items" size="small" border empty-text="暂无项目">
      <el-table-column prop="itemName" label="项目" width="120" />
      <el-table-column label="目的" min-width="140">
        <template #default="{ row }">
          <el-input v-model="row.purpose" size="small" placeholder="检查/处置目的" />
        </template>
      </el-table-column>
      <el-table-column label="部位" width="100">
        <template #default="{ row }">
          <el-input v-model="row.bodyPart" size="small" placeholder="可选" />
        </template>
      </el-table-column>
      <el-table-column label="备注" width="100">
        <template #default="{ row }">
          <el-input v-model="row.remark" size="small" />
        </template>
      </el-table-column>
      <el-table-column label="" width="56" fixed="right">
        <template #default="{ $index }">
          <el-button link type="danger" @click="removeItem($index)">删</el-button>
        </template>
      </el-table-column>
    </el-table>

    <p class="hint">ADR-015：医生编辑草稿后点击「确认提交」才会开立医嘱（status=10）。</p>

    <template #footer>
      <el-button @click="onClose">取消</el-button>
      <el-button :loading="saving" @click="onSaveEdit">保存编辑</el-button>
      <el-button type="primary" :loading="confirming" @click="onConfirm">确认提交</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.reason-alert {
  margin-bottom: 12px;
}

.hint {
  margin: 12px 0 0;
  font-size: 12px;
  color: #64748b;
}
</style>
