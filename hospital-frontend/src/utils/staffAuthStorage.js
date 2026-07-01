/** 医护登录态：sessionStorage 按标签页隔离，避免多角色演示时 localStorage 串号 */
export const STAFF_AUTH_STORAGE_KEY = 'hospital_staff_auth'

/** 读取当前标签页登录态；兼容旧版 localStorage 并迁移到 sessionStorage */
export function readStaffAuthRaw() {
  let raw = sessionStorage.getItem(STAFF_AUTH_STORAGE_KEY)
  if (raw) return raw

  raw = localStorage.getItem(STAFF_AUTH_STORAGE_KEY)
  if (raw) {
    sessionStorage.setItem(STAFF_AUTH_STORAGE_KEY, raw)
    localStorage.removeItem(STAFF_AUTH_STORAGE_KEY)
  }
  return raw
}

export function writeStaffAuthRaw(json) {
  sessionStorage.setItem(STAFF_AUTH_STORAGE_KEY, json)
  localStorage.removeItem(STAFF_AUTH_STORAGE_KEY)
}

export function clearStaffAuthRaw() {
  sessionStorage.removeItem(STAFF_AUTH_STORAGE_KEY)
  localStorage.removeItem(STAFF_AUTH_STORAGE_KEY)
}
