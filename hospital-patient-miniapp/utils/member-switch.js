const { readAccounts, switchAccount } = require('./auth')
const accountStore = require('./account-store')
const { fetchFamilyMembers } = require('../api/patient')

function showMemberSwitchSheet(options) {
  options = options || {}
  var onSwitched = options.onSwitched || function () {}

  return fetchFamilyMembers().then(function (res) {
    var family = (res && res.data && res.data.list) || []
    var saved = readAccounts()
    var sheetOptions = []
    var targets = []

    saved.forEach(function (a) {
      sheetOptions.push('★ ' + a.realName + '（本机已登录）')
      targets.push({ type: 'local', patientId: a.patientId })
    })

    family.forEach(function (m) {
      if (saved.some(function (a) { return a.patientId === m.memberPatientId })) return
      sheetOptions.push(m.realName + (m.isSelf ? '' : '（家属，切换登录）'))
      targets.push({ type: 'switch', patientId: m.memberPatientId })
    })

    if (!sheetOptions.length) {
      wx.showToast({ title: '暂无可切换账户', icon: 'none' })
      return
    }

    wx.showActionSheet({
      itemList: sheetOptions,
      success: function (tap) {
        var item = targets[tap.tapIndex]
        if (!item) return
        if (item.type === 'local') {
          accountStore.setCurrentAccount(item.patientId)
          onSwitched()
          return
        }
        wx.showLoading({ title: '切换中' })
        switchAccount(item.patientId).then(function () {
          wx.showToast({ title: '已切换', icon: 'success' })
          onSwitched()
        }).catch(function (err) {
          wx.showToast({ title: (err && err.message) || '切换失败', icon: 'none' })
        }).finally(function () {
          wx.hideLoading()
        })
      },
    })
  }).catch(function () {
    wx.showToast({ title: '加载就诊人失败', icon: 'none' })
  })
}

module.exports = {
  showMemberSwitchSheet,
}
