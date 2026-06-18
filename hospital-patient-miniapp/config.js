/** 患者小程序配置（联调默认关 Mock；演示可建 config.local.js 设 USE_MOCK: true） */
const defaults = {
  API_BASE: 'http://127.0.0.1:9000/api/v1',
  /** false = 经 Gateway 联调；true = 本地 mock */
  USE_MOCK: false,
}

let local = {}
try {
  // eslint-disable-next-line import/no-unresolved
  local = require('./config.local.js')
} catch (e) {
  // 无 config.local.js 时使用 defaults
}

module.exports = Object.assign({}, defaults, local)
