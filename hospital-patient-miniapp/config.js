/** 患者小程序配置（默认 Mock；联调见 config.local.js） */
const defaults = {
  API_BASE: 'http://127.0.0.1:9000/api/v1',
  /** true = 本地 mock；false = 经 Gateway 联调后端 */
  USE_MOCK: true,
}

let local = {}
try {
  // eslint-disable-next-line import/no-unresolved
  local = require('./config.local.js')
} catch (e) {
  // 无 config.local.js 时使用 defaults
}

module.exports = Object.assign({}, defaults, local)
