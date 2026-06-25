<template>
  <div class="mpr-root" :class="{ 'is-loading': loading }">
    <div v-if="loading" class="load-overlay">
      <p class="load-title">{{ loadStage || '正在加载影像…' }}</p>
      <div class="progress-track">
        <div class="progress-fill" :style="{ width: loadProgress + '%' }"></div>
      </div>
      <span class="load-pct">{{ loadProgress }}%</span>
    </div>

    <p v-if="secondaryLoading && showViews" class="load-sub banner">
      后台加载冠状 / 矢状（仅 CT）；点击「叠加 AI 掩码」后三视图均显示蓝色伪影
    </p>

    <div class="mpr-content" :class="{ visible: showViews }">
      <div class="mpr-row top">
        <div class="view-cell expandable" @click="openExpandedView('axial')">
          <div class="view-head">
            <span class="view-title">轴位 Axial</span>
            <span v-if="ready" class="zoom-hint">点击放大</span>
          </div>
          <canvas ref="axialRef" class="view-canvas"></canvas>
          <div v-if="!linkedSlices" class="slider-row" @click.stop>
            <label>轴位 Z</label>
            <input
              type="range"
              min="0"
              :max="Math.max(dims.z - 1, 0)"
              v-model.number="sliceZ"
              :disabled="!ready"
              @input="onSliderInput"
            />
            <span>{{ sliceZ }} / {{ Math.max(dims.z - 1, 0) }}</span>
          </div>
        </div>
        <div class="view-cell expandable" @click="openExpandedView('coronal')">
          <div class="view-head">
            <span class="view-title">冠状 Coronal</span>
            <span v-if="ready" class="zoom-hint">点击放大</span>
          </div>
          <canvas ref="coronalRef" class="view-canvas"></canvas>
          <div v-if="!linkedSlices" class="slider-row" @click.stop>
            <label>冠状 Y</label>
            <input
              type="range"
              min="0"
              :max="Math.max(dims.y - 1, 0)"
              v-model.number="sliceY"
              :disabled="!ready"
              @input="onSliderInput"
            />
            <span>{{ sliceY }} / {{ Math.max(dims.y - 1, 0) }}</span>
          </div>
        </div>
        <div class="view-cell expandable" @click="openExpandedView('sagittal')">
          <div class="view-head">
            <span class="view-title">矢状 Sagittal</span>
            <span v-if="ready" class="zoom-hint">点击放大</span>
          </div>
          <canvas ref="sagittalRef" class="view-canvas"></canvas>
          <div v-if="!linkedSlices" class="slider-row" @click.stop>
            <label>矢状 X</label>
            <input
              type="range"
              min="0"
              :max="Math.max(dims.x - 1, 0)"
              v-model.number="sliceX"
              :disabled="!ready"
              @input="onSliderInput"
            />
            <span>{{ sliceX }} / {{ Math.max(dims.x - 1, 0) }}</span>
          </div>
        </div>
      </div>

      <div v-if="linkedSlices && ready" class="unified-slider">
        <label>联动切片</label>
        <input
          type="range"
          min="0"
          :max="unifiedMax"
          v-model.number="unifiedSlice"
          @input="onUnifiedSlider"
        />
        <span>{{ unifiedSlice }} / {{ unifiedMax }}</span>
        <span class="unified-hint">三视图同步到同一层号（取最短轴范围）</span>
      </div>

      <div class="mpr-row bottom">
        <div class="view-cell wide">
          <div class="view-head">
            <span class="view-title">掩码三维表面</span>
            <span class="view-sub">方位立方体 · 拖动旋转</span>
            <button
              v-if="maskUrl && !render3dReady"
              type="button"
              class="mini-btn"
              :disabled="render3dLoading"
              @click="loadRender3d"
            >
              {{ render3dLoading ? '构建中…' : '加载三维表面' }}
            </button>
            <button
              v-if="maskUrl && render3dReady"
              type="button"
              class="mini-btn"
              @click="reset3dView"
            >
              重置视角
            </button>
          </div>
          <div class="canvas-shell short">
            <canvas
              ref="renderRef"
              class="view-canvas short"
              :class="{ 'canvas-hidden': maskUrl && !render3dReady && !render3dLoading }"
            ></canvas>
            <p v-if="!maskUrl" class="panel-tip">生成掩码后显示三维预览</p>
            <p v-else-if="!render3dReady && !render3dLoading" class="canvas-placeholder">
              默认不自动构建三维，点击上方按钮按需加载
            </p>
          </div>
        </div>
        <div class="view-cell wide">
          <div class="view-head">
            <span class="view-title">掩码预览</span>
            <span class="view-sub">{{ maskSubtitle }}</span>
            <button
              v-if="maskUrl && !maskSliceReady"
              type="button"
              class="mini-btn"
              :disabled="maskSliceLoading"
              @click="loadMaskSlicePreview"
            >
              {{ maskSliceLoading ? '加载中…' : '加载掩码预览' }}
            </button>
          </div>
          <div class="canvas-shell short">
            <canvas
              ref="maskRef"
              class="view-canvas short"
              :class="{ 'canvas-hidden': maskUrl && !maskSliceReady && !maskSliceLoading }"
            ></canvas>
            <p v-if="!maskUrl" class="panel-tip">生成掩码后显示伪影分割结果</p>
            <p v-else-if="!maskSliceReady && !maskSliceLoading" class="canvas-placeholder">
              可在工具栏点击「叠加 AI 掩码」后，再按需加载独立预览
            </p>
          </div>
        </div>
      </div>

      <div class="mpr-toolbar">
        <div class="toolbar-left">
          <label class="toggle">
            <input
              type="checkbox"
              v-model="linkedSlices"
              @change="onLinkedToggle"
            />
            联动切片
          </label>
          <span class="hint">滚轮翻层 · 左键定位</span>
          <button
            v-if="pendingMaskUrl && !maskOverlayReady"
            type="button"
            class="mode-btn accent"
            :disabled="maskOverlayLoading"
            @click="loadMaskOverlay"
          >
            {{ maskOverlayLoading ? '掩码叠加中…' : '叠加 AI 掩码' }}
          </button>
          <span v-if="maskOverlayReady && maskHint" class="mask-hint">{{ maskHint }}</span>
        </div>
        <div class="toolbar-right">
          <div v-if="maskOverlayReady && maskUrl" class="display-modes">
            <button
              type="button"
              class="mode-btn"
              :class="{ active: displayMode === 'ct' }"
              @click="setDisplayMode('ct')"
            >
              原图
            </button>
            <button
              type="button"
              class="mode-btn"
              :class="{ active: displayMode === 'overlay' }"
              @click="setDisplayMode('overlay')"
            >
              叠加
            </button>
            <button
              type="button"
              class="mode-btn"
              :class="{ active: displayMode === 'mask' }"
              @click="setDisplayMode('mask')"
            >
              掩码
            </button>
          </div>
          <label v-if="maskUrl && displayMode === 'overlay'" class="opacity">
            掩码透明度
            <input
              type="range"
              min="0"
              max="100"
              :value="Math.round(maskOpacity * 100)"
              @input="onOpacityInput"
            />
          </label>
        </div>
      </div>
    </div>

    <p v-if="error" class="loading-msg error">{{ error }}</p>

    <div v-if="expandedPlane" class="expand-overlay" @click.self="closeExpandedView">
      <div class="expand-panel">
        <div class="expand-head">
          <span>{{ expandTitle }}</span>
          <button type="button" class="mini-btn" @click="closeExpandedView">关闭</button>
        </div>
        <canvas ref="expandCanvasRef" class="expand-canvas"></canvas>
        <div class="expand-slider" @click.stop>
          <label>{{ expandSliderLabel }}</label>
          <input
            type="range"
            min="0"
            :max="expandSliderMax"
            v-model.number="expandSliderValue"
            @input="onExpandSliderInput"
          />
          <span>{{ expandSliderValue }} / {{ expandSliderMax }}</span>
        </div>
        <p class="expand-hint">滚轮或滑条翻层 · 左键定位 · 点击空白处或关闭按钮退出</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { Niivue } from "@niivue/niivue";

