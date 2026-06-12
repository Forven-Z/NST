/**
 * 跨团队 AI / 大模型集成配置（前端只负责跳转与展示，推理由后端/智能体组实现）
 *
 * 对接说明：
 * - AI 分诊台：GET /ai/triage/assignments → 队列 triageLevel / triageNote
 * - 影像 CNN：Gateway → pacs → hospital-ai 异步 job；阅片内嵌于 /pacs/imaging-ai
 * - 医技结果：PUT /lis|pacs|disposal/requests/{id}/result → resultText（无 ai-report 契约接口）
 * - 管理员 AI 排班：POST /admin/scheduling/ai-suggest → suggestions[]
 */
export const INTEGRATION_ROUTES = {
  imagingAiWorkbench: '/pacs/imaging-ai',
}

export const INTEGRATION_ENV = {
  /** 影像工作台内嵌于 /pacs/imaging-ai，前端不直连 :8000 */
  ctModelViewerUrl: '',
}

export const TRIAGE_LEVEL_MAP = {
  EMERGENCY: { label: '急诊', type: 'danger' },
  URGENT: { label: '优先', type: 'warning' },
  NORMAL: { label: '普通', type: 'success' },
}
