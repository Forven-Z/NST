const { clearSession, readAccounts, removeAccount, switchAccount } = require('../../utils/auth')
const accountStore = require('../../utils/account-store')
const { fetchProfile } = require('../../api/patient')
const { openReportsTab } = require('../../utils/report-nav')

const MENUS = [
  { id: 'cards', name: '家属管理', url: '/pages/cards/cards' },
  { id: 'registers', name: '挂号记录', url: '/pages/registers/registers' },
  { id: 'pending', name: '待缴费用', url: '/pages/bills/bills?tab=pending' },
  { id: 'paid', name: '缴费记录', url: '/pages/bills/bills?tab=paid' },
  { id: 'refunds', name: '退款记录', url: '/pages/refunds/refunds' },
  { id: 'reports', name: '报告查询', url: '/pages/reports/reports' },
  { id: 'record', name: '就诊记录', url: '/pages/medical-record/medical-record' },
  { id: 'profile', name: '个人档案', url: '/pages/profile/profile' },
]

function maskPhone(phone) {
  if (!phone || phone.length < 7) return '未绑定手机'
  return phone.slice(0, 3) + '******' + phone.slice(-3)
}

Page({
  data: {
    loggedIn: false,
    userName: '未登录',
    phoneMasked: '登录后查看档案',
    medicalRecordNo: '—',
    avatarLetter: '访',
    accounts: [],
    menus: MENUS,
  },

  onShow() {
    const loggedIn = !!accountStore.getCurrentAccount()
    this.setData({
      loggedIn: loggedIn,
      accounts: readAccounts(),
    })
    if (loggedIn) this.loadProfile()
    else {
      this.setData({
        userName: '未登录',
        phoneMasked: '登录病人账户后可切换就诊人',
        medicalRecordNo: '—',
        avatarLetter: '访',
      })
    }
  },

  loadProfile() {
    fetchProfile().then((res) => {
      const p = (res && res.data) || {}
      this.setData({
        userName: p.realName || '就诊人',
        phoneMasked: maskPhone(p.phone),
        medicalRecordNo: p.medicalRecordNo || '—',
        avatarLetter: (p.realName || '患').charAt(0),
      })
    }).catch((err) => {
      wx.showToast({ title: (err && err.message) || '加载失败', icon: 'none' })
    })
  },

  onGoLogin() {
    wx.navigateTo({ url: '/pages/login/login?redirect=' + encodeURIComponent('/pages/mine/mine') })
  },

  onGoAddAccount() {
    wx.navigateTo({ url: '/pages/login/login?add=1' })
  },

  onSwitchAccount(e) {
    const id = Number(e.currentTarget.dataset.id)
    const acc = accountStore.getCurrentAccount()
    if (acc && acc.patientId === id) return
    wx.showLoading({ title: '切换中' })
    switchAccount(id).then(() => {
      wx.showToast({ title: '已切换', icon: 'success' })
      this.onShow()
    }).catch((err) => {
      wx.showToast({ title: err.message || '切换失败', icon: 'none' })
    }).finally(() => wx.hideLoading())
  },

  onRemoveAccount(e) {
    const id = Number(e.currentTarget.dataset.id)
    wx.showModal({
      title: '移除账户',
      content: '仅从本机移除，不影响医院档案',
      success: (res) => {
        if (res.confirm) {
          removeAccount(id)
          this.onShow()
        }
      },
    })
  },

  onMenuTap(e) {
    const url = e.currentTarget.dataset.url
    if (!url) return
    if (!accountStore.getCurrentAccount()) {
      wx.navigateTo({ url: '/pages/login/login?redirect=' + encodeURIComponent(url) })
      return
    }
    if (url.indexOf('/pages/reports/reports') === 0) {
      var match = url.match(/[?&]type=([^&]+)/)
      openReportsTab(match ? match[1] : 'all')
      return
    }
    wx.navigateTo({ url: url })
  },

  onLogout() {
    wx.showModal({
      title: '退出全部账户',
      content: '确定清除本机所有已登录的病人账户？',
      success(res) {
        if (res.confirm) {
          clearSession()
          wx.switchTab({ url: '/pages/home/home' })
        }
      },
    })
  },
})