const props = defineProps({
  ctUrl: { type: String, default: "" },
  maskUrl: { type: String, default: "" },
  ctFilename: { type: String, default: "CT.nii.gz" },
  maskFilename: { type: String, default: "mask.nii.gz" },
  maskSlices: { type: Array, default: () => [] },
});

const axialRef = ref(null);
const coronalRef = ref(null);
const sagittalRef = ref(null);
const renderRef = ref(null);
const maskRef = ref(null);

const loading = ref(false);
const loadProgress = ref(0);
const loadStage = ref("");
const secondaryLoading = ref(false);
const render3dReady = ref(false);
const render3dLoading = ref(false);
const maskSliceReady = ref(false);
const maskSliceLoading = ref(false);
const maskOverlayReady = ref(false);
const maskOverlayLoading = ref(false);
const showViews = ref(false);
const error = ref("");
const ready = ref(false);
const maskOpacity = ref(0.85);
const dims = ref({ x: 0, y: 0, z: 0 });
const sliceX = ref(0);
const sliceY = ref(0);
const sliceZ = ref(0);
const linkedSlices = ref(false);
const unifiedSlice = ref(0);
const displayMode = ref("overlay");

let nvAxial = null;
let nvCoronal = null;
let nvSagittal = null;
let nvRender = null;
let nvMask = null;
let syncing = false;
let loadSeq = 0;
let pendingMaskUrl = "";
let pendingCtUrl = "";
let nvExpand = null;
let expandLoadedKey = "";

