/**
 * 跨团队 AI / 大模型集成配置（前端只负责跳转与展示，推理由后端/智能体组实现）
 *
 * 对接说明：
 * - 医生工作台右侧栏：展示 AI 诊疗草稿摘要（无独立 assistant HTTP 接口）
 * - 影像 CNN：Gateway → pacs → hospital-ai 异步 job；阅片内嵌于 /pacs/imaging-ai
 * - 医技结果：LIS/PACS 支持 result-detail + ai-report；disposal 仅 result-detail + 手工录入
 * - 管理员排班 AI：POST /admin/scheduling/ai-suggest、ai-replace（management 规则引擎，已联调）
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
