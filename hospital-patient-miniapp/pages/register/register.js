const { fetchSchedules, createRegister } = require('../../api/patient')

Page({
  data: {
    loading: false,
    submitting: false,
    deptId: 1,
    schedules: [],
  },

  onShow() {
    this.loadSchedules()
  },

  onPullDownRefresh() {
    this.loadSchedules().finally(() => wx.stopPullDownRefresh())
  },

  async loadSchedules() {
    this.setData({ loading: true })
    try {
      const res = await fetchSchedules({ deptId: this.data.deptId })
      this.setData({ schedules: res.data?.list || [] })
    } catch (err) {
      wx.showToast({ title: err.message || '加载排班失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  async onRegister(e) {
    const index = e.currentTarget.dataset.index
    const sched = this.data.schedules[index]
    if (!sched || this.data.submitting) return

    const confirmed = await new Promise((resolve) => {
      wx.showModal({
        title: '确认挂号',
        content: `${sched.deptName} · ${sched.doctorName} · ${sched.levelName}\n挂号费 ¥${sched.registFee}`,
        success(res) {
          resolve(res.confirm)
        },
      })
    })
    if (!confirmed) return

    this.setData({ submitting: true })
    try {
      const res = await createRegister({
        schedulingId: sched.schedulingId,
        deptId: sched.deptId,
        employeeId: sched.employeeId,
        registLevelId: sched.registLevelId,
        settleCategoryId: 1,
      })
      wx.showModal({
        title: '挂号成功',
        content: `挂号单已创建，请前往「待缴费用」支付。\n账单号：${res.data.billNo || res.data.billId}`,
        showCancel: false,
        success() {
          wx.navigateTo({ url: '/pages/bills/bills' })
        },
      })
    } catch (err) {
      wx.showToast({ title: err.message || '挂号失败', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },
})
