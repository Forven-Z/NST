const dict = require('./dict')

let patientId = 10001
let profile = {
  id: 10001,
  realName: '微信用户',
  medicalRecordNo: 'MR202606040100',
  gender: 1,
  phone: '',
  idCard: '',
  address: '',
}

const familyMembers = []
let nextRegisterId = 31000
let nextBillId = 82000
let nextPaymentId = 90000
const registers = []
const bills = []
const payments = []

function ok(data) {
  return Promise.resolve({ success: true, data })
}

function normalizeBill(b) {
  return {
    id: b.id,
    billNo: b.billNo,
    billTitle: b.billTitle || b.itemName || '费用项',
    bizType: b.bizType,
    amount: b.amount,
    status: b.status,
    registerId: b.registerId,
    patientId: b.patientId,
    createTime: b.createTime || '',
  }
}

function filterBills(params) {
  params = params || {}
  var list = bills.map(normalizeBill)
  if (params.status != null && params.status !== '') {
    list = list.filter(function (b) { return b.status === Number(params.status) })
  }
  if (params.patientId) {
    list = list.filter(function (b) { return b.patientId === Number(params.patientId) })
  }
  if (params.registerId) {
    list = list.filter(function (b) { return b.registerId === Number(params.registerId) })
  }
  if (params.scope === 'outpatient') {
    list = list.filter(function (b) { return b.bizType === 'REGIST' || b.bizType === 'REGISTER' })
  } else if (params.scope === 'exam') {
    list = list.filter(function (b) {
      return ['CHECK', 'LIS', 'INSPECTION', 'PACS', 'EXAM'].indexOf(b.bizType) >= 0
    })
  }
  return list
}

function seedRegisters() {
  if (registers.length) return
  registers.push({
    registerId: 3001,
    patientId: 10001,
    patientName: profile.realName,
    medicalRecordNo: profile.medicalRecordNo,
    deptName: '内科',
    doctorName: '张医生',
    registLevelName: '普通号',
    visitState: 1,
    workDate: new Date().toISOString().slice(0, 10),
    noonType: 1,
    noonLabel: '上午',
    registFee: 20,
  })
  payments.push({
    paymentId: 90001,
    paymentNo: 'P202606040001',
    paidAt: '2026-06-03 09:30:00',
    amount: 20,
    channel: '模拟支付',
    registerId: 3000,
    patientId: 10001,
    summary: '挂号费 · 内科',
  })
}

seedRegisters()

function login(nickName) {
  profile.realName = nickName || profile.realName
  return ok({
    accessToken: 'mock-patient-token',
    expiresIn: 7200,
    patientId,
    medicalRecordNo: profile.medicalRecordNo,
    isNewPatient: false,
  })
}

function getProfile() {
  return ok({ ...profile })
}

function updateProfile(body) {
  Object.assign(profile, body || {})
  return ok(profile)
}

function listDepartments() {
  return ok({ list: dict.OUTPATIENT_DEPTS })
}

function listSchedules(params) {
  return ok({ list: dict.getSchedules(params) })
}

function listFamily() {
  const self = {
    memberPatientId: patientId,
    realName: profile.realName,
    medicalRecordNo: profile.medicalRecordNo,
    relationType: 0,
    isSelf: true,
  }
  return ok({ list: [self, ...familyMembers] })
}

function addFamily(body) {
  const member = {
    memberPatientId: 10000 + familyMembers.length + 2,
    realName: body.realName,
    medicalRecordNo: `MR20260604${String(2000 + familyMembers.length)}`,
    idCard: body.idCard,
    gender: body.gender || 1,
    phone: body.phone || '',
    relationType: body.relationType || 4,
    isSelf: false,
  }
  familyMembers.push(member)
  return ok(member)
}

