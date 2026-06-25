/** 小程序检验报告展示辅助（字段与 PC LabReportSheet / 后端 LabReportComposer 对齐） */

function flagLabel(flag) {
  if (flag === 'H') return '↑'
  if (flag === 'L') return '↓'
  return ''
}

module.exports = {
  flagLabel,
}
