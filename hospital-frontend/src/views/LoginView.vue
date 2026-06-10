<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { resolveHomeRoute } from '../utils/roles'
import { fetchAuthHealth } from '../api/auth'
import { useMock } from '../utils/mock'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const loading = ref(false)
const backendStatus = ref('checking') // checking | up | down
const mockMode = useMock()

const form = reactive({
  username: 'doctor01',
  password: '123456',
})

onMounted(checkBackend)

async function checkBackend() {
  if (mockMode) {
    backendStatus.value = 'mock'
    return
  }
  try {
    await fetchAuthHealth()
    backendStatus.value = 'up'
  } catch {
    backendStatus.value = 'down'
  }
}

async function onSubmit() {
  loading.value = true
  try {
    await auth.login({ ...form })
    ElMessage.success('登录成功')
    const redirect = route.query.redirect
    if (redirect) {
      await router.replace(String(redirect))
    } else {
      await router.replace(resolveHomeRoute(auth.roles))
    }
  } catch (err) {
    ElMessage.error(err.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <h1>智慧云脑诊疗平台</h1>
        <p>医护端登录 · Gateway :9000</p>
      </div>

      <el-alert
        v-if="backendStatus === 'down'"
        type="error"
        :closable="false"
        show-icon
        title="后端未就绪"
        description="Gateway :9000 无响应。联调登录需先启动：PostgreSQL → Nacos :8848 → auth :9101 → gateway :9000（详见 docs/RUNBOOK.md §四、§5.2）。若只想浏览界面，在 .env.development 设 VITE_USE_MOCK=true 后重启 npm run dev。"
        class="status-alert"
      />
      <el-alert
        v-else-if="backendStatus === 'mock'"
        type="warning"
        :closable="false"
        show-icon
        title="Mock 演示模式"
        description="全链路本地 Mock：挂号→收费→叫号→开单→检验/检查/药房/处置均可演示，无需启动后端。密码均为 123456。"
        class="status-alert"
      />
      <el-alert
        v-else-if="backendStatus === 'up'"
        type="success"
        :closable="false"
        show-icon
        title="Gateway 已连通"
        class="status-alert"
      />

      <el-form label-position="top" @submit.prevent="onSubmit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="doctor01" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="123456"
            show-password
            autocomplete="current-password"
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <el-button type="primary" class="submit-btn" :loading="loading" @click="onSubmit">
          登录
        </el-button>
      </el-form>

      <p class="hint">
        开发账号（密码 123456）：doctor01 · check01 · inspection01 · pharmacy01 · registrar01 · disposal01 · admin
      </p>
      <p class="hint sub">推荐演示路径：registrar01 挂号 → 收费 → doctor01 开单 → check01（检查）/ inspection01（检验）/ pharmacy01</p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e8f4fc 0%, #f5f7fa 50%, #eef2ff 100%);
}

.login-card {
  width: 400px;
  padding: 40px 36px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(15, 23, 42, 0.08);
}

.brand h1 {
  margin: 0 0 8px;
  font-size: 22px;
  color: #1e293b;
}

.brand p {
  margin: 0 0 28px;
  color: #64748b;
  font-size: 14px;
}

.status-alert {
  margin-bottom: 16px;
}

.submit-btn {
  width: 100%;
  margin-top: 8px;
}

.hint {
  margin: 20px 0 0;
  text-align: center;
  font-size: 12px;
  color: #94a3b8;
}

.hint.sub {
  margin-top: 8px;
}
</style>
