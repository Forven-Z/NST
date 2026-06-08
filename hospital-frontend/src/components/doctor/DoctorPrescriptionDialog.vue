<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchDrugs } from '../../api/admin'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  registerId: { type: Number, default: null },
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const loading = ref(false)
const drugs = ref([])
const form = ref({
  drugId: null,
  quantity: 1,
  usageMethod: '口服',
  dosage: '',
  frequency: 'tid',
  days: 7,
  entrust: '',
})

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) return
    form.value = {
      drugId: null,
      quantity: 1,
      usageMethod: '口服',
      dosage: '',
      frequency: 'tid',
      days: 7,
      entrust: '饭后服用',
    }
    loading.value = true
    try {
      const res = await fetchDrugs({ pageSize: 50 })
      drugs.value = res.data?.list ?? []
      if (drugs.value.length) {
        form.value.drugId = drugs.value[0].id
        form.value.dosage = drugs.value[0].drugFormat?.includes('0.25') ? '0.5g' : '1片'
      }
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

function onSubmit() {
  if (!props.registerId) return ElMessage.warning('请先选择接诊患者')
  if (!form.value.drugId) return ElMessage.warning('请选择药品')
  emit('confirm', {
    registerId: props.registerId,
    remark: '门诊处方',
    items: [{ ...form.value }],
  })
  onClose()
}
</script>

<template>
  <el-dialog :model-value="modelValue" title="开立处方" width="520px" @close="onClose">
    <el-form v-loading="loading" label-width="88px">
      <el-form-item label="药品" required>
        <el-select v-model="form.drugId" placeholder="选择药品" style="width: 100%">
          <el-option
            v-for="d in drugs"
            :key="d.id"
            :label="`${d.drugName}（¥${d.retailPrice}/${d.unit}）`"
            :value="d.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="数量">
        <el-input-number v-model="form.quantity" :min="1" :max="99" />
      </el-form-item>
      <el-form-item label="用法">
        <el-input v-model="form.usageMethod" />
      </el-form-item>
      <el-form-item label="单次剂量">
        <el-input v-model="form.dosage" placeholder="如 0.5g" />
      </el-form-item>
      <el-form-item label="频次">
        <el-input v-model="form.frequency" placeholder="tid / bid" />
      </el-form-item>
      <el-form-item label="天数">
        <el-input-number v-model="form.days" :min="1" :max="30" />
      </el-form-item>
      <el-form-item label="嘱托">
        <el-input v-model="form.entrust" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="onClose">取消</el-button>
      <el-button type="primary" @click="onSubmit">确认开立</el-button>
    </template>
  </el-dialog>
</template>
