<script setup>
/** P4 前为占位面板，保留 7:3 布局右侧位 */
</script>

<template>
  <div class="ai-panel">
    <div class="ai-header">
      <h3>AI 助理</h3>
      <el-tag size="small" type="info">P4 启用</el-tag>
    </div>
    <div class="ai-body">
      <el-empty description="Spring AI 流式助理将在 P4 接入">
        <p class="tip">当前阶段请专注左侧队列与病历流程</p>
      </el-empty>
    </div>
  </div>
</template>

<style scoped>
.ai-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.ai-header {
  padding: 16px;
  border-bottom: 1px solid #f1f5f9;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ai-header h3 {
  margin: 0;
  font-size: 16px;
  color: #334155;
}

.ai-body {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

.tip {
  margin-top: 8px;
  font-size: 12px;
  color: #94a3b8;
}
</style>
