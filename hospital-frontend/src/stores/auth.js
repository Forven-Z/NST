import { defineStore } from 'pinia'
import { staffLogin } from '../api/auth'

const STORAGE_KEY = 'hospital_staff_auth'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: '',
    refreshToken: '',
    user: null,
  }),

  getters: {
    isLoggedIn: (state) => !!state.accessToken,
    roles: (state) => state.user?.roles ?? [],
    isOutpatientDoctor: (state) => state.user?.roles?.includes('OUTPATIENT_DOCTOR'),
    isPharmacist: (state) =>
      state.user?.roles?.includes('PHARMACIST') || state.user?.roles?.includes('ADMIN'),
    isRegistrar: (state) =>
      state.user?.roles?.includes('REGISTRAR') || state.user?.roles?.includes('ADMIN'),
  },

  actions: {
    restore() {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (!raw) return
      try {
        const data = JSON.parse(raw)
        this.accessToken = data.accessToken || ''
        this.refreshToken = data.refreshToken || ''
        this.user = data.user || null
      } catch {
        this.logout()
      }
    },

    persist() {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({
          accessToken: this.accessToken,
          refreshToken: this.refreshToken,
          user: this.user,
        }),
      )
    },

    async login(form) {
      const res = await staffLogin(form)
      const data = res.data
      this.accessToken = data.accessToken
      this.refreshToken = data.refreshToken || ''
      this.user = {
        userId: data.userId,
        employeeId: data.employeeId,
        realName: data.realName,
        roles: data.roles,
        deptId: data.deptId,
        deptName: data.deptName,
      }
      this.persist()
      return data
    },

    logout() {
      this.accessToken = ''
      this.refreshToken = ''
      this.user = null
      localStorage.removeItem(STORAGE_KEY)
    },
  },
})
