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
    id: 5001,
    requestId: 5001,
    patientId: 10001,
    type: 'lab',
    typeLabel: '检验',
    reportName: '血常规',
    patientName: '微信用户',
    reportTime: '2026-06-04 11:20',
    summary: '白细胞略高，其余指标未见明显异常。',
    purpose: '发热查因',
    bodyPart: '',
    resultText: '白细胞 11.2×10^9/L（略高），中性粒细胞比例正常；血红蛋白、血小板均在参考范围内。建议结合临床随访。',
    registerId: 3001,
  },
  {
    id: 6001,
    requestId: 6001,
    patientId: 10001,
    type: 'exam',
    typeLabel: '检查',
    reportName: '头部 CT 平扫',
    patientName: '微信用户',
    reportTime: '2026-06-04 15:40',
    summary: '未见明显占位性病变，建议结合临床。',
    purpose: '头痛查因',
    bodyPart: '头部',
    resultText: '颅脑 CT 平扫：脑实质密度未见明显异常，脑室系统形态正常，中线结构居中。未见明显占位性病变及出血灶。建议结合临床。',
    registerId: 3001,
  },
  {
    id: 7001,
    requestId: 7001,
    patientId: 10001,
    type: 'disposal',
    typeLabel: '处置记录',
    reportName: '洗胃',
    patientName: '微信用户',
    reportTime: '2026-06-04 16:10',
    summary: '洗胃完成，患者生命体征平稳。',
    purpose: '急性中毒',
    bodyPart: '',
    resultText: '洗胃完成，出入量记录完整，洗出液清亮；洗胃后生命体征平稳，未诉明显不适。嘱观察后离院。',
    registerId: 3001,
  },
]

function listReports(params) {
  params = params || {}
  let list = REPORTS.map(function (r) {
    return {
      id: r.id,
      requestId: r.requestId,
      type: r.type,
      typeLabel: r.typeLabel,
      reportName: r.reportName,
      patientName: r.patientName,
      reportTime: r.reportTime,
      summary: r.summary,
      registerId: r.registerId,
    }
  })
  if (params.patientId) {
    list = list.filter(function (r) {
      var full = REPORTS.find(function (x) { return x.id === r.id })
      return full && full.patientId === Number(params.patientId)
    })
  }
  const type = params.type
  if (type && type !== 'all') list = list.filter(function (r) { return r.type === type })
  return ok({ list: list })
}

function getReportDetail(type, requestId) {
  var row = REPORTS.find(function (r) {
    return r.type === type && r.requestId === Number(requestId)
  })
  if (!row) return Promise.reject(new Error('报告不存在'))
  return ok({
    requestId: row.requestId,
    type: row.type,
    typeLabel: row.typeLabel,
    reportName: row.reportName,
    registerId: row.registerId,
    patientId: row.patientId,
    purpose: row.purpose,
    bodyPart: row.bodyPart,
    resultText: row.resultText,
    reportTime: row.reportTime,
    status: 40,
  })
}

function getRegisterOrders(registerId) {
  var rid = Number(registerId)
  var list = [
    {
      kind: 'inspection',
      typeLabel: '检验',
      requestId: 5001,
      itemName: '血常规',
      status: 40,
      statusLabel: '已出结果',
      registerId: rid,
    },
    {
      kind: 'check',
      typeLabel: '检查',
      requestId: 6001,
      itemName: '头部 CT 平扫',
      status: 20,
      statusLabel: '已缴费',
      registerId: rid,
    },
    {
      kind: 'disposal',
      typeLabel: '处置记录',
      requestId: 7001,
      itemName: '洗胃',
      status: 40,
      statusLabel: '已出结果',
      registerId: rid,
    },
    {
      kind: 'prescription',
      typeLabel: '处方',
      requestId: 64001,
      itemName: '阿莫西林胶囊',
      status: 20,
      statusLabel: '已缴费',
      registerId: rid,
    },
  ]
  return ok({
    registerId: rid,
    list: list,
    checks: list.filter(function (o) { return o.kind === 'check' }),
    inspections: list.filter(function (o) { return o.kind === 'inspection' }),
    disposals: list.filter(function (o) { return o.kind === 'disposal' }),
    prescriptions: list.filter(function (o) { return o.kind === 'prescription' }),
  })
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
  getReportDetail,
  getRegisterOrders,
  setProfileFromLogin(data) {
    if (data.patientId) patientId = data.patientId
    if (data.medicalRecordNo) profile.medicalRecordNo = data.medicalRecordNo
  },
}
