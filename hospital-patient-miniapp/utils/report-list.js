function groupReportsByDate(list) {
  var groups = []
  var map = {}
  var order = []
  ;(list || []).forEach(function (item) {
    var dateKey = '未知日期'
    if (item.reportTime && item.reportTime.length >= 10) {
      dateKey = item.reportTime.slice(0, 10)
    }
    if (!map[dateKey]) {
      map[dateKey] = []
      order.push(dateKey)
    }
    map[dateKey].push(item)
  })
  order.forEach(function (key) {
    groups.push({ dateLabel: key, items: map[key] })
  })
  return groups
}

module.exports = {
  groupReportsByDate,
}
