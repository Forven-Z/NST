/**
 * Mock：智能体组 / 大模型组负责的真实 AI 报告占位数据
 * 前端 Mock：检验/处置 instrumentData（仪器只读）与 aiReportText（AI 生成可编辑）
 * 检查报告走 checkReport.generateCheckAiReportStub，不在此维护 CHECK 模板。
 */

const INSTRUMENT_SAMPLES = {
  INSPECTION: {
    血常规: `【仪器原始数据 · 只读】
白细胞 WBC        6.2 ×10⁹/L    参考 3.5-9.5
红细胞 RBC        4.65×10¹²/L   参考 4.3-5.8
血红蛋白 Hb       138 g/L       参考 130-175
血小板 PLT        210 ×10⁹/L    参考 125-350
中性粒细胞%       62.0 %        参考 40-75
采样时间：仪器自动记录`,
    default: `【仪器原始数据 · 只读】
检验指标由 LIS 仪器自动上传，本区域不可修改。`,
  },
  DISPOSAL: {
    default: `【执行记录 · 只读】
处置过程由执行护士/技师系统记录，本区域不可修改。`,
  },
}

const AI_REPORT_SAMPLES = {
  INSPECTION: {
    血常规: `【AI 智能检验报告】
综合血常规指标：白细胞、红细胞、血红蛋白、血小板均在参考范围内，中性粒细胞比例正常。
AI 提示：未见明显感染或贫血征象，建议结合临床症状继续观察。`,
    default: `【AI 智能检验报告】
各项指标与参考范围比对后未见明显异常模式。`,
  },
  DISPOSAL: {
    default: `【AI 处置摘要】
处置过程顺利，患者生命体征平稳。`,
  },
}

export function mockInstrumentData(techType, itemName) {
  const bucket = INSTRUMENT_SAMPLES[techType] || INSTRUMENT_SAMPLES.INSPECTION
  return bucket[itemName] || bucket.default
}

export function mockAiReportText(techType, itemName) {
  const bucket = AI_REPORT_SAMPLES[techType] || AI_REPORT_SAMPLES.INSPECTION
  return bucket[itemName] || bucket.default
}

export function mockAiSchedulingSuggestions(schedules) {
  return (schedules || []).slice(0, 5).map((s, i) => {
    const canReplace = i % 2 === 0
    const newQuota = s.totalQuota + 3
    const newRemain = Math.min(s.remainQuota + 3, newQuota)
    return {
      schedulingId: s.schedulingId,
      workDate: s.workDate,
      noonLabel: s.noonLabel,
      employeeName: s.employeeName,
      suggestion: canReplace
        ? `AI 建议：${s.workDate} ${s.noonLabel} 将号源由 ${s.totalQuota} 增至 ${newQuota}，优化负载均衡`
        : `AI 建议：维持当前排班，号源利用率适中`,
      confidence: canReplace ? 0.82 : 0.71,
      replaceable: canReplace,
      proposedSchedule: canReplace
        ? {
            totalQuota: newQuota,
            remainQuota: newRemain,
            aiOptimized: true,
          }
        : null,
    }
  })
}
