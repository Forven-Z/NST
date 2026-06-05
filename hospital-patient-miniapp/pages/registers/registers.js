const { fetchMyRegisters, cancelRegister } = require('../../api/patient')
const { visitStateLabel, canCancel, canViewQueue } = require('../../utils/visit-state')
const patientContext = require('../../utils/patient-context')
const billUtil = require('../../utils/bill-util')

Page({
  data: {
    loading: false,
    list: [],
    filter: 'all',
  },

  onShow() {
    this.loadList()
  },

  onPullDownRefresh() {
    this.loadList().finally(() => wx.stopPullDownRefresh())
  },

  onFilter(e) {
    this.setData({ filter: e.currentTarget.dataset.filter })
    this.loadList()
  },

  async loadList() {
    this.setData({ loading: true })
    try {
      const params = {}
      if (this.data.filter !== 'all') params.visitState = Number(this.data.filter)
      const active = patientContext.getActiveMember()
      if (active.memberPatientId) params.patientId = active.memberPatientId
      const res = await fetchMyRegisters(params)
      const list = (res.data?.list || []).map((r) => ({
        ...r,
        stateLabel: visitStateLabel(r.visitState),
        canCancel: canCancel(r.visitState),
        canQueue: canViewQueue(r.visitState),
      }))
      this.setData({ list })
    } catch (err) {
      wx.showToast({ title: err.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  goPay(e) {
    var id = e && e.currentTarget && e.currentTarget.dataset.id
    var url = billUtil.buildBillsUrl({
      tab: 'pending',
      scope: 'outpatient',
      registerId: id || '',
    })
    wx.navigateTo({ url: url })
  },

  goQueue(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: `/pages/queue/queue?registerId=${id}` })
  },

  goRecord(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({ url: `/pages/medical-record/medical-record?registerId=${id}` })
  },

  async onCancel(e) {
    const id = e.currentTarget.dataset.id
    const ok = await new Promise((resolve) => {
      wx.showModal({
        title: '退号',
        content: '确定取消该挂号？已支付将原路退回（演示环境为模拟）',
        success: (res) => resolve(res.confirm),
      })
    })
    if (!ok) return
    try {
      await cancelRegister(id, '患者主动退号')
      wx.showToast({ title: '退号成功', icon: 'success' })
      this.loadList()
    } catch (err) {
      wx.showToast({ title: err.message || '退号失败', icon: 'none' })
    }
  },
})