const expandedPlane = ref(null);
const expandCanvasRef = ref(null);

const expandTitle = computed(() => {
  const map = {
    axial: "轴位 Axial — 放大查看",
    coronal: "冠状 Coronal — 放大查看",
    sagittal: "矢状 Sagittal — 放大查看",
  };
  return map[expandedPlane.value] || "放大查看";
});

const expandSliderLabel = computed(() => {
  const map = { axial: "轴位 Z", coronal: "冠状 Y", sagittal: "矢状 X" };
  return map[expandedPlane.value] || "";
});

const expandSliderMax = computed(() => {
  const d = dims.value;
  if (expandedPlane.value === "axial") return Math.max(d.z - 1, 0);
  if (expandedPlane.value === "coronal") return Math.max(d.y - 1, 0);
  if (expandedPlane.value === "sagittal") return Math.max(d.x - 1, 0);
  return 0;
});

const expandSliderValue = computed({
  get() {
    if (expandedPlane.value === "axial") return sliceZ.value;
    if (expandedPlane.value === "coronal") return sliceY.value;
    if (expandedPlane.value === "sagittal") return sliceX.value;
    return 0;
  },
  set(v) {
    if (expandedPlane.value === "axial") sliceZ.value = v;
    else if (expandedPlane.value === "coronal") sliceY.value = v;
    else if (expandedPlane.value === "sagittal") sliceX.value = v;
  },
});

const DEFAULT_AZIMUTH = 0;
const DEFAULT_ELEVATION = -15;

const maskSubtitle = computed(() =>
  props.maskUrl ? "轴位掩码切片（蓝色）" : "— / —",
);

const maskHint = computed(() => {
  const slices = props.maskSlices || [];
  if (!props.maskUrl) return "";
  if (!slices.length) return "AI 未检出掩码体素";
  const labels = slices.slice(0, 6).map((z) => z + 1);
  const suffix = slices.length > 6 ? ` 等共 ${slices.length} 层` : "";
  return `掩码在第 ${labels.join("、")} 层${suffix}`;
});

const unifiedMax = computed(() => {
  const { x, y, z } = dims.value;
  if (x < 1 || y < 1 || z < 1) return 0;
  return Math.min(x, y, z) - 1;
});

const mprViewers = () => [nvAxial, nvCoronal, nvSagittal];

function makeNv() {
  return new Niivue({
    backColor: [0.06, 0.08, 0.11, 1],
    crosshairColor: [0.2, 0.75, 1, 0.85],
    isRadiologicalConvention: false,
    show3Dcrosshair: true,
    isResizeCanvas: true,
    dragMode: 0,
    dragModePrimary: 0,
    isOrientationTextVisible: true,
  });
}

function makeNv3d() {
  return new Niivue({
    backColor: [0.06, 0.08, 0.11, 1],
    isResizeCanvas: true,
    isOrientCube: true,
    isOrientationTextVisible: true,
    show3Dcrosshair: true,
  });
}

function readDims(nv) {
  const vol = nv?.volumes?.[0];
  if (!vol?.dimsRAS) return { x: 0, y: 0, z: 0 };
  return {
    x: vol.dimsRAS[1],
    y: vol.dimsRAS[2],
    z: vol.dimsRAS[3],
  };
}

function clampSlice(v, max) {
  return Math.max(0, Math.min(max, v));
}

function updateSlidersFromLocation(data) {
  if (syncing || !ready.value) return;
  const vox = data?.values?.[0]?.vox;
  if (!vox) return;
  const { x, y, z } = dims.value;
  sliceX.value = clampSlice(Math.round(vox[0]), x - 1);
  sliceY.value = clampSlice(Math.round(vox[1]), y - 1);
  sliceZ.value = clampSlice(Math.round(vox[2]), z - 1);
  if (linkedSlices.value) {
    unifiedSlice.value = Math.min(sliceX.value, sliceY.value, sliceZ.value);
  }
}

function syncAllCrosshairsFrom(sourceNv) {
  if (!sourceNv?.volumes?.length) return;
  const mm = sourceNv.frac2mm(sourceNv.scene.crosshairPos);
  for (const nv of [nvAxial, nvCoronal, nvSagittal, nvMask]) {
    if (!nv?.volumes?.length) continue;
    nv.scene.crosshairPos = nv.mm2frac(mm);
    nv.drawScene();
  }
}

function hookLocationSync(nv) {
  nv.onLocationChange = (data) => {
    updateSlidersFromLocation(data);
    if (!syncing) {
      syncing = true;
      syncAllCrosshairsFrom(nv);
      syncing = false;
    }
  };
}

