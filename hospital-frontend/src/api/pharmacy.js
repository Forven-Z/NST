import request from './request'

export function fetchPendingPrescriptions(params) {
  return request.get('/pharmacy/pending', { params })
}

export function dispensePrescription(prescriptionId) {
  return request.post(`/pharmacy/prescriptions/${prescriptionId}/dispense`)
}

export function returnDrug(prescriptionId) {
  return request.post(`/pharmacy/prescriptions/${prescriptionId}/return-drug`)
}
