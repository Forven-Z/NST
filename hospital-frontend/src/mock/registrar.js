import { mockResult } from '../utils/mock'
import {
  countExpertSessions,
  getDeptById,
  getDoctorById,
  getDoctorsByDept,
  getScheduleById,
  getSchedules,
  MOCK_OUTPATIENT_DEPTS,
  MOCK_REGIST_LEVELS,
  MOCK_SETTLE_CATEGORIES,
} from './dict'
import {
  addRegisterFromWindow,
  chargeBills,
  getBillsByMedicalRecord,
  getPatientIdByMr,
  refundBillById,
} from './store'

export function mockDepartments() {
  return mockResult({ list: MOCK_OUTPATIENT_DEPTS, page: 1, pageSize: 20 })
}

export function mockRegistLevels() {
  return mockResult({ list: MOCK_REGIST_LEVELS, page: 1, pageSize: 20 })
}

export function mockSettleCategories() {
  return mockResult({ list: MOCK_SETTLE_CATEGORIES, page: 1, pageSize: 20 })
}

export function mockDoctors(deptId) {
  const list = getDoctorsByDept(deptId).map((d) => ({
    ...d,
    clinicRole: d.role === 'expert' ? 'EXPERT' : 'REGULAR',
    expertSessionCount: d.role === 'expert' ? countExpertSessions(deptId, d.employeeId) : 0,
  }))
  return mockResult({ list })
}

export function mockSchedules(params) {
  const { deptId, employeeId, registLevelId } = params || {}
  const levelId = registLevelId ? Number(registLevelId) : undefined
  return mockResult({
    list: getSchedules(deptId, employeeId, levelId),
    page: 1,
    pageSize: 50,
  })
}

export function mockWindowRegister(body) {
  const schedule = body?.schedulingId ? getScheduleById(body.schedulingId) : null
  const dept = getDeptById(body?.deptId)
  const doctor = getDoctorById(body?.employeeId)
  const fee = schedule?.registFee ?? (body?.registLevelId === 2 ? 65 : 20)

  const { registerId, medicalRecordNo, bill } = addRegisterFromWindow(body, schedule, fee)

  return mockResult({
    registerId,
    medicalRecordNo,
    billId: bill.id,
    billNo: bill.billNo,
    amount: fee,
    visitState: 0,
    deptName: dept?.deptName,
    doctorName: doctor?.realName ?? schedule?.employeeName,
    patientName: body?.patientName,
    workDate: schedule?.workDate,
    noonLabel: schedule?.noonLabel,
    registLevelName: schedule?.registLevelName,
    message: '挂号成功，请患者至收费窗口缴纳挂号费后进入「已挂号」状态',
  })
}

export function mockWindowCharge(body) {
  const billIds = body?.billIds || []
  const { paidAmount } = chargeBills(billIds)
  return mockResult({
    paymentId: 92001,
    paymentNo: `P${Date.now()}`,
    paidAmount,
    message: paidAmount > 0 ? `收费成功，实收 ¥${paidAmount.toFixed(2)}` : '未找到可结算账单',
  })
}

export function mockPatientBills(medicalRecordNo, params) {
  const status = params?.status
  const list = getBillsByMedicalRecord(medicalRecordNo.trim(), status)
  return mockResult({
    list,
    patientId: getPatientIdByMr(medicalRecordNo.trim()),
  })
}

export function mockRefundBill(body) {
  const bill = refundBillById(body?.billId)
  if (!bill) return Promise.reject(new Error('仅「已支付」账单可退费'))
  return mockResult({
    billId: bill.id,
    amount: bill.amount,
    message: '退费成功',
  })
}