function createRegister(body) {
  const sched = dict.getScheduleById(body.schedulingId)
  if (!sched) return Promise.reject(new Error('排班不存在'))
  dict.consumeQuota(body.schedulingId)
  nextRegisterId += 1
  nextBillId += 1
  const member = body.memberPatientId
    ? [...familyMembers].find((m) => m.memberPatientId === body.memberPatientId)
    : null
  const reg = {
    registerId: nextRegisterId,
    patientId: member ? member.memberPatientId : patientId,
    patientName: member ? member.realName : profile.realName,
    medicalRecordNo: member ? member.medicalRecordNo : profile.medicalRecordNo,
    deptName: sched.deptName,
    doctorName: sched.doctorName,
    registLevelName: sched.levelName,
    visitState: 0,
    workDate: sched.workDate,
    noonType: sched.noonType,
    noonLabel: sched.noonLabel,
    registFee: sched.registFee,
  }
  registers.unshift(reg)
  bills.unshift({
    id: nextBillId,
    billNo: 'B' + Date.now(),
    billTitle: sched.levelName + ' · ' + sched.deptName,
    itemName: sched.levelName + ' · ' + sched.deptName,
    bizType: 'REGIST',
    amount: sched.registFee,
    status: 0,
    registerId: reg.registerId,
    patientId: reg.patientId,
    createTime: new Date().toISOString().slice(0, 16).replace('T', ' '),
  })
  return ok({
    registerId: reg.registerId,
    billId: nextBillId,
    billNo: bills[0].billNo,
    amount: sched.registFee,
    visitState: 0,
    message: '请完成支付后进入已挂号状态',
  })
}

function listRegisters(params) {
  let list = [...registers]
  if (params && params.patientId) {
    list = list.filter((r) => r.patientId === Number(params.patientId))
  }
  if (params && params.visitState != null && params.visitState !== '') {
    list = list.filter((r) => r.visitState === Number(params.visitState))
  }
  return ok({ list, page: 1, pageSize: 20 })
}

function getRegister(registerId) {
  const reg = registers.find((r) => r.registerId === Number(registerId))
  if (!reg) return Promise.reject(new Error('挂号记录不存在'))
  return ok(reg)
}

function queueStatus(registerId) {
  const reg = registers.find((r) => r.registerId === Number(registerId))
  if (!reg) return Promise.reject(new Error('挂号记录不存在'))
  let ahead = 0
  let hint = '请先完成缴费'
  if (reg.visitState === 1) {
    ahead = registers.filter((r) => r.visitState === 1 && r.registerId < reg.registerId).length
    hint = ahead === 0 ? '即将轮到您，请至诊室候诊' : `前面还有 ${ahead} 人，请留意叫号`
  } else if (reg.visitState === 2) hint = '医生正在接诊'
  return ok({ ...reg, aheadCount: ahead, queueHint: hint })
}

function listBills(params) {
  return ok({ list: filterBills(params) })
}

function listPayments(params) {
  params = params || {}
  let list = [...payments]
  if (params.patientId) {
    list = list.filter((p) => p.patientId === Number(params.patientId))
  }
  if (params.registerId) {
    list = list.filter((p) => p.registerId === Number(params.registerId))
  }
  list.sort((a, b) => (b.paymentId || 0) - (a.paymentId || 0))
  return ok({ list, page: 1, pageSize: 20 })
}

function mockPay(billIds) {
  let paid = 0
  const paidBills = []
  for (const id of billIds || []) {
    const bill = bills.find((b) => b.id === Number(id))
    if (bill && bill.status === 0) {
      bill.status = 1
      paid += bill.amount
      paidBills.push(bill)
      const reg = registers.find((r) => r.registerId === bill.registerId)
      if (reg && reg.visitState === 0) reg.visitState = 1
      if (reg && reg.visitState === 1 && bill.bizType !== 'REGIST') {
        /* 医技费支付不改变挂号状态 */
      }
    }
  }
  if (paid > 0 && paidBills.length) {
    nextPaymentId += 1
    const first = paidBills[0]
    const titles = paidBills.map((b) => b.billTitle || b.itemName).join('、')
    payments.unshift({
      paymentId: nextPaymentId,
      paymentNo: 'P' + Date.now(),
      paidAt: new Date().toISOString().slice(0, 16).replace('T', ' '),
      amount: Math.round(paid * 100) / 100,
      channel: '模拟支付',
      registerId: first.registerId,
      patientId: first.patientId,
      summary: titles,
    })
  }
  return ok({ paidAmount: paid, message: '支付成功' })
}

