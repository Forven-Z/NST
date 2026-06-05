/** @param {string[] | undefined} roles */
export function resolveHomeRoute(roles = []) {
  if (roles.includes('OUTPATIENT_DOCTOR')) return { name: 'doctor-workspace' }
  if (roles.includes('LAB_DOCTOR')) return { name: 'lis-queue' }
  if (roles.includes('CHECK_DOCTOR')) return { name: 'pacs-queue' }
  if (roles.includes('DISPOSAL_DOCTOR')) return { name: 'disposal-queue' }
  if (roles.includes('PHARMACIST')) return { name: 'pharmacy-pending' }
  if (roles.includes('REGISTRAR')) return { name: 'registrar-register' }
  if (roles.includes('ADMIN')) return { name: 'admin-dict' }
  return { name: 'login' }
}

/** @param {string[] | undefined} userRoles @param {string} required */
export function hasRole(userRoles = [], required) {
  if (userRoles.includes('ADMIN')) return true
  return userRoles.includes(required)
}