function applyCrosshair() {
  if (!nvAxial || !ready.value) return;
  const { x, y, z } = dims.value;
  if (x < 1 || y < 1 || z < 1) return;
  syncing = true;
  nvAxial.scene.crosshairPos = [
    (sliceX.value + 0.5) / x,
    (sliceY.value + 0.5) / y,
    (sliceZ.value + 0.5) / z,
  ];
  syncAllCrosshairsFrom(nvAxial);
  syncing = false;
}

function onSliderInput() {
  if (linkedSlices.value) {
    unifiedSlice.value = Math.min(sliceX.value, sliceY.value, sliceZ.value);
  }
  applyCrosshair();
}

function onUnifiedSlider() {
  const v = unifiedSlice.value;
  sliceX.value = clampSlice(v, dims.value.x - 1);
  sliceY.value = clampSlice(v, dims.value.y - 1);
  sliceZ.value = clampSlice(v, dims.value.z - 1);
  applyCrosshair();
}

function onLinkedToggle() {
  if (linkedSlices.value) {
    unifiedSlice.value = Math.min(sliceX.value, sliceY.value, sliceZ.value);
    onUnifiedSlider();
  }
}

function setDisplayMode(mode) {
  displayMode.value = mode;
  applyDisplayMode();
}

function maskVolumeConfig(maskUrl, opacity = 1) {
  return {
    url: maskUrl,
    name: props.maskFilename || "mask.nii.gz",
    colormap: "blue",
    opacity,
    cal_min: 0,
    cal_max: 255,
    trustCalMinMax: false,
    ignoreZeroVoxels: false,
    alphaThreshold: false,
  };
}

function configureBinaryMaskVolume(vol) {
  if (!vol) return;
  const gMin = Number.isFinite(vol.global_min) ? vol.global_min : 0;
  const gMax = Number.isFinite(vol.global_max) ? vol.global_max : 255;
  vol.cal_min = gMin;
  vol.cal_max = gMax > gMin ? gMax : 255;
  vol.colormap = "blue";
  vol.trustCalMinMax = false;
  vol.ignoreZeroVoxels = false;
  if ("alphaThreshold" in vol) vol.alphaThreshold = false;
}

function applyMaskLayerSettings(nv) {
  configureBinaryMaskVolume(nv?.volumes?.[1]);
  if (typeof nv?.updateGLVolume === "function") {
    nv.updateGLVolume();
  }
}

function applyMaskOnlySettings(nv) {
  configureBinaryMaskVolume(nv?.volumes?.[0]);
  if (typeof nv?.updateGLVolume === "function") {
    nv.updateGLVolume();
  }
}

function applyDisplayMode() {
  for (const nv of mprViewers()) {
    if (!nv?.volumes?.length) continue;
    if (nv.volumes.length < 2) {
      nv.setOpacity(0, 1);
      nv.drawScene();
      continue;
    }
    if (displayMode.value === "ct") {
      nv.setOpacity(0, 1);
      nv.setOpacity(1, 0);
    } else if (displayMode.value === "mask") {
      nv.setOpacity(0, 0);
      nv.setOpacity(1, 1);
    } else {
      nv.setOpacity(0, 1);
      nv.setOpacity(1, maskOpacity.value);
    }
    applyMaskLayerSettings(nv);
    nv.drawScene();
  }
}

function onOpacityInput(e) {
  maskOpacity.value = Number(e.target.value) / 100;
  if (displayMode.value === "overlay") {
    applyDisplayMode();
  }
}

function reset3dView() {
  if (!nvRender) return;
  nvRender.setRenderAzimuthElevation(DEFAULT_AZIMUTH, DEFAULT_ELEVATION);
  nvRender.drawScene();
}

function yieldFrame() {
  return new Promise((resolve) => {
    requestAnimationFrame(() => resolve());
  });
}

function deferPause(ms = 250) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

function isAborted(seq) {
  return seq !== loadSeq;
}

async function ensureVolumeUrl(url, label) {
  if (!url) {
    throw new Error(`${label}地址为空`);
  }
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`${label}下载失败 (${res.status})`);
  }
}

function ctVolumes(ctUrl) {
  return [
    {
      url: ctUrl,
      name: props.ctFilename,
      colormap: "gray",
      opacity: 1,
    },
  ];
}

function mprVolumes(ctUrl, maskUrl) {
  const list = ctVolumes(ctUrl);
  if (maskUrl) {
    list.push(maskVolumeConfig(maskUrl, maskOpacity.value));
  }
  return list;
}

function ensureMaskLayer(nv, maskUrl) {
  if (!maskUrl || nv?.volumes?.length >= 2) return true;
  error.value = "掩码层加载失败，请确认后端已返回 download_url 且文件可下载";
  return false;
}

