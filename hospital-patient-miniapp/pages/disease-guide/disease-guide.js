const { triageChat } = require('../../api/patient')
const auth = require('../../utils/auth')

function buildMessage(role, text) {
  return {
    id: role + '-' + Date.now() + '-' + Math.floor(Math.random() * 1000),
    role,
    text,
  }
}

function normalizeRecommendations(list) {
  return (list || []).map(function (item) {
    var confidence = Number(item.confidence || 0)
    return Object.assign({}, item, {
      confidenceText: confidence ? Math.round(confidence * 100) + '%' : '',
    })
  })
}

Page({
  data: {
    sessionId: '',
    inputValue: '',
    loading: false,
    messages: [],
    quickReplies: [],
    recommendations: [],
    primaryRecommendation: null,
    showRecommendModal: false,
    safetyNotice: '',
    emergency: false,
    scrollIntoView: '',
  },

  onShow() {
    if (!auth.requireLogin({
      redirect: '/pages/disease-guide/disease-guide',
      message: '智能导诊请先登录病人账户',
    })) return
    if (!this.data.sessionId && !this.data.loading) {
      this.startTriage()
    }
  },

  startTriage() {
    this.setData({
      sessionId: '',
      inputValue: '',
      messages: [],
      quickReplies: [],
      recommendations: [],
      primaryRecommendation: null,
      showRecommendModal: false,
      safetyNotice: '',
      emergency: false,
    })
    this.sendToAssistant('')
  },

  onInput(e) {
    this.setData({ inputValue: e.detail.value })
  },

  onSend() {
    var text = String(this.data.inputValue || '').trim()
    if (!text || this.data.loading) return
    this.setData({ inputValue: '' })
    this.sendToAssistant(text)
  },

  onQuickReply(e) {
    var text = e.currentTarget.dataset.text
    if (!text || this.data.loading) return
    this.sendToAssistant(text)
  },

  sendToAssistant(text) {
    var that = this
    var messages = this.data.messages.slice()
    if (text) {
      messages.push(buildMessage('user', text))
    }
    this.setData({
      messages: messages,
      loading: true,
      quickReplies: [],
      recommendations: [],
      primaryRecommendation: null,
      showRecommendModal: false,
      emergency: false,
      scrollIntoView: messages.length ? messages[messages.length - 1].id : '',
    })

    var account = auth.getCurrentAccount() || {}
    triageChat({
      patientId: account.patientId || wx.getStorageSync('patientId') || null,
      sessionId: this.data.sessionId || undefined,
      message: text,
    }).then(function (res) {
      var data = res.data || {}
      var nextMessages = that.data.messages.slice()
      var recommendations = normalizeRecommendations(data.recommendedDepartments)
      if (data.reply) {
        nextMessages.push(buildMessage('assistant', data.reply))
      }
      var last = nextMessages[nextMessages.length - 1]
      that.setData({
        sessionId: data.sessionId || that.data.sessionId,
        messages: nextMessages,
        quickReplies: data.quickReplies || [],
        recommendations: recommendations,
        primaryRecommendation: recommendations[0] || null,
        showRecommendModal: recommendations.length > 0,
        safetyNotice: data.safetyNotice || that.data.safetyNotice,
        emergency: !!data.emergency,
        scrollIntoView: last ? last.id : '',
      })
    }).catch(function (err) {
      var nextMessages = that.data.messages.concat([
        buildMessage('assistant', err.message || '导诊服务暂时不可用，请稍后重试。'),
      ])
      var last = nextMessages[nextMessages.length - 1]
      that.setData({
        messages: nextMessages,
        scrollIntoView: last.id,
      })
    }).finally(function () {
      that.setData({ loading: false })
    })
  },

  onGoRegister(e) {
    var item = e.currentTarget.dataset.item
    this.goRegisterByDept(item)
  },

  onModalGoRegister() {
    this.goRegisterByDept(this.data.primaryRecommendation)
  },

  onCloseRecommend() {
    this.setData({ showRecommendModal: false })
  },

  noop() {},

  goRegisterByDept(item) {
    if (!item || !item.deptId) return
    var sessionQuery = this.data.sessionId ? `&triageSessionId=${encodeURIComponent(this.data.sessionId)}` : ''
    wx.navigateTo({
      url: `/pages/register/register?deptId=${item.deptId}&deptName=${encodeURIComponent(item.deptName || '')}${sessionQuery}`,
    })
  },
})
