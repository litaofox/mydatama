<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">
        <span class="logo-text">mydatama 数据中台</span>
      </div>
      <el-menu :default-active="activeMenu" router class="menu" background-color="#001529" text-color="#a6adb4"
        active-text-color="#ffffff">
        <el-menu-item index="/screen">
          <el-icon><Monitor /></el-icon>
          <span>可视化大屏</span>
        </el-menu-item>
        <el-sub-menu index="processing">
          <template #title>
            <el-icon><FolderOpened /></el-icon>
            <span>数据处理</span>
          </template>
          <el-menu-item index="/processing/upload">文件上传</el-menu-item>
          <el-menu-item index="/processing/files">文件列表</el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="governance">
          <template #title>
            <el-icon><Coin /></el-icon>
            <span>资产治理</span>
          </template>
          <el-menu-item index="/governance/assets">资产目录</el-menu-item>
          <el-menu-item index="/governance/heatmap">数据热力图</el-menu-item>
          <el-menu-item index="/governance/standards">数据标准</el-menu-item>
          <el-menu-item index="/governance/quality-rules">质量规则</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/dataset/list">
          <el-icon><Collection /></el-icon>
          <span>数据集</span>
        </el-menu-item>
        <el-menu-item index="/product/list">
          <el-icon><Goods /></el-icon>
          <span>数据产品</span>
        </el-menu-item>
        <el-sub-menu index="iam">
          <template #title>
            <el-icon><Lock /></el-icon>
            <span>权限管理</span>
          </template>
          <el-menu-item index="/iam/users">用户管理</el-menu-item>
          <el-menu-item index="/iam/roles">角色管理</el-menu-item>
          <el-menu-item index="/iam/policies">ABAC 策略</el-menu-item>
          <el-menu-item index="/iam/audit">审计日志</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/openapi-demo">
          <el-icon><Connection /></el-icon>
          <span>开放API演示</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header" height="56px">
        <div class="header-title">{{ route.meta.title || '' }}</div>
        <div class="header-right">
          <el-tag size="small" :type="secretLevelTagType(user?.secretLevel)" effect="dark" class="secret-tag">
            密级：{{ secretLevelText(user?.secretLevel) }}
          </el-tag>
          <el-dropdown @command="onCommand">
            <span class="user-info">
              <el-icon><User /></el-icon>
              {{ user?.realName || user?.username || '用户' }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ user?.username }}</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Monitor, FolderOpened, Coin, Collection, Goods, Lock, Connection, User, ArrowDown } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'
import { logout as apiLogout } from '@/api/auth'
import { secretLevelText, secretLevelTagType } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const user = computed(() => auth.user)

const activeMenu = computed(() => {
  const p = route.path
  if (p.startsWith('/processing/files')) return '/processing/files'
  if (p.startsWith('/governance/assets')) return '/governance/assets'
  if (p.startsWith('/governance/lineage')) return '/governance/assets'
  if (p.startsWith('/dataset')) return '/dataset/list'
  if (p.startsWith('/product')) return '/product/list'
  return p
})

async function onCommand(cmd: string) {
  if (cmd === 'logout') {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    try {
      await apiLogout()
    } catch {
      // ignore
    }
    auth.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout {
  height: 100%;
}
.aside {
  background-color: #001529;
  display: flex;
  flex-direction: column;
}
.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.logo-text {
  color: #fff;
  font-weight: 600;
  font-size: 15px;
}
.menu {
  border-right: none;
  flex: 1;
}
.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e4e7ed;
}
.header-title {
  font-size: 16px;
  font-weight: 600;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.secret-tag {
  border: none;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #303133;
}
.main {
  background-color: #f5f7fa;
  padding: 16px;
  overflow-y: auto;
}
</style>
