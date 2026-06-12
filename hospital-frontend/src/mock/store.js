/**
 * Mock 统一业务状态：挂号 → 收费 → 叫号 → 开单 → 医技/药房
 * 各模块 mock 共享此内存状态，演示完整门诊链路。
 */
import { mockAiReportText, mockInstrumentData } from './ai-reports'
import {
  consumeScheduleQuota,
  getDeptById,
  getDoctorById,
  getDrugById,
  getMedicalTechById,
} from './dict'

function buildPublishedResultText({ instrumentData = '', aiReportText = '', doctorReportText = '' }) {
  const parts = []
  if (instrumentData) parts.push(instrumentData)
  if (aiReportText) parts.push(aiReportText)
  if (doctorReportText) parts.push(`【医师意见】\n${doctorReportText}`)
  return parts.join('\n\n')
}

function initResultFields(row, techType) {
  row.instrumentData = ''
  row.aiReportText = ''
  row.doctorReportText = ''
  row.aiReportStatus = 'PENDING'
  row.techType = techType
}

function ensureInstrumentData(row, techType) {
  if (!row.instrumentData) {
    row.instrumentData = mockInstrumentData(techType, row.itemName)
  }
  return row.instrumentData
}

let nextPatientId = 100
let nextRegisterId = 30000
let nextBillId = 81000
let nextInspectionId = 61000
let nextCheckId = 62000
let nextDisposalId = 63000
let nextPrescriptionId = 64000
let nextDraftId = 8000

const state = {
  registers: [],
  bills: [],
  medicalRecords: {},
  inspectionRequests: [],
  checkRequests: [],
  disposalRequests: [],
  prescriptions: [],
}

function nowIso() {
  return new Date().toISOString()
}

function createBill({ medicalRecordNo, patientId, registerId, bizType, bizId, itemName, amount, status = 0 }) {
  nextBillId += 1
  const bill = {
    id: nextBillId,
    billId: nextBillId,
    medicalRecordNo,
    patientId,
    registerId,
    bizType,
    bizId,
    itemName,
    billTitle: itemName,
    amount: Number(amount),
    status,
    statusText: status === 0 ? '待支付' : status === 1 ? '已支付' : '已退款',
    createTime: nowIso(),
  }
  state.bills.push(bill)
  return bill
}

