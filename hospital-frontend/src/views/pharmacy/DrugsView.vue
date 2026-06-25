<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createPharmacyDrug,
  disablePharmacyDrug,
  enablePharmacyDrug,
  fetchPharmacyDrugs,
  updatePharmacyDrug,
} from '../../api/pharmacy'

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const keyword = ref('')
const includeDisabled = ref(false)
const dialogVisible = ref(false)
const editingId = ref(null)
const showExtra = ref(false)

const emptyForm = () => ({
  drugName: '',
  retailPrice: null,
  stockQty: 0,
  drugFormat: '',
  drugDosage: '',
  drugType: '',
  unit: '',
})

const form = reactive(emptyForm())

let searchTimer = null

onMounted(loadList)

watch(keyword, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(loadList, 300)
})

watch(includeDisabled, loadList)

async function loadList() {
  loading.value = true
  try {
    const res = await fetchPharmacyDrugs({
      keyword: keyword.value || undefined,
      includeDisabled: includeDisabled.value,
      page: 1,
      pageSize: 50,
    })
    list.value = res.data?.list ?? []
  } catch (err) {
    ElMessage.error(err.message || '加载药品目录失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, emptyForm())
  showExtra.value = false
}

function openCreate() {
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.drugName = row.drugName
  form.retailPrice = row.retailPrice
  form.stockQty = row.stockQty
  form.drugFormat = row.drugFormat || ''
  form.drugDosage = row.drugDosage || ''
  form.drugType = row.drugType || ''
  form.unit = row.unit || ''
  showExtra.value = !!(row.drugFormat || row.drugDosage || row.drugType || row.unit)
  dialogVisible.value = true
}

function buildPayload() {
  const payload = {
    drugName: form.drugName.trim(),
    retailPrice: form.retailPrice,
    stockQty: form.stockQty,
  }
  if (form.drugFormat) payload.drugFormat = form.drugFormat.trim()
  if (form.drugDosage) payload.drugDosage = form.drugDosage.trim()
  if (form.drugType) payload.drugType = form.drugType.trim()
  if (form.unit) payload.unit = form.unit.trim()
  return payload
}

async function onSubmit() {
  if (!form.drugName?.trim()) return ElMessage.warning('请填写药品名称')
  if (form.retailPrice == null || form.retailPrice <= 0) return ElMessage.warning('零售价须大于 0')
  if (form.stockQty == null || form.stockQty < 0) return ElMessage.warning('库存不能为负数')

  saving.value = true
  try {
    const payload = buildPayload()
    if (editingId.value) {
      await updatePharmacyDrug(editingId.value, payload)
      ElMessage.success('药品已更新')
    } else {
      await createPharmacyDrug(payload)
      ElMessage.success('药品已新增')
    }
    dialogVisible.value = false
    await loadList()
  } catch (err) {
    ElMessage.error(err.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onToggleDisabled(row) {
  const isDisable = !row.disabled
  const action = isDisable ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(`确认${action}「${row.drugName}」？`, `${action}药品`, {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    if (isDisable) {
      await disablePharmacyDrug(row.id)
    } else {
      await enablePharmacyDrug(row.id)
    }
    ElMessage.success(`${action}成功`)
    await loadList()
  } catch (err) {
    ElMessage.error(err.message || `${action}失败`)
  }
}

function rowClassName({ row }) {
  return row.disabled ? 'row-disabled' : ''
}
</script>

<template>
  <div class="pharmacy-page">
    <div class="page-head">
      <h2 class="page-title">药品管理</h2>
      <p class="page-desc">
        维护药房药品目录：新增药品、调整零售价与库存；停用后门诊医生开处方时将不再显示该药品。
      </p>
    </div>

    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-header">
          <span>药品目录</span>
          <div class="filters">
            <el-input
              v-model="keyword"
              placeholder="搜索名称或编码"
              clearable
              style="width: 200px"
              @clear="loadList"
            />
            <el-switch v-model="includeDisabled" active-text="显示已停用" />
            <el-button type="primary" @click="openCreate">新增药品</el-button>
            <el-button :loading="loading" @click="loadList">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="list"
        :row-class-name="rowClassName"
        empty-text="暂无药品"
        stripe
      >
        <el-table-column prop="drugCode" label="编码" width="110" />
        <el-table-column prop="drugName" label="名称" min-width="140" />
        <el-table-column prop="drugFormat" label="规格" min-width="120" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column label="零售价" width="90">
          <template #default="{ row }">¥{{ row.retailPrice }}</template>
        </el-table-column>
        <el-table-column prop="stockQty" label="库存" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.disabled ? 'info' : 'success'" size="small">
              {{ row.disabled ? '已停用' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button
              :type="row.disabled ? 'success' : 'warning'"
              link
              @click="onToggleDisabled(row)"
            >
              {{ row.disabled ? '启用' : '停用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑药品' : '新增药品'"
      width="480px"
      @closed="resetForm"
    >
      <el-form label-width="88px">
        <el-form-item label="药品名称" required>
          <el-input v-model="form.drugName" placeholder="必填" />
        </el-form-item>
        <el-form-item label="零售价" required>
          <el-input-number v-model="form.retailPrice" :min="0.01" :precision="2" :step="1" />
        </el-form-item>
        <el-form-item label="库存" required>
          <el-input-number v-model="form.stockQty" :min="0" :step="1" />
        </el-form-item>
        <el-form-item>
          <el-button link type="primary" @click="showExtra = !showExtra">
            {{ showExtra ? '收起更多信息' : '展开更多信息（选填）' }}
          </el-button>
        </el-form-item>
        <template v-if="showExtra">
          <el-form-item label="规格">
            <el-input v-model="form.drugFormat" placeholder="如 0.25g×24粒" />
          </el-form-item>
          <el-form-item label="剂型">
            <el-input v-model="form.drugDosage" placeholder="如 胶囊" />
          </el-form-item>
          <el-form-item label="类型">
            <el-input v-model="form.drugType" placeholder="如 处方药" />
          </el-form-item>
          <el-form-item label="单位">
            <el-input v-model="form.unit" placeholder="如 盒" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.pharmacy-page {
  max-width: 1100px;
}

.page-head {
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.page-desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
}

.section-card {
  border-radius: 10px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.filters {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

:deep(.row-disabled) {
  opacity: 0.55;
}
</style>
