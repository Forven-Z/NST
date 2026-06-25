const dict = require('./dict')
const phoneUtil = require('../utils/phone')

let patientId = 10001
let profile = {
  id: 10001,
  realName: '测试患者',
  medicalRecordNo: 'MR202606040100',
  gender: 1,
  birthDate: '',
  phone: '13800138001',
  idCard: '110101199001011234',
  address: '',
}

const familyMembers = []
/** 非空手机号 -> memberPatientId（Mock 对齐 ux_patient_phone） */
const phoneRegistry = new Map()

function syncPhoneRegistry() {
  phoneRegistry.clear()
  if (profile.phone) phoneRegistry.set(profile.phone, patientId)
  familyMembers.forEach(function (m) {
    if (m.phone) phoneRegistry.set(m.phone, m.memberPatientId)
  })
}

let nextRegisterId = 31000
let nextBillId = 82000
let nextPaymentId = 90000
let nextRefundId = 91000
const registers = []
const bills = []
const payments = []
const refunds = []
const triageSessions = {}

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

function mergeMedicalBookBills(list) {
  var medicalByRegister = {}
  list.forEach(function (b) {
    if (b.bizType === 'MEDICAL_BOOK' && b.registerId) {
      medicalByRegister[b.registerId] = b
    }
  })
  return list
    .filter(function (b) { return b.bizType !== 'MEDICAL_BOOK' })
    .map(function (b) {
      if (b.bizType !== 'REGISTER' && b.bizType !== 'REGIST') return b
      var mb = b.registerId && medicalByRegister[b.registerId]
      if (!mb) return b
      return Object.assign({}, b, {
        amount: Number(b.amount) + Number(mb.amount),
        billTitle: '挂号费（含病历本）',
      })
    })
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
  list = mergeMedicalBookBills(list)
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
    channel: 'WECHAT',
    channelLabel: '微信',
    registerId: 3000,
    patientId: 10001,
    summary: '挂号费 · 内科',
  })
}

seedRegisters()

function login(body) {
  return patientLogin(body)
}

function findPatientByCredentials(phone, idCard) {
  var p = String(phone || '').trim()
  var c = String(idCard || '').trim().toUpperCase()
  if (profile.phone === p && profile.idCard && profile.idCard.toUpperCase() === c) {
    return { patientId: 10001, data: profile }
  }
  for (var i = 0; i < familyMembers.length; i++) {
    var m = familyMembers[i]
    if (m.idCard && m.idCard.toUpperCase() === c && m.phone === p) {
      return { patientId: m.memberPatientId, data: m }
    }
  }
  return null
}

function sessionPayload(id, data) {
  return {
    accessToken: 'mock-patient-token-' + id,
    expiresIn: 7200,
    patientId: id,
    medicalRecordNo: data.medicalRecordNo || profile.medicalRecordNo,
    realName: data.realName || '就诊人',
    isNewPatient: false,
  }
}

function applyProfileFields(target, data) {
  if (!target || !data) return
  if (data.realName) target.realName = data.realName
  if (data.idCard) target.idCard = data.idCard
  if (data.phone) target.phone = data.phone
  if (data.gender) target.gender = data.gender
  if (data.birthDate) target.birthDate = data.birthDate
  if (data.address != null) target.address = data.address
}

function activateMockPatient(id, data) {
  patientId = id
  if (id === 10001) {
    applyProfileFields(profile, data)
    syncPhoneRegistry()
    return
  }
  var member = familyMembers.find(function (m) { return m.memberPatientId === id })
  if (member) applyProfileFields(member, data)
}

