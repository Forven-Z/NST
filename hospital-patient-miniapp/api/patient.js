const { get, post, put } = require('../utils/request')

function fetchProfile() {
  return get('/patient/profile')
}

function updateProfile(data) {
  return put('/patient/profile', data)
}

function fetchSchedules(params) {
  return get('/patient/schedules', params)
}

function createRegister(data) {
  return post('/patient/registers', data)
}

function fetchPendingBills() {
  return get('/patient/bills')
}

function mockPayment(billIds) {
  return post('/patient/payments', { billIds })
}

function fetchMedicalRecord(registerId) {
  return get(`/patient/medical-records/${registerId}`)
}

module.exports = {
  fetchProfile,
  updateProfile,
  fetchSchedules,
  createRegister,
  fetchPendingBills,
  mockPayment,
  fetchMedicalRecord,
}
