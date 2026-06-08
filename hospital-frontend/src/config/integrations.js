/**
 * 跨团队 AI / 大模型集成配置（前端只负责跳转与展示，推理由后端/智能体组实现）
 *
 * 对接说明：
 * - AI 分诊台：GET /ai/triage/assignments → 队列 triageLevel / triageNote
 * - 影像 CNN：hospital-ai FastAPI → aiReportText；本前端跳转 VITE_IMAGING_AI_URL
 * - 检验智能报告：POST /lis/requests/{id}/ai-report → aiReportText（无影像）
 * - 管理员 AI 排班：POST /admin/scheduling/ai-suggest → suggestions[]
 */
export const INTEGRATION_ROUTES = {
  imagingAiWorkbench: '/pacs/imaging-ai',
}

export const INTEGRATION_ENV = {
  /** 大模型训练组 CT 阅片/选取软件（含脑部 CT 进度条等），仅跳转 */
  ctModelViewerUrl: import.meta.env.VITE_CT_MODEL_URL || import.meta.env.VITE_IMAGING_AI_URL || '',
}

export const TRIAGE_LEVEL_MAP = {
  EMERGENCY: { label: '急诊', type: 'danger' },
  URGENT: { label: '优先', type: 'warning' },
  NORMAL: { label: '普通', type: 'success' },
}
