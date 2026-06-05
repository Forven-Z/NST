const { USE_MOCK } = require('../config')
const store = require('./store')

function useMock() {
  return USE_MOCK
}

module.exports = { useMock, store }
