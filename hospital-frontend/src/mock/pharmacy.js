import { mockResult } from '../utils/mock'
import { dispensePrescription, getPharmacyQueue, returnPrescription } from './store'

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
