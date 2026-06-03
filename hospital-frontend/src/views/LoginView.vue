<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const loading = ref(false)
const form = reactive({
  username: 'doctor01',
  password: '123456',
})

async function onSubmit() {
  loading.value = true
  try {
    await auth.login({ ...form })
    ElMessage.success('登录成功')
    const redirect = route.query.redirect
    if (redirect) {
      await router.replace(String(redirect))
    } else if (auth.isOutpatientDoctor) {
      await router.replace({ name: 'doctor-workspace' })
    } else if (auth.isPharmacist) {
      await router.replace({ name: 'pharmacy-pending' })
    } else if (auth.isRegistrar) {
      await router.replace({ name: 'registrar-refund' })
    } else {
      await router.replace({ name: 'login' })
      ElMessage.warning('当前账号无可用工作台')
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

      <p class="hint">开发账号：doctor01 / pharmacy01 / registrar01，密码 123456</p>
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
</style>
