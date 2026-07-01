<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import MprViewer from '../../components/imaging/MprViewer.vue'
import CheckReportSheet from '../../components/medical/CheckReportSheet.vue'
import {
  fetchPacsImagingPreview,
  fetchPacsPreviewBlob,
  fetchPacsResultDetail,
  generatePacsAiReport,
  generatePacsLlmReport,
  savePacsResult,
  uploadPacsImaging,
  uploadPacsReportSnapshots,
} from '../../api/pacs'
import { mergeCheckReportAfterLlm } from '../../utils/checkReport'

const route = useRoute()
const router = useRouter()

const checkRequestId = computed(() => Number(route.query.checkRequestId) || null)
const patientName = computed(() => route.query.patientName || '-')
const itemName = computed(() => route.query.itemName || '-')
const viewMode = computed(() => route.query.view === '1' || route.query.view === 'true')
const showResultEntry = computed(() => !viewMode.value && !!checkRequestId.value)

const taskKind = computed(() => {
  const name = `${itemName.value || ''}`
  if (/肿瘤|病灶|肿物|TUMOR/i.test(name)) return 'tumor'
  if (/胸|肺|CHEST|LUNG/i.test(name)) return 'lung'
  return 'head'
})

const isTumorSeg = computed(() => taskKind.value === 'tumor')

const pageTitle = computed(() => {
  if (taskKind.value === 'tumor') return '肿瘤分割分析'
  if (taskKind.value === 'lung') return '肺部 CT 伪影检测'
  return '头部 CT 金属伪影检测'
})

const analyzeButtonLabel = computed(() => {
  if (generating.value) return 'AI 分析中（约 15–60 秒）…'
  if (uploading.value) return '正在上传源数据…'
  return isTumorSeg.value ? '开始 AI 分割' : '开始 AI 检测'
})

const uploadTip = computed(() => {
  if (isTumorSeg.value && mode.value === 'dicom') {
    return '选文件夹后自动上传。'
  }
  if (isTumorSeg.value) {
    return '选文件后自动上传源数据；AI 完成后分割掩码与预览将自动保存。'
  }
  return '选文件后自动上传源数据；AI 完成后伪影掩码与预览将自动保存。'
})

const maskEmptyHint = computed(() => {
  if (!isTumorSeg.value || !showResults.value || generating.value) return ''
  if (maskVoxelCount.value !== 0) return ''
  return '本次未检出病灶：肿瘤模型针对脑部 CT 训练。请改用 UploadDemo 下 case005 或 case039，重新上传并分析。'
})

const mode = ref('nifti')
const niftiFile = ref(null)
const dicomFiles = ref([])
const dicomCount = ref(0)
const uploading = ref(false)
const generating = ref(false)
const previewLoading = ref(false)
const showResults = ref(false)
const studyStatus = ref('请选择影像文件')
const uploadedFingerprint = ref('')
const progressPct = ref(0)
const progressLabel = ref('')
let progressTimer = null

const error = ref('')
const detail = ref(null)
const ctObjectUrl = ref('')
const maskObjectUrl = ref('')
const maskSlices = ref([])
const maskVoxelCount = ref(null)
const mountViewer = ref(false)

const resultDialogVisible = ref(false)
const checkReport = ref(null)
const detailLoading = ref(false)
const savingResult = ref(false)
const generatingLlm = ref(false)
const mprViewerRef = ref(null)
/** preview | entry | review | readonly */
const dialogMode = ref('entry')

const isEditableEntry = computed(() => dialogMode.value === 'entry')

const resultDialogTitle = computed(() => {
  const name = itemName.value || ''
  if (dialogMode.value === 'preview') return `检查报告预览 · ${name}`
  if (dialogMode.value === 'review') return `审阅检查报告 · ${name}`
  if (dialogMode.value === 'readonly') return `查看检查报告 · ${name}`
  return `录入检查报告 · ${name}`
})

