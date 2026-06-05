const MEMBER_ID_KEY = 'activeMemberPatientId'
const MEMBER_NAME_KEY = 'activeMemberName'
const MEMBER_MRN_KEY = 'activeMemberMrn'

function getOwnerPatientId() {
  var app = getApp()
  return app.globalData.ownerPatientId || wx.getStorageSync('patientId') || null
}

function getActiveMember() {
  var id = wx.getStorageSync(MEMBER_ID_KEY)
  var name = wx.getStorageSync(MEMBER_NAME_KEY)
  var mrn = wx.getStorageSync(MEMBER_MRN_KEY)
  if (!id) {
    var ownerId = getOwnerPatientId()
    return {
      memberPatientId: ownerId,
      realName: name || '',
      medicalRecordNo: mrn || wx.getStorageSync('medicalRecordNo') || '',
      isSelf: true,
    }
  }
  return {
    memberPatientId: id,
    realName: name || '就诊人',
    medicalRecordNo: mrn || '—',
    isSelf: id === getOwnerPatientId(),
  }
}

function setActiveMember(member) {
  if (!member || !member.memberPatientId) return
  wx.setStorageSync(MEMBER_ID_KEY, member.memberPatientId)
  wx.setStorageSync(MEMBER_NAME_KEY, member.realName || '')
  wx.setStorageSync(MEMBER_MRN_KEY, member.medicalRecordNo || '')
  var app = getApp()
  if (app.globalData) {
    app.globalData.activeMemberPatientId = member.memberPatientId
  }
}

function clearActiveMember() {
  wx.removeStorageSync(MEMBER_ID_KEY)
  wx.removeStorageSync(MEMBER_NAME_KEY)
  wx.removeStorageSync(MEMBER_MRN_KEY)
}

function setOwnerFromLogin(data) {
  var app = getApp()
  var ownerId = data.patientId || null
  if (app.globalData) app.globalData.ownerPatientId = ownerId
  if (ownerId && !wx.getStorageSync(MEMBER_ID_KEY)) {
    setActiveMember({
      memberPatientId: ownerId,
      realName: data.realName || '微信用户',
      medicalRecordNo: data.medicalRecordNo || '',
      isSelf: true,
    })
  }
}

module.exports = {
  getOwnerPatientId,
  getActiveMember,
  setActiveMember,
  clearActiveMember,
  setOwnerFromLogin,
}
