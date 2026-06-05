<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchDepartments,
  fetchDrugs,
  fetchMedicalTechnologies,
  fetchRegistLevels,
} from '../../api/admin'

const loading = ref(false)
const activeTab = ref('departments')
const tableData = ref([])

const techTypeLabel = {
  CHECK: '检查',
  INSPECTION: '检验',
  DISPOSAL: '处置',
}

const deptTypeLabel = {
  1: '临床门诊',
  2: '医技科室',
  3: '药房',
  4: '行政',
}

const loaders = {
  departments: () => fetchDepartments({ page: 1, pageSize: 50 }),
  registLevels: () => fetchRegistLevels({ page: 1, pageSize: 50 }),
  drugs: () => fetchDrugs({ page: 1, pageSize: 50 }),
  technologies: () => fetchMedicalTechnologies({ page: 1, pageSize: 50 }),
}

onMounted(loadTab)

async function loadTab() {
  loading.value = true
  try {
    const res = await loaders[activeTab.value]()
    tableData.value = res.data?.list ?? []
  } catch (err) {
    ElMessage.error(err.message || '加载失败')
    tableData.value = []
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  loadTab()
}
</script>

<template>
  <div class="dict-page">
    <div class="page-head">
      <h2 class="page-title">基础字典</h2>
      <p class="page-desc">
        科室、号别、药品、医技项目等主数据由 management 模块维护；此处为只读查看。Mock 数据与 seed-dict.sql 对齐并扩展演示科室。
      </p>
    </div>

    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="科室" name="departments" />
        <el-tab-pane label="号别" name="registLevels" />
        <el-tab-pane label="药品" name="drugs" />
        <el-tab-pane label="医技项目" name="technologies" />
      </el-tabs>

      <el-table v-loading="loading" :data="tableData" empty-text="暂无数据" stripe>
        <el-table-column v-if="activeTab === 'departments'" prop="deptCode" label="编码" width="140" />
        <el-table-column v-if="activeTab === 'departments'" prop="deptName" label="科室名称" min-width="120" />
        <el-table-column v-if="activeTab === 'departments'" label="类型" width="110">
          <template #default="{ row }">{{ deptTypeLabel[row.deptType] || row.deptType }}</template>
        </el-table-column>

        <el-table-column v-if="activeTab === 'registLevels'" prop="levelName" label="号别" min-width="100" />
        <el-table-column v-if="activeTab === 'registLevels'" label="挂号费" width="100">
          <template #default="{ row }">¥{{ row.fee ?? row.registFee }}</template>
        </el-table-column>
        <el-table-column v-if="activeTab === 'registLevels'" label="说明" min-width="200">
          <template #default="{ row }">
            {{ row.levelCode === 'EXPERT' ? '副主任医师及以上，固定时段出诊' : '日常门诊，各时段均有' }}
          </template>
        </el-table-column>

        <el-table-column v-if="activeTab === 'drugs'" prop="drugCode" label="编码" width="120" />
        <el-table-column v-if="activeTab === 'drugs'" prop="drugName" label="药品" min-width="140" />
        <el-table-column v-if="activeTab === 'drugs'" prop="specification" label="规格" min-width="120" />
        <el-table-column v-if="activeTab === 'drugs'" label="零售价" width="90">
          <template #default="{ row }">¥{{ row.retailPrice }}</template>
        </el-table-column>
        <el-table-column v-if="activeTab === 'drugs'" prop="stockQty" label="库存" width="80" />

        <el-table-column v-if="activeTab === 'technologies'" prop="itemCode" label="编码" width="130" />
        <el-table-column v-if="activeTab === 'technologies'" prop="itemName" label="项目名称" min-width="120" />
        <el-table-column v-if="activeTab === 'technologies'" label="类型" width="90">
          <template #default="{ row }">{{ techTypeLabel[row.techType] || row.techType }}</template>
        </el-table-column>
        <el-table-column v-if="activeTab === 'technologies'" label="单价" width="90">
          <template #default="{ row }">¥{{ row.price }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.dict-page {
  max-width: 1000px;
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
</style>
