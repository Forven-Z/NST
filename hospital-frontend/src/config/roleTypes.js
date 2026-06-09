export const ROLE_TYPE_OPTIONS = [
  { value: 'OUTPATIENT_DOCTOR', label: '门诊医生' },
  { value: 'CHECK_DOCTOR', label: '检查医生' },
  { value: 'LAB_DOCTOR', label: '检验医生' },
  { value: 'DISPOSAL_DOCTOR', label: '处置医生' },
  { value: 'PHARMACIST', label: '药师' },
  { value: 'REGISTRAR', label: '挂号收费员' },
  { value: 'ADMIN', label: '管理员' },
]

export const DEPT_TYPE_OPTIONS = [
  { value: 1, label: '临床门诊' },
  { value: 2, label: '医技科室' },
  { value: 3, label: '药房' },
  { value: 4, label: '行政' },
]

export function roleTypeLabel(value) {
  return ROLE_TYPE_OPTIONS.find((r) => r.value === value)?.label || value
}