function applyWindowing(nv) {
  const ct = nv?.volumes?.[0];
  if (!ct) return;
  ct.cal_min = ct.robust_min;
  ct.cal_max = ct.robust_max;
  if (typeof nv.updateGLVolume === "function") {
    nv.updateGLVolume();
  }
}

function focusFirstMaskSlice() {
  const slices = (props.maskSlices || []).filter(
    (z) => z >= 0 && z < dims.value.z,
  );
  if (!slices.length) return;
  sliceZ.value = slices[0];
  if (linkedSlices.value) {
    unifiedSlice.value = Math.min(sliceX.value, sliceY.value, sliceZ.value);
  }
  applyCrosshair();
}

async function attachAll() {
  await nvAxial.attachToCanvas(axialRef.value);
  await nvCoronal.attachToCanvas(coronalRef.value);
  await nvSagittal.attachToCanvas(sagittalRef.value);
  await nvRender.attachToCanvas(renderRef.value);
  await nvMask.attachToCanvas(maskRef.value);

  nvAxial.setSliceType(nvAxial.sliceTypeAxial);
  nvCoronal.setSliceType(nvCoronal.sliceTypeCoronal);
  nvSagittal.setSliceType(nvSagittal.sliceTypeSagittal);
  nvRender.setSliceType(nvRender.sliceTypeRender);
  nvMask.setSliceType(nvMask.sliceTypeAxial);

  [nvAxial, nvCoronal, nvSagittal].forEach(hookLocationSync);
  reset3dView();
}

function initSliceState() {
  dims.value = readDims(nvAxial);
  sliceX.value = Math.floor((dims.value.x - 1) / 2);
  sliceY.value = Math.floor((dims.value.y - 1) / 2);
  sliceZ.value = Math.floor((dims.value.z - 1) / 2);
  unifiedSlice.value = Math.min(sliceX.value, sliceY.value, sliceZ.value);
}

async function loadSecondaryViews(seq, ctUrl) {
  if (isAborted(seq)) return;
  secondaryLoading.value = true;
  loadStage.value = "后台加载冠状 / 矢状（仅 CT）…";
  try {
    const ctOnly = ctVolumes(ctUrl);
    await deferPause();
    if (isAborted(seq)) return;
    await nvCoronal.loadVolumes(ctOnly);
    applyWindowing(nvCoronal);
    await deferPause();
    if (isAborted(seq)) return;
    await nvSagittal.loadVolumes(ctOnly);
    applyWindowing(nvSagittal);
    applyCrosshair();
    nvCoronal.drawScene();
    nvSagittal.drawScene();
    loadProgress.value = 100;
    loadStage.value = "";
  } catch (err) {
    console.error(err);
    error.value = `辅视图加载失败: ${err.message || err}`;
  } finally {
    secondaryLoading.value = false;
  }
}

async function applyMaskToMprViews(mpr) {
  await nvAxial.loadVolumes(mpr);
  applyWindowing(nvAxial);
  await deferPause(80);
  await nvCoronal.loadVolumes(mpr);
  applyWindowing(nvCoronal);
  await deferPause(80);
  await nvSagittal.loadVolumes(mpr);
  applyWindowing(nvSagittal);
  if (
    !ensureMaskLayer(nvAxial, pendingMaskUrl) ||
    !ensureMaskLayer(nvCoronal, pendingMaskUrl) ||
    !ensureMaskLayer(nvSagittal, pendingMaskUrl)
  ) {
    throw new Error(error.value);
  }
  applyMaskLayerSettings(nvAxial);
  applyMaskLayerSettings(nvCoronal);
  applyMaskLayerSettings(nvSagittal);
  initSliceState();
  focusFirstMaskSlice();
  displayMode.value = "overlay";
  applyDisplayMode();
  nvAxial.drawScene();
  nvCoronal.drawScene();
  nvSagittal.drawScene();
}

async function loadMaskOverlay() {
  if (!pendingMaskUrl || !pendingCtUrl || maskOverlayLoading.value || maskOverlayReady.value) {
    return;
  }
  maskOverlayLoading.value = true;
  loadStage.value = "叠加 AI 掩码到三视图（请稍候）…";
  expandLoadedKey = "";
  try {
    await ensureVolumeUrl(pendingMaskUrl, "掩码");
    await deferPause(80);
    await applyMaskToMprViews(mprVolumes(pendingCtUrl, pendingMaskUrl));
    maskOverlayReady.value = true;
  } catch (err) {
    console.error(err);
    error.value = `掩码叠加失败: ${err.message || err}`;
  } finally {
    maskOverlayLoading.value = false;
    loadStage.value = "";
  }
}

