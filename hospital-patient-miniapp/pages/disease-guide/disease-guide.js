const DISEASES = require('../../utils/disease-guide')

Page({
  data: {
    list: DISEASES,
  },

  onSelect(e) {
    const item = e.currentTarget.dataset.item
    wx.navigateTo({
      url: `/pages/register/register?deptId=${item.deptId}&deptName=${encodeURIComponent(item.deptName)}`,
    })
  },
})
