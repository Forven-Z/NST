const { fetchProfile, fetchMyRegisters, fetchPendingBills } = require('../../api/patient')
const { fetchFamilyMembers } = require('../../api/patient')
const { TABS, buildGridItems, findGridItem } = require('../../utils/home-services')
const { getAccessToken } = require('../../utils/auth')
const patientContext = require('../../utils/patient-context')
const tripCard = require('../../utils/trip-card')

Page({
  data: {
    ownerName: '患者',
    ownerMedicalRecordNo: '—',
    activeMemberName: '本人',
    memberList: [],
    trip: null,
    tabs: TABS,
    activeTab: 'outpatient',
    gridItems: buildGridItems('outpatient'),
  },

  onShow() {
    if (!getAccessToken()) {
      wx.reLaunch({ url: '/pages/login/login' })
      return
    }
    this.loadData()
  },

  loadData() {
    var that = this
    Promise.all([
      this.loadProfile(),
      this.loadMembers(),
      this.loadTrip(),
    ]).catch(function () {})
  },

  loadProfile() {
    var that = this
    return fetchProfile().then(function (res) {
      var p = (res && res.data) || {}
      that.setData({
        ownerName: p.realName || '患者',
        ownerMedicalRecordNo: p.medicalRecordNo || '—',
      })
    })
  },

  loadMembers() {
    var that = this
    return fetchFamilyMembers().then(function (res) {
      var list = (res && res.data && res.data.list) || []
      that.setData({ memberList: list })
      var active = patientContext.getActiveMember()
      var found = list.find(function (m) {
        return m.memberPatientId === active.memberPatientId
      })
      if (found) {
        that.setData({
          activeMemberName: found.realName + (found.isSelf ? '（本人）' : ''),
        })
      } else if (list.length) {
        patientContext.setActiveMember(list[0])
        that.setData({
          activeMemberName: list[0].realName + (list[0].isSelf ? '（本人）' : ''),
        })
      }
    })
  },

  loadTrip() {
    var that = this
    var active = patientContext.getActiveMember()
    var patientId = active.memberPatientId
    if (!patientId) {
      that.setData({ trip: null })
      return Promise.resolve()
    }
    return fetchMyRegisters({ patientId: patientId }).then(function (res) {
      var list = (res && res.data && res.data.list) || []
      var reg = tripCard.pickActiveRegister(list)
      if (!reg) {
        that.setData({ trip: null })
        return
      }
      return fetchPendingBills({
        patientId: patientId,
        status: 0,
        registerId: reg.registerId,
      }).then(function (billRes) {
        var pending = (billRes && billRes.data && billRes.data.list) || []
        var card = tripCard.buildTripCard(reg, pending)
        that.setData({ trip: card })
      })
    }).catch(function () {
      that.setData({ trip: null })
    })
  },

  onSwitchMember() {
    var list = this.data.memberList
    if (!list.length) {
      wx.showToast({ title: '暂无就诊人', icon: 'none' })
      return
    }
    var names = list.map(function (m) {
      return m.realName + (m.isSelf ? '（本人）' : '')
    })
    var that = this
    wx.showActionSheet({
      itemList: names,
      success: function (res) {
        var member = list[res.tapIndex]
        patientContext.setActiveMember(member)
        that.setData({
          activeMemberName: member.realName + (member.isSelf ? '（本人）' : ''),
        })
        that.loadTrip()
      },
    })
  },

  onTabChange(e) {
    var key = e.currentTarget.dataset.key
    this.setData({
      activeTab: key,
      gridItems: buildGridItems(key),
    })
  },

  onGridTap(e) {
    var id = e.currentTarget.dataset.id
    var item = findGridItem(this.data.activeTab, id)
    if (!item) return
    require('../../utils/service-nav').handleService(item)
  },

  onTripAction() {
    var trip = this.data.trip
    if (!trip || !trip.actionUrl) return
    if (trip.actionType === 'queue' || trip.actionType === 'registers' || trip.actionType === 'reports') {
      wx.navigateTo({ url: trip.actionUrl })
      return
    }
    if (trip.actionType === 'bills') {
      wx.navigateTo({ url: trip.actionUrl })
    }
  },

  goRegister() {
    wx.navigateTo({ url: '/pages/register/register' })
  },
})
