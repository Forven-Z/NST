const { addFamilyMember } = require('../../../api/patient')
const { RELATION_TYPES } = require('../../../utils/visit-state')

Page({
  data: {
    saving: false,
    relationTypes: RELATION_TYPES,
    form: {
      realName: '',
      idCard: '',
      phone: '',
      gender: 1,
      relationType: 4,
    },
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`form.${field}`]: e.detail.value })
  },

  onRelationChange(e) {
    this.setData({ 'form.relationType': Number(this.data.relationTypes[e.detail.value].value) })
  },

  async onSubmit() {
    const { realName, idCard } = this.data.form
    if (!realName.trim() || !idCard.trim()) {
      wx.showToast({ title: '请填写姓名和身份证', icon: 'none' })
      return
    }
    this.setData({ saving: true })
    try {
      await addFamilyMember(this.data.form)
      wx.showToast({ title: '添加成功', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 500)
    } catch (err) {
      wx.showToast({ title: err.message || '添加失败', icon: 'none' })
    } finally {
      this.setData({ saving: false })
    }
  },
})