/** 演示：将某次挂号标记为看诊结束并生成医技待缴（便于行程卡「去缴费」） */
function addExamBillForDemo(registerId) {
  const reg = registers.find((r) => r.registerId === Number(registerId))
  if (!reg) return ok(null)
  reg.visitState = 3
  nextBillId += 1
  bills.unshift({
    id: nextBillId,
    billNo: 'B' + Date.now(),
    billTitle: '血常规',
    itemName: '血常规',
    bizType: 'LIS',
    amount: 35,
    status: 0,
    registerId: reg.registerId,
    patientId: reg.patientId,
    createTime: new Date().toISOString().slice(0, 16).replace('T', ' '),
  })
  return ok({ registerId: reg.registerId })
}

function cancelRegister(registerId) {
  const reg = registers.find((r) => r.registerId === Number(registerId))
  if (!reg) return Promise.reject(new Error('挂号记录不存在'))
  if (reg.visitState !== 1) return Promise.reject(new Error('仅已挂号未接诊可退号'))
  reg.visitState = 4
  return ok({ registerId, visitState: 4, message: '退号成功' })
}

function getMedicalRecord(registerId) {
  return ok({
    registerId: Number(registerId),
    readme: '反复头痛 2 周',
    diagnosis: '头痛待查',
    cure: '建议完善检查',
  })
}

const REPORTS = [
  {
    id: 1,
    type: 'lab',
    typeLabel: '检验',
    reportName: '血常规',
    patientName: '王小明',
    reportTime: '2026-06-04 11:20',
    summary: '白细胞略高，其余指标未见明显异常。',
  },
  {
    id: 2,
    type: 'exam',
    typeLabel: '检查',
    reportName: '头部 CT 平扫',
    patientName: '王小明',
    reportTime: '2026-06-04 15:40',
    summary: '未见明显占位性病变，建议结合临床。',
  },
]

function listReports(params) {
  let list = [...REPORTS]
  const type = params && params.type
  if (type && type !== 'all') list = list.filter((r) => r.type === type)
  return ok({ list })
}

const MESSAGES = [
  {
    id: 1,
    title: '挂号成功提醒',
    content: '您已成功预约内科 · 张医生普通号，请尽快完成缴费。',
    timeLabel: '今天 09:12',
    read: false,
    link: '/pages/bills/bills',
  },
  {
    id: 2,
    title: '候诊提醒',
    content: '您当前前面还有 2 人候诊，请留意叫号屏。',
    timeLabel: '今天 10:05',
    read: false,
    link: '/pages/queue/queue?registerId=3001',
  },
  {
    id: 3,
    title: '报告已出',
    content: '血常规报告已出，可在「报告查询」中查看。',
    timeLabel: '昨天 16:30',
    read: true,
    link: '/pages/reports/reports',
  },
]

function listMessages() {
  return ok({ list: MESSAGES })
}

module.exports = {
  login,
  getProfile,
  updateProfile,
  listDepartments,
  listSchedules,
  listFamily,
  addFamily,
  createRegister,
  listRegisters,
  getRegister,
  queueStatus,
  listBills,
  listPayments,
  mockPay,
  cancelRegister,
  getMedicalRecord,
  listReports,
  listMessages,
  setProfileFromLogin(data) {
    if (data.patientId) patientId = data.patientId
    if (data.medicalRecordNo) profile.medicalRecordNo = data.medicalRecordNo
  },
}
