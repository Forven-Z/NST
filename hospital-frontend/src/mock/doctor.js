import { mockResult } from '../utils/mock'
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
  saveMedicalRecord,
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

export function mockCreatePrescription(data) {
  const rx = createPrescription(data)
  return mockResult({
    prescriptionId: rx.prescriptionId,
    totalAmount: rx.totalAmount,
    status: rx.status,
    message: '处方已开立，请患者缴费后至药房取药',
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
      reason: '【Mock·头痛路径】结合主诉的辅助检查建议，请医生确认后开单',
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
      reason: '【Mock·发热路径】初诊感染筛查建议',
    })
  }
  return mockResult({
    stub: true,
    registerId,
    suggestions: ['初诊建议完善基础检验（血常规）', '根据体格检查再决定是否影像检查'],
    needCheck: false,
    needInspection: true,
    needDisposal: false,
    reason: '【Mock】通用初诊建议；请结合病史与查体',
  })
}
