const { get } = require('../utils/request')
const { useMock, store } = require('../mock/index')

function fetchVisits(params) {
  if (useMock()) return store.listVisits(params)
  return get('/patient/visits', params)
}

function fetchVisitHub(registerId) {
  if (useMock()) return store.getVisitHub(registerId)
  return get('/patient/visits/' + registerId + '/hub')
}

/** Hub 详情页路径（就诊记录） */
function visitHubUrl(registerId) {
  return '/pages/medical-record/detail/detail?registerId=' + registerId
}

module.exports = {
  fetchVisits,
  fetchVisitHub,
  visitHubUrl,
}
