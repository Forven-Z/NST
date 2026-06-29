const { API_BASE, USE_MOCK } = require('../config')

const { getAccessToken } = require('./auth')



const SNAPSHOT_PLANES = [

  { key: 'axial', label: '轴位' },

  { key: 'coronal', label: '冠状' },

  { key: 'sagittal', label: '矢状' },

]



function isExamReportDetail(detail) {

  if (!detail) return false

  if (detail.reportType === 'check') return true

  if (detail.type === 'exam') return true

  return false

}



/**

 * 将 API 返回的路径转为可请求的完整 URL。

 */

function resolveSnapshotUrl(path) {

  if (!path) return ''

  if (path.indexOf('http://') === 0 || path.indexOf('https://') === 0) return path

  if (path.indexOf('/api/v1/') === 0) {

    var base = API_BASE.replace(/\/api\/v1\/?$/, '')

    return base + path

  }

  if (path.indexOf('/') === 0) {

    return API_BASE.replace(/\/$/, '') + path

  }

  return API_BASE.replace(/\/$/, '') + '/' + path.replace(/^\//, '')

}



function writeArrayBufferToTempFile(arrayBuffer, plane) {

  return new Promise(function (resolve, reject) {

    var fs = wx.getFileSystemManager()

    var filePath = wx.env.USER_DATA_PATH + '/exam-snap-' + plane + '-' + Date.now() + '.png'

    fs.writeFile({

      filePath: filePath,

      data: arrayBuffer,

      success: function () {

        resolve(filePath)

      },

      fail: function (err) {

        reject(new Error((err && err.errMsg) || '保存影像失败'))

      },

    })

  })

}



/**

 * 带 JWT 拉取 PNG（wx.request 比 downloadFile 更可靠地携带 Authorization）。

 */

function downloadAuthImageViaDownloadFile(url) {
  return new Promise(function (resolve, reject) {
    wx.downloadFile({
      url: url,
      header: {
        Authorization: 'Bearer ' + getAccessToken(),
      },
      success: function (res) {
        if (res.statusCode === 200 && res.tempFilePath) {
          resolve(res.tempFilePath)
          return
        }
        reject(new Error('影像下载失败（HTTP ' + res.statusCode + '）'))
      },
      fail: function (err) {
        reject(new Error((err && err.errMsg) || '影像下载失败'))
      },
    })
  })
}

function downloadAuthImage(path) {
  var url = resolveSnapshotUrl(path)
  if (!url) return Promise.reject(new Error('无效的图片地址'))

  var token = getAccessToken()
  if (!token) return Promise.reject(new Error('请先登录'))

  return new Promise(function (resolve, reject) {
    wx.request({
      url: url,
      method: 'GET',
      header: {
        Authorization: 'Bearer ' + token,
      },
      responseType: 'arraybuffer',
      success: function (res) {
        if (res.statusCode === 200 && res.data) {
          var plane = (path.match(/\/(axial|coronal|sagittal)(?:\?|$)/i) || [])[1] || 'snap'
          writeArrayBufferToTempFile(res.data, plane).then(resolve).catch(reject)
          return
        }
        downloadAuthImageViaDownloadFile(url).then(resolve).catch(function () {
          reject(new Error('影像下载失败（HTTP ' + res.statusCode + '）'))
        })
      },
      fail: function () {
        downloadAuthImageViaDownloadFile(url).then(resolve).catch(reject)
      },
    })
  })
}



/**

 * 为检查报告详情加载三视图本地临时路径。

 */

function loadExamSnapshots(detail) {

  if (!isExamReportDetail(detail)) {

    return Promise.resolve({ detail: detail, snapshotViews: [], hasSnapshots: false })

  }



  if (USE_MOCK) {

    return Promise.resolve({ detail: detail, snapshotViews: [], hasSnapshots: false })

  }



  var reportImages = (detail.findings && detail.findings.reportImages) || null

  if (!reportImages) {

    return Promise.resolve({ detail: detail, snapshotViews: [], hasSnapshots: false })

  }



  var planes = SNAPSHOT_PLANES.filter(function (item) {

    return !!reportImages[item.key]

  })



  if (!planes.length) {

    return Promise.resolve({ detail: detail, snapshotViews: [], hasSnapshots: false })

  }



  var tasks = planes.map(function (item) {

    return downloadAuthImage(reportImages[item.key]).then(function (localPath) {

      return { key: item.key, label: item.label, src: localPath }

    })

  })



  return Promise.all(tasks.map(function (task) {
    return task.catch(function () { return null })
  })).then(function (results) {
    var views = results.filter(Boolean)
    return {
      detail: detail,
      snapshotViews: views,
      hasSnapshots: views.length > 0,
    }
  })
}



module.exports = {

  SNAPSHOT_PLANES,

  isExamReportDetail,

  resolveSnapshotUrl,

  downloadAuthImage,

  loadExamSnapshots,

}


