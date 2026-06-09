/**
 * QQ 式多病人账户：本机保存多个已登录账户，切换 = 换 JWT。
 */
const ACCOUNTS_KEY = 'patientAccounts'
const CURRENT_ID_KEY = 'currentAccountPatientId'

function readAccounts() {
  var raw = wx.getStorageSync(ACCOUNTS_KEY)
  if (!raw || !Array.isArray(raw)) return []
  return raw.filter(function (a) { return a && a.patientId && a.accessToken })
}

function writeAccounts(list) {
  wx.setStorageSync(ACCOUNTS_KEY, list)
}

function getCurrentPatientId() {
  var id = wx.getStorageSync(CURRENT_ID_KEY)
  if (id) return id
  var list = readAccounts()
  return list.length ? list[0].patientId : null
}

function getCurrentAccount() {
  var id = getCurrentPatientId()
  if (!id) return null
  var list = readAccounts()
  return list.find(function (a) { return a.patientId === id }) || null
}

function upsertAccount(session) {
  if (!session || !session.patientId || !session.accessToken) return
  var list = readAccounts()
  var idx = list.findIndex(function (a) { return a.patientId === session.patientId })
  var row = {
    patientId: session.patientId,
    accessToken: session.accessToken,
    medicalRecordNo: session.medicalRecordNo || '',
    realName: session.realName || '就诊人',
  }
  if (idx >= 0) {
    list[idx] = Object.assign({}, list[idx], row)
  } else {
    list.push(row)
  }
  writeAccounts(list)
  setCurrentAccount(session.patientId)
}

function setCurrentAccount(patientId) {
  wx.setStorageSync(CURRENT_ID_KEY, patientId)
  var acc = readAccounts().find(function (a) { return a.patientId === patientId })
  if (acc) {
    wx.setStorageSync('accessToken', acc.accessToken)
    wx.setStorageSync('patientId', acc.patientId)
    wx.setStorageSync('medicalRecordNo', acc.medicalRecordNo || '')
  }
  var app = getApp()
  if (app && app.globalData) {
    app.globalData.currentPatientId = patientId
    app.globalData.medicalRecordNo = acc ? acc.medicalRecordNo : ''
  }
}

function removeAccount(patientId) {
  var list = readAccounts().filter(function (a) { return a.patientId !== patientId })
  writeAccounts(list)
  if (getCurrentPatientId() === patientId) {
    if (list.length) {
      setCurrentAccount(list[0].patientId)
    } else {
      clearAllAccounts()
    }
  }
}

function clearAllAccounts() {
  wx.removeStorageSync(ACCOUNTS_KEY)
  wx.removeStorageSync(CURRENT_ID_KEY)
  wx.removeStorageSync('accessToken')
  wx.removeStorageSync('patientId')
  wx.removeStorageSync('medicalRecordNo')
  var app = getApp()
  if (app && app.globalData) {
    app.globalData.currentPatientId = null
    app.globalData.medicalRecordNo = ''
  }
}

/** 与旧 patient-context 兼容：当前 JWT 即当前就诊人 */
function getActiveMember() {
  var acc = getCurrentAccount()
  if (!acc) {
    return {
      memberPatientId: null,
      realName: '',
      medicalRecordNo: '',
      isSelf: true,
    }
  }
  return {
    memberPatientId: acc.patientId,
    realName: acc.realName,
    medicalRecordNo: acc.medicalRecordNo,
    isSelf: true,
  }
}

module.exports = {
  readAccounts,
  getCurrentAccount,
  getCurrentPatientId,
  upsertAccount,
  setCurrentAccount,
  removeAccount,
  clearAllAccounts,
  getActiveMember,
}
