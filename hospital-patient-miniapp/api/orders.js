const { get } = require('../utils/request')
const { useMock, store } = require('../mock/index')

function fetchRegisterOrders(registerId) {
  if (useMock()) return store.getRegisterOrders(registerId)
  return get('/patient/registers/' + registerId + '/orders')
}

module.exports = {
  fetchRegisterOrders,
}
