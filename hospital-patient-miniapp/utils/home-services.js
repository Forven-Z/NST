/**
 * 首页四 Tab 服务入口
 */
var billUtil = require('./bill-util')
var reportNav = require('./report-nav')

var STUB = 'stub'

var TABS = [
  { key: 'outpatient', label: '门诊' },
  { key: 'inpatient', label: '住院' },
  { key: 'reports', label: '报告' },
  { key: 'other', label: '其它' },
]

var GRIDS = {
  outpatient: [
    { id: 'register', name: '线上挂号', icon: '挂', action: 'navigate', url: '/pages/register/register' },
    {
      id: 'pending_bills',
      name: '待缴费用',
      icon: '缴',
      action: 'navigate',
      url: billUtil.buildBillsUrl({ tab: 'pending' }),
    },
    { id: 'registers', name: '挂号记录', icon: '单', action: 'navigate', url: '/pages/registers/registers' },
    { id: 'triage', name: '智能分诊', icon: '诊', action: 'navigate', url: '/pages/disease-guide/disease-guide' },
    { id: 'record', name: '就诊记录', icon: '历', action: 'navigate', url: '/pages/medical-record/medical-record' },
  ],
  inpatient: [
    { id: 'ip_prepay', name: '在院预交', icon: '交', action: STUB, stubMsg: '住院模块暂未开通，请至窗口办理' },
    { id: 'ip_cost', name: '住院费用查询', icon: '费', action: STUB, stubMsg: '住院模块暂未开通，请至窗口办理' },
    { id: 'escort', name: '电子陪护证', icon: '护', action: STUB, stubMsg: '住院模块暂未开通，请至窗口办理' },
    { id: 'record_copy', name: '病案复印', icon: '案', action: STUB, stubMsg: '住院模块暂未开通，请至窗口办理' },
  ],
  reports: [
    { id: 'all_reports', name: '全部报告', icon: '全', action: 'openReports', reportTab: 'all' },
    { id: 'lab_report', name: '检验报告', icon: '验', action: 'openReports', reportTab: 'lab' },
    { id: 'exam_report', name: '检查报告', icon: '影', action: 'openReports', reportTab: 'exam' },
    { id: 'disposal_report', name: '处置记录', icon: '置', action: 'openReports', reportTab: 'disposal' },
  ],
  other: [
    { id: 'revisit', name: '智能复诊', icon: '复', action: STUB, stubMsg: '智能复诊即将开通，敬请期待' },
    { id: 'e_invoice', name: '电子发票', icon: '票', action: STUB, stubMsg: '电子发票暂未开通，请至窗口索取' },
  ],
}

function buildGridItems(tabKey) {
  var raw = GRIDS[tabKey] || []
  return raw.map(function (item) {
    return {
      id: item.id,
      name: item.name,
      icon: item.icon,
      comingSoon: item.action === STUB,
    }
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
  reportNav,
}
