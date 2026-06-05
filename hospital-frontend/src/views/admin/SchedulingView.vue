<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchAdminSchedules, fetchDepartments } from '../../api/admin'

const loading = ref(false)
const deptFilter = ref(null)
const schedules = ref([])
const outpatientDepts = ref([])

onMounted(async () => {
  const deptRes = await fetchDepartments({ deptType: 1, pageSize: 20 })
  outpatientDepts.value = deptRes.data?.list ?? []
  await loadSchedules()
})

async function loadSchedules() {
  loading.value = true
  try {
    const res = await fetchAdminSchedules({
      deptId: deptFilter.value || undefined,
      pageSize: 100,
    })
    schedules.value = res.data?.list ?? []
  } catch (err) {
    ElMessage.error(err.message || '加载排班失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="sched-page">
    <div class="page-head">
      <h2 class="page-title">排班查看</h2>
      <p class="page-desc">
        未来 7 天门诊排班（Mock 只读）。各科室每个开诊半天均有普通号；专家号仅副高在固定时段出诊。
        P5 将接入 Timefold 排班求解与 CRUD。
      </p>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <span class="label">筛选科室</span>
        <el-select v-model="deptFilter" clearable placeholder="全部门诊科室" style="width: 160px" @change="loadSchedules">
          <el-option v-for="d in outpatientDepts" :key="d.id" :label="d.deptName" :value="d.id" />
        </el-select>
        <el-button :loading="loading" @click="loadSchedules">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="schedules" stripe max-height="520" empty-text="暂无排班">
        <el-table-column prop="workDate" label="日期" width="110" />
        <el-table-column prop="noonLabel" label="午别" width="72" />
        <el-table-column prop="timeRange" label="时段" width="110" />
        <el-table-column prop="deptId" label="科室" width="90">
          <template #default="{ row }">
            {{ outpatientDepts.find((d) => d.id === row.deptId)?.deptName }}
          </template>
        </el-table-column>
        <el-table-column prop="employeeName" label="医生" width="88" />
        <el-table-column prop="employeeTitle" label="职称" width="110" />
        <el-table-column label="号别" width="88">
          <template #default="{ row }">
            <el-tag size="small" :type="row.registLevelId === 2 ? 'warning' : 'info'">
              {{ row.registLevelName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="号源" width="110">
          <template #default="{ row }">余 {{ row.remainQuota }} / {{ row.totalQuota }}</template>
        </el-table-column>
        <el-table-column label="挂号费" width="80">
          <template #default="{ row }">¥{{ row.registFee }}</template>
        </el-table-column>
      </el-table>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="foot-tip"
        title="排班维护说明"
        description="编辑排班、发布号源、Timefold 智能排班建议等功能在 P5 由 lzr 实现；当前 Mock 数据用于挂号与字典联调。"
      />
    </el-card>
  </div>
</template>

<style scoped>
.sched-page {
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
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.toolbar .label {
  font-size: 13px;
  color: #64748b;
}

.foot-tip {
  margin-top: 16px;
}
</style>
