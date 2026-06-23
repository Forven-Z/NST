<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createEmployee,
  deleteEmployee,
  fetchDepartments,
  fetchEmployees,
  updateEmployee,
} from '../../api/admin'
import { ROLE_TYPE_OPTIONS, roleTypeLabel } from '../../config/roleTypes'

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const departments = ref([])
const dialogVisible = ref(false)
const editingId = ref(null)

const filters = reactive({
  keyword: '',
  deptId: null,
  roleType: null,
})

const form = reactive({
  empNo: '',
  realName: '',
  gender: 1,
  deptId: null,
  title: '',
  roleType: 'OUTPATIENT_DOCTOR',
  phone: '',
  username: '',
  password: '123456',
})

onMounted(async () => {
  const res = await fetchDepartments({ pageSize: 100 })
  departments.value = res.data?.list ?? []
  await loadList()
})

watch(
  () => [filters.keyword, filters.deptId, filters.roleType],
  () => loadList(),
)

async function loadList() {
  loading.value = true
  try {
    const res = await fetchEmployees({
      keyword: filters.keyword || undefined,
      deptId: filters.deptId || undefined,
      roleType: filters.roleType || undefined,
      delmark: 0,
      pageSize: 200,
    })
    list.value = res.data?.list ?? []
  } catch (err) {
    ElMessage.error(err.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, {
    empNo: '',
    realName: '',
    gender: 1,
    deptId: departments.value[0]?.id ?? null,
    title: '',
    roleType: 'OUTPATIENT_DOCTOR',
    phone: '',
    username: '',
    password: '123456',
  })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.employeeId
  Object.assign(form, {
    empNo: row.empNo,
    realName: row.realName,
    gender: row.gender ?? 1,
    deptId: row.deptId,
    title: row.title || '',
    roleType: row.roleType,
    phone: row.phone || '',
    username: row.username || '',
    password: '',
  })
  dialogVisible.value = true
}

async function onSave() {
  if (!form.empNo.trim() || !form.realName.trim()) return ElMessage.warning('请填写工号与姓名')
  if (!form.deptId) return ElMessage.warning('请选择科室')
  if (!form.roleType) return ElMessage.warning('请选择岗位角色')
  if (!editingId.value && !form.username.trim()) return ElMessage.warning('请填写登录用户名')
  saving.value = true
  try {
    const payload = { ...form }
    if (editingId.value && !payload.password) delete payload.password
    if (editingId.value) {
      const res = await updateEmployee(editingId.value, payload)
      ElMessage.success(res.data?.message || '已更新')
    } else {
      const res = await createEmployee(payload)
      ElMessage.success(res.data?.message || '已建档并开通登录')
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
    await ElMessageBox.confirm(
      `停用「${row.realName}」后将无法登录，历史业务记录仍保留。`,
      '停用员工',
      { type: 'warning' },
    )
    await deleteEmployee(row.employeeId)
    ElMessage.success('员工已停用')
    await loadList()
  } catch (err) {
    if (err !== 'cancel' && err?.message) ElMessage.error(err.message)
  }
}

function formatGender(g) {
  if (g === 1) return '男'
  if (g === 2) return '女'
  return '—'
}

function onRoleTypeChange(roleType) {
  if (roleType === 'REGISTRAR' && (!form.title || form.title === '收费员' || form.title === '挂号员')) {
    form.title = '挂号收费员'
  }
  if (roleType === 'ADMIN' && !form.deptId) {
    const adminDept = departments.value.find((d) => d.deptCode === 'INFO_CENTER')
    if (adminDept) form.deptId = adminDept.id
  }
}
</script>

<template>
  <div class="page">
    <div class="page-head">
      <h2 class="page-title">员工管理</h2>
      <p class="page-desc">
        建档即开通 PC 登录（用户名 + 密码）。排班「新建」时从此列表按科室筛选人员，无需单独账号管理页。
      </p>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="姓名 / 工号 / 用户名"
          style="width: 200px"
        />
        <el-select v-model="filters.deptId" clearable placeholder="科室" style="width: 140px">
          <el-option v-for="d in departments" :key="d.id" :label="d.deptName" :value="d.id" />
        </el-select>
        <el-select v-model="filters.roleType" clearable placeholder="岗位" style="width: 140px">
          <el-option v-for="r in ROLE_TYPE_OPTIONS" :key="r.value" :label="r.label" :value="r.value" />
        </el-select>
        <el-button type="primary" @click="openCreate">新增员工</el-button>
        <el-button :loading="loading" @click="loadList">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="list" stripe empty-text="暂无员工">
        <el-table-column prop="empNo" label="工号" width="88" />
        <el-table-column prop="realName" label="姓名" width="96" />
        <el-table-column prop="deptName" label="科室" width="110" />
        <el-table-column prop="title" label="职称" width="110" />
        <el-table-column label="岗位" width="110">
          <template #default="{ row }">{{ roleTypeLabel(row.roleType) }}</template>
        </el-table-column>
        <el-table-column prop="username" label="登录名" width="120" />
        <el-table-column label="性别" width="64">
          <template #default="{ row }">{{ formatGender(row.gender) }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="手机" width="120" />
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
      :title="editingId ? '编辑员工' : '新增员工（建档即开通登录）'"
      width="520px"
      destroy-on-close
    >
      <el-alert
        v-if="!editingId"
        type="info"
        :closable="false"
        show-icon
        class="tip"
        title="保存后该员工即可用下方用户名登录对应角色菜单"
      />
      <el-form label-width="96px" class="form">
        <el-form-item label="工号" required>
          <el-input v-model="form.empNo" placeholder="院内唯一，如 E023" />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="科室" required>
          <el-select v-model="form.deptId" filterable style="width: 100%">
            <el-option v-for="d in departments" :key="d.id" :label="d.deptName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位角色" required>
          <el-select v-model="form.roleType" style="width: 100%" @change="onRoleTypeChange">
            <el-option v-for="r in ROLE_TYPE_OPTIONS" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="职称">
          <el-input
            v-model="form.title"
            :placeholder="form.roleType === 'REGISTRAR' ? '如 挂号收费员' : '如 主治医师'"
          />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio :value="1">男</el-radio>
            <el-radio :value="2">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="手机">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="登录用户名" required>
          <el-input v-model="form.username" :disabled="!!editingId" placeholder="如 doctor02" />
        </el-form-item>
        <el-form-item :label="editingId ? '重置密码' : '初始密码'">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="editingId ? '留空则不修改' : '默认 123456'"
          />
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

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.tip {
  margin-bottom: 12px;
}

.form {
  padding-top: 4px;
}
</style>