function patientLogin(body) {
  body = body || {}
  var phone = String(body.phone || '').trim()
  var idCard = String(body.idCard || '').trim().toUpperCase()
  var realName = String(body.realName || '').trim()
  if (!realName) {
    return Promise.reject(new Error('请填写姓名'))
  }
  if (!phone.match(/^1\d{10}$/) || idCard.length !== 18) {
    return Promise.reject(new Error('手机号或身份证格式不正确'))
  }
  if (!body.gender || (body.gender !== 1 && body.gender !== 2)) {
    return Promise.reject(new Error('请选择性别'))
  }
  if (!body.birthDate) {
    return Promise.reject(new Error('请选择出生日期'))
  }
  var profileData = {
    realName: realName,
    idCard: idCard,
    phone: phone,
    gender: body.gender,
    birthDate: body.birthDate,
    address: String(body.address || '').trim(),
  }
  var found = findPatientByCredentials(phone, idCard)
  if (found) {
    applyProfileFields(found.data, profileData)
    activateMockPatient(found.patientId, profileData)
    return ok(sessionPayload(found.patientId, found.data))
  }
  return assertPhoneAvailable(phone, null).then(function (normalized) {
    var newId = 10000 + familyMembers.length + 2
    var member = Object.assign({
      memberPatientId: newId,
      medicalRecordNo: 'MR20260604' + String(3000 + familyMembers.length),
      relationType: 4,
      isSelf: false,
    }, profileData, { phone: normalized })
    familyMembers.push(member)
    if (normalized) phoneRegistry.set(normalized, newId)
    activateMockPatient(newId, member)
    return ok(Object.assign(sessionPayload(newId, member), { isNewPatient: true }))
  })
}

function switchAccount(targetPatientId) {
  var target = Number(targetPatientId)
  if (target === patientId) {
    var cur = target === 10001 ? profile : familyMembers.find(function (m) { return m.memberPatientId === target })
    return ok(sessionPayload(target, cur || profile))
  }
  if (target === 10001) {
    activateMockPatient(10001, profile)
    return ok(sessionPayload(10001, profile))
  }
  var member = familyMembers.find(function (m) { return m.memberPatientId === target })
  if (!member) {
    return Promise.reject(new Error('无权切换到该就诊账户'))
  }
  activateMockPatient(target, member)
  return ok(sessionPayload(target, member))
}

function assertPhoneAvailable(phone, excludePatientId) {
  return normalizeOptionalPhone(phone).then(function (normalized) {
    if (!normalized) return ''
    var owner = phoneRegistry.get(normalized)
    if (owner != null && owner !== excludePatientId) {
      return Promise.reject(new Error('该手机号已被其他就诊人使用'))
    }
    return normalized
  })
}

function getProfile() {
  if (patientId === 10001) {
    return ok(Object.assign({ id: 10001 }, profile))
  }
  var m = familyMembers.find(function (x) { return x.memberPatientId === patientId })
  if (m) {
    return ok({
      id: m.memberPatientId,
      realName: m.realName,
      medicalRecordNo: m.medicalRecordNo,
      gender: m.gender,
      birthDate: m.birthDate,
      phone: m.phone,
      idCard: m.idCard,
      address: m.address || '',
    })
  }
  return ok(Object.assign({ id: patientId }, profile))
}

function updateProfile(body) {
  body = body || {}
  return assertPhoneAvailable(body.phone, patientId).then(function (normalized) {
    if (profile.phone && profile.phone !== normalized) {
      phoneRegistry.delete(profile.phone)
    }
    Object.assign(profile, body)
    profile.phone = normalized
    if (normalized) phoneRegistry.set(normalized, patientId)
    return ok({ ...profile })
  })
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
    gender: profile.gender || 1,
    birthDate: profile.birthDate || null,
    idCard: profile.idCard || null,
    phone: profile.phone || null,
    address: profile.address || '',
    relationType: 0,
    noIdCard: false,
    isSelf: true,
  }
  return ok({ list: [self, ...familyMembers] })
}

