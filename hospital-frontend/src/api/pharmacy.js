import request from './request'
import { useMock } from '../utils/mock'
import {
  mockCreatePharmacyDrug,
  mockDisablePharmacyDrug,
  mockDispense,
  mockEnablePharmacyDrug,
  mockPharmacyDrugs,
  mockPharmacyPending,
  mockReturnDrug,
  mockUpdatePharmacyDrug,
} from '../mock/pharmacy'

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

export function fetchPharmacyDrugs(params) {
  if (useMock()) return mockPharmacyDrugs(params)
  return request.get('/pharmacy/drugs', { params })
}

export function createPharmacyDrug(body) {
  if (useMock()) return mockCreatePharmacyDrug(body)
  return request.post('/pharmacy/drugs', body)
}

export function updatePharmacyDrug(id, body) {
  if (useMock()) return mockUpdatePharmacyDrug(id, body)
  return request.put(`/pharmacy/drugs/${id}`, body)
}

export function disablePharmacyDrug(id) {
  if (useMock()) return mockDisablePharmacyDrug(id)
  return request.post(`/pharmacy/drugs/${id}/disable`)
}

export function enablePharmacyDrug(id) {
  if (useMock()) return mockEnablePharmacyDrug(id)
  return request.post(`/pharmacy/drugs/${id}/enable`)
}
