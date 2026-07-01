import { mockResult } from '../utils/mock'
import { MOCK_DRUGS, MOCK_MEDICAL_TECHNOLOGIES } from './dict'
import {
  callRegister,
  confirmAiDraft,
  confirmMedicalRecord,
  createCheckOrder,
  createDisposalOrder,
  createInspectionOrder,
  createPrescription,
  finishRegister,
  getCheckResult,
  getDisposalResult,
  getDoctorQueue,
  getInspectionResult,
  getMedicalRecord,
  getRegisterById,
  getRegisterOrders,
  getRegisterResults,
  getPatientVisits,
  getPatientVisitHub,
  resubmitPrescriptionMock,
  saveMedicalRecord,
  updatePrescriptionItems,
} from './store'

export function mockDoctorQueue(params) {
  return mockResult({ list: getDoctorQueue(params), page: 1, pageSize: 50 })
}

export function mockCallPatient(registerId) {
  const reg = callRegister(registerId)
  return mockResult({ registerId: reg.registerId, visitState: reg.visitState })
}

export function mockFinishVisit(registerId) {
  const reg = finishRegister(registerId)
  return mockResult({ registerId: reg.registerId, visitState: reg.visitState })
}

export function mockFetchMedicalRecord(registerId) {
  return mockResult(getMedicalRecord(registerId))
}

export function mockSaveMedicalRecord(registerId, data) {
  return mockResult(saveMedicalRecord(registerId, data))
}

export function mockConfirmMedicalRecord(registerId, data) {
  return mockResult(confirmMedicalRecord(registerId, data))
}

export function mockCreateInspectionOrder(data) {
  const row = createInspectionOrder(data)
  return mockResult({
    inspectionRequestId: row.inspectionRequestId,
    itemName: row.itemName,
    status: row.status,
    message: `已开立检验：${row.itemName}，请患者缴费后至检验科`,
  })
}

export function mockCreateCheckOrder(data) {
  const row = createCheckOrder(data)
  return mockResult({
    checkRequestId: row.checkRequestId,
    itemName: row.itemName,
    status: row.status,
    message: `已开立检查：${row.itemName}，请患者缴费后至放射科`,
  })
}

export function mockCreateDisposalOrder(data) {
  const row = createDisposalOrder(data)
  return mockResult({
    disposalRequestId: row.disposalRequestId,
    itemName: row.itemName,
    status: row.status,
    message: `已开立处置：${row.itemName}，请患者缴费后至处置科`,
  })
}

export function mockFetchInspectionResult(id) {
  return mockResult(getInspectionResult(id))
}

export function mockFetchCheckResult(id) {
  return mockResult(getCheckResult(id))
}

export function mockFetchDisposalResult(id) {
  return mockResult(getDisposalResult(id))
}

export function mockFetchRegisterOrders(registerId) {
  return mockResult(getRegisterOrders(registerId))
}

export function mockFetchPatientVisits(patientId, params) {
  return mockResult(getPatientVisits(patientId, params))
}

export function mockFetchPatientVisitHub(patientId, registerId) {
  return mockResult(getPatientVisitHub(patientId, registerId))
}

export function mockFetchPatientVisitOrderResult(patientId, kind, requestId) {
  void patientId
  if (kind === 'inspection') return mockFetchInspectionResult(requestId)
  if (kind === 'check') return mockFetchCheckResult(requestId)
  if (kind === 'disposal') return mockFetchDisposalResult(requestId)
  return Promise.reject(new Error('不支持的结果类型'))
}

export function mockFetchRegisterResults(registerId) {
  return mockResult(getRegisterResults(registerId))
}

export function mockCreatePrescription(data) {
  const rx = createPrescription(data)
  return mockResult({
    prescriptionId: rx.prescriptionId,
    totalAmount: rx.totalAmount,
    status: rx.status,
    message: '处方已开立，请患者缴费后至药房取药',
  })
}

export function mockUpdatePrescription(prescriptionId, data) {
  const rx = updatePrescriptionItems(prescriptionId, data.items)
  return mockResult({
    prescriptionId: rx.prescriptionId,
    totalAmount: rx.totalAmount,
    status: rx.status,
  })
}

export function mockResubmitPrescription(prescriptionId) {
  const rx = resubmitPrescriptionMock(prescriptionId)
  return mockResult({
    prescriptionId: rx.prescriptionId,
    totalAmount: rx.totalAmount,
    status: rx.status,
    billId: rx.billId,
    message: '处方已重新提交，请通知患者缴费',
  })
}

export function mockConfirmClinicalDraft(type, draftId, registerId, items) {
  const res = confirmAiDraft(type, registerId, items)
  return mockResult({
    stub: true,
    draftId,
    draftType: type,
    requests: res.requests,
    message: 'AI 草稿已确认提交，请患者至收费窗口缴费',
  })
}

export function mockDiagnosisSuggestForRegister(registerId, recordOverride) {
  const reg = getRegisterById(registerId)
  const saved = getMedicalRecord(registerId)
  const record = recordOverride?.readme ? recordOverride : saved
  const readme = record.readme || reg?.patientName || ''
  const isHeadache = /头痛|头疼|头晕/.test(readme)
  const isFever = /发热|发烧|咳嗽/.test(readme)

  if (isHeadache) {
    return mockResult({
      stub: true,
      registerId,
      suggestions: [
        '主诉头痛：建议完善头颅 CT 排除颅内占位或出血',
        '建议血常规排查感染或血液系统异常',
        '若伴喷射性呕吐或意识改变，需优先排除颅内高压',
      ],
      needCheck: true,
      needInspection: true,
      needDisposal: false,
      reason: '结合主诉的辅助检查建议，请医生确认后开单',
    })
  }
  if (isFever) {
    return mockResult({
      stub: true,
      registerId,
      suggestions: ['建议血常规 + CRP', '必要时胸部 X 线排查肺炎'],
      needCheck: true,
      needInspection: true,
      needDisposal: false,
      reason: '初诊感染筛查建议，请医生确认后开单',
    })
  }
  return mockResult({
    stub: true,
    registerId,
    suggestions: ['初诊建议完善基础检验（血常规）', '根据体格检查再决定是否影像检查'],
    needCheck: false,
    needInspection: true,
    needDisposal: false,
    reason: '通用初诊建议；请结合病史与查体确认',
  })
}

export function mockDoctorMedicalTechnologies(params) {
  let list = [...MOCK_MEDICAL_TECHNOLOGIES]
  if (params?.techType) {
    list = list.filter((t) => t.techType === params.techType)
  }
  return mockResult({ list, page: 1, pageSize: params?.pageSize ?? 50 })
}

export function mockDoctorDrugs() {
  return mockResult({ list: MOCK_DRUGS, page: 1, pageSize: 50 })
}
