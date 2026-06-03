import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/login',
    },
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
        {
          path: '',
          redirect: '/doctor/workspace',
        },
        {
          path: 'workspace',
          name: 'doctor-workspace',
          component: () => import('../views/doctor/WorkspaceView.vue'),
        },
      ],
    },
    {
      path: '/pharmacy',
      component: () => import('../layouts/PharmacyLayout.vue'),
      meta: { requiresAuth: true, role: 'PHARMACIST' },
      children: [
        {
          path: '',
          redirect: '/pharmacy/pending',
        },
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
        {
          path: '',
          redirect: '/registrar/refund',
        },
        {
          path: 'refund',
          name: 'registrar-refund',
          component: () => import('../views/registrar/RefundView.vue'),
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
      return redirectByRole(auth)
    }
    return true
  }

  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  const requiredRole = to.meta.role
  if (requiredRole) {
    if (requiredRole === 'PHARMACIST' && !auth.isPharmacist) {
      return { name: 'login' }
    }
    if (requiredRole === 'REGISTRAR' && !auth.isRegistrar) {
      return { name: 'login' }
    }
    if (requiredRole === 'OUTPATIENT_DOCTOR' && !auth.isOutpatientDoctor) {
      return { name: 'login' }
    }
  }

  return true
})

function redirectByRole(auth) {
  if (auth.isOutpatientDoctor) {
    return { name: 'doctor-workspace' }
  }
  if (auth.isPharmacist) {
    return { name: 'pharmacy-pending' }
  }
  if (auth.isRegistrar) {
    return { name: 'registrar-refund' }
  }
  return { name: 'login' }
}

export default router