function addFamily(body) {
  const noIdCard = !!body.noIdCard
  if (noIdCard) {
    if (!body.guardianName || !body.guardianIdCard || !body.guardianPhone) {
      return Promise.reject(new Error('请填写陪诊人信息'))
    }
    if (profile.idCard && body.guardianIdCard.toUpperCase() !== profile.idCard.toUpperCase()) {
      return Promise.reject(new Error('陪诊人须为当前账号本人（身份证与本人档案一致）'))
    }
    const member = {
      memberPatientId: 10000 + familyMembers.length + 2,
      realName: body.realName,
      medicalRecordNo: 'MR20260604' + String(2000 + familyMembers.length),
      idCard: null,
      gender: body.gender || 1,
      birthDate: body.birthDate || null,
      phone: null,
      address: body.address || '',
      relationType: body.relationType || 3,
      noIdCard: true,
      guardianName: body.guardianName,
      guardianIdCard: body.guardianIdCard,
      guardianPhone: body.guardianPhone,
      isSelf: false,
    }
    familyMembers.push(member)
    return ok(member)
  }
  return assertPhoneAvailable(body.phone, null).then(function (normalized) {
    const member = {
      memberPatientId: 10000 + familyMembers.length + 2,
      realName: body.realName,
      medicalRecordNo: 'MR20260604' + String(2000 + familyMembers.length),
      idCard: body.idCard,
      gender: body.gender || 1,
      birthDate: body.birthDate || null,
      phone: normalized,
      address: body.address || '',
      relationType: body.relationType || 4,
      noIdCard: false,
      isSelf: false,
    }
    familyMembers.push(member)
    if (normalized) phoneRegistry.set(normalized, member.memberPatientId)
    return ok(member)
  })
}

function normalizeOptionalPhone(phone) {
  const normalized = phoneUtil.normalizePhoneOptional(phone)
  if (normalized === null) {
    return Promise.reject(new Error('手机号格式不正确'))
  }
  return Promise.resolve(normalized)
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
  list = list.map(function (p) {
    return Object.assign({}, p, {
      channelLabel: p.channelLabel || p.channel || '—',
    })
  })
  return ok({ list, page: 1, pageSize: 20 })
}

