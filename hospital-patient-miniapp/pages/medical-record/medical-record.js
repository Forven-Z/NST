const { fetchMedicalRecord } = require('../../api/patient')

Page({
  data: {
    registerId: '',
    loading: false,
    record: null,
  },

  onRegisterInput(e) {
    this.setData({ registerId: e.detail.value })
  },

  async onQuery() {
    const registerId = this.data.registerId.trim()
    if (!registerId) {
      wx.showToast({ title: '请输入挂号单号', icon: 'none' })
      return
    }

    this.setData({ loading: true, record: null })
    try {
      const res = await fetchMedicalRecord(Number(registerId))
      this.setData({ record: res.data || null })
    } catch (err) {
      wx.showToast({ title: err.message || '查询失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },
})
