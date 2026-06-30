/**
 * 跳转报告 Tab（switchTab 无法带 query，经 globalData 传递筛选类型）
 */
function openReportsTab(type) {
  var app = getApp()
  if (!app.globalData) {
    app.globalData = {}
  }
  app.globalData.pendingReportTab = type || 'all'
  wx.switchTab({ url: '/pages/reports/reports' })
}

function consumePendingReportTab(defaultTab) {
  var app = getApp()
  var pending = app.globalData && app.globalData.pendingReportTab
  if (pending) {
    app.globalData.pendingReportTab = null
    return pending
  }
  return defaultTab || 'all'
}

module.exports = {
  openReportsTab,
  consumePendingReportTab,
}
