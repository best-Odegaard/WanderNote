import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/login/index.vue'),
      meta: { public: true, title: '登录' }
    },
    {
      path: '/',
      component: () => import('@/layouts/AdminLayout.vue'),
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/dashboard/index.vue'),
          meta: { title: '数据看板', perm: 'dashboard:view' }
        },
        {
          path: 'feedback',
          name: 'feedback',
          component: () => import('@/views/feedback/index.vue'),
          meta: { title: '意见反馈', perm: 'feedback:view' }
        },
        {
          path: 'user',
          name: 'user',
          component: () => import('@/views/user/index.vue'),
          meta: { title: '用户管理', perm: 'user:view' }
        },
        {
          path: 'journal',
          name: 'journal',
          component: () => import('@/views/journal/index.vue'),
          meta: { title: '游记管理', perm: 'journal:view' }
        },
        {
          path: 'scenic',
          name: 'scenic',
          component: () => import('@/views/scenic/index.vue'),
          meta: { title: '景点管理', perm: 'content:manage' }
        },
        {
          path: 'activity',
          name: 'activity',
          component: () => import('@/views/activity/index.vue'),
          meta: { title: '活动管理', perm: 'content:manage' }
        },
        {
          path: 'banner',
          name: 'banner',
          component: () => import('@/views/banner/index.vue'),
          meta: { title: 'Banner管理', perm: 'home:manage' }
        },
        {
          path: 'featured',
          name: 'featured',
          component: () => import('@/views/featured/index.vue'),
          meta: { title: '精选行程', perm: 'home:manage' }
        },
        {
          path: 'city',
          name: 'city',
          component: () => import('@/views/city/index.vue'),
          meta: { title: '城市管理', perm: 'home:manage' }
        },
        {
          path: 'admin',
          name: 'admin',
          component: () => import('@/views/admin/index.vue'),
          meta: { title: '管理员', perm: 'admin:manage' }
        },
        {
          path: 'role',
          name: 'role',
          component: () => import('@/views/role/index.vue'),
          meta: { title: '角色管理', perm: 'admin:manage' }
        }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.meta.public) {
    return true
  }
  if (!userStore.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  const perm = to.meta.perm as string | undefined
  if (perm && !userStore.hasPerm(perm)) {
    return { path: '/dashboard' }
  }
  return true
})

export default router