function seedDemoPatients() {
  if (state.registers.length) return

  // ① 已挂号、待叫号（初诊常见：先挂普通号）
  nextPatientId += 1
  const p1 = nextPatientId
  nextRegisterId += 1
  const r1 = nextRegisterId
  state.registers.push({
    registerId: r1,
    patientId: p1,
    medicalRecordNo: 'MR202606040001',
    patientName: '王小明',
    gender: 1,
    age: 28,
    phone: '13800138001',
    deptId: 1,
    deptName: '内科',
    employeeId: 1,
    doctorName: '张医生',
    registLevelId: 1,
    registLevelName: '普通号',
    visitState: 1,
    registTime: nowIso(),
    workDate: new Date().toISOString().slice(0, 10),
    noonLabel: '上午',
    triageLevel: 'NORMAL',
    triageNote: 'AI 分诊：常见病初诊，分配普通门诊',
    assignedByAi: true,
  })
  createBill({
    medicalRecordNo: 'MR202606040001',
    patientId: p1,
    registerId: r1,
    bizType: 'REGIST',
    bizId: r1,
    itemName: '普通号 · 内科',
    amount: 20,
    status: 1,
  })

  // ② 接诊中（专家号复诊场景）
  nextPatientId += 1
  const p2 = nextPatientId
  nextRegisterId += 1
  const r2 = nextRegisterId
  state.registers.push({
    registerId: r2,
    patientId: p2,
    medicalRecordNo: 'MR202606040002',
    patientName: '李小红',
    gender: 2,
    age: 45,
    phone: '13900139002',
    deptId: 1,
    deptName: '内科',
    employeeId: 11,
    doctorName: '王教授',
    registLevelId: 2,
    registLevelName: '专家号',
    visitState: 2,
    registTime: nowIso(),
    workDate: new Date().toISOString().slice(0, 10),
    noonLabel: '上午',
    triageLevel: 'URGENT',
    triageNote: 'AI 分诊：反复头痛，优先接诊',
    assignedByAi: true,
  })
  createBill({
    medicalRecordNo: 'MR202606040002',
    patientId: p2,
    registerId: r2,
    bizType: 'REGIST',
    bizId: r2,
    itemName: '专家号 · 内科',
    amount: 65,
    status: 1,
  })
  state.medicalRecords[r2] = {
    readme: '反复头痛 2 周，加重 3 天',
    present: '两侧颞部胀痛，伴轻度恶心，无呕吐，无肢体麻木',
    presentTreat: '自行服用布洛芬，效果不佳',
    history: '高血压病史 5 年，规律服药',
    allergy: '无药物过敏史',
    physique: 'T 36.8℃，BP 138/86mmHg，神志清，颈软',
    diagnosis: '头痛待查',
    cure: '完善头颅影像学及血常规，排除颅内病变及感染',
    checkAdvice: '头部 CT',
    inspectionAdvice: '血常规',
  }

  // ③ 检验科队列：已缴费待执行（开单→缴费→检验 常识链路）
  nextPatientId += 1
  const p3 = nextPatientId
  nextRegisterId += 1
  const r3 = nextRegisterId
  state.registers.push({
    registerId: r3,
    patientId: p3,
    medicalRecordNo: 'MR202606040003',
    patientName: '赵大爷',
    gender: 1,
    age: 67,
    deptId: 1,
    deptName: '内科',
    employeeId: 1,
    doctorName: '张医生',
    registLevelId: 1,
    registLevelName: '普通号',
    visitState: 2,
    registTime: nowIso(),
    triageLevel: 'NORMAL',
    triageNote: 'AI 分诊：老年慢病复诊',
    assignedByAi: true,
  })
  nextInspectionId += 1
  const inspId = nextInspectionId
  state.inspectionRequests.push({
    inspectionRequestId: inspId,
    registerId: r3,
    medicalRecordNo: 'MR202606040003',
    patientName: '赵大爷',
    itemName: '血常规',
    itemPrice: 35,
    status: 20,
    createTime: nowIso(),
    resultText: '',
  })
  initResultFields(state.inspectionRequests[state.inspectionRequests.length - 1], 'INSPECTION')
  createBill({
    medicalRecordNo: 'MR202606040003',
    patientId: p3,
    registerId: r3,
    bizType: 'INSPECTION',
    bizId: inspId,
    itemName: '血常规',
    amount: 35,
    status: 1,
  })

  // ④ 放射科：已缴费待检查
  nextCheckId += 1
  const chkId = nextCheckId
  state.checkRequests.push({
    checkRequestId: chkId,
    registerId: r3,
    medicalRecordNo: 'MR202606040003',
    patientName: '赵大爷',
    itemName: '头部 CT',
    itemPrice: 280,
    status: 20,
    createTime: nowIso(),
    resultText: '',
  })
  initResultFields(state.checkRequests[state.checkRequests.length - 1], 'CHECK')
  createBill({
    medicalRecordNo: 'MR202606040003',
    patientId: p3,
    registerId: r3,
    bizType: 'CHECK',
    bizId: chkId,
    itemName: '头部 CT',
    amount: 280,
    status: 1,
  })

  // ⑤ 药房：已缴费待发药
  nextPrescriptionId += 1
  const rxId = nextPrescriptionId
  state.prescriptions.push({
    prescriptionId: rxId,
    registerId: r2,
    medicalRecordNo: 'MR202606040002',
    patientName: '李小红',
    doctorName: '王教授',
    totalAmount: 37,
    status: 20,
    createTime: nowIso(),
    items: [{ drugName: '阿莫西林胶囊', quantity: 2, dosage: '0.5g tid', days: 7 }],
  })
  createBill({
    medicalRecordNo: 'MR202606040002',
    patientId: p2,
    registerId: r2,
    bizType: 'PRESCRIPTION',
    bizId: rxId,
    itemName: '处方 · 阿莫西林等',
    amount: 37,
    status: 1,
  })
}