function syncExpandFromSource(plane) {
  if (!nvExpand) return;
  const srcNv = plane === "axial" ? nvAxial : plane === "coronal" ? nvCoronal : nvSagittal;
  if (srcNv?.volumes?.length) {
    nvExpand.scene.crosshairPos = [...srcNv.scene.crosshairPos];
  }
  if (plane === "axial") nvExpand.setSliceType(nvExpand.sliceTypeAxial);
  else if (plane === "coronal") nvExpand.setSliceType(nvExpand.sliceTypeCoronal);
  else nvExpand.setSliceType(nvExpand.sliceTypeSagittal);
  if (maskOverlayReady.value && pendingMaskUrl) {
    applyMaskLayerSettings(nvExpand);
    if (displayMode.value === "ct") {
      nvExpand.setOpacity(0, 1);
      nvExpand.setOpacity(1, 0);
    } else if (displayMode.value === "mask") {
      nvExpand.setOpacity(0, 0);
      nvExpand.setOpacity(1, 1);
    } else {
      nvExpand.setOpacity(0, 1);
      nvExpand.setOpacity(1, maskOpacity.value);
    }
  }
  nvExpand.drawScene();
}

function onExpandSliderInput() {
  applyCrosshair();
  if (!nvExpand || !expandedPlane.value) return;
  const { x, y, z } = dims.value;
  if (x < 1 || y < 1 || z < 1) return;
  nvExpand.scene.crosshairPos = [
    (sliceX.value + 0.5) / x,
    (sliceY.value + 0.5) / y,
    (sliceZ.value + 0.5) / z,
  ];
  nvExpand.drawScene();
}

async function openExpandedView(plane) {
  if (!ready.value || !pendingCtUrl) return;
  expandedPlane.value = plane;
  await nextTick();
  await deferPause(50);
  const canvas = expandCanvasRef.value;
  if (!canvas) return;

  nvExpand = makeNv();
  await nvExpand.attachToCanvas(canvas);
  nvExpand.onLocationChange = (data) => {
    if (!expandedPlane.value) return;
    const tgt = expandedPlane.value === "axial"
      ? nvAxial
      : expandedPlane.value === "coronal"
        ? nvCoronal
        : nvSagittal;
    if (!tgt?.volumes?.length) return;
    syncing = true;
    tgt.scene.crosshairPos = [...nvExpand.scene.crosshairPos];
    tgt.drawScene();
    syncAllCrosshairsFrom(tgt);
    updateSlidersFromLocation(data);
    syncing = false;
  };

  const vols = maskOverlayReady.value && pendingMaskUrl
    ? mprVolumes(pendingCtUrl, pendingMaskUrl)
    : ctVolumes(pendingCtUrl);
  await nvExpand.loadVolumes(vols);
  applyWindowing(nvExpand);
  expandLoadedKey = `${pendingCtUrl}|${pendingMaskUrl}|${maskOverlayReady.value}|expand`;

  syncExpandFromSource(plane);
  if (typeof nvExpand.resizeListener === "function") {
    nvExpand.resizeListener();
  } else {
    nvExpand.drawScene();
  }
}

function closeExpandedView() {
  if (expandedPlane.value && nvExpand) {
    const plane = expandedPlane.value;
    const tgt = plane === "axial" ? nvAxial : plane === "coronal" ? nvCoronal : nvSagittal;
    if (tgt?.volumes?.length) {
      syncing = true;
      tgt.scene.crosshairPos = [...nvExpand.scene.crosshairPos];
      syncAllCrosshairsFrom(tgt);
      syncing = false;
    }
  }
  expandedPlane.value = null;
  nvExpand = null;
  expandLoadedKey = "";
}

async function loadMaskSlicePreview() {
  if (!pendingMaskUrl || maskSliceLoading.value || maskSliceReady.value) return;
  maskSliceLoading.value = true;
  try {
    await deferPause(100);
    await nvMask.loadVolumes([maskVolumeConfig(pendingMaskUrl, 1)]);
    applyMaskOnlySettings(nvMask);
    nvMask.drawScene();
    maskSliceReady.value = true;
  } catch (err) {
    console.error(err);
    error.value = `掩码预览加载失败: ${err.message || err}`;
  } finally {
    maskSliceLoading.value = false;
  }
}

async function loadRender3d() {
  if (!pendingMaskUrl || render3dLoading.value || render3dReady.value) return;
  render3dLoading.value = true;
  loadStage.value = "构建三维表面…";
  try {
    await deferPause(50);
    await nvRender.loadVolumes([maskVolumeConfig(pendingMaskUrl, 0.85)]);
    applyMaskOnlySettings(nvRender);
    reset3dView();
    render3dReady.value = true;
  } catch (err) {
    console.error(err);
    error.value = `三维表面加载失败: ${err.message || err}`;
  } finally {
    render3dLoading.value = false;
    loadStage.value = "";
  }
}

