<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchMedicalTechnologies } from '../../api/admin'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  orderType: { type: String, required: true },
  registerId: { type: Number, default: null },
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const titleMap = {
  CHECK: '开立检查',
  INSPECTION: '开立检验',
  DISPOSAL: '开立处置',
}

const loading = ref(false)
const techList = ref([])
const form = ref({
  medicalTechnologyId: null,
  purpose: '',
  bodyPart: '',
})

const dialogTitle = computed(() => titleMap[props.orderType] || '开立医嘱')

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    form.value = { medicalTechnologyId: null, purpose: '', bodyPart: '' }
    loading.value = true
    try {
      const res = await fetchMedicalTechnologies({ pageSize: 50 })
      techList.value = (res.data?.list ?? []).filter((t) => t.techType === props.orderType)
      if (techList.value.length) {
        form.value.medicalTechnologyId = techList.value[0].id
      }
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
  if (!form.value.medicalTechnologyId) return ElMessage.warning('请选择项目')
  emit('confirm', {
    registerId: props.registerId,
    medicalTechnologyId: form.value.medicalTechnologyId,
    purpose: form.value.purpose,
    bodyPart: form.value.bodyPart,
  })
  onClose()
}
</script>

<template>
  <el-dialog :model-value="modelValue" :title="dialogTitle" width="480px" @close="onClose">
    <el-form v-loading="loading" label-width="88px">
      <el-form-item label="项目" required>
        <el-select v-model="form.medicalTechnologyId" placeholder="选择医技项目" style="width: 100%">
          <el-option
            v-for="t in techList"
            :key="t.id"
            :label="`${t.itemName}（¥${t.price}）`"
            :value="t.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="目的">
        <el-input v-model="form.purpose" placeholder="如：排除颅内病变" />
      </el-form-item>
      <el-form-item v-if="orderType !== 'DISPOSAL'" label="部位">
        <el-input v-model="form.bodyPart" placeholder="如：头部、血液" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="onClose">取消</el-button>
      <el-button type="primary" @click="onSubmit">确认开立</el-button>
    </template>
  </el-dialog>
</template>