seedDemoPatients()

// —— 挂号收费 ——

export function addRegisterFromWindow(body, schedule, fee) {
  nextPatientId += 1
  nextRegisterId += 1
  const registerId = nextRegisterId
  const medicalRecordNo = `MR${new Date().toISOString().slice(0, 10).replace(/-/g, '')}${String(registerId).slice(-4)}`
  const dept = getDeptById(body.deptId)
  const doctor = getDoctorById(body.employeeId)

  if (body.schedulingId) consumeScheduleQuota(body.schedulingId)

  state.registers.push({
    registerId,
    patientId: nextPatientId,
    medicalRecordNo,
    patientName: body.patientName,
    gender: body.gender ?? 1,
    age: body.age,
    phone: body.phone,
    deptId: body.deptId,
    deptName: dept?.deptName,
    employeeId: body.employeeId,
    doctorName: doctor?.realName ?? schedule?.employeeName,
    registLevelId: body.registLevelId ?? schedule?.registLevelId ?? 1,
    registLevelName: schedule?.registLevelName ?? (body.registLevelId === 2 ? '专家号' : '普通号'),
    visitState: 0,
    registTime: nowIso(),
    workDate: schedule?.workDate,
    noonLabel: schedule?.noonLabel,
    schedulingId: body.schedulingId,
  })

  const bill = createBill({
    medicalRecordNo,
    patientId: nextPatientId,
    registerId,
    bizType: 'REGIST',
    bizId: registerId,
    itemName: `${schedule?.registLevelName ?? '普通号'} · ${dept?.deptName ?? '门诊'}`,
    amount: fee,
    status: 0,
  })

  return { registerId, medicalRecordNo, bill, fee }
}

export function getBillsByMedicalRecord(medicalRecordNo, status) {
  return state.bills.filter((b) => {
    if (b.medicalRecordNo !== medicalRecordNo) return false
    if (status !== undefined && status !== null && status !== '') {
      return b.status === Number(status)
    }
    return true
  })
}

export function getPatientIdByMr(medicalRecordNo) {
  const reg = state.registers.find((r) => r.medicalRecordNo === medicalRecordNo)
  return reg?.patientId ?? null
}

export function chargeBills(billIds) {
  const ids = billIds.map(Number)
  let paidAmount = 0
  const paid = []
  for (const bill of state.bills) {
    if (!ids.includes(bill.id) || bill.status !== 0) continue
    bill.status = 1
    bill.statusText = '已支付'
    paidAmount += bill.amount
    paid.push(bill)

    if (bill.bizType === 'REGIST') {
      const reg = state.registers.find((r) => r.registerId === bill.registerId)
      if (reg && reg.visitState === 0) reg.visitState = 1
    }
    if (bill.bizType === 'INSPECTION') {
      const req = state.inspectionRequests.find((r) => r.inspectionRequestId === bill.bizId)
      if (req && req.status === 10) req.status = 20
    }
    if (bill.bizType === 'CHECK') {
      const req = state.checkRequests.find((r) => r.checkRequestId === bill.bizId)
      if (req && req.status === 10) req.status = 20
    }
    if (bill.bizType === 'DISPOSAL') {
      const req = state.disposalRequests.find((r) => r.disposalRequestId === bill.bizId)
      if (req && req.status === 10) req.status = 20
    }
    if (bill.bizType === 'PRESCRIPTION') {
      const rx = state.prescriptions.find((r) => r.prescriptionId === bill.bizId)
      if (rx && rx.status === 10) rx.status = 20
    }
  }
  return { paidAmount, paid }
}

