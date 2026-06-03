import request from './request'

export function fetchDoctorQueue(params) {
  return request.get('/doctor/queues', { params })
}

export function callPatient(registerId) {
  return request.post(`/doctor/call/${registerId}`)
}

export function fetchMedicalRecord(registerId) {
  return request.get(`/doctor/medical-records/${registerId}`)
}

export function saveMedicalRecord(registerId, data) {
  return request.put(`/doctor/medical-records/${registerId}`, data)
}

export function createInspectionOrder(data) {
  return request.post('/doctor/inspection-requests', data)
}

export function fetchInspectionResult(inspectionRequestId) {
  return request.get(`/doctor/inspection-requests/${inspectionRequestId}/result`)
}

export function createPrescription(data) {
  return request.post('/doctor/prescriptions', data)
}
