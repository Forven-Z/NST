import request from './request'

export function fetchPatientBills(medicalRecordNo, params) {
  return request.get(`/registrar/patients/${medicalRecordNo}/bills`, { params })
}

export function refundBill(data) {
  return request.post('/registrar/refunds', data)
}

export function cancelRegister(registerId, data) {
  return request.post(`/registrar/registers/${registerId}/cancel`, data)
}