export function refundBillById(billId) {
  const bill = state.bills.find((b) => b.id === Number(billId))
  if (!bill || bill.status !== 1) return null
  bill.status = 2
  bill.statusText = '已退款'
  return bill
}

// —— 医生 ——

export function getDoctorQueue(params = {}) {
  const { visitState } = params
  let list = state.registers.filter((r) => r.deptId === 1)
  if (visitState !== undefined && visitState !== null && visitState !== '') {
    list = list.filter((r) => r.visitState === Number(visitState))
  } else {
    list = list.filter((r) => r.visitState >= 1 && r.visitState <= 2)
  }
  return list.sort((a, b) => a.registTime.localeCompare(b.registTime))
}

export function callRegister(registerId) {
  const reg = state.registers.find((r) => r.registerId === Number(registerId))
  if (!reg) throw new Error('挂号记录不存在')
  if (reg.visitState !== 1) throw new Error('仅「已挂号」患者可叫号')
  reg.visitState = 2
  return reg
}

export function finishRegister(registerId) {
  const reg = state.registers.find((r) => r.registerId === Number(registerId))
  if (!reg) throw new Error('挂号记录不存在')
  reg.visitState = 3
  return reg
}

export function getMedicalRecord(registerId) {
  return state.medicalRecords[Number(registerId)] || {}
}

const RECORD_STATUS_LABEL = { 0: '书写中', 1: '已保存', 2: '已确诊提交' }

export function saveMedicalRecord(registerId, data) {
  state.medicalRecords[Number(registerId)] = {
    ...data,
    status: 1,
    statusLabel: RECORD_STATUS_LABEL[1],
  }
  return state.medicalRecords[Number(registerId)]
}

export function confirmMedicalRecord(registerId, data) {
  const existing = getMedicalRecord(registerId)
  state.medicalRecords[Number(registerId)] = {
    ...existing,
    ...data,
    status: 2,
    statusLabel: RECORD_STATUS_LABEL[2],
    confirmed: true,
    confirmedAt: nowIso(),
  }
  return state.medicalRecords[Number(registerId)]
}

export function getRegisterById(registerId) {
  return state.registers.find((r) => r.registerId === Number(registerId))
}

function createTechOrder(registerId, techId, kind) {
  const reg = getRegisterById(registerId)
  if (!reg) throw new Error('挂号记录不存在')
  const tech = getMedicalTechById(techId)
  if (!tech) throw new Error('医技项目不存在')

  if (kind === 'INSPECTION') {
    nextInspectionId += 1
    const row = {
      inspectionRequestId: nextInspectionId,
      registerId: reg.registerId,
      medicalRecordNo: reg.medicalRecordNo,
      patientName: reg.patientName,
      itemName: tech.itemName,
      itemPrice: tech.price,
      status: 10,
      createTime: nowIso(),
      resultText: '',
    }
    initResultFields(row, 'INSPECTION')
    state.inspectionRequests.push(row)
    createBill({
      medicalRecordNo: reg.medicalRecordNo,
      patientId: reg.patientId,
      registerId: reg.registerId,
      bizType: 'INSPECTION',
      bizId: row.inspectionRequestId,
      itemName: tech.itemName,
      amount: tech.price,
    })
    return { ...row, itemName: tech.itemName, inspectionRequestId: row.inspectionRequestId }
  }

  if (kind === 'CHECK') {
    nextCheckId += 1
    const row = {
      checkRequestId: nextCheckId,
      registerId: reg.registerId,
      medicalRecordNo: reg.medicalRecordNo,
      patientName: reg.patientName,
      itemName: tech.itemName,
      itemPrice: tech.price,
      status: 10,
      createTime: nowIso(),
      resultText: '',
    }
    initResultFields(row, 'CHECK')
    state.checkRequests.push(row)
    createBill({
      medicalRecordNo: reg.medicalRecordNo,
      patientId: reg.patientId,
      registerId: reg.registerId,
      bizType: 'CHECK',
      bizId: row.checkRequestId,
      itemName: tech.itemName,
      amount: tech.price,
    })
    return { ...row, itemName: tech.itemName, checkRequestId: row.checkRequestId }
  }

  if (kind === 'DISPOSAL') {
    nextDisposalId += 1
    const row = {
      disposalRequestId: nextDisposalId,
      registerId: reg.registerId,
      medicalRecordNo: reg.medicalRecordNo,
      patientName: reg.patientName,
      itemName: tech.itemName,
      itemPrice: tech.price,
      status: 10,
      createTime: nowIso(),
      resultText: '',
    }
    initResultFields(row, 'DISPOSAL')
    state.disposalRequests.push(row)
    createBill({
      medicalRecordNo: reg.medicalRecordNo,
      patientId: reg.patientId,
      registerId: reg.registerId,
      bizType: 'DISPOSAL',
      bizId: row.disposalRequestId,
      itemName: tech.itemName,
      amount: tech.price,
    })
    return { ...row, itemName: tech.itemName, disposalRequestId: row.disposalRequestId }
  }

  throw new Error('未知申请类型')
}

