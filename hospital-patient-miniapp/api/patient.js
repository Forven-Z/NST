const { get, post, put } = require('../utils/request')
const { useMock, store } = require('../mock/index')

function fetchProfile() {
  if (useMock()) return store.getProfile()
  return get('/patient/profile')
}

function updateProfile(data) {
  if (useMock()) return store.updateProfile(data)
  return put('/patient/profile', data)
}

function fetchDepartments() {
  if (useMock()) return store.listDepartments()
  return get('/patient/departments')
}

function fetchSchedules(params) {
  if (useMock()) return store.listSchedules(params)
  return get('/patient/schedules', params)
}

function fetchFamilyMembers() {
  if (useMock()) return store.listFamily()
  return get('/patient/family-members')
}

function addFamilyMember(data) {
  if (useMock()) return store.addFamily(data)
  return post('/patient/family-members', data)
}

function createRegister(data) {
  if (useMock()) return store.createRegister(data)
  return post('/patient/registers', data)
}

function fetchMyRegisters(params) {
  if (useMock()) return store.listRegisters(params)
  return get('/patient/registers', params)
}

function fetchRegisterDetail(registerId) {
  if (useMock()) return store.getRegister(registerId)
  return get(`/patient/registers/${registerId}`)
}

function fetchQueueStatus(registerId) {
  if (useMock()) return store.queueStatus(registerId)
  return get(`/patient/registers/${registerId}/queue-status`)
}

function fetchBills(params) {
  if (useMock()) return store.listBills(params)
  return get('/patient/bills', params)
}

function fetchPendingBills(params) {
  return fetchBills(Object.assign({}, params || {}, { status: 0 }))
}

function fetchPayments(params) {
  if (useMock()) return store.listPayments(params)
  return get('/patient/payments', params)
}

function fetchRefunds(params) {
  if (useMock()) return store.listRefunds(params)
  return get('/patient/refunds', params)
}

function mockPayment(billIds) {
  if (useMock()) return store.mockPay(billIds)
  return post('/patient/payments', { billIds })
}

function cancelRegister(registerId, reason) {
  if (useMock()) return store.cancelRegister(registerId)
  return post(`/patient/registers/${registerId}/cancel`, { reason })
}

function fetchMedicalRecord(registerId) {
  if (useMock()) return store.getMedicalRecord(registerId)
  return get(`/patient/medical-records/${registerId}`)
}

function fetchReports(params) {
  if (useMock()) return store.listReports(params)
  return get('/patient/reports', params)
}

function fetchReportDetail(type, requestId) {
  if (useMock()) return store.getReportDetail(type, requestId)
  return get('/patient/reports/' + type + '/' + requestId)
}

function triageChat(data) {
  if (useMock()) return store.triageChat(data)
  return post('/ai/triage/chat', data)
}

module.exports = {
  fetchProfile,
  updateProfile,
  fetchDepartments,
  fetchSchedules,
  fetchFamilyMembers,
  addFamilyMember,
  createRegister,
  fetchMyRegisters,
  fetchRegisterDetail,
  fetchQueueStatus,
  fetchBills,
  fetchPendingBills,
  fetchPayments,
  fetchRefunds,
  mockPayment,
  cancelRegister,
  fetchMedicalRecord,
  fetchReports,
  fetchReportDetail,
  triageChat,
}