function revokeUrls() {
  if (ctObjectUrl.value) URL.revokeObjectURL(ctObjectUrl.value)
  if (maskObjectUrl.value) URL.revokeObjectURL(maskObjectUrl.value)
  ctObjectUrl.value = ''
  maskObjectUrl.value = ''
}

function resetResults() {
  showResults.value = false
  mountViewer.value = false
  detail.value = null
  maskSlices.value = []
  maskVoxelCount.value = null
  revokeUrls()
}

function applyPreviewMeta(data) {
  const slices = data?.maskSlices ?? data?.maskSliceIndices ?? []
  maskSlices.value = Array.isArray(slices) ? slices : []
  const count = data?.maskVoxelCount
  maskVoxelCount.value = typeof count === 'number' ? count : count != null ? Number(count) : null
}

function stopProgressTimer() {
  if (progressTimer) {
    clearInterval(progressTimer)
    progressTimer = null
  }
}

function resetProgress() {
  stopProgressTimer()
  progressPct.value = 0
  progressLabel.value = ''
}

function setProgress(pct, label) {
  progressPct.value = Math.min(100, Math.max(0, Math.round(pct)))
  if (label) progressLabel.value = label
}

function onUploadProgress(e) {
  if (!e.total) return
  const uploadPct = Math.round((e.loaded / e.total) * 100)
  setProgress(uploadPct * 0.25, `上传源数据 ${uploadPct}%`)
}

function startInferenceProgress() {
  stopProgressTimer()
  setProgress(30, 'AI 分析中（约 15–60 秒，请勿重复点击）…')
  progressTimer = setInterval(() => {
    if (progressPct.value < 88) {
      setProgress(progressPct.value + 1)
    } else if (progressPct.value < 95) {
      setProgress(progressPct.value + 1, 'AI 仍在分析，请耐心等待…')
    }
  }, 3000)
}

function currentFiles() {
  if (mode.value === 'nifti') {
    return niftiFile.value ? [niftiFile.value] : []
  }
  return dicomFiles.value
}

function fileFingerprint() {
  const files = currentFiles()
  if (!files.length) return ''
  return files.map((f) => `${f.name}:${f.size}`).join('|')
}

const canAnalyze = computed(() => currentFiles().length > 0)

async function uploadToMinio() {
  if (!checkRequestId.value || !canAnalyze.value) return false
  const files = currentFiles()
  const fp = fileFingerprint()
  if (uploadedFingerprint.value === fp && !uploading.value) return true

  uploading.value = true
  resetResults()
  resetProgress()
  error.value = ''
  studyStatus.value = '正在上传源数据…'
  setProgress(2, '准备上传…')
  try {
    const res = await uploadPacsImaging(checkRequestId.value, files, onUploadProgress)
    uploadedFingerprint.value = fp
    studyStatus.value = '源数据已入库，可开始 AI 检测'
    setProgress(25, '源数据已上传')
    ElMessage.success(`已自动上传 ${res.data?.uploadedCount || files.length} 个文件`)
    return true
  } catch (err) {
    uploadedFingerprint.value = ''
    error.value = err.message || '影像上传失败'
    studyStatus.value = '上传失败'
    ElMessage.error(error.value)
    return false
  } finally {
    uploading.value = false
  }
}

async function onNiftiChange(e) {
  const f = e.target.files?.[0]
  niftiFile.value = f || null
  error.value = ''
  uploadedFingerprint.value = ''
  if (f) await uploadToMinio()
  else studyStatus.value = '请选择影像文件'
}

async function onDicomChange(e) {
  const list = Array.from(e.target.files || [])
  dicomFiles.value = list
  dicomCount.value = list.filter((f) => f.name.toLowerCase().endsWith('.dcm')).length
  error.value = ''
  uploadedFingerprint.value = ''
  if (dicomCount.value > 0) await uploadToMinio()
  else studyStatus.value = '请选择影像文件'
}