export function createInspectionOrder(data) {
  return createTechOrder(data.registerId, data.medicalTechnologyId ?? 2, 'INSPECTION')
}

export function createCheckOrder(data) {
  return createTechOrder(data.registerId, data.medicalTechnologyId ?? 1, 'CHECK')
}

export function createDisposalOrder(data) {
  return createTechOrder(data.registerId, data.medicalTechnologyId ?? 5, 'DISPOSAL')
}

function formatResultPayload(row, idKey) {
  return {
    [idKey]: row[idKey],
    itemName: row.itemName,
    resultText: row.resultText || '',
    resultAttachment: row.resultAttachment || '',
    reportTime: row.resultTime ?? nowIso(),
  }
}

function parsePublishedText(resultText) {
  const text = (resultText || '').trim()
  if (!text) return { ai: '', doctor: '' }
  const match = /^AI：([\s\S]*?)(?:\n医师：([\s\S]*))?$/.exec(text)
  if (match) {
    return { ai: (match[1] || '').trim(), doctor: (match[2] || '').trim() }
  }
  return { ai: text, doctor: '' }
}

function techTypeForIdKey(idKey) {
  if (idKey === 'inspectionRequestId') return 'INSPECTION'
  if (idKey === 'checkRequestId') return 'CHECK'
  return 'DISPOSAL'
}

function enrichDoctorResultView(row, idKey) {
  const parsed = parsePublishedText(row.resultText)
  const aiReportText = row.aiReportText || parsed.ai
  const doctorReportText = row.doctorReportText || parsed.doctor
  const techType = row.techType || techTypeForIdKey(idKey)
  const instrumentData = row.instrumentData || mockInstrumentData(techType, row.itemName)
  return {
    ...formatResultPayload(row, idKey),
    status: row.status,
    instrumentData,
    aiReportText,
    doctorReportText,
    aiReportStatus: aiReportText || doctorReportText ? 'READY' : 'PENDING',
  }
}

export function getInspectionResult(inspectionRequestId) {
  const row = state.inspectionRequests.find((r) => r.inspectionRequestId === Number(inspectionRequestId))
  if (!row) throw new Error('检验申请不存在')
  if (row.status < 40) throw new Error('检验结果尚未出具，请待检验科录入')
  return enrichDoctorResultView(row, 'inspectionRequestId')
}

export function getCheckResult(checkRequestId) {
  const row = state.checkRequests.find((r) => r.checkRequestId === Number(checkRequestId))
  if (!row) throw new Error('检查申请不存在')
  if (row.status < 40) throw new Error('检查报告尚未出具，请待放射科录入')
  return enrichDoctorResultView(row, 'checkRequestId')
}

export function getDisposalResult(disposalRequestId) {
  const row = state.disposalRequests.find((r) => r.disposalRequestId === Number(disposalRequestId))
  if (!row) throw new Error('处置申请不存在')
  if (row.status < 40) throw new Error('处置记录尚未出具，请待处置科录入')
  return enrichDoctorResultView(row, 'disposalRequestId')
}

