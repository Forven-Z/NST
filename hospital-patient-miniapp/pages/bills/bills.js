const { fetchPendingBills, mockPayment } = require('../../api/patient')

Page({
  data: {
    loading: false,
    paying: false,
    bills: [],
    selectedIds: [],
    totalAmount: 0,
  },

  onShow() {
    this.loadBills()
  },

  onPullDownRefresh() {
    this.loadBills().finally(() => wx.stopPullDownRefresh())
  },

  async loadBills() {
    this.setData({ loading: true, selectedIds: [], totalAmount: 0 })
    try {
      const res = await fetchPendingBills()
      const list = (res.data?.list || []).map((b) => ({ ...b, checked: false }))
      this.setData({ bills: list, selectedIds: [], totalAmount: 0 })
    } catch (err) {
      wx.showToast({ title: err.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  bizTypeText(type) {
    const map = { REGISTER: '挂号费', INSPECTION: '检验费' }
    return map[type] || type || '费用'
  },

  syncBillSelection(selectedIds) {
    const idSet = new Set(selectedIds)
    const bills = this.data.bills.map((b) => ({
      ...b,
      checked: idSet.has(b.id),
    }))
    const totalAmount = bills
      .filter((b) => b.checked)
      .reduce((sum, b) => sum + Number(b.amount), 0)
    this.setData({
      bills,
      selectedIds,
      totalAmount: Math.round(totalAmount * 100) / 100,
    })
  },

  onToggleBill(e) {
    const id = Number(e.currentTarget.dataset.id)
    let selectedIds = [...this.data.selectedIds]
    const idx = selectedIds.indexOf(id)
    if (idx >= 0) {
      selectedIds.splice(idx, 1)
    } else {
      selectedIds.push(id)
    }
    this.syncBillSelection(selectedIds)
  },

  onSelectAll() {
    if (this.data.selectedIds.length === this.data.bills.length) {
      this.syncBillSelection([])
      return
    }
    this.syncBillSelection(this.data.bills.map((b) => b.id))
  },

  async onPay() {
    if (this.data.paying || this.data.selectedIds.length === 0) {
      wx.showToast({ title: '请选择账单', icon: 'none' })
      return
    }

    this.setData({ paying: true })
    try {
      await mockPayment(this.data.selectedIds)
      wx.showToast({ title: '支付成功', icon: 'success' })
      await this.loadBills()
    } catch (err) {
      wx.showToast({ title: err.message || '支付失败', icon: 'none' })
    } finally {
      this.setData({ paying: false })
    }
  },
})
