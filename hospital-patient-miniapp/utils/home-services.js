/**
 * 首页四 Tab 服务入口（已定稿）
 */
var billUtil = require('./bill-util')

var STUB = 'stub'

var TABS = [
  { key: 'outpatient', label: '门诊' },
  { key: 'inpatient', label: '住院' },
  { key: 'exam', label: '检查' },
  { key: 'other', label: '其它' },
]

var GRIDS = {
  outpatient: [
    { id: 'register', name: '线上挂号', icon: '挂', action: 'navigate', url: '/pages/register/register' },
    {
      id: 'outpatient_pay',
      name: '门诊缴费',
      icon: '缴',
      action: 'navigate',
      url: billUtil.buildBillsUrl({ tab: 'pending', scope: 'outpatient' }),
    },
    { id: 'registers', name: '挂号记录', icon: '单', action: 'navigate', url: '/pages/registers/registers' },
    { id: 'triage', name: '智能分诊', icon: '诊', action: 'navigate', url: '/pages/disease-guide/disease-guide' },
    { id: 'record', name: '电子病历', icon: '历', action: 'navigate', url: '/pages/medical-record/medical-record' },
  ],
  inpatient: [
    { id: 'ip_prepay', name: '在院预交', icon: '交', action: STUB, stubMsg: '住院模块暂未开通，请至窗口办理' },
    { id: 'ip_cost', name: '住院费用查询', icon: '费', action: STUB, stubMsg: '住院模块暂未开通' },
    { id: 'escort', name: '电子陪护证', icon: '护', action: STUB, stubMsg: '住院模块暂未开通' },
    { id: 'record_copy', name: '病案复印', icon: '案', action: STUB, stubMsg: '住院模块暂未开通' },
  ],
  exam: [
    { id: 'lab_report', name: '检验报告', icon: '验', action: 'navigate', url: '/pages/reports/reports?type=lab' },
    { id: 'exam_report', name: '检查报告', icon: '影', action: 'navigate', url: '/pages/reports/reports?type=exam' },
    {
      id: 'exam_pending',
      name: '待缴清单',
      icon: '缴',
      action: 'navigate',
      url: billUtil.buildBillsUrl({ tab: 'pending', scope: 'exam' }),
    },
  ],
  other: [
    { id: 'revisit', name: '智能复诊', icon: '复', action: STUB, stubMsg: '智能复诊即将开通，敬请期待' },
    { id: 'e_invoice', name: '电子发票', icon: '票', action: STUB, stubMsg: '电子发票暂未开通' },
  ],
}

function buildGridItems(tabKey) {
  var raw = GRIDS[tabKey] || []
  return raw.map(function (item) {
    return { id: item.id, name: item.name, icon: item.icon }
  })
}

function findGridItem(tabKey, id) {
  var list = GRIDS[tabKey] || []
  for (var i = 0; i < list.length; i += 1) {
    if (list[i].id === id) return list[i]
  }
  return null
}

module.exports = {
  STUB,
  TABS,
  GRIDS,
  buildGridItems,
  findGridItem,
}