function findTechRow(techType, id) {
  if (techType === 'INSPECTION') {
    return state.inspectionRequests.find((r) => r.inspectionRequestId === Number(id))
  }
  if (techType === 'CHECK') {
    return state.checkRequests.find((r) => r.checkRequestId === Number(id))
  }
  if (techType === 'DISPOSAL') {
    return state.disposalRequests.find((r) => r.disposalRequestId === Number(id))
  }
  return null
}

export function getTechResultDetail(techType, id) {
  const row = findTechRow(techType, id)
  if (!row) throw new Error('申请不存在')
  ensureInstrumentData(row, techType)
  const idKey = techType === 'INSPECTION'
    ? 'inspectionRequestId'
    : techType === 'CHECK'
      ? 'checkRequestId'
      : 'disposalRequestId'
  return {
    ...formatResultPayload(row, idKey),
    status: row.status,
    medicalRecordNo: row.medicalRecordNo,
    patientName: row.patientName,
    techType,
  }
}

export function generateTechAiReport(techType, id) {
  const row = findTechRow(techType, id)
  if (!row) throw new Error('申请不存在')
  ensureInstrumentData(row, techType)
  row.aiReportText = mockAiReportText(techType, row.itemName)
  row.aiReportStatus = 'READY'
  return getTechResultDetail(techType, id)
}

const ORDER_STATUS_LABEL = {
  10: '已开立',
  20: '已缴费',
  30: '执行完成',
  40: '已出结果',
  50: '已退费',
}

const RX_STATUS_LABEL = {
  10: '已开立',
  20: '已缴费',
  30: '已发药',
  40: '已退药',
  50: '已退费',
}

export function getRegisterOrders(registerId) {
  const rid = Number(registerId)
  const list = []

  for (const row of state.inspectionRequests.filter((r) => r.registerId === rid)) {
    list.push({
      kind: 'inspection',
      typeLabel: '检验',
      requestId: row.inspectionRequestId,
      itemName: row.itemName,
      status: row.status,
      statusLabel: ORDER_STATUS_LABEL[row.status] ?? String(row.status),
    })
  }
  for (const row of state.checkRequests.filter((r) => r.registerId === rid)) {
    list.push({
      kind: 'check',
      typeLabel: '检查',
      requestId: row.checkRequestId,
      itemName: row.itemName,
      status: row.status,
      statusLabel: ORDER_STATUS_LABEL[row.status] ?? String(row.status),
    })
  }
  for (const row of state.disposalRequests.filter((r) => r.registerId === rid)) {
    list.push({
      kind: 'disposal',
      typeLabel: '处置记录',
      requestId: row.disposalRequestId,
      itemName: row.itemName,
      status: row.status,
      statusLabel: ORDER_STATUS_LABEL[row.status] ?? String(row.status),
    })
  }
  for (const row of state.prescriptions.filter((r) => r.registerId === rid)) {
    list.push({
      kind: 'prescription',
      typeLabel: '处方',
      requestId: row.prescriptionId,
      itemName: row.items?.map((i) => i.drugName).join('、') || '处方',
      status: row.status,
      statusLabel: RX_STATUS_LABEL[row.status] ?? String(row.status),
    })
  }

  return {
    registerId: rid,
    list,
    checks: state.checkRequests.filter((r) => r.registerId === rid),
    inspections: state.inspectionRequests.filter((r) => r.registerId === rid),
    disposals: state.disposalRequests.filter((r) => r.registerId === rid),
    prescriptions: state.prescriptions.filter((r) => r.registerId === rid),
  }
}