async function loadStoredPreview() {
  if (!checkRequestId.value) return
  error.value = ''
  resetResults()
  previewLoading.value = true
  studyStatus.value = '正在加载历史影像…'
  try {
    const previewRes = await fetchPacsImagingPreview(checkRequestId.value)
    applyPreviewMeta(previewRes.data)
    const [ctUrl, maskUrl] = await Promise.all([
      fetchPacsPreviewBlob(checkRequestId.value, 'ct'),
      fetchPacsPreviewBlob(checkRequestId.value, 'mask'),
    ])
    ctObjectUrl.value = ctUrl
    maskObjectUrl.value = maskUrl
    showResults.value = true
    studyStatus.value = '已加载历史影像与掩码'
    await nextTick()
    mountViewer.value = true
  } catch (err) {
    showResults.value = false
    studyStatus.value = '暂无可查看的 AI 影像'
    const msg = err.message || '加载失败'
    error.value = /尚未|未完成|不存在|404|COMPLETED/i.test(msg)
      ? '尚未 AI 检测，请先上传并完成检测'
      : msg
    ElMessage.warning(error.value)
  } finally {
    previewLoading.value = false
  }
}

async function loadPreviewVolumes() {
  if (!checkRequestId.value) return
  revokeUrls()
  previewLoading.value = true
  setProgress(92, '正在加载三视图预览…')
  try {
    const [ctUrl, maskUrl] = await Promise.all([
      fetchPacsPreviewBlob(checkRequestId.value, 'ct'),
      fetchPacsPreviewBlob(checkRequestId.value, 'mask'),
    ])
    ctObjectUrl.value = ctUrl
    maskObjectUrl.value = maskUrl
    const previewRes = await fetchPacsImagingPreview(checkRequestId.value)
    applyPreviewMeta(previewRes.data)
    setProgress(100, '分析完成')
    await nextTick()
    setTimeout(() => {
      mountViewer.value = true
    }, 0)
  } catch (err) {
    revokeUrls()
    error.value = err.message || '预览加载失败'
    ElMessage.error(error.value)
  } finally {
    previewLoading.value = false
  }
}

async function onAgentAiAnalysis() {
  if (!checkRequestId.value || !canAnalyze.value) {
    ElMessage.warning('请先选择 NIfTI 或 DICOM 文件夹')
    return
  }
  generating.value = true
  error.value = ''
  resetProgress()
  try {
    const uploaded = await uploadToMinio()
    if (!uploaded) return

    studyStatus.value = 'AI 分析中，结果将自动保存…'
    startInferenceProgress()
    const res = await generatePacsAiReport(checkRequestId.value)
    stopProgressTimer()
    setProgress(90, '推理完成，正在加载预览…')
    showResults.value = true
    detail.value = res.data
    studyStatus.value = isTumorSeg.value
      ? '分析完成（分割掩码与预览已保存）'
      : '分析完成（伪影掩码与预览已保存）'
    ElMessage.success(isTumorSeg.value ? 'AI 肿瘤分割完成' : 'AI 影像分析完成')
    await loadPreviewVolumes()
  } catch (err) {
    stopProgressTimer()
    resetProgress()
    studyStatus.value = '分析失败'
    error.value = err.message || 'AI 分析失败'
    ElMessage.error(error.value)
  } finally {
    generating.value = false
  }
}

function goBack() {
  router.push('/pacs/queue')
}

async function captureAndApplySnapshots() {
  const captured =
    (await mprViewerRef.value?.captureReportSnapshotsAsync?.()) ||
    mprViewerRef.value?.captureReportSnapshots?.()
  if (!captured?.axial && !captured?.coronal && !captured?.sagittal) {
    ElMessage.warning('三视图尚未就绪，请待影像加载完成后再采图')
    return null
  }
  if (checkReport.value) {
    checkReport.value = {
      ...checkReport.value,
      findings: {
        ...checkReport.value.findings,
        localSnapshots: {
          axial: captured.axial,
          coronal: captured.coronal,
          sagittal: captured.sagittal,
        },
        snapshotMeta: captured.meta,
      },
    }
  }
  try {
    await uploadPacsReportSnapshots(checkRequestId.value, captured)
  } catch (err) {
    ElMessage.warning(err.message || '采图上传失败，将仅本地预览')
  }
  return captured
}

