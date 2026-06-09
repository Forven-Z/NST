Component({
  properties: {
    title: { type: String, value: '' },
    bgColor: { type: String, value: '#1677ff' },
    textColor: { type: String, value: '#ffffff' },
    /** 强制显示返回；默认根据页面栈判断 */
    forceBack: { type: Boolean, value: false },
  },

  data: {
    statusBarHeight: 20,
    navBarHeight: 44,
    showBack: false,
  },

  lifetimes: {
    attached() {
      this.updateLayout()
    },
  },

  pageLifetimes: {
    show() {
      this.updateLayout()
    },
  },

  methods: {
    updateLayout() {
      const sys = wx.getSystemInfoSync()
      let navBarHeight = 44
      try {
        const menu = wx.getMenuButtonBoundingClientRect()
        if (menu && menu.top) {
          navBarHeight = (menu.top - sys.statusBarHeight) * 2 + menu.height
        }
      } catch (e) {
        // use default
      }
      const pages = getCurrentPages()
      this.setData({
        statusBarHeight: sys.statusBarHeight || 20,
        navBarHeight: navBarHeight,
        showBack: this.properties.forceBack || pages.length > 1,
      })
    },

    onBack() {
      const pages = getCurrentPages()
      if (pages.length > 1) {
        wx.navigateBack({ delta: 1 })
        return
      }
      wx.switchTab({ url: '/pages/home/home' })
    },
  },
})