export function getRegisterResults(registerId) {
  const orders = getRegisterOrders(registerId)
  const results = []
  for (const item of orders.list) {
    if (item.kind === 'prescription' || item.status < 40) continue
    try {
      let detail
      if (item.kind === 'inspection') detail = getInspectionResult(item.requestId)
      else if (item.kind === 'check') detail = getCheckResult(item.requestId)
      else if (item.kind === 'disposal') detail = getDisposalResult(item.requestId)
      else continue
      results.push({
        kind: item.kind,
        requestId: item.requestId,
        typeLabel: item.typeLabel,
        itemName: item.itemName,
        resultText: detail.resultText,
        resultAttachment: detail.resultAttachment,
        reportTime: detail.reportTime,
      })
    } catch {
      /* 尚未出具 */
    }
  }
  return { registerId: Number(registerId), orders: orders.list, results }
}

export function getImagingStudies(params = {}) {
  const status = params.status
  let list = state.checkRequests.map((row) => ({
    studyId: row.checkRequestId,
    checkRequestId: row.checkRequestId,
    patientName: row.patientName,
    medicalRecordNo: row.medicalRecordNo,
    itemName: row.itemName,
    modality: row.itemName?.includes('CT') ? 'CT' : 'XR',
    status: row.status >= 40 ? 'COMPLETED' : row.status >= 20 ? 'IN_PROGRESS' : 'PENDING',
    uploadStatus: row.status >= 20 ? 'UPLOADED' : 'WAITING',
    resultReady: row.status >= 40,
  }))
  if (status) list = list.filter((s) => s.status === status)
  return list
}

export function createPrescription(data) {
  const reg = getRegisterById(data.registerId)
  if (!reg) throw new Error('挂号记录不存在')
  const items = (data.items || []).map((it) => {
    const drug = getDrugById(it.drugId)
    return {
      drugId: it.drugId,
      drugName: drug?.drugName ?? '药品',
      quantity: it.quantity,
      dosage: it.dosage,
      frequency: it.frequency,
      days: it.days,
      unitPrice: drug?.retailPrice ?? 0,
    }
  })
  const totalAmount = items.reduce((s, it) => s + (it.unitPrice ?? 0) * (it.quantity ?? 1), 0)
  nextPrescriptionId += 1
  const rx = {
    prescriptionId: nextPrescriptionId,
    registerId: reg.registerId,
    medicalRecordNo: reg.medicalRecordNo,
    patientName: reg.patientName,
    doctorName: reg.doctorName,
    totalAmount: Math.round(totalAmount * 100) / 100,
    status: 10,
    createTime: nowIso(),
    items,
  }
  state.prescriptions.push(rx)
  createBill({
    medicalRecordNo: reg.medicalRecordNo,
    patientId: reg.patientId,
    registerId: reg.registerId,
    bizType: 'PRESCRIPTION',
    bizId: rx.prescriptionId,
    itemName: `处方 · ${items.map((i) => i.drugName).join('、')}`,
    amount: rx.totalAmount,
  })
  return rx
}

export function confirmAiDraft(type, registerId, items) {
  nextDraftId += 1
  const draftId = nextDraftId
  const results = []
  for (const item of items) {
    const techId = item.medicalTechnologyId
    if (type === 'CHECK') results.push(createTechOrder(registerId, techId, 'CHECK'))
    else if (type === 'INSPECTION') results.push(createTechOrder(registerId, techId, 'INSPECTION'))
    else if (type === 'DISPOSAL') results.push(createTechOrder(registerId, techId, 'DISPOSAL'))
  }
  return { draftId, requests: results }
}

// —— 医技队列 ——

function enrichTechQueueRow(row) {
  const reg = getRegisterById(row.registerId)
  return {
    ...row,
    triageLevel: reg?.triageLevel || 'NORMAL',
    triageNote: reg?.triageNote || '',
    assignedByAi: reg?.assignedByAi ?? false,
  }
}

export function getInspectionQueue(status) {
  const s = status ?? 20
  return state.inspectionRequests
    .filter((r) => r.status === Number(s))
    .map(enrichTechQueueRow)
}

