import { mockResult } from '../utils/mock'
import { mockConfirmClinicalDraft, mockDiagnosisSuggestForRegister } from './doctor'

let draftSeq = 8000
const draftContext = {}

const DRAFT_ITEMS = {
  CHECK: [{ medicalTechnologyId: 1, itemName: '头部 CT', purpose: '排除颅内病变', bodyPart: '头部', remark: '' }],
  INSPECTION: [{ medicalTechnologyId: 2, itemName: '血常规', purpose: '感染/贫血筛查', bodyPart: '血液', remark: '' }],
  DISPOSAL: [{ medicalTechnologyId: 5, itemName: '洗胃', purpose: '急性中毒', bodyPart: '', remark: '' }],
}

export function mockDiagnosisSuggest(registerId) {
  return mockDiagnosisSuggestForRegister(registerId)
}

export function mockClinicalAiDraft(type, registerId) {
  draftSeq += 1
  draftContext[draftSeq] = { type, registerId }
  return mockResult({
    stub: true,
    draftId: draftSeq,
    draftType: type,
    registerId,
    aiReason: '【Mock】AI 根据病历主诉生成的申请草稿，请医生核对后确认',
    items: DRAFT_ITEMS[type] || [],
  })
}

export function mockConfirmAiDraft(type, draftId) {
  const ctx = draftContext[draftId] || {}
  const items = DRAFT_ITEMS[type] || []
  return mockConfirmClinicalDraft(type, draftId, ctx.registerId, items)
}