function reportHasEntry() {
  if (!checkReport.value) return false
  const ai = checkReport.value.analysis?.aiReportText?.trim() || ''
  const doctor = checkReport.value.analysis?.doctorReportText?.trim() || ''
  const findings = checkReport.value.findings?.findingsText?.trim() || ''
  return !!(ai || doctor || findings)
}

function enterEntryMode() {
  dialogMode.value = 'entry'
}

async function openResultDialog(mode = 'entry') {
  if (!checkRequestId.value) return
  dialogMode.value = mode
  checkReport.value = null
  detailLoading.value = true
  try {
    const res = await fetchPacsResultDetail(checkRequestId.value)
    checkReport.value = { ...res.data }
    if (mountViewer.value && (mode === 'entry' || mode === 'preview')) {
      await nextTick()
      await mprViewerRef.value?.waitForCaptureReady?.()
      await captureAndApplySnapshots()
      const refreshed = await fetchPacsResultDetail(checkRequestId.value)
      checkReport.value = {
        ...refreshed.data,
        findings: {
          ...refreshed.data.findings,
          localSnapshots: checkReport.value?.findings?.localSnapshots,
        },
      }
    }
    if (mode === 'review' && !reportHasEntry()) {
      ElMessage.warning('报告尚未录入，请先由录入医师完成录入')
      return
    }
    resultDialogVisible.value = true
  } catch (err) {
    ElMessage.error(err.message || '加载报告详情失败')
  } finally {
    detailLoading.value = false
  }
}

async function onRecaptureSnapshots() {
  detailLoading.value = true
  try {
    await captureAndApplySnapshots()
    ElMessage.success('已重新采集三视图采图')
  } finally {
    detailLoading.value = false
  }
}

async function onGenerateLlmReport() {
  if (!checkRequestId.value || !checkReport.value) return
  const findingsText = checkReport.value.findings?.findingsText?.trim() || ''
  if (!findingsText) {
    ElMessage.warning('请先填写 CT 所见')
    return
  }
  generatingLlm.value = true
  try {
    const res = await generatePacsLlmReport(checkRequestId.value, findingsText)
    checkReport.value = mergeCheckReportAfterLlm(checkReport.value, res.data)
    ElMessage.success('AI 报告已生成，请核对诊断印象')
  } catch (err) {
    ElMessage.error(err.message || 'AI 报告生成失败')
  } finally {
    generatingLlm.value = false
  }
}

function updateCheckFindings(text) {
  if (!checkReport.value) return
  checkReport.value = {
    ...checkReport.value,
    findings: { ...checkReport.value.findings, findingsText: text },
    findingsText: text,
  }
}

function updateCheckAi(text) {
  if (!checkReport.value) return
  checkReport.value = {
    ...checkReport.value,
    analysis: { ...checkReport.value.analysis, aiReportText: text, aiReportStatus: 'READY' },
    aiReportText: text,
    aiReportStatus: 'READY',
  }
}

function updateCheckDoctor(text) {
  if (!checkReport.value) return
  checkReport.value = {
    ...checkReport.value,
    analysis: { ...checkReport.value.analysis, doctorReportText: text },
    doctorReportText: text,
  }
}

async function submitResult(signOpts, successMessage) {
  if (!checkRequestId.value || !checkReport.value) return
  const findings = checkReport.value.findings?.findingsText?.trim() || ''
  const ai = checkReport.value.analysis?.aiReportText?.trim() || ''
  const doctor = checkReport.value.analysis?.doctorReportText?.trim() || ''
  if (!signOpts.signAsReviewerOnly && !ai && !doctor) {
    ElMessage.warning('请生成 AI 报告或填写检查医师意见')
    return
  }
  savingResult.value = true
  try {
    await savePacsResult(checkRequestId.value, {
      findingsText: findings,
      aiReportText: ai,
      doctorReportText: doctor,
      ...signOpts,
    })
    ElMessage.success(successMessage)
    resultDialogVisible.value = false
  } catch (err) {
    ElMessage.error(err.message || '保存失败')
  } finally {
    savingResult.value = false
  }
}