export function executeInspection(id) {
  const row = state.inspectionRequests.find((r) => r.inspectionRequestId === Number(id))
  if (!row || row.status !== 20) throw new Error('仅已缴费项目可执行')
  row.status = 30
  ensureInstrumentData(row, 'INSPECTION')
  if (!row.aiReportText) {
    row.aiReportText = mockAiReportText('INSPECTION', row.itemName)
    row.aiReportStatus = 'READY'
  }
  return row
}

export function saveInspectionResult(id, payload) {
  const row = state.inspectionRequests.find((r) => r.inspectionRequestId === Number(id))
  if (!row) throw new Error('申请不存在')
  if (typeof payload === 'string') {
    row.resultText = payload
  } else {
    row.resultText = payload.resultText ?? ''
    row.resultAttachment = payload.resultAttachment ?? ''
  }
  row.status = 40
  return row
}

export function getCheckQueue(status) {
  const s = status ?? 20
  return state.checkRequests
    .filter((r) => r.status === Number(s))
    .map(enrichTechQueueRow)
}

export function executeCheck(id) {
  const row = state.checkRequests.find((r) => r.checkRequestId === Number(id))
  if (!row || row.status !== 20) throw new Error('仅已缴费项目可执行')
  row.status = 30
  ensureInstrumentData(row, 'CHECK')
  return row
}

export function saveCheckResult(id, payload) {
  const row = state.checkRequests.find((r) => r.checkRequestId === Number(id))
  if (!row) throw new Error('申请不存在')
  if (typeof payload === 'string') {
    row.resultText = payload
  } else {
    row.resultText = payload.resultText ?? ''
    row.resultAttachment = payload.resultAttachment ?? ''
  }
  row.status = 40
  return row
}

export function getDisposalQueue(status) {
  const s = status ?? 20
  return state.disposalRequests.filter((r) => r.status === Number(s))
}

export function executeDisposal(id) {
  const row = state.disposalRequests.find((r) => r.disposalRequestId === Number(id))
  if (!row || row.status !== 20) throw new Error('仅已缴费项目可执行')
  row.status = 30
  ensureInstrumentData(row, 'DISPOSAL')
  if (!row.aiReportText) {
    row.aiReportText = mockAiReportText('DISPOSAL', row.itemName)
    row.aiReportStatus = 'READY'
  }
  return row
}

export function saveDisposalResult(id, payload) {
  const row = state.disposalRequests.find((r) => r.disposalRequestId === Number(id))
  if (!row) throw new Error('申请不存在')
  if (typeof payload === 'string') {
    row.resultText = payload
  } else if (payload.aiReportText || payload.doctorReportText) {
    const ai = payload.aiReportText?.trim() || ''
    const doctor = payload.doctorReportText?.trim() || ''
    if (ai && doctor) row.resultText = `AI：${ai}\n医师：${doctor}`
    else if (ai) row.resultText = `AI：${ai}`
    else row.resultText = `医师：${doctor}`
    row.aiReportText = ai
    row.doctorReportText = doctor
    row.resultAttachment = payload.resultAttachment ?? ''
  } else {
    row.resultText = payload.resultText ?? ''
    row.resultAttachment = payload.resultAttachment ?? ''
  }
  row.status = 40
  return row
}

// —— 药房 ——

export function getPharmacyQueue(status) {
  const s = status ?? 20
  return state.prescriptions.filter((r) => r.status === Number(s))
}

export function dispensePrescription(prescriptionId) {
  const rx = state.prescriptions.find((r) => r.prescriptionId === Number(prescriptionId))
  if (!rx || rx.status !== 20) throw new Error('仅已缴费处方可发药')
  rx.status = 30
  rx.dispenseTime = nowIso()
  return rx
}

export function returnPrescription(prescriptionId) {
  const rx = state.prescriptions.find((r) => r.prescriptionId === Number(prescriptionId))
  if (!rx || rx.status !== 30) throw new Error('仅已发药处方可退药')
  rx.status = 10
  return rx
}
