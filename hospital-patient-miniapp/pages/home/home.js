const { fetchProfile, fetchMyRegisters, fetchPendingBills, fetchFamilyMembers } = require('../../api/patient')
const { fetchRegisterOrders } = require('../../api/orders')
const { TABS, buildGridItems, findGridItem } = require('../../utils/home-services')
const { isLoggedIn, requireLogin, switchAccount, readAccounts } = require('../../utils/auth')
const accountStore = require('../../utils/account-store')
const tripCard = require('../../utils/trip-card')
const reportNav = require('../../utils/report-nav')

/** 切 Tab 回首页时短时间去重，避免 onShow 连续打满 Gateway */
var HOME_RELOAD_MS = 5000

Page({
  data: {
    loggedIn: false,
    currentAccountName: '—',
    currentMedicalRecordNo: '—',
    accountList: [],
    familyList: [],
    trip: null,
    tabs: TABS,
    activeTab: 'outpatient',
    gridItems: buildGridItems('outpatient'),
    gridColClass: 'service-grid--five',
  },

  onShow() {
    var loggedIn = isLoggedIn()
    this.setData({ loggedIn: loggedIn })
    if (loggedIn) {
      var now = Date.now()
      if (!this._lastLoadAt || now - this._lastLoadAt >= HOME_RELOAD_MS) {
        this._lastLoadAt = now
        this.loadData()
      }
    } else {
      this._lastLoadAt = 0
      this.setData({
        currentAccountName: '未登录',
        currentMedicalRecordNo: '登录后可切换就诊账户',
        accountList: [],
        familyList: [],
        trip: null,
      })
    }
  },

  loadData() {
    this._lastLoadAt = Date.now()
    var that = this
    Promise.all([
      this.loadProfile(),
      this.loadFamily(),
      this.loadTrip(),
    ]).then(function () {
      that.setData({ accountList: readAccounts() })
    }).catch(function () {})
  },

  loadProfile() {
    var that = this
    var acc = accountStore.getCurrentAccount()
    if (acc) {
      that.setData({
        currentAccountName: acc.realName,
        currentMedicalRecordNo: acc.medicalRecordNo || '—',
      })
    }
    return fetchProfile().then(function (res) {
      var p = (res && res.data) || {}
      that.setData({
        currentAccountName: p.realName || '患者',
        currentMedicalRecordNo: p.medicalRecordNo || '—',
      })
    })
  },

  loadFamily() {
    var that = this
    return fetchFamilyMembers().then(function (res) {
      var list = (res && res.data && res.data.list) || []
      that.setData({ familyList: list })
    })
  },

  loadTrip() {
    var that = this
    var acc = accountStore.getCurrentAccount()
    if (!acc || !acc.patientId) {
      that.setData({ trip: null })
      return Promise.resolve()
    }
    return fetchMyRegisters({}).then(function (res) {
      var list = (res && res.data && res.data.list) || []
      var reg = tripCard.pickActiveRegister(list)
      if (!reg) {
        that.setData({ trip: null })
        return
      }
      return fetchPendingBills({
        status: 0,
        registerId: reg.registerId,
      }).then(function (billRes) {
        var pending = (billRes && billRes.data && billRes.data.list) || []
        var ordersPromise = reg.visitState === 3
          ? fetchRegisterOrders(reg.registerId).then(function (o) {
            return ((o && o.data && o.data.list) || [])
          }).catch(function () { return [] })
          : Promise.resolve([])
        return ordersPromise.then(function (ordersList) {
          that.setData({ trip: tripCard.buildTripCard(reg, pending, ordersList) })
        })
      })
    }).catch(function () {
      that.setData({ trip: null })
    })
  },

  onGoLogin() {
    wx.navigateTo({ url: '/pages/login/login' })
  },

  onSwitchAccount() {
    if (!requireLogin({ mode: 'modal', message: '切换就诊账户请先登录' })) return
    var that = this
    var saved = readAccounts()
    var family = this.data.familyList
    var options = []
    var targets = []

    saved.forEach(function (a) {
      options.push('★ ' + a.realName + '（本机已登录）')
      targets.push({ type: 'local', patientId: a.patientId, data: a })
    })

    family.forEach(function (m) {
      if (saved.some(function (a) { return a.patientId === m.memberPatientId })) return
      options.push(m.realName + (m.isSelf ? '' : '（家属，切换登录）'))
      targets.push({ type: 'switch', patientId: m.memberPatientId, data: m })
    })

    options.push('＋ 添加其他病人账户')
    targets.push({ type: 'add' })

    wx.showActionSheet({
      itemList: options,
      success: function (res) {
        var item = targets[res.tapIndex]
        if (!item) return
        if (item.type === 'add') {
          wx.navigateTo({ url: '/pages/login/login?add=1' })
          return
        }
        if (item.type === 'local') {
          accountStore.setCurrentAccount(item.patientId)
          that.loadData()
          return
        }
        wx.showLoading({ title: '切换中' })
        switchAccount(item.patientId).then(function () {
          wx.showToast({ title: '已切换', icon: 'success' })
          that.loadData()
        }).catch(function (err) {
          wx.showToast({ title: err.message || '切换失败', icon: 'none' })
        }).finally(function () {
          wx.hideLoading()
        })
      },
    })
  },

  onTabChange(e) {
    var key = e.currentTarget.dataset.key
    var items = buildGridItems(key)
    this.setData({
      activeTab: key,
      gridItems: items,
      gridColClass: items.length === 5 ? 'service-grid--five' : (items.length <= 3 ? 'service-grid--few' : ''),
    })
  },

  onGridTap(e) {
    var id = e.currentTarget.dataset.id
    var item = findGridItem(this.data.activeTab, id)
    if (!item) return
    require('../../utils/service-nav').handleService(item)
  },

  onTripAction() {
    if (!requireLogin({ mode: 'modal', message: '请先登录' })) return
    var trip = this.data.trip
    if (!trip || !trip.actionUrl) return
    if (trip.actionType === 'queue' || trip.actionType === 'registers' || trip.actionType === 'orders' || trip.actionType === 'visit') {
      wx.navigateTo({ url: trip.actionUrl })
      return
    }
    if (trip.actionType === 'reports') {
      reportNav.openReportsTab(trip.reportTab || 'all')
      return
    }
    if (trip.actionType === 'bills') {
      wx.navigateTo({ url: trip.actionUrl })
    }
  },

  goRegister() {
    if (!requireLogin({
      mode: 'modal',
      message: '在线挂号请先登录',
      redirect: '/pages/register/register',
    })) return
    wx.navigateTo({ url: '/pages/register/register' })
  },
})