async function onSaveEntry() {
  await submitResult({ pendingReview: true }, '录入已保存，待审核医师发布')
}

async function onPublishReview() {
  await submitResult({ signAsReviewerOnly: true }, '报告已发布')
}

onMounted(() => {
  if (viewMode.value && checkRequestId.value) {
    loadStoredPreview()
  }
})

watch(
  () => [route.query.checkRequestId, route.query.view],
  () => {
    if (viewMode.value && checkRequestId.value) {
      loadStoredPreview()
    }
  },
)

onBeforeUnmount(() => {
  stopProgressTimer()
  revokeUrls()
})
</script>

<template>
  <div class="ct-workbench">
    <header class="header">
      <div>
        <h1>{{ pageTitle }}</h1>
        <p class="subtitle">
          智慧云脑 · 影像 AI 工作台
          <template v-if="checkRequestId">
            · 检查 #{{ checkRequestId }} {{ patientName }} · {{ itemName }}
          </template>
          <template v-if="viewMode"> · 查看模式</template>
        </p>
      </div>
      <div class="header-actions">
        <template v-if="showResultEntry">
          <el-button type="success" @click="openResultDialog('entry')">录入</el-button>
          <el-button type="primary" @click="openResultDialog('review')">审阅</el-button>
        </template>
        <el-button link type="primary" @click="goBack">返回检查队列</el-button>
      </div>
    </header>

    <div v-if="!checkRequestId" class="empty-hint">请从「检查队列」进入本页。</div>

    <main v-else class="layout">
      <aside class="panel">
        <section class="block">
          <h2>上传方式</h2>
          <div class="mode-tabs">
            <button type="button" :class="{ active: mode === 'nifti' }" @click="mode = 'nifti'">NIfTI</button>
            <button type="button" :class="{ active: mode === 'dicom' }" @click="mode = 'dicom'">DICOM 文件夹</button>
          </div>
        </section>

        <section class="block">
          <h2>选择数据</h2>
          <template v-if="mode === 'nifti'">
            <input type="file" accept=".nii,.nii.gz" @change="onNiftiChange" />
            <p v-if="niftiFile" class="file-name">{{ niftiFile.name }}</p>
          </template>
          <template v-else>
            <input type="file" webkitdirectory directory multiple @change="onDicomChange" />
            <p v-if="dicomCount" class="file-name">已选 {{ dicomCount }} 个 .dcm</p>
            <p class="tip">选择含 DICOM 切片的文件夹</p>
          </template>
        </section>

        <section class="block">
          <h2>{{ isTumorSeg ? 'AI 肿瘤分割' : 'AI 伪影检测' }}</h2>
          <p v-if="isTumorSeg" class="task-desc">
            本检查使用<strong>肿瘤分割模型</strong>，输出为病灶区域的分割掩码（非金属伪影检测）。
          </p>
          <button
            type="button"
            class="primary"
            :disabled="generating || uploading || !canAnalyze"
            @click="onAgentAiAnalysis"
          >
            {{ analyzeButtonLabel }}
          </button>
          <p class="status">任务状态：{{ studyStatus }}</p>
          <div v-if="progressPct > 0 || uploading || generating || previewLoading" class="progress-wrap">
            <div class="progress-track">
              <div class="progress-fill" :style="{ width: progressPct + '%' }"></div>
            </div>
            <p class="progress-label">{{ progressLabel || studyStatus }}</p>
            <span class="progress-pct">{{ progressPct }}%</span>
          </div>
          <p class="tip">{{ uploadTip }}</p>
          <p v-if="maskEmptyHint" class="warn-tip">{{ maskEmptyHint }}</p>
          <div v-if="showResults" class="usage-hint">
            <p class="usage-title">{{ isTumorSeg ? '分割掩码阅片说明' : '伪影掩码阅片说明' }}</p>
            <ul v-if="isTumorSeg">
              <li>分析完成后，右侧<strong>默认只加载源数据 CT</strong>（灰度图）。</li>
              <li>要看 AI 分割结果，请点击<strong>「叠加 AI 分割掩码」</strong>——蓝色区域为肿瘤/病灶范围。</li>
              <li>某层无蓝色区域表示该层 AI 未检出明显病灶。</li>
              <li>本结果为 AI 辅助分析，<strong>不能替代临床诊断</strong>。</li>
              <li>点击轴位 / 冠状 / 矢状视图可<strong>放大</strong>查看。</li>
            </ul>
            <ul v-else>
              <li>分析完成后，右侧<strong>默认只加载源数据 CT</strong>（灰度图）。</li>
              <li>要看 AI 检测到的金属伪影，请点击<strong>「叠加 AI 掩码」</strong>——蓝色区域为伪影范围。</li>
              <li>点击轴位 / 冠状 / 矢状视图可<strong>放大</strong>查看。</li>
            </ul>
          </div>
        </section>

        <p v-if="error" class="error">{{ error }}</p>
      </aside>

      <section class="viewer-panel">
        <h2>影像浏览</h2>
        <div v-if="previewLoading" class="preview-loading">正在加载预览…</div>
        <div v-else-if="showResults && ctObjectUrl && mountViewer && !generating" class="viewer-wrap">
          <MprViewer
            ref="mprViewerRef"
            :key="`${checkRequestId}-${ctObjectUrl}`"
            :ct-url="ctObjectUrl"
            :mask-url="maskObjectUrl"
            :mask-slices="maskSlices"
            :mask-mode="isTumorSeg ? 'tumor' : 'artifact'"
          />
        </div>
        <p v-else-if="showResults && !ctObjectUrl && error" class="panel-tip error">
          分析已完成，但预览加载失败：{{ error }}
        </p>
        <p v-else-if="showResults && ctObjectUrl && !mountViewer" class="panel-tip">
          掩码已就绪，正在准备阅片组件…
        </p>
        <p v-else class="panel-tip">
          {{ viewMode ? '正在尝试加载已保存的影像…' : '上传并完成 AI 检测后显示三视图' }}
        </p>
      </section>
    </main>

    <el-dialog
      v-model="resultDialogVisible"
      :title="resultDialogTitle"
      width="860px"
      destroy-on-close
    >
      <div v-loading="detailLoading" class="report-dialog-wrap">
        <CheckReportSheet
          v-if="checkReport"
          :report="checkReport"
          :editable-findings="isEditableEntry"
          :editable-ai="isEditableEntry"
          :editable-doctor="isEditableEntry"
          :show-recapture="isEditableEntry"
          @update:findings-text="updateCheckFindings"
          @update:ai-report-text="updateCheckAi"
          @update:doctor-report-text="updateCheckDoctor"
          @recapture="onRecaptureSnapshots"
        />
        <div v-if="dialogMode === 'preview'" class="report-corner-actions">
          <el-button type="primary" @click="enterEntryMode">录入</el-button>
        </div>
      </div>
      <template #footer>
        <el-button
          v-if="dialogMode === 'entry'"
          type="primary"
          plain
          :loading="generatingLlm"
          :disabled="detailLoading"
          @click="onGenerateLlmReport"
        >
          生成 AI 报告
        </el-button>
        <el-button @click="resultDialogVisible = false">
          {{ dialogMode === 'preview' || dialogMode === 'readonly' ? '关闭' : '取消' }}
        </el-button>
        <el-button
          v-if="dialogMode === 'entry'"
          type="primary"
          :loading="savingResult"
          @click="onSaveEntry"
        >
          保存录入
        </el-button>
        <el-button
          v-if="dialogMode === 'review'"
          type="success"
          :loading="savingResult"
          @click="onPublishReview"
        >
          发布
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ct-workbench {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  height: 100%;
  max-height: 100%;
  margin: 0;
  background: #0d1218;
  color: #e2e8f0;
  border-radius: 0;
  overflow: hidden;
}
.header { display: flex; justify-content: space-between; align-items: center; padding: 12px 20px; border-bottom: 1px solid #243040; background: linear-gradient(135deg, #121a22, #0d1218); flex-shrink: 0; }
.header-actions { display: flex; align-items: center; gap: 12px; flex-shrink: 0; }
.header h1 { margin: 0; font-size: 20px; }
.subtitle { margin: 4px 0 0; font-size: 12px; color: #8aa0b4; }
.layout {
  flex: 1;
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 0;
  min-height: 0;
  height: 100%;
}
.panel { padding: 16px; background: #111820; border-right: 1px solid #243040; overflow-y: auto; }
.block { margin-bottom: 18px; }
.block h2 { margin: 0 0 10px; font-size: 13px; color: #b8c8d8; text-transform: uppercase; }
.task-desc { font-size: 12px; line-height: 1.6; color: #9eb4c8; margin: 0 0 12px; padding: 8px 10px; background: #141c28; border-radius: 6px; border-left: 3px solid #4ecdc4; }
.task-desc strong { color: #c8e8f0; }
.mode-tabs { display: flex; gap: 8px; }
.mode-tabs button { flex: 1; padding: 8px; border: 1px solid #304050; border-radius: 8px; background: #182028; color: #c8d8e8; cursor: pointer; }
.mode-tabs button.active { background: #1e3a52; border-color: #3d8fd1; color: #fff; }
.file-name, .tip, .status { font-size: 12px; color: #8aa0b4; margin-top: 8px; }
.warn-tip { font-size: 12px; color: #ffb86c; margin-top: 8px; line-height: 1.5; padding: 8px 10px; background: #2a2218; border-radius: 6px; border-left: 3px solid #ffb86c; }
.primary, .secondary { width: 100%; padding: 12px; border: none; border-radius: 8px; font-weight: 600; cursor: pointer; margin-bottom: 8px; }
.primary { background: linear-gradient(135deg, #2a7bd6, #1e5fad); color: #fff; }
.secondary { background: #243040; color: #d0dce8; }
.primary:disabled, .secondary:disabled { opacity: 0.5; cursor: not-allowed; }
.error { color: #ff9a9a; font-size: 13px; }
.viewer-panel {
  padding: 10px 14px 12px;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  height: 100%;
}
.viewer-panel h2 { margin: 0 0 8px; font-size: 13px; color: #b8c8d8; flex-shrink: 0; }
.progress-wrap { margin-top: 10px; }
.progress-track { height: 6px; background: #243040; border-radius: 3px; overflow: hidden; }
.progress-fill { height: 100%; background: linear-gradient(90deg, #2a7bd6, #4ecdc4); transition: width 0.25s ease; }
.progress-label { margin: 8px 0 0; font-size: 12px; color: #9eb4c8; }
.progress-pct { display: block; margin-top: 4px; font-size: 11px; color: #6f8598; text-align: right; }
.usage-hint {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #141c28;
  border: 1px solid #2a4058;
}
.usage-title { margin: 0 0 8px; font-size: 12px; font-weight: 600; color: #9ec8e8; }
.usage-hint ul { margin: 0; padding-left: 18px; font-size: 12px; line-height: 1.65; color: #8aa0b4; }
.usage-hint li { margin-bottom: 4px; }
.usage-hint strong { color: #c8dce8; font-weight: 600; }
.viewer-wrap {
  flex: 1;
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
}
.panel-tip, .preview-loading { color: #8aa0b4; padding: 48px 0; text-align: center; }
.panel-tip.error { color: #ff9a9a; }
.empty-hint { padding: 48px; text-align: center; color: #8aa0b4; }
.sign-options {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 12px;
  padding: 8px 0;
}
.report-dialog-wrap {
  position: relative;
}
.report-corner-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
  padding-top: 8px;
  border-top: 1px dashed #e2e8f0;
}
@media (max-width: 960px) { .layout { grid-template-columns: 1fr; } }
</style>
