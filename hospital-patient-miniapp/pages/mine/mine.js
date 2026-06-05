const { fetchProfile } = require('../../api/patient')
const { getAccessToken, clearSession } = require('../../utils/auth')
const billUtil = require('../../utils/bill-util')

const MENUS = [
  { id: 'cards', name: '就诊人管理', url: '/pages/cards/cards' },
  { id: 'registers', name: '挂号记录', url: '/pages/registers/registers' },
  { id: 'pending', name: '待缴费用', url: billUtil.buildBillsUrl({ tab: 'pending', scope: 'all' }) },
  { id: 'paid', name: '缴费记录', url: billUtil.buildBillsUrl({ tab: 'paid' }) },
  { id: 'reports', name: '报告查询', url: '/pages/reports/reports' },
  { id: 'record', name: '电子病历', url: '/pages/medical-record/medical-record' },
  { id: 'profile', name: '个人档案', url: '/pages/profile/profile' },
]

function maskPhone(phone) {
  if (!phone || phone.length < 7) return '未绑定手机'
  return phone.slice(0, 3) + '******' + phone.slice(-3)
}

function avatarLetter(name) {
  if (!name) return '患'
  return name.charAt(0)
}

Page({
  data: {
    userName: '微信用户',
    phoneMasked: '未绑定手机',
    medicalRecordNo: '—',
    avatarLetter: '患',
    menus: MENUS,
  },

  onShow() {
    if (!getAccessToken()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    this.loadProfile()
  },

  loadProfile() {
    var that = this
    fetchProfile().then(function (res) {
      var p = (res && res.data) || {}
      that.setData({
        userName: p.realName || '微信用户',
        phoneMasked: maskPhone(p.phone),
        medicalRecordNo: p.medicalRecordNo || '—',
        avatarLetter: avatarLetter(p.realName),
      })
    }).catch(function (err) {
      wx.showToast({ title: (err && err.message) || '加载失败', icon: 'none' })
    })
  },

  onMenuTap(e) {
    var url = e.currentTarget.dataset.url
    if (url) wx.navigateTo({ url: url })
  },

  onLogout() {
    wx.showModal({
      title: '退出登录',
      content: '确定退出当前账号？',
      success: function (res) {
        if (res.confirm) {
          clearSession()
          wx.reLaunch({ url: '/pages/login/login' })
        }
      },
    })
  },
})
