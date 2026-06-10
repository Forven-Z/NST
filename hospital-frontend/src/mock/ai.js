import { mockResult } from '../utils/mock'
import { mockConfirmClinicalDraft, mockDiagnosisSuggestForRegister } from './doctor'
import { MOCK_DRUGS } from './dict'

let draftSeq = 8000
const draftContext = {}

const DRAFT_ITEMS = {
  CHECK: [{ medicalTechnologyId: 1, itemName: '头部 CT', purpose: '排除颅内病变', bodyPart: '头部', remark: '' }],
  INSPECTION: [{ medicalTechnologyId: 2, itemName: '血常规', purpose: '感染/贫血筛查', bodyPart: '血液', remark: '' }],
  DISPOSAL: [{ medicalTechnologyId: 5, itemName: '洗胃', purpose: '急性中毒', bodyPart: '', remark: '' }],
}

const PRESCRIPTION_DRAFT_ITEMS = [
  {
    drugId: 2,
    drugName: '布洛芬缓释胶囊',
    quantity: 1,
    usageMethod: '口服',
    dosage: '0.3g',
    frequency: 'bid',
    days: 3,
    entrust: '饭后服用',
  },
]

export function mockDiagnosisSuggest(payload) {
  return mockDiagnosisSuggestForRegister(payload.registerId, payload)
}

export function mockClinicalAiDraft(type, registerId) {
  draftSeq += 1
  const items = structuredClone(DRAFT_ITEMS[type] || [])
  draftContext[draftSeq] = { type, registerId, items, draftType: type }
  return mockResult({
    stub: true,
    draftId: draftSeq,
    draftType: type,
    registerId,
    aiReason: '【Mock】AI 根据病历主诉生成的申请草稿，请医生核对后确认',
    items,
  })
}

export function mockUpdateClinicalAiDraft(type, draftId, body) {
  const ctx = draftContext[draftId]
  if (!ctx || ctx.type !== type) throw new Error('草稿不存在或类型不匹配')
  if (body.items) ctx.items = structuredClone(body.items)
  if (body.aiReason) ctx.aiReason = body.aiReason
  return mockResult({
    draftId,
    draftType: type,
    registerId: ctx.registerId,
    aiReason: ctx.aiReason,
    items: ctx.items,
  })
}

export function mockConfirmAiDraft(type, draftId) {
  const ctx = draftContext[draftId] || {}
  const items = ctx.items || DRAFT_ITEMS[type] || []
  return mockConfirmClinicalDraft(type, draftId, ctx.registerId, items)
}

export function mockPrescriptionAiDraft(registerId) {
  draftSeq += 1
  const items = structuredClone(PRESCRIPTION_DRAFT_ITEMS)
  draftContext[draftSeq] = { type: 'PRESCRIPTION', registerId, items, draftType: 'PRESCRIPTION' }
  return mockResult({
    stub: true,
    draftId: draftSeq,
    draftType: 'PRESCRIPTION',
    registerId,
    aiReason: '【Mock】AI 根据病历生成的处方草稿，请医生编辑后确认',
    items,
  })
}

export function mockUpdatePrescriptionAiDraft(draftId, body) {
  const ctx = draftContext[draftId]
  if (!ctx || ctx.type !== 'PRESCRIPTION') throw new Error('处方草稿不存在')
  if (body.items) {
    ctx.items = body.items.map((it) => ({
      ...it,
      drugName: MOCK_DRUGS.find((d) => d.id === it.drugId)?.drugName || it.drugName,
    }))
  }
  return mockResult({
    draftId,
    draftType: 'PRESCRIPTION',
    registerId: ctx.registerId,
    items: ctx.items,
  })
}