function listRefunds(params) {
  params = params || {}
  let list = [...refunds]
  if (params.patientId) {
    list = list.filter(function (r) { return r.patientId === Number(params.patientId) })
  }
  if (params.registerId) {
    list = list.filter(function (r) { return r.registerId === Number(params.registerId) })
  }
  list.sort(function (a, b) { return (b.refundId || 0) - (a.refundId || 0) })
  list = list.map(function (r) {
    return Object.assign({}, r, {
      channelLabel: r.channelLabel || r.channel || '—',
      amount: r.amount != null ? r.amount : r.refundAmount,
    })
  })
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
      channel: 'WECHAT',
      channelLabel: '微信',
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
  if (reg.visitState === 0) {
    reg.visitState = 4
    return ok({ registerId, visitState: 4, message: '待支付挂号已取消' })
  }
  if (reg.visitState !== 1 || reg.callTime) {
    return Promise.reject(new Error('仅已挂号且医生未叫号可退号'))
  }
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
    reportType: 'lab',
    reportNo: 'INS-20260604-05001',
    patientName: '微信用户',
    reportTime: '2026-06-04 11:20',
    summary: '白细胞偏高，其余指标未见明显异常。',
    purpose: '发热查因',
    bodyPart: '',
    registerId: 3001,
    header: {
      patientName: '微信用户',
      genderLabel: '男',
      ageLabel: '35岁',
      sampleType: '全血',
      department: '内科',
      clinicalDiagnosis: '上呼吸道感染',
    },
    items: [
      { code: 'WBC', name: '白细胞', result: '12.8', unit: '×10⁹/L', refRange: '3.5-9.5', flag: 'H' },
      { code: 'RBC', name: '红细胞', result: '4.65', unit: '×10¹²/L', refRange: '4.3-5.8', flag: 'N' },
      { code: 'HGB', name: '血红蛋白', result: '138', unit: 'g/L', refRange: '130-175', flag: 'N' },
      { code: 'PLT', name: '血小板', result: '210', unit: '×10⁹/L', refRange: '125-350', flag: 'N' },
    ],
    analysis: {
      aiReportText: '【AI 智能检验报告 · 血常规】\n异常项目：白细胞 12.8×10⁹/L↑。\nAI 提示：存在偏离参考范围指标，建议结合临床排查感染可能，请检验师审核确认。',
      doctorReportText: '已镜检复查，结合临床考虑细菌性感染可能。',
      aiReportStatus: 'READY',
    },
    footer: {
      testTime: '2026-06-04 11:10',
      reportTime: '2026-06-04 11:20',
      testerName: '周检验',
      reviewerName: '周检验',
    },
    resultText: '白细胞 12.8×10⁹/L↑，其余指标正常。',
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
    reportType: 'disposal',
    recordNo: 'DIS-20260604-07001',
    patientName: '微信用户',
    reportTime: '2026-06-04 16:10',
    summary: '洗胃完成，患者生命体征平稳。',
    purpose: '急性中毒',
    bodyPart: '',
    registerId: 3001,
    header: {
      patientName: '微信用户',
      genderLabel: '男',
      ageLabel: '35岁',
      medicalRecordNo: 'MR202606040100',
      department: '处置科',
      itemName: '洗胃',
      clinicalDiagnosis: '急性中毒',
      purpose: '急性中毒',
    },
    record: {
      processText: '左侧卧位，16Fr 胃管；温盐水 500ml 入/450ml 出（澄清）；过程顺利，未见误吸。',
      outcomeText: '洗胃后 BP 118/76 mmHg，HR 78 次/分，SpO₂ 98%；患者未诉明显不适，嘱观察。',
    },
    footer: {
      executeTime: '2026-06-04 16:00',
      recordTime: '2026-06-04 16:10',
      executorName: '孙处置',
      recorderName: '孙处置',
    },
    resultText: '【处置过程】\n左侧卧位洗胃…\n【观察与结果】\n生命体征平稳…',
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
    reportType: row.reportType,
    recordNo: row.recordNo,
    registerId: row.registerId,
    patientId: row.patientId,
    purpose: row.purpose,
    bodyPart: row.bodyPart,
    resultText: row.resultText,
    reportTime: row.reportTime,
    header: row.header,
    items: row.items,
    analysis: row.analysis,
    record: row.record,
    footer: row.footer,
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

function newTriageSessionId() {
  return 'mock-triage-' + Date.now() + '-' + Math.floor(Math.random() * 1000)
}

function matchTriageDept(text) {
  var value = String(text || '')
  if (value.indexOf('儿童') >= 0 || value.indexOf('小孩') >= 0 || value.indexOf('宝宝') >= 0) {
    return { deptId: 8, deptCode: 'PEDIATRICS', deptName: '儿科', matchedDeptName: '儿科', confidence: 0.82 }
  }
  if (value.indexOf('孕') >= 0 || value.indexOf('产检') >= 0 || value.indexOf('妇科') >= 0) {
    return { deptId: 9, deptCode: 'OBGYN', deptName: '妇产科', matchedDeptName: '妇产科', confidence: 0.82 }
  }
  if (value.indexOf('外伤') >= 0 || value.indexOf('肿块') >= 0 || value.indexOf('摔') >= 0 || value.indexOf('伤口') >= 0) {
    return { deptId: 6, deptCode: 'SURGERY', deptName: '外科', matchedDeptName: '外科', confidence: 0.78 }
  }
  return { deptId: 1, deptCode: 'INTERNAL', deptName: '内科', matchedDeptName: '内科', confidence: 0.74 }
}

function hasAny(text, words) {
  return words.some(function (word) { return text.indexOf(word) >= 0 })
}

function triageChat(body) {
  body = body || {}
  var sessionId = body.sessionId || newTriageSessionId()
  var session = triageSessions[sessionId] || { sessionId: sessionId, messages: [] }
  triageSessions[sessionId] = session

  var message = String(body.message || '').trim()
  if (!message) {
    return ok({
      sessionId: sessionId,
      reply: '请简单描述你的主要不适，例如：头痛两天、咳嗽发热、腹痛腹泻、皮疹瘙痒等。',
      stage: 'ASKING',
      needMoreInfo: true,
      needRegister: null,
      emergency: false,
      emergencyReason: '',
      summary: '',
      askedQuestions: ['请简单描述你的主要不适。'],
      quickReplies: ['发热咳嗽', '头痛头晕', '腹痛腹泻', '胸闷胸痛', '皮疹瘙痒', '其他不适'],
      recommendedDepartments: [],
      safetyNotice: '本结果仅用于就诊分诊参考，不能替代医生诊断。如症状严重或持续加重，请及时就医。',
    })
  }

  session.messages.push(message)
  var fullText = session.messages.join(' ')
  var emergencyWords = ['胸痛', '呼吸困难', '意识不清', '大出血', '剧烈头痛', '昏迷', '抽搐']
  var durationWords = ['今天', '昨天', '小时', '天', '周', '月', '刚刚', '两天', '三天']
  var severityWords = ['轻微', '明显', '严重', '剧烈', '很痛', '加重', '持续', '反复']

  if (hasAny(fullText, emergencyWords)) {
    return ok({
      sessionId: sessionId,
      reply: '你描述的症状可能存在急症风险，建议立即前往急诊或拨打 120，不建议仅通过线上分诊处理。',
      stage: 'EMERGENCY',
      needMoreInfo: false,
      needRegister: false,
      emergency: true,
      emergencyReason: '描述中包含急症风险信号',
      summary: '患者描述：' + fullText,
      askedQuestions: [],
      quickReplies: [],
      recommendedDepartments: [],
      safetyNotice: '本结果仅用于就诊分诊参考，不能替代医生诊断。如症状严重或持续加重，请及时就医。',
    })
  }

  if (session.messages.length < 2 && (!hasAny(fullText, durationWords) || !hasAny(fullText, severityWords))) {
    var questions = []
    if (!hasAny(fullText, durationWords)) questions.push('这个症状大概持续多久了？')
    if (!hasAny(fullText, severityWords)) questions.push('症状程度是轻微、明显还是非常剧烈？')
    return ok({
      sessionId: sessionId,
      reply: questions.join(' '),
      stage: 'ASKING',
      needMoreInfo: true,
      needRegister: null,
      emergency: false,
      emergencyReason: '',
      summary: '患者描述：' + fullText,
      askedQuestions: questions,
      quickReplies: questions.join(' ').indexOf('多久') >= 0
        ? ['今天刚出现', '两三天', '一周以上', '不清楚']
        : ['轻微', '明显', '非常剧烈', '不清楚'],
      recommendedDepartments: [],
      safetyNotice: '本结果仅用于就诊分诊参考，不能替代医生诊断。如症状严重或持续加重，请及时就医。',
    })
  }

  var dept = matchTriageDept(fullText)
  return ok({
    sessionId: sessionId,
    reply: '根据你提供的信息，建议先选择推荐科室挂号，由医生进一步评估。',
    stage: 'RECOMMENDED',
    needMoreInfo: false,
    needRegister: true,
    emergency: false,
    emergencyReason: '',
    summary: '患者描述：' + fullText,
    askedQuestions: [],
    quickReplies: [],
    recommendedDepartments: [{
      deptId: dept.deptId,
      deptCode: dept.deptCode,
      deptName: dept.deptName,
      matchedDeptName: dept.matchedDeptName,
      confidence: dept.confidence,
      reason: '根据症状描述，建议先到' + dept.deptName + '进行初诊评估。',
      nextAction: '可点击去挂号，选择该科室号源。',
    }],
    safetyNotice: '本结果仅用于就诊分诊参考，不能替代医生诊断。如症状严重或持续加重，请及时就医。',
  })
}

module.exports = {
  login,
  patientLogin,
  switchAccount,
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
  listRefunds,
  mockPay,
  cancelRegister,
  getMedicalRecord,
  listReports,
  getReportDetail,
  getRegisterOrders,
  triageChat,
  setProfileFromLogin(data) {
    if (data.patientId) patientId = data.patientId
    if (data.medicalRecordNo) profile.medicalRecordNo = data.medicalRecordNo
  },
}