async function loadVolumes(ctUrl, maskUrl) {
  if (!ctUrl || !nvAxial) return;

  const seq = ++loadSeq;
  pendingCtUrl = ctUrl;
  pendingMaskUrl = maskUrl || "";
  expandLoadedKey = "";
  expandedPlane.value = null;
  render3dReady.value = false;
  render3dLoading.value = false;
  maskSliceReady.value = false;
  maskSliceLoading.value = false;
  maskOverlayReady.value = false;
  maskOverlayLoading.value = false;
  loading.value = true;
  secondaryLoading.value = false;
  showViews.value = false;
  loadProgress.value = 0;
  loadStage.value = "校验预览数据…";
  error.value = "";
  ready.value = false;

  try {
    await ensureVolumeUrl(ctUrl, "CT 预览");
    if (isAborted(seq)) return;

    loadStage.value = "加载 CT 轴位（仅灰度，较快）…";
    loadProgress.value = 25;
    await nvAxial.loadVolumes(ctVolumes(ctUrl));
    if (isAborted(seq)) return;

    applyWindowing(nvAxial);
    initSliceState();
    ready.value = true;
    displayMode.value = "ct";
    applyCrosshair();
    nvAxial.drawScene();
    showViews.value = true;
    loading.value = false;
    loadProgress.value = 55;
    loadStage.value = maskUrl
      ? "CT 已显示；掩码需点击「叠加 AI 掩码」（避免页面卡死）"
      : "轴位已就绪，后台加载其余视图…";

    loadSecondaryViews(seq, ctUrl);
  } catch (err) {
    console.error(err);
    error.value = `影像加载失败: ${err.message || err}`;
    showViews.value = false;
    loading.value = false;
  }
}

onMounted(async () => {
  nvAxial = makeNv();
  nvCoronal = makeNv();
  nvSagittal = makeNv();
  nvRender = makeNv3d();
  nvMask = makeNv();
  await attachAll();
  if (props.ctUrl) {
    await loadVolumes(props.ctUrl, props.maskUrl || "");
  }
});

watch(
  () => [props.ctUrl, props.maskUrl],
  ([ct, mask], prev) => {
    if (!ct || !nvAxial) return;
    const prevCt = prev?.[0] || "";
    const prevMask = prev?.[1] || "";
    if (ct === prevCt && (mask || "") === (prevMask || "")) return;
    loadVolumes(ct, mask || "");
  },
);

onBeforeUnmount(() => {
  nvAxial = nvCoronal = nvSagittal = nvRender = nvMask = nvExpand = null;
});

function canvasToDataUrl(canvas) {
  if (!canvas || !canvas.width) return null
  try {
    return canvas.toDataURL('image/png')
  } catch {
    return null
  }
}

function captureReportSnapshots() {
  return {
    axial: canvasToDataUrl(axialRef.value),
    coronal: canvasToDataUrl(coronalRef.value),
    sagittal: canvasToDataUrl(sagittalRef.value),
    meta: {
      sliceX: sliceX.value,
      sliceY: sliceY.value,
      sliceZ: sliceZ.value,
      maskOverlayReady: maskOverlayReady.value,
      capturedAt: new Date().toISOString(),
    },
  }
}

defineExpose({ captureReportSnapshots })
</script>

<style scoped>
.mpr-root {
  position: relative;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  height: 100%;
}

.mpr-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  min-height: 0;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.2s ease;
}

.mpr-content.visible {
  opacity: 1;
  pointer-events: auto;
  flex: 1;
  height: 100%;
}

.mpr-root.is-loading .mpr-content {
  opacity: 0;
  pointer-events: none;
}

.load-overlay {
  position: absolute;
  inset: 0;
  z-index: 10;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: rgba(8, 12, 16, 0.92);
  border: 1px solid #243040;
  border-radius: 10px;
}

.load-title {
  margin: 0;
  font-size: 14px;
  color: #c8d8e8;
}

.progress-track {
  width: min(360px, 80%);
  height: 8px;
  background: #243040;
  border-radius: 4px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #2a7bd6, #3d9fef);
  transition: width 0.15s ease;
}

.load-pct {
  font-size: 12px;
  color: #8aa0b4;
}

.load-sub.banner {
  margin: 0 0 8px;
  padding: 6px 10px;
  font-size: 12px;
  color: #9eb4c8;
  background: #182430;
  border-radius: 6px;
  border: 1px solid #2a4058;
}

.mpr-row {
  display: grid;
  gap: 8px;
}

.mpr-row.top {
  grid-template-columns: repeat(3, 1fr);
  flex: 3 1 0;
  min-height: 320px;
}

