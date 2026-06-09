/** @deprecated 请使用 account-store；JWT 即当前就诊账户 */
const accountStore = require('./account-store')

module.exports = {
  getOwnerPatientId: accountStore.getCurrentPatientId,
  getActiveMember: accountStore.getActiveMember,
  setActiveMember: function () {
    // QQ 模式下切换请用 auth.switchAccount
  },
  clearActiveMember: function () {},
  setOwnerFromLogin: function (data) {
    accountStore.upsertAccount({
      patientId: data.patientId,
      accessToken: wx.getStorageSync('accessToken'),
      medicalRecordNo: data.medicalRecordNo,
      realName: data.realName,
    })
  },
}
