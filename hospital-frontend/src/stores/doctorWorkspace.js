import { defineStore } from 'pinia'
import { ref } from 'vue'

const DRAFT_LABELS = {
  CHECK: '检查',
  INSPECTION: '检验',
  DISPOSAL: '处置',
  PRESCRIPTION: '处方',
}

function formatDraftMessage(type, draft) {
  const label = DRAFT_LABELS[type] || type
  const lines = (draft.items || []).map((it) => {
    const name = it.itemName || it.drugName || '项目'
    const extra = it.purpose ? ` — ${it.purpose}` : ''
    return `· ${name}${extra}`
  })
  const body = lines.length ? lines.join('\n') : '（暂无具体项目）'
  return `【AI ${label}草稿已生成】\n${draft.aiReason || ''}\n${body}\n\n请在弹窗中编辑并「确认提交」（ADR-015 三步流程）。`
}

export const useDoctorWorkspaceStore = defineStore('doctorWorkspace', () => {
  const aiDrafts = ref({
    CHECK: null,
    INSPECTION: null,
    DISPOSAL: null,
    PRESCRIPTION: null,
  })

  const draftMessages = ref([])

  function setAiDraft(type, draft) {
    aiDrafts.value[type] = draft
    draftMessages.value.push({
      id: `${type}-${Date.now()}`,
      role: 'assistant',
      text: formatDraftMessage(type, draft),
      draftType: type,
    })
  }

  function getPreselectedTechIds(type) {
    const draft = aiDrafts.value[type]
    if (!draft?.items?.length) return []
    return draft.items.map((it) => it.medicalTechnologyId).filter(Boolean)
  }

  function getDraftItemMeta(type, techId) {
    const draft = aiDrafts.value[type]
    const item = draft?.items?.find((it) => it.medicalTechnologyId === techId)
    return item || {}
  }

  function getPreselectedDrugIds() {
    const draft = aiDrafts.value.PRESCRIPTION
    if (!draft?.items?.length) return []
    return draft.items.map((it) => it.drugId).filter(Boolean)
  }

  function clearForNewPatient() {
    aiDrafts.value = {
      CHECK: null,
      INSPECTION: null,
      DISPOSAL: null,
      PRESCRIPTION: null,
    }
    draftMessages.value = []
  }

  return {
    aiDrafts,
    draftMessages,
    setAiDraft,
    getPreselectedTechIds,
    getDraftItemMeta,
    getPreselectedDrugIds,
    clearForNewPatient,
  }
})