.mpr-row.bottom {
  grid-template-columns: 1fr 1fr;
  flex: 2 1 0;
  min-height: 220px;
}

.unified-slider {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  font-size: 11px;
  color: #8aa0b4;
  background: #101820;
  border: 1px solid #243040;
  border-radius: 8px;
}

.unified-slider input[type="range"] {
  flex: 1;
  max-width: 360px;
}

.unified-hint {
  color: #6a8094;
  font-size: 10px;
}

.view-cell {
  position: relative;
  display: flex;
  flex-direction: column;
  background: #0a0e12;
  border: 1px solid #243040;
  border-radius: 8px;
  overflow: hidden;
  min-height: 0;
}

.view-cell.expandable {
  cursor: zoom-in;
}

.view-cell.expandable:hover {
  border-color: #3d6f9a;
}

.zoom-hint {
  font-size: 10px;
  color: #6a90b0;
}

.view-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 10px;
  background: #141c24;
  border-bottom: 1px solid #243040;
}

.view-title {
  font-size: 12px;
  font-weight: 600;
  color: #d0dce8;
}

.view-sub {
  font-size: 10px;
  color: #7a90a4;
  flex: 1;
  text-align: right;
}

.mini-btn {
  padding: 2px 8px;
  border: 1px solid #3a5060;
  border-radius: 4px;
  background: #1a2834;
  color: #a8c0d4;
  font-size: 10px;
  cursor: pointer;
}

.mini-btn:hover {
  background: #243444;
}

.view-canvas {
  flex: 1;
  width: 100%;
  min-height: 200px;
  display: block;
}

.view-canvas.short {
  min-height: 180px;
}

.canvas-shell {
  position: relative;
  flex: 1;
  min-height: 180px;
}

.canvas-shell.short {
  min-height: 180px;
}

.canvas-hidden {
  visibility: hidden;
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.canvas-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px;
  margin: 0;
  text-align: center;
  font-size: 12px;
  color: #8aa0b4;
}

.slider-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  font-size: 11px;
  color: #8aa0b4;
  background: #101820;
  border-top: 1px solid #243040;
}

.slider-row input[type="range"] {
  flex: 1;
}

.panel-tip {
  position: absolute;
  left: 50%;
  top: 58%;
  transform: translate(-50%, -50%);
  margin: 0;
  font-size: 12px;
  color: #6a8094;
  text-align: center;
  pointer-events: none;
  white-space: pre-line;
}

.mpr-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 8px 10px;
  font-size: 11px;
  color: #7a90a4;
  background: #101820;
  border: 1px solid #243040;
  border-radius: 8px;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #b8c8d8;
  cursor: pointer;
  user-select: none;
}

.hint {
  color: #6a8094;
}

.mask-hint {
  color: #5eb8ff;
  font-size: 11px;
}

.display-modes {
  display: flex;
  gap: 4px;
}

.mode-btn {
  padding: 4px 10px;
  border: 1px solid #304050;
  border-radius: 6px;
  background: #182028;
  color: #a8b8c8;
  font-size: 11px;
  cursor: pointer;
}

.mode-btn.active {
  background: #1e4a6e;
  border-color: #3d8fd1;
  color: #fff;
}

.mode-btn.accent {
  border-color: #3d8fd1;
  color: #d8ecff;
}

.mode-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.opacity {
  display: flex;
  align-items: center;
  gap: 8px;
}

.opacity input[type="range"] {
  width: 100px;
}

.loading-msg.error {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  margin: 0;
  padding: 10px 16px;
  border-radius: 8px;
  background: rgba(10, 16, 22, 0.9);
  font-size: 13px;
  color: #ff9a9a;
  z-index: 11;
}

.expand-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(4, 8, 12, 0.88);
}

.expand-panel {
  width: min(96vw, 1200px);
  height: min(88vh, 900px);
  display: flex;
  flex-direction: column;
  background: #0d1218;
  border: 1px solid #304050;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.45);
}

.expand-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: #141c24;
  border-bottom: 1px solid #243040;
  font-size: 14px;
  color: #d8e8f4;
}

.expand-canvas {
  flex: 1;
  width: 100%;
  min-height: 0;
  display: block;
}

.expand-slider {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  font-size: 12px;
  color: #8aa0b4;
  background: #101820;
  border-top: 1px solid #243040;
}

.expand-slider input[type="range"] {
  flex: 1;
}

.expand-hint {
  margin: 0;
  padding: 8px 14px;
  font-size: 12px;
  color: #8aa0b4;
  background: #101820;
  border-top: 1px solid #243040;
}

@media (max-width: 1100px) {
  .mpr-row.top {
    grid-template-columns: 1fr;
  }
}
</style>
