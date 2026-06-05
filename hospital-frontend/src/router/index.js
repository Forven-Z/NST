import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { hasRole, resolveHomeRoute } from '../utils/roles'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/doctor',
      component: () => import('../layouts/DoctorLayout.vue'),
      meta: { requiresAuth: true, role: 'OUTPATIENT_DOCTOR' },
      children: [
        { path: '', redirect: '/doctor/workspace' },
        {
          path: 'workspace',
          name: 'doctor-workspace',
          component: () => import('../views/doctor/WorkspaceView.vue'),
        },
      ],
    },
    {
      path: '/lis',
      component: () => import('../layouts/LisLayout.vue'),
      meta: { requiresAuth: true, role: 'LAB_DOCTOR' },
      children: [
        { path: '', redirect: '/lis/queue' },
        { path: 'queue', name: 'lis-queue', component: () => import('../views/lis/QueueView.vue') },
      ],
    },
    {
      path: '/pacs',
      component: () => import('../layouts/PacsLayout.vue'),
      meta: { requiresAuth: true, role: 'CHECK_DOCTOR' },
      children: [
        { path: '', redirect: '/pacs/queue' },
        { path: 'queue', name: 'pacs-queue', component: () => import('../views/pacs/QueueView.vue') },
      ],
    },
    {
      path: '/disposal',
      component: () => import('../layouts/DisposalLayout.vue'),
      meta: { requiresAuth: true, role: 'DISPOSAL_DOCTOR' },
      children: [
        { path: '', redirect: '/disposal/queue' },
        {
          path: 'queue',
          name: 'disposal-queue',
          component: () => import('../views/disposal/QueueView.vue'),
        },
      ],
    },
    {
      path: '/pharmacy',
      component: () => import('../layouts/PharmacyLayout.vue'),
      meta: { requiresAuth: true, role: 'PHARMACIST' },
      children: [
        { path: '', redirect: '/pharmacy/pending' },
        {
          path: 'pending',
          name: 'pharmacy-pending',
          component: () => import('../views/pharmacy/PendingView.vue'),
        },
      ],
    },
    {
      path: '/registrar',
      component: () => import('../layouts/RegistrarLayout.vue'),
      meta: { requiresAuth: true, role: 'REGISTRAR' },
      children: [
        { path: '', redirect: '/registrar/register' },
        {
          path: 'register',
          name: 'registrar-register',
          component: () => import('../views/registrar/RegisterView.vue'),
        },
        {
          path: 'charge',
          name: 'registrar-charge',
          component: () => import('../views/registrar/ChargeView.vue'),
        },
        {
          path: 'refund',
          name: 'registrar-refund',
          component: () => import('../views/registrar/RefundView.vue'),
        },
      ],
    },
    {
      path: '/admin',
      component: () => import('../layouts/AdminLayout.vue'),
      meta: { requiresAuth: true, role: 'ADMIN' },
      children: [
        { path: '', redirect: '/admin/dict' },
        {
          path: 'dict',
          name: 'admin-dict',
          component: () => import('../views/admin/DictView.vue'),
        },
        {
          path: 'scheduling',
          name: 'admin-scheduling',
          component: () => import('../views/admin/SchedulingView.vue'),
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  auth.restore()

  if (to.meta.public) {
    if (auth.isLoggedIn && to.name === 'login') {
      return resolveHomeRoute(auth.roles)
    }
    return true
  }

  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  const requiredRole = to.meta.role
  if (requiredRole && !hasRole(auth.roles, requiredRole)) {
    return resolveHomeRoute(auth.roles)
  }

  return true
})

export default router
