<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">
        <span class="logo-text">文旅管理后台</span>
      </div>
      <el-menu
        :default-active="route.path"
        router
        background-color="#001529"
        text-color="#a6adb4"
        active-text-color="#ffffff"
      >
        <template v-for="menu in menus" :key="menu.path">
          <el-sub-menu v-if="menu.children && menu.children.length" :index="menu.path">
            <template #title>
              <el-icon><component :is="menu.icon" /></el-icon>
              <span>{{ menu.title }}</span>
            </template>
            <el-menu-item v-for="child in menu.children" :key="child.path" :index="child.path">
              {{ child.title }}
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="menu.path">
            <el-icon><component :is="menu.icon" /></el-icon>
            <span>{{ menu.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-title">{{ route.meta.title || '' }}</div>
        <el-dropdown @command="handleCommand">
          <span class="user-info">
            <el-icon><User /></el-icon>
            <span class="username">{{ userStore.info?.nickname || userStore.info?.username }}</span>
            <el-tag v-if="userStore.info?.roleName" size="small" type="info" class="role-tag">
              {{ userStore.info.roleName }}
            </el-tag>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
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
import { User, ArrowDown, DataLine, ChatDotRound, UserFilled, Document, Picture, Location, Setting } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

interface MenuItem {
  path: string
  title: string
  icon: unknown
  perm?: string
  children?: MenuItem[]
}

const allMenus: MenuItem[] = [
  { path: '/dashboard', title: '数据看板', icon: DataLine, perm: 'dashboard:view' },
  { path: '/feedback', title: '意见反馈', icon: ChatDotRound, perm: 'feedback:view' },
  { path: '/user', title: '用户管理', icon: UserFilled, perm: 'user:view' },
  { path: '/journal', title: '游记管理', icon: Document, perm: 'journal:view' },
  {
    path: '/content',
    title: '内容管理',
    icon: Picture,
    perm: 'content:manage',
    children: [
      { path: '/scenic', title: '景点管理', perm: 'content:manage' },
      { path: '/activity', title: '活动管理', perm: 'content:manage' }
    ]
  },
  {
    path: '/home',
    title: '首页管理',
    icon: Location,
    perm: 'home:manage',
    children: [
      { path: '/banner', title: 'Banner管理', perm: 'home:manage' },
      { path: '/featured', title: '精选行程', perm: 'home:manage' },
      { path: '/city', title: '城市管理', perm: 'home:manage' }
    ]
  },
  {
    path: '/system',
    title: '系统管理',
    icon: Setting,
    perm: 'admin:manage',
    children: [
      { path: '/admin', title: '管理员', perm: 'admin:manage' },
      { path: '/role', title: '角色管理', perm: 'admin:manage' }
    ]
  }
]

const menus = computed(() =>
  allMenus.filter((m) => {
    if (m.children) {
      const kids = m.children.filter((c) => userStore.hasPerm(c.perm))
      return kids.length > 0 ? { ...m, children: kids } : false
    }
    return userStore.hasPerm(m.perm)
  })
)

function handleCommand(command: string) {
  if (command === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.layout {
  height: 100%;
}

.aside {
  background: #001529;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #002140;
}

.logo-text {
  color: #fff;
  font-size: 17px;
  font-weight: 600;
  letter-spacing: 1px;
}

.aside :deep(.el-menu) {
  border-right: none;
  flex: 1;
}

.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e8e8e8;
  padding: 0 20px;
}

.header-title {
  font-size: 16px;
  font-weight: 500;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #333;
  outline: none;
}

.username {
  font-size: 14px;
}

.role-tag {
  margin-left: 4px;
}

.main {
  background: #f0f2f5;
  padding: 16px;
  overflow-y: auto;
}
</style>
