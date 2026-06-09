/**
 * 联调配置示例：复制为 config.local.js 即可关闭 Mock
 *
 *   copy config.local.example.js config.local.js
 *
 * config.local.js 已加入 .gitignore，不会提交。
 */
module.exports = {
  API_BASE: 'http://127.0.0.1:9000/api/v1',
  USE_MOCK: false,
}
