import { mockResult } from '../utils/mock'
import {
  createPharmacyDrug,
  getPharmacyDrugList,
  setPharmacyDrugDisabled,
  updatePharmacyDrug,
} from './dict'
import { dispensePrescription, getPharmacyQueue, getPrescriptionDetail, rejectPrescription, returnPrescription } from './store'

export function mockPharmacyPending(params) {
  const list = getPharmacyQueue(params?.status ?? 20)
  return mockResult({ list, page: 1, pageSize: 50 })
}

export function mockDispense(prescriptionId) {
  const rx = dispensePrescription(prescriptionId)
  return mockResult({
    prescriptionId: rx.prescriptionId,
    status: rx.status,
    message: '发药完成，请核对患者身份与药品',
  })
}

export function mockReturnDrug(prescriptionId) {
  returnPrescription(prescriptionId)
  return mockResult({
    prescriptionId,
    message: '退药成功，请通知患者至收费窗口办理退费',
  })
}

export function mockPrescriptionDetail(prescriptionId) {
  return mockResult(getPrescriptionDetail(prescriptionId))
}

export function mockRejectPrescription(prescriptionId, data) {
  const rx = rejectPrescription(prescriptionId, data?.reason)
  return mockResult({
    prescriptionId: rx.prescriptionId,
    status: rx.status,
    message: '已拒绝发药并退费，处方已退回开方医生',
  })
}

export function mockPharmacyDrugs(params) {
  const list = getPharmacyDrugList(params?.keyword, params?.includeDisabled)
  return mockResult({ list, page: params?.page ?? 1, pageSize: params?.pageSize ?? 20 })
}

export function mockCreatePharmacyDrug(body) {
  return mockResult(createPharmacyDrug(body))
}

export function mockUpdatePharmacyDrug(id, body) {
  return mockResult(updatePharmacyDrug(id, body))
}

export function mockDisablePharmacyDrug(id) {
  return mockResult(setPharmacyDrugDisabled(id, true))
}

export function mockEnablePharmacyDrug(id) {
  return mockResult(setPharmacyDrugDisabled(id, false))
}
