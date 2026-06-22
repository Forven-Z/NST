<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchFinanceDailySummary } from '../../api/admin'

const loading = ref(false)
const dateFrom = ref(new Date().toISOString().slice(0, 10))
const dateTo = ref(new Date().toISOString().slice(0, 10))
const summary = ref(null)

onMounted(loadSummary)

async function loadSummary() {
  loading.value = true
  try {
    const res = await fetchFinanceDailySummary({
      dateFrom: dateFrom.value,
      dateTo: dateTo.value,
    })
    summary.value = res.data ?? null
  } catch (err) {
    ElMessage.error(err.message || '加载失败')
    summary.value = null
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="finance-page">
    <div class="page-head">
      <div>
        <h2 class="page-title">财务汇总</h2>
        <p class="page-desc">全院窗口收费与退费汇总（按支付渠道分组），不含第三方支付对账。</p>
      </div>
    </div>

    <el-card shadow="never" class="panel-card">
      <el-form inline class="search-form" @submit.prevent="loadSummary">
        <el-form-item label="起始日期">
          <el-date-picker
            v-model="dateFrom"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="起始"
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker
            v-model="dateTo"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="结束"
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadSummary">查询</el-button>
        </el-form-item>
      </el-form>

      <div v-loading="loading">
        <template v-if="summary">
          <div class="summary-grid">
            <div class="summary-stat">
              <span class="label">收费笔数</span>
              <strong>{{ summary.paymentCount ?? 0 }}</strong>
            </div>
            <div class="summary-stat">
              <span class="label">收费合计</span>
              <strong class="fee">¥{{ summary.paymentTotal ?? 0 }}</strong>
            </div>
            <div class="summary-stat">
              <span class="label">退费笔数</span>
              <strong>{{ summary.refundCount ?? 0 }}</strong>
            </div>
            <div class="summary-stat">
              <span class="label">退费合计</span>
              <strong class="fee">¥{{ summary.refundTotal ?? 0 }}</strong>
            </div>
          </div>
          <div class="net-row">
            区间净收（{{ summary.dateFrom }} ~ {{ summary.dateTo }}）：
            <span class="fee-lg">¥{{ summary.netTotal ?? 0 }}</span>
          </div>

          <div class="channel-block">
            <h3>收费按渠道</h3>
            <el-table :data="summary.paymentsByChannel || []" stripe empty-text="暂无数据">
              <el-table-column prop="channelLabel" label="渠道" />
              <el-table-column prop="count" label="笔数" width="100" />
              <el-table-column label="金额" width="120" align="right">
                <template #default="{ row }">¥{{ row.totalAmount }}</template>
              </el-table-column>
            </el-table>
          </div>

          <div class="channel-block">
            <h3>退费按渠道</h3>
            <el-table :data="summary.refundsByChannel || []" stripe empty-text="暂无数据">
              <el-table-column prop="channelLabel" label="渠道" />
              <el-table-column prop="count" label="笔数" width="100" />
              <el-table-column label="金额" width="120" align="right">
                <template #default="{ row }">¥{{ row.totalAmount }}</template>
              </el-table-column>
            </el-table>
          </div>
        </template>
        <el-empty v-else-if="!loading" description="请选择日期范围查询" />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.finance-page {
  max-width: 960px;
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

.panel-card {
  border-radius: 10px;
  border: 1px solid #e2e8f0;
}

.search-form {
  margin-bottom: 16px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.summary-stat {
  padding: 14px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.summary-stat .label {
  display: block;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
}

.net-row {
  margin-bottom: 20px;
  font-size: 14px;
  color: #475569;
}

.fee {
  color: #ea580c;
  font-weight: 600;
}

.fee-lg {
  color: #ea580c;
  font-weight: 700;
  font-size: 20px;
  margin-left: 8px;
}

.channel-block {
  margin-bottom: 20px;
}

.channel-block h3 {
  margin: 0 0 10px;
  font-size: 15px;
  font-weight: 600;
}

@media (max-width: 900px) {
  .summary-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
