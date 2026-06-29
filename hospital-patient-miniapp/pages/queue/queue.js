const { fetchQueueStatus } = require('../../api/patient')

var POLL_MS = 30000

Page({
  data: {
    loading: false,
    loadError: false,
    registerId: '',
    info: null,
  },

  onLoad(options) {
    this.setData({ registerId: options.registerId || '' })
    if (options.registerId) this.load()
  },

  onShow() {
    this.startPoll()
  },

  onHide() {
    this.stopPoll()
  },

  onUnload() {
    this.stopPoll()
  },

  startPoll() {
    this.stopPoll()
    if (!this.data.registerId) return
    var that = this
    this._pollTimer = setInterval(function () {
      that.load(true)
    }, POLL_MS)
  },

  stopPoll() {
    if (this._pollTimer) {
      clearInterval(this._pollTimer)
      this._pollTimer = null
    }
  },

  load(silent) {
    var that = this
    if (!silent) this.setData({ loading: true, loadError: false })
    return fetchQueueStatus(this.data.registerId).then(function (res) {
      var info = res.data || null
      if (info) {
        var ahead = Number(info.aheadCount)
        var state = Number(info.visitState)
        if (ahead === 0 && state === 2) {
          info.queueHint = '即将轮到您，请前往诊室候诊'
        } else if (ahead === 0 && state === 1) {
          info.queueHint = info.queueHint || '您排在队列前列，请留意叫号'
        }
      }
      that.setData({ info: info, loading: false, loadError: false })
    }).catch(function () {
      if (!silent) {
        that.setData({ loading: false, loadError: true })
      }
    })
  },

  onRetry() {
    this.load()
  },

  onPullDownRefresh() {
    this.load().finally(function () {
      wx.stopPullDownRefresh()
    })
  },
})
