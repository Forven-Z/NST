import request from './request'
import { useMock } from '../utils/mock'
import { mockDispense, mockPharmacyPending, mockReturnDrug } from '../mock/pharmacy'

export function fetchPendingPrescriptions(params) {
  if (useMock()) return mockPharmacyPending(params)
  return request.get('/pharmacy/pending', { params })
}

export function dispensePrescription(prescriptionId) {
  if (useMock()) return mockDispense(prescriptionId)
  return request.post(`/pharmacy/prescriptions/${prescriptionId}/dispense`)
}

export function returnDrug(prescriptionId) {
  if (useMock()) return mockReturnDrug(prescriptionId)
  return request.post(`/pharmacy/prescriptions/${prescriptionId}/return-drug`)
}
