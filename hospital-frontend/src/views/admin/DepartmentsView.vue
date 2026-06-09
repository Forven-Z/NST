<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createDepartment,
  deleteDepartment,
  fetchDepartments,
  updateDepartment,
} from '../../api/admin'
import { DEPT_TYPE_OPTIONS } from '../../config/roleTypes'

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)

const form = reactive({
  deptCode: '',
  deptName: '',
  deptType: 1,
  sortNo: 10,
})

onMounted(loadList)

async function loadList() {
  loading.value = true
  try {
    const res = await fetchDepartments({ pageSize: 100 })
    list.value = res.data?.list ?? []
  } catch (err) {
    ElMessage.error(err.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { deptCode: '', deptName: '', deptType: 1, sortNo: list.value.length + 1 })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    deptCode: row.deptCode,
    deptName: row.deptName,
    deptType: row.deptType,
    sortNo: row.sortNo ?? 10,
  })
  dialogVisible.value = true
}

async function onSave() {
  if (!form.deptName.trim()) return ElMessage.warning('请填写科室名称')
  if (!editingId.value && !form.deptCode.trim()) return ElMessage.warning('请填写科室编码')
  saving.value = true
  try {
    if (editingId.value) {
      await updateDepartment(editingId.value, form)
      ElMessage.success('科室已更新')
    } else {
      await createDepartment(form)
      ElMessage.success('科室已创建')
    }
    dialogVisible.value = false
    await loadList()
  } catch (err) {
    ElMessage.error(err.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onDisable(row) {
  try {
    await ElMessageBox.confirm(`确定停用科室「${row.deptName}」？需先无在职员工。`, '停用科室', {
      type: 'warning',
    })
    await deleteDepartment(row.id)
    ElMessage.success('科室已停用')
    await loadList()
  } catch (err) {
    if (err !== 'cancel' && err?.message) ElMessage.error(err.message)
  }
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <h2 class="page-title">科室管理</h2>
      <p class="page-desc">维护科室主数据；员工建档、排班选人、挂号展示均依赖科室列表。</p>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <el-button type="primary" @click="openCreate">新增科室</el-button>
        <el-button :loading="loading" @click="loadList">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="list" stripe empty-text="暂无科室">
        <el-table-column prop="deptCode" label="编码" width="140" />
        <el-table-column prop="deptName" label="名称" min-width="120" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            {{ DEPT_TYPE_OPTIONS.find((d) => d.value === row.deptType)?.label || row.deptType }}
          </template>
        </el-table-column>
        <el-table-column prop="sortNo" label="排序" width="72" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="onDisable(row)">停用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑科室' : '新增科室'"
      width="480px"
      destroy-on-close
    >
      <el-form label-width="88px">
        <el-form-item label="科室编码" required>
          <el-input v-model="form.deptCode" :disabled="!!editingId" placeholder="如 INTERNAL" />
        </el-form-item>
        <el-form-item label="科室名称" required>
          <el-input v-model="form.deptName" placeholder="如 内科" />
        </el-form-item>
        <el-form-item label="科室类型" required>
          <el-select v-model="form.deptType" style="width: 100%">
            <el-option v-for="d in DEPT_TYPE_OPTIONS" :key="d.value" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="form.sortNo" :min="1" :max="999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  max-width: 900px;
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
}

.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
</style>
