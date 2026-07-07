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
    <div class="login-bg" aria-hidden="true" />
    <div class="login-overlay" aria-hidden="true" />

    <div class="login-panel">
      <div class="login-card">
        <div class="brand">
          <h1>智慧云脑诊疗平台</h1>
          <p>医护端登录</p>
        </div>

        <el-alert
          v-if="backendStatus === 'down'"
          type="error"
          :closable="false"
          show-icon
          title="后端未就绪"
          description="无法连接服务端。请先启动数据库、Nacos、认证服务与网关（详见 docs/RUNBOOK.md）。若只想浏览界面，可在 .env.development 开启本地演示模式后重启 npm run dev。"
          class="status-alert"
        />
        <el-alert
          v-else-if="backendStatus === 'mock'"
          type="warning"
          :closable="false"
          show-icon
          title="本地演示模式"
          description="全链路本地演示：挂号→收费→叫号→开单→检验/检查/药房/处置均可操作，无需启动后端。密码均为 123456。"
          class="status-alert"
        />
        <el-alert
          v-else-if="backendStatus === 'up'"
          type="success"
          :closable="false"
          show-icon
          title="服务端已连通"
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

        <p v-if="mockMode" class="hint">
          开发账号（密码 123456）：doctor01～doctor06 · lab01 · lab02 · check01～check03 · pharmacy01 · registrar01 · disposal01 · admin
        </p>
        <p v-if="mockMode" class="hint sub">
          推荐演示：registrar01 挂号 → doctor 接诊开 CT → check01～03 影像队列 · lab01/lab02 检验 · disposal01 处置
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  --phi: 61.8%;
  position: relative;
  min-height: 100vh;
  overflow: hidden;
}

.login-bg {
  position: absolute;
  inset: 0;
  background: url('/login-bg.jpg') center / cover no-repeat;
}

.login-overlay {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.12);
  pointer-events: none;
}

.login-panel {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
}

.login-card {
  pointer-events: auto;
  position: absolute;
  top: 50%;
  left: calc(var(--phi) + (100% - var(--phi)) / 2);
  transform: translate(-50%, -50%);
  width: min(400px, calc(100vw - 48px));
  padding: 40px 36px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(255, 255, 255, 0.55);
  border-radius: 16px;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.18);
  backdrop-filter: blur(10px);
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

@media (max-width: 768px) {
  .login-card {
    left: 50%;
    width: min(400px, calc(100vw - 40px));
  }
}
</style>
