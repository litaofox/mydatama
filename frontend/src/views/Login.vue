<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-header">
        <h1>mydatama 数据中台</h1>
        <p class="subtitle">MVP 功能演示系统</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="onSubmit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" show-password :prefix-icon="Lock" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="login-btn" :loading="loading" @click="onSubmit">登 录</el-button>
        </el-form-item>
      </el-form>
      <el-divider class="divider">演示账号</el-divider>
      <div class="demo-accounts">
        <el-button size="small" round @click="fill('admin', 'Admin@123')">admin（管理员）</el-button>
        <el-button size="small" round @click="fill('operator', 'Operator@123')">operator（运营）</el-button>
        <el-button size="small" round @click="fill('viewer', 'Viewer@123')">viewer（只读）</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login } from '@/api/auth'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

function fill(username: string, password: string) {
  form.username = username
  form.password = password
}

async function onSubmit() {
  await formRef.value?.validate()
  loading.value = true
  try {
    const data = await login({ username: form.username, password: form.password })
    auth.setLogin(data)
    ElMessage.success(`欢迎，${data.user.realName || data.user.username}`)
    const redirect = (route.query.redirect as string) || '/screen'
    router.push(redirect)
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0b1c3f 0%, #123a7d 60%, #1c5bc4 100%);
}
.login-card {
  width: 400px;
  background: #fff;
  border-radius: 12px;
  padding: 40px 36px 28px;
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.25);
}
.login-header {
  text-align: center;
  margin-bottom: 28px;
}
.login-header h1 {
  margin: 0;
  font-size: 24px;
  color: #1f2d3d;
}
.subtitle {
  margin: 8px 0 0;
  color: #909399;
  font-size: 13px;
}
.login-btn {
  width: 100%;
}
.divider {
  margin: 20px 0 12px;
  font-size: 12px;
}
.demo-accounts {
  display: flex;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
