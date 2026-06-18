import { defineStore } from 'pinia'
import { staffLogin } from '../api/auth'
import { refreshStaffToken } from '../api/refreshToken'

const STORAGE_KEY = 'hospital_staff_auth'

let refreshInFlight = null

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
    isLabDoctor: (state) => state.user?.roles?.includes('LAB_DOCTOR'),
    isCheckDoctor: (state) => state.user?.roles?.includes('CHECK_DOCTOR'),
    isDisposalDoctor: (state) => state.user?.roles?.includes('DISPOSAL_DOCTOR'),
    isPharmacist: (state) =>
      state.user?.roles?.includes('PHARMACIST') || state.user?.roles?.includes('ADMIN'),
    isRegistrar: (state) =>
      state.user?.roles?.includes('REGISTRAR') || state.user?.roles?.includes('ADMIN'),
    isAdmin: (state) => state.user?.roles?.includes('ADMIN'),
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

    applyTokenPair(data) {
      this.accessToken = data.accessToken || ''
      if (data.refreshToken) {
        this.refreshToken = data.refreshToken
      }
      this.persist()
    },

    /** 用 refreshToken 换取新 accessToken（并发调用会合并为同一 Promise） */
    refreshAccessToken() {
      if (!this.refreshToken) {
        return Promise.reject(new Error('无 refreshToken'))
      }
      if (refreshInFlight) return refreshInFlight
      refreshInFlight = refreshStaffToken(this.refreshToken)
        .then((res) => {
          this.applyTokenPair(res.data)
          return this.accessToken
        })
        .finally(() => {
          refreshInFlight = null
        })
      return refreshInFlight
    },

    logout() {
      this.accessToken = ''
      this.refreshToken = ''
      this.user = null
      localStorage.removeItem(STORAGE_KEY)
    },
  },
})
