const {
  fetchDepartments,
  fetchFamilyMembers,
  fetchSchedules,
  createRegister,
} = require('../../api/patient')
const { nextDays } = require('../../utils/date')
const { getAccessToken } = require('../../utils/auth')
const patientContext = require('../../utils/patient-context')

Page({
  data: {
    step: 1,
    loading: false,
    submitting: false,
    departments: [],
    dateOptions: [],
    deptId: null,
    workDate: '',
    noonType: 1,
    registLevelId: null,
    members: [],
    memberPatientId: null,
    schedules: [],
    levelOptions: [
      { id: null, label: '全部' },
      { id: 1, label: '普通号' },
      { id: 2, label: '专家号' },
    ],
  },

  onLoad(options) {
    if (options.deptId) {
      this._presetDeptId = Number(options.deptId)
      this._presetStep = 2
    }
  },

  onShow() {
    if (!getAccessToken()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    this.init()
  },

  async init() {
    const dateOptions = nextDays(7)
    this.setData({ dateOptions, workDate: dateOptions[0]?.workDate || '' })
    await Promise.all([this.loadDepartments(), this.loadMembers()])
  },

  async loadDepartments() {
    try {
      const res = await fetchDepartments()
      const list = res.data?.list || []
      this.setData({
        departments: list,
        deptId: this._presetDeptId || list[0]?.id || 1,
        step: this._presetStep || this.data.step,
      })
      if (this._presetStep === 2) {
        this._presetStep = null
        this.loadSchedules()
      }
    } catch (err) {
      wx.showToast({ title: err.message || '加载科室失败', icon: 'none' })
    }
  },

  async loadMembers() {
    try {
      const res = await fetchFamilyMembers()
      const list = res.data?.list || []
      const active = patientContext.getActiveMember()
      const match = list.find((m) => m.memberPatientId === active.memberPatientId)
      const self = match || list.find((m) => m.isSelf) || list[0]
      this.setData({
        members: list,
        memberPatientId: self?.memberPatientId || null,
      })
    } catch (err) {
      /* noop */
    }
  },

  onSelectDept(e) {
    this.setData({ deptId: Number(e.currentTarget.dataset.id), step: 2 })
    this.loadSchedules()
  },

  onSelectDate(e) {
    this.setData({ workDate: e.currentTarget.dataset.date })
    this.loadSchedules()
  },

  onSelectNoon(e) {
    this.setData({ noonType: Number(e.currentTarget.dataset.noon) })
    this.loadSchedules()
  },

  onSelectLevel(e) {
    this.setData({ registLevelId: e.currentTarget.dataset.id === 'null' ? null : Number(e.currentTarget.dataset.id) })
    this.loadSchedules()
  },

  onSelectMember(e) {
    this.setData({ memberPatientId: Number(e.currentTarget.dataset.id) })
  },

  async loadSchedules() {
    const { deptId, workDate, noonType, registLevelId } = this.data
    if (!deptId || !workDate) return
    this.setData({ loading: true })
    try {
      const params = { deptId, workDate, noonType }
      if (registLevelId) params.registLevelId = registLevelId
      const res = await fetchSchedules(params)
      this.setData({ schedules: res.data?.list || [], step: 3 })
    } catch (err) {
      wx.showToast({ title: err.message || '加载号源失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  backStep() {
    const step = this.data.step
    if (step > 1) this.setData({ step: step - 1 })
  },

  async onRegister(e) {
    const index = e.currentTarget.dataset.index
    const sched = this.data.schedules[index]
    if (!sched || this.data.submitting) return
    const member = this.data.members.find((m) => m.memberPatientId === this.data.memberPatientId)
    const name = member?.realName || '本人'
    const confirmed = await new Promise((resolve) => {
      wx.showModal({
        title: '确认挂号',
        content: `就诊人：${name}\n${sched.deptName} · ${sched.doctorName}\n${sched.workDate} ${sched.noonLabel} · ${sched.levelName}\n挂号费 ¥${sched.registFee}`,
        success: (res) => resolve(res.confirm),
      })
    })
    if (!confirmed) return

    this.setData({ submitting: true })
    try {
      const body = {
        schedulingId: sched.schedulingId,
        deptId: sched.deptId,
        employeeId: sched.employeeId,
        registLevelId: sched.registLevelId,
        settleCategoryId: 1,
      }
      if (this.data.memberPatientId) body.memberPatientId = this.data.memberPatientId
      const res = await createRegister(body)
      wx.showModal({
        title: '挂号成功',
        content: res.data?.message || '请前往待缴费用支付',
        showCancel: false,
        success: () => wx.navigateTo({ url: '/pages/bills/bills' }),
      })
    } catch (err) {
      wx.showToast({ title: err.message || '挂号失败', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },
})
